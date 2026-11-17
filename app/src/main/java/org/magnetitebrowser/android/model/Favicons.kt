package org.magnetitebrowser.android.model

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.scale
import androidx.core.net.toUri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.util.concurrent.Executors

/**
 * Site icons, kept on this device. One is saved when a page of the site was
 * loaded here (the web engine fetched it for the page anyway), and replaced
 * after `KEEP` at the earliest; a row never makes the app contact a site.
 * Private pages leave none.
 */
object Favicons {
    /** Goes up when an icon was saved, so rows showing a letter look again. */
    var saved by mutableIntStateOf(0)
        private set
    private val loaded = HashMap<String, ImageBitmap?>()
    private lateinit var dir: File
    private val disk = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /** How long an icon is used before a newer one replaces it. */
    private const val KEEP = 7 * 24 * 60 * 60 * 1000L
    private const val SIDE = 128

    fun start(context: Context) {
        dir = File(context.cacheDir, "favicons")
    }

    /**
     * The file name for a page's site: its host in lowercase without `www.`,
     * as on the desktop.
     */
    fun key(page: String): String? {
        val uri = page.toUri()
        val host = uri.host?.lowercase()?.removePrefix("www.").orEmpty()
        val safe = host.all { it in 'a'..'z' || it in '0'..'9' || it == '.' || it == '-' }
        if (host.isEmpty() || !safe || ".." in host) return null
        return if (uri.port >= 0) "${host}_${uri.port}" else host
    }

    /** The icon of `page`'s site, if one is kept. */
    fun image(page: String): ImageBitmap? {
        saved // Read, so the caller draws again when an icon was saved.
        val key = key(page) ?: return null
        return loaded.getOrPut(key) { BitmapFactory.decodeFile(file(key).path)?.asImageBitmap() }
    }

    private fun file(key: String) = File(dir, "$key.png")

    /** The engine has the icon of `page`, a page it loaded: keep it unless a recent one is kept. */
    fun received(page: String, icon: Bitmap) {
        if (page.toUri().scheme != "https" || icon.width < 16) return
        val key = key(page) ?: return
        val file = file(key)
        if (file.exists() && System.currentTimeMillis() - file.lastModified() < KEEP) return
        val small = if (maxOf(icon.width, icon.height) > SIDE) icon.scale(SIDE, SIDE) else icon
        val image = small.asImageBitmap()
        disk.execute {
            dir.mkdirs()
            val temp = File(dir, "$key.part")
            val written = runCatching {
                temp.outputStream().use { small.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }.getOrDefault(false)
            if (written && temp.renameTo(file)) {
                main.post {
                    loaded[key] = image
                    saved++
                }
            }
        }
    }
}
