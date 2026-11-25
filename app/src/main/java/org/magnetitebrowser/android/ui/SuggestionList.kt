package org.magnetitebrowser.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.magnetitebrowser.android.R
import org.magnetitebrowser.android.model.Address
import org.magnetitebrowser.android.model.Browser
import org.magnetitebrowser.android.model.Suggestion
import org.magnetitebrowser.android.model.suggestions

/**
 * What the address field offers while typing `text` (`Browser.suggestions`),
 * on a floating surface; nothing while nothing matches. `chosen` runs once a
 * row was picked. As tall as its rows, at most as `modifier` allows.
 */
@Composable
fun SuggestionList(browser: Browser, text: String, chosen: () -> Unit, modifier: Modifier = Modifier) {
    val rows = browser.suggestions(text)
    if (rows.isEmpty()) return

    fun pick(row: Suggestion) {
        when (row) {
            is Suggestion.Go -> browser.load(row.url)
            is Suggestion.Search -> browser.load(row.url)
            is Suggestion.Open -> browser.show(row.tab)
            is Suggestion.Chain -> {
                browser.show(row.space)
                browser.open(row.page)
            }
        }
        chosen()
    }

    Floating(modifier, RoundedCornerShape(26.dp)) {
        LazyColumn(contentPadding = PaddingValues(vertical = 6.dp)) {
            items(rows, key = Suggestion::key) { row ->
                Row(
                    Modifier.fillMaxWidth().height(58.dp).clickable { pick(row) }.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    when (row) {
                        is Suggestion.Go -> {
                            Symbol(painterResource(R.drawable.ic_space_globe))
                            Lines(text.trim(), "Open ${Address.site(row.url)}")
                        }
                        is Suggestion.Search -> {
                            Symbol(rememberVectorPainter(Icons.Default.Search))
                            Lines("“${text.trim()}”", "Search with ${Address.engine(browser.searchTemplate)}")
                        }
                        is Suggestion.Open -> {
                            SiteMark(row.tab.title, row.tab.url, 30.dp)
                            Lines(row.tab.label, "${Address.site(row.tab.url)} · Open here")
                        }
                        is Suggestion.Chain -> {
                            SiteMark(row.page.title, row.page.url, 30.dp)
                            val site = Address.site(row.page.url)
                            Lines(row.page.title.ifEmpty { site }, "$site · ${row.space.title}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Symbol(icon: Painter) {
    Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Lines(title: String, detail: String) {
    Column {
        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            detail, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
