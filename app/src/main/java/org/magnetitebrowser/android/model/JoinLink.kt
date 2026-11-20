package org.magnetitebrowser.android.model

import androidx.core.net.toUri

/**
 * What the QR code beside the sync code on a computer holds: the link
 * `magnetite://sync?server=…&words=…` with the chain's server and its 24
 * words (the browser's `docs/spec/sync.md`).
 */
data class JoinLink(val server: String, val words: String) {
    companion object {
        /** `null` when [text] is not such a link. */
        fun parse(text: String): JoinLink? {
            val link = text.toUri()
            if (link.scheme != "magnetite" || link.host != "sync") return null
            val server = link.getQueryParameter("server").orEmpty()
            val words = link.getQueryParameter("words").orEmpty()
            return if (server.isEmpty() || words.isEmpty()) null else JoinLink(server, words)
        }
    }
}
