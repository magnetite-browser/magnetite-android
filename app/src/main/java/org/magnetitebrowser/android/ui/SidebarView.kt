package org.magnetitebrowser.android.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.magnetitebrowser.android.R
import org.magnetitebrowser.android.model.Address
import org.magnetitebrowser.android.model.Browser
import org.magnetitebrowser.android.model.Favicons
import org.magnetitebrowser.android.model.Tab
import uniffi.magnetite_mobile_core.Page
import uniffi.magnetite_mobile_core.RowItem
import uniffi.magnetite_mobile_core.Space
import kotlin.math.abs
import uniffi.magnetite_mobile_core.Row as PinnedRow

/**
 * The sidebar of the space in front: favorites, the pinned tree, the tabs
 * open here, and the Today tabs of the other devices. The start page, a
 * sheet over a page (`inSheet`), and on a wide screen the column beside it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SidebarView(browser: Browser, inSheet: Boolean, onSettings: () -> Unit, onJoin: () -> Unit) {
    val space = browser.space
    /** Folders folded shut, by id. Open is the usual state. */
    var folded by rememberSaveable { mutableStateOf(setOf<String>()) }

    val list: @Composable () -> Unit = {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        space.title, Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings") }
                }
            }
            if (browser.spaces.size > 1) item { Spaces(browser) }
            if (space.favorites.isNotEmpty()) item { Favorites(space.favorites, browser::open) }
            if (space.pinned.isNotEmpty()) {
                section("Pinned")
                items(visible(space.pinned, folded)) { row ->
                    Box(Modifier.padding(start = 18.dp * minOf(row.depth.toInt(), 6))) {
                        when (val item = row.item) {
                            is RowItem.Folder -> FolderRow(item.title, item.smart, open = item.id !in folded) {
                                folded = if (item.id in folded) folded - item.id else folded + item.id
                            }
                            is RowItem.Page -> PageRow(item.page.title, item.page.url, onOpen = { browser.open(item.page) })
                        }
                    }
                }
            }
            here(browser, space)
            if (browser.sent.isNotEmpty()) sent(browser)
            if (space.today.isNotEmpty()) {
                section("Today on your other devices")
                items(space.today, key = { "today-" + it.id }) { page ->
                    PageRow(page.title, page.url, onOpen = { browser.open(page) })
                }
            }
            if (!browser.sync.joined) item { Invitation(onJoin) }
        }
    }
    // In a sheet, pulling down closes the sheet.
    if (inSheet) {
        list()
    } else {
        PullToRefreshBox(isRefreshing = browser.sync.busy, onRefresh = browser::syncSoon) { list() }
    }
}

/** The rows without those inside a folder that is folded shut. */
private fun visible(rows: List<PinnedRow>, folded: Set<String>): List<PinnedRow> {
    var shut: UInt? = null
    return rows.filter { row ->
        shut?.let { depth ->
            if (row.depth > depth) return@filter false
            shut = null
        }
        val item = row.item
        if (item is RowItem.Folder && item.id in folded) shut = row.depth
        true
    }
}

private fun LazyListScope.section(title: String) = item {
    Text(
        title.uppercase(), Modifier.padding(top = 22.dp, bottom = 6.dp),
        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Note(text: String) {
    Text(
        text, Modifier.padding(vertical = 6.dp),
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Spaces(browser: Browser) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(browser.spaces, key = Space::id) { space ->
            FilterChip(
                selected = space.id == browser.space.id,
                modifier = Modifier.testTag("space." + space.id),
                onClick = { browser.show(space) },
                label = { Text(space.title) },
                leadingIcon = {
                    val emoji = space.emoji
                    if (emoji != null) {
                        Text(emoji)
                    } else {
                        Box(Modifier.size(10.dp).background(space.tint ?: MaterialTheme.colorScheme.primary, CircleShape))
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Favorites(pages: List<Page>, open: (Page) -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth().padding(top = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        for (page in pages) {
            Column(
                Modifier.width(76.dp).clip(RoundedCornerShape(12.dp)).clickable { open(page) }.padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SiteMark(page.title, page.url, 52.dp)
                Text(
                    page.title.ifEmpty { Address.site(page.url) }, Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun LazyListScope.here(browser: Browser, space: Space) {
    val tabs = browser.tabs(space)
    section("Open here")
    items(tabs, key = Tab::id) { tab ->
        PageRow(
            tab.label, tab.url, marked = tab.id == browser.front,
            onOpen = { browser.show(tab) }, onClose = { browser.close(tab) },
        )
    }
    if (tabs.isEmpty()) {
        item { Note("No tabs open in this space. Type an address below, or pick a page above.") }
    } else if (tabs.size > 1) {
        item {
            TextButton(onClick = { browser.closeAll(space) }) {
                Text("Close All", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun LazyListScope.sent(browser: Browser) {
    section("Sent to your desktop")
    items(browser.sent, key = { "sent-" + it.id }) { tab ->
        PageRow(tab.label, tab.url, onOpen = { browser.takeBack(tab) }, onClose = { browser.forgetSent(tab) })
    }
    item { Note("These wait for Magnetite on a computer to open them. Tap one to open it here again.") }
}

@Composable
private fun Invitation(onJoin: () -> Unit) {
    Column(
        Modifier
            .padding(top = 22.dp)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Your spaces from the desktop", style = MaterialTheme.typography.titleMedium)
        Text(
            "Join the sync chain of Magnetite on your computer to see its spaces, favorites and pinned tabs here.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onJoin) { Text("Join a Sync Chain") }
    }
}

/**
 * A page in a list: its mark, title and site; with `onClose`, a button to
 * close it.
 */
@Composable
private fun PageRow(title: String, url: String, marked: Boolean = false, onOpen: () -> Unit, onClose: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onOpen).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SiteMark(title, url, 30.dp)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                title.ifEmpty { Address.site(url) }, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = if (marked) FontWeight.SemiBold else FontWeight.Normal,
            )
            Text(
                Address.site(url), maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onClose != null) {
            IconButton(onClick = onClose, Modifier.size(36.dp)) {
                Icon(Icons.Default.Close, "Close", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun FolderRow(title: String, smart: Boolean, open: Boolean, toggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = toggle).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(30.dp), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_folder), null, tint = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (smart) {
                Text(
                    "Fills itself on the desktop",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!smart) {
            Icon(
                if (open) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                if (open) "Open" else "Folded", tint = MaterialTheme.colorScheme.outline,
            )
        }
        Spacer(Modifier.width(6.dp))
    }
}

/**
 * A site's mark: its icon if one is kept (`Favicons`), else the first
 * letter of its name on a colour of its own.
 */
@Composable
fun SiteMark(title: String, url: String, size: Dp) {
    val shape = RoundedCornerShape(size * 0.26f)
    val icon = Favicons.image(url)
    if (icon != null) {
        Image(
            icon, null,
            // Icons are drawn for a light ground.
            Modifier
                .size(size)
                .background(Color.White, shape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                .padding(size * 0.14f),
        )
    } else {
        val name = Address.site(url)
        val letter = (title.firstOrNull() ?: name.firstOrNull())?.uppercase() ?: "?"
        // The same site gets the same colour at every start, and the one
        // the iOS app gives it.
        val hue = abs(name.codePoints().asLongStream().reduce(5381L) { sum, char -> sum * 33 + char } % 360)
        Box(Modifier.size(size).background(Color.hsv(hue.toFloat(), 0.5f, 0.7f), shape), contentAlignment = Alignment.Center) {
            Text(letter, color = Color.White, fontSize = (size.value * 0.45f).sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
