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
import uniffi.magnetite_mobile_core.Space
import uniffi.magnetite_mobile_core.SyncException
import uniffi.magnetite_mobile_core.cached
import uniffi.magnetite_mobile_core.addFavorite
import uniffi.magnetite_mobile_core.exchange
import uniffi.magnetite_mobile_core.forget
import uniffi.magnetite_mobile_core.join as joinChain
import uniffi.magnetite_mobile_core.newSpace
import uniffi.magnetite_mobile_core.pin
import uniffi.magnetite_mobile_core.refresh
import uniffi.magnetite_mobile_core.remove
import uniffi.magnetite_mobile_core.rename
import java.io.File
import java.util.UUID

/**
 * This device's place in a sync chain. It reads the chain's sidebar and
 * never writes to it (see the core's notes on why); its changes to the
 * sidebar go to a desktop of the chain, which makes them, and show at once
 * marked `pending` until then.
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
     * Tell the chain which `tabs` are open here, and the changes to the
     * sidebar no desktop made yet. Returns the ids of the tabs another device
     * took over; none when it could not be asked.
     */
    suspend fun exchange(tabs: List<DeviceTab>): List<String> {
        val server = server ?: return emptyList()
        val code = secret.read() ?: return emptyList()
        val device = device()
        return try {
            withContext(Dispatchers.IO) { exchange(server, code, device, tabs, dir) }
        } catch (e: Exception) {
            lastError = text(e)
            emptyList()
        }
    }

    // Changes to the sidebar

    /** Pin `url` last in `space`'s pinned tree. */
    suspend fun pin(url: String, title: String, space: Space) = change { pin(it, space.id, url, title) }

    /** Make `url` a favorite of `space`'s profile. */
    suspend fun addFavorite(url: String, title: String, space: Space) = change { addFavorite(it, space.id, url, title) }

    /** A new space after the others, with the desktop's default icon. */
    suspend fun newSpace(title: String) = change { newSpace(it, title, null) }

    /** Name the space, folder, pinned page or favorite `item` `title`. */
    suspend fun rename(item: String, title: String) = change { rename(it, item, title) }

    /** Unpin pinned page `item`, or remove favorite `item`. */
    suspend fun remove(item: String) = change { remove(it, item) }

    /** Keep a change for the desktop (`make` gets the core's folder) and show the sidebar with it. */
    private suspend fun change(make: (String) -> Sidebar?) {
        try {
            withContext(Dispatchers.IO) { make(dir) }?.let { sidebar = it }
        } catch (e: Exception) {
            lastError = text(e)
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
