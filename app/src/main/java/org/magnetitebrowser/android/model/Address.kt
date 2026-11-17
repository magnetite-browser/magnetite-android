package org.magnetitebrowser.android.model

import android.net.Uri
import androidx.core.net.toUri

/** What the address field's text means: an address, or a search. */
object Address {
    /**
     * The search engines Settings offers; `%s` stands for the search words.
     * The same list as on the desktop.
     */
    val engines = listOf(
        "DuckDuckGo" to "https://duckduckgo.com/?q=%s",
        "Startpage" to "https://www.startpage.com/do/search?q=%s",
        "Ecosia" to "https://www.ecosia.org/search?q=%s",
        "Brave Search" to "https://search.brave.com/search?q=%s",
        "Kagi" to "https://kagi.com/search?q=%s",
        "Google" to "https://www.google.com/search?q=%s",
    )

    /**
     * The page for `text`: the address when it is one (`example.org`,
     * `localhost:3000`, `https://…`), else a search for it with `template`.
     */
    fun url(text: String, template: String): String? {
        val text = text.trim()
        if (text.isEmpty()) return null
        if (isWeb(text)) return text
        if (text.none { it.isWhitespace() }) {
            val host = "https://$text".toUri().host.orEmpty()
            // Pages on this device rarely have https.
            if (host == "localhost") return "http://$text"
            if ("." in host) return "https://$text"
        }
        return template.replace("%s", Uri.encode(text))
    }

    /** Is `url` a page of the web, with a host? */
    fun isWeb(url: String): Boolean {
        val uri = url.toUri()
        return uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrEmpty()
    }

    /** The site's name for a row or the address field: the host without `www.`. */
    fun site(url: String): String = (url.toUri().host ?: url).removePrefix("www.")
}
