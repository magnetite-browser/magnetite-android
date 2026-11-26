package org.magnetitebrowser.android.ui

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import org.magnetitebrowser.android.model.Browser
import uniffi.magnetite_mobile_core.Archived

/** The most closed tabs listed, the newest: as many as the desktop's archive view lists. */
private const val LISTED = 300

/**
 * The tabs closed on the devices of the sync chain, newest first, with a
 * search over title and address. A tap brings a tab back: it is a Today tab
 * of its space again, on every device, and opens here. The × on its row
 * removes it from the archive for good.
 */
@Composable
fun ArchiveView(browser: Browser, onDone: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val words = query.trim()
    val closed = browser.sync.archive
        .filter { words.isEmpty() || it.title.contains(words, ignoreCase = true) || it.url.contains(words, ignoreCase = true) }
        .take(LISTED)

    Screen("Archive", onDone) {
        OutlinedTextField(
            value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
            singleLine = true, placeholder = { Text("Search Archive") },
        )
        when {
            closed.isNotEmpty() -> {}
            words.isNotEmpty() -> Hint("No archived tab matches “$words”.")
            !browser.sync.joined -> Hint("With sync on, the tabs you close on your devices show here.")
            else -> Hint("Tabs closed on your desktop, and pinned tabs and favorites you remove, show here.")
        }
        for (tab in closed) {
            ArchivedRow(
                tab,
                onOpen = {
                    // Back among the Today tabs of its space, for the whole
                    // chain, and open here.
                    browser.change { it.restore(tab.id) }
                    browser.open(tab.url)
                    onDone()
                },
                onRemove = { browser.change { it.forgetArchived(tab.id) } },
            )
        }
    }
}

@Composable
private fun ArchivedRow(tab: Archived, onOpen: () -> Unit, onRemove: () -> Unit) {
    val site = tab.url.toUri().host?.removePrefix("www.") ?: tab.url
    val closed = DateUtils.getRelativeTimeSpanString(tab.archivedAt * 1000).toString()
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SiteMark(tab.title, tab.url, 30.dp)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(tab.title.ifEmpty { site }, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(site, tab.space, closed).joinToString(" · "),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Close, "Remove from Archive", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
