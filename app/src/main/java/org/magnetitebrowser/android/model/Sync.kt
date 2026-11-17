package org.magnetitebrowser.android.model

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.core.content.edit
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uniffi.magnetite_mobile_core.Device
import uniffi.magnetite_mobile_core.DeviceTab
import uniffi.magnetite_mobile_core.Sidebar
import uniffi.magnetite_mobile_core.SyncException
import uniffi.magnetite_mobile_core.cached
import uniffi.magnetite_mobile_core.exchange
import uniffi.magnetite_mobile_core.forget
import uniffi.magnetite_mobile_core.join as joinChain
import uniffi.magnetite_mobile_core.refresh
import java.io.File
import java.util.UUID

/**
 * This device's place in a sync chain. It reads the chain's sidebar and
 * never writes to it (see the core's notes on why).
 */
class Sync(context: Context) {
    var sidebar by mutableStateOf<Sidebar?>(null)
        private set
    var server by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var lastError by mutableStateOf<String?>(null)
        private set
    /** When the chain was last read, in milliseconds since 1970. */
    var lastSync by mutableStateOf<Long?>(null)
        private set

    val joined get() = server != null

    val prefs = context.getSharedPreferences("magnetite", Context.MODE_PRIVATE)
    private val secret = SecretStore(prefs, "sync.secret")
    /** Where the core keeps the sidebar it last read. */
    private val dir = File(context.filesDir, "sync").path
    private val deviceName = Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
        ?: Build.MODEL

    init {
        val known = prefs.getString(SERVER, null)
        if (known != null && secret.read() != null) {
            server = known
            sidebar = runCatching { cached(dir) }.getOrNull()
        }
    }

    /** Join the chain of the 24 `words` on `server`. The caller reads it next. */
    suspend fun join(server: String, words: String) {
        busy = true
        lastError = null
        try {
            val code = withContext(Dispatchers.IO) { joinChain(server, words) }
            if (!secret.write(code)) throw IllegalStateException("the sync code could not be stored on this device")
            prefs.edit { putString(SERVER, server) }
            this.server = server
        } catch (e: Exception) {
            lastError = text(e)
        }
        busy = false
    }

    /**
     * Forget the code and the sidebar here. The chain and the other devices
     * stay as they are.
     */
    fun leave() {
        secret.remove()
        prefs.edit { remove(SERVER) }
        runCatching { forget(dir) }
        server = null
        sidebar = null
        lastError = null
        lastSync = null
    }

    suspend fun refresh() {
        val server = server ?: return
        val code = secret.read() ?: return
        if (busy) return
        busy = true
        try {
            withContext(Dispatchers.IO) { refresh(server, code, dir) }?.let { sidebar = it }
            lastError = null
            lastSync = System.currentTimeMillis()
        } catch (e: Exception) {
            lastError = text(e)
        }
        busy = false
    }

    /** Joined, and the last sync is a while ago. */
    val due get() = joined && (lastSync?.let { System.currentTimeMillis() - it > INTERVAL } ?: true)

    /** Whether the chain's other devices see the tabs open here. */
    var sharesTabs by mutableStateOf(prefs.getBoolean(SHARES_TABS, true))
        private set

    fun shareTabs(on: Boolean) {
        prefs.edit { putBoolean(SHARES_TABS, on) }
        sharesTabs = on
    }

    /** This install, as the chain's other devices see it. */
    private fun device(): Device {
        val id = prefs.getString(DEVICE, null)
            ?: UUID.randomUUID().toString().also { prefs.edit { putString(DEVICE, it) } }
        return Device(id, deviceName)
    }

    /**
     * Tell the chain which `tabs` are open here. Returns the ids of those
     * another device took over; none when it could not be asked.
     */
    suspend fun exchange(tabs: List<DeviceTab>): List<String> {
        val server = server ?: return emptyList()
        val code = secret.read() ?: return emptyList()
        val device = device()
        return try {
            withContext(Dispatchers.IO) { exchange(server, code, device, tabs) }
        } catch (e: Exception) {
            lastError = text(e)
            emptyList()
        }
    }

    private fun text(error: Exception): String {
        val message = (error as? SyncException)?.message ?: error.message ?: error.toString()
        return message.replaceFirstChar { it.uppercase() } + "."
    }

    private companion object {
        const val SERVER = "sync.server"
        const val SHARES_TABS = "sync.sharesTabs"
        const val DEVICE = "device.id"
        /** Asked again when the app comes to the front after this long. */
        const val INTERVAL = 5 * 60 * 1000L
    }
}
