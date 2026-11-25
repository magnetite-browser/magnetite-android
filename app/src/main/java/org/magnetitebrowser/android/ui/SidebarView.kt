package org.magnetitebrowser.android.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
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
 * The sidebar: a page per space, swiped through as on the desktop, with the
 * spaces' icons below to pick one. A space's page holds its favorites, the
 * pinned tree, the tabs open here and the Today tabs of the other devices.
 * The start page (its pages keep clear of `insets`), a sheet over a page
 * (`onClose` closes it), and on a wide screen the column beside it.
 *
 * In a chain, holding an item renames or removes it, and "+" beside the
 * spaces adds one. A desktop of the chain makes the change (`Sync`); until
 * then it shows with a mark that it waits.
 */
@Composable
fun SidebarView(
    browser: Browser,
    onSettings: () -> Unit,
    onJoin: () -> Unit,
    onClose: (() -> Unit)? = null,
    insets: WindowInsets = WindowInsets(0),
) {
    val spaces = browser.spaces
    val front = spaces.indexOfFirst { it.id == browser.space.id }.coerceAtLeast(0)
    val pager = rememberPagerState(front) { spaces.size }
    /** Folders folded shut, by id, in every space. Open is the usual state. */
    var folded by rememberSaveable { mutableStateOf(setOf<String>()) }
    /** What is being renamed, or named (a new space). */
    var naming by remember { mutableStateOf<Naming?>(null) }

    // A page swiped to is the space in front; a space picked otherwise is swiped to.
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { page ->
            browser.spaces.getOrNull(page)?.takeIf { it.id != browser.space.id }?.let(browser::show)
        }
    }
    LaunchedEffect(front) {
        if (front != pager.currentPage) pager.animateScrollToPage(front)
    }

    Column(Modifier.fillMaxSize().spaceWash(browser.space).windowInsetsPadding(insets)) {
        HorizontalPager(pager, Modifier.weight(1f), key = { spaces[it].id }) { index ->
            val space = spaces[index]
            MagnetiteTheme(space.tint) {
                SpacePage(
                    browser, space, folded,
                    fold = { id -> folded = if (id in folded) folded - id else folded + id },
                    rename = { id, title -> naming = Naming(id, title) },
                    onSettings, onJoin, onClose,
                )
            }
        }
        if (spaces.size > 1 || browser.sync.joined) {
            SpaceSwitcher(browser, add = { naming = Naming(null, "") }, Modifier.align(Alignment.CenterHorizontally).padding(6.dp))
        }
    }
    naming?.let { item -> NameDialog(browser, item) { naming = null } }
}

/** Something being named: item `id` to rename, with its name so far, or a new space (no `id`). */
private data class Naming(val id: String?, val title: String)

/** A name for `item`; the change goes to a desktop of the chain. */
@Composable
private fun NameDialog(browser: Browser, item: Naming, close: () -> Unit) {
    var name by remember { mutableStateOf(TextFieldValue(item.title, TextRange(0, item.title.length))) }
    val field = remember { FocusRequester() }
    LaunchedEffect(Unit) { field.requestFocus() }
    val title = name.text.trim()
    fun save() {
        close()
        val id = item.id
        if (id != null) {
            browser.change { it.rename(id, title) }
        } else {
            browser.change { sync ->
                sync.newSpace(title)
                // The new space is the last one: show it.
                browser.spaces.lastOrNull()?.let(browser::show)
            }
        }
    }
    AlertDialog(
        onDismissRequest = close,
        title = { Text(if (item.id == null) "New Space" else "Rename") },
        text = {
            // A dialog is a window of its own: its tags need saying again for UI tests.
            Column(Modifier.semantics { testTagsAsResourceId = true }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Your computer makes the change when it syncs.")
                OutlinedTextField(
                    name, { name = it }, Modifier.focusRequester(field).testTag("name"),
                    label = { Text("Name") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (title.isNotEmpty()) save() }),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = ::save, enabled = title.isNotEmpty()) { Text(if (item.id == null) "Add" else "Rename") }
        },
        dismissButton = { TextButton(onClick = close) { Text("Cancel") } },
    )
}

/** A soft wash of the space's colour from the top, as the desktop tints its sidebar. */
@Composable
private fun Modifier.spaceWash(space: Space): Modifier {
    val ground = MaterialTheme.colorScheme.background
    val tint = space.tint ?: MaterialTheme.colorScheme.primary
    val wash by animateColorAsState(tint.copy(alpha = if (ground.isDark) 0.22f else 0.12f), label = "wash")
    return background(ground).background(Brush.verticalGradient(0f to wash, 0.45f to Color.Transparent))
}

/** The spaces' icons, the one in front marked; a tap shows that space. In a chain, "+" adds a space (`add`). */
@Composable
private fun SpaceSwitcher(browser: Browser, add: () -> Unit, modifier: Modifier) {
    Floating(modifier) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (space in browser.spaces) {
                val front = space.id == browser.space.id
                val tint = space.tint ?: MaterialTheme.colorScheme.primary
                Box(
                    Modifier
                        .size(40.dp, 36.dp)
                        .clip(CircleShape)
                        .background(if (front) tint.copy(alpha = 0.18f) else Color.Transparent)
                        .selectable(selected = front, role = Role.Tab) { browser.show(space) }
                        .semantics { contentDescription = space.title }
                        .testTag("space." + space.id),
                    contentAlignment = Alignment.Center,
                ) {
                    SpaceIcon(space, 20.dp)
                }
            }
            if (browser.sync.joined && browser.sync.sidebar != null) {
                IconButton(onClick = add, Modifier.size(40.dp, 36.dp)) {
                    Icon(Icons.Default.Add, "New Space", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** One space's page of the sidebar. In a sheet (`onClose`), pulling down closes the sheet rather than syncing. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpacePage(
    browser: Browser,
    space: Space,
    folded: Set<String>,
    fold: (String) -> Unit,
    rename: (id: String, title: String) -> Unit,
    onSettings: () -> Unit,
    onJoin: () -> Unit,
    onClose: (() -> Unit)?,
) {
    // Items can be changed: the sidebar is a chain's, and one is here to
    // change (not before the first is read).
    val editable = browser.sync.joined && browser.sync.sidebar != null
    val renaming = rememberVectorPainter(Icons.Outlined.Edit)
    fun renameItem(id: String, title: String) = Edit("Rename…", renaming) { rename(id, title) }
    val unpinning = painterResource(R.drawable.ic_unpin)
    val removing = rememberVectorPainter(Icons.Outlined.Delete)

    val list = @Composable {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 24.dp)) {
            item {
                val edits = if (editable) listOf(Edit("Rename Space…", renaming) { rename(space.id, space.title) }) else emptyList()
                Header(space, edits, onSettings, onClose)
            }
            if (!browser.sync.joined) item { Invitation(onJoin) }
            // Pages open in this page's space: while the pager moves, the
            // space in front may still be the one before.
            if (space.favorites.isNotEmpty()) {
                item {
                    Favorites(space.favorites, { browser.open(it, space.id) }) { page ->
                        if (!editable) return@Favorites emptyList()
                        listOf(
                            renameItem(page.id, page.title),
                            Edit("Remove from Favorites", removing, danger = true) { browser.change { it.remove(page.id) } },
                        )
                    }
                }
            }
            if (space.pinned.isNotEmpty()) {
                section("Pinned")
                items(visible(space.pinned, folded)) { row ->
                    Box(Modifier.padding(start = 20.dp * minOf(row.depth.toInt(), 6))) {
                        when (val item = row.item) {
                            is RowItem.Folder -> FolderRow(
                                item.title, item.smart, open = item.id !in folded, item.pending,
                                if (editable) listOf(renameItem(item.id, item.title)) else emptyList(),
                            ) { fold(item.id) }
                            is RowItem.Page -> PageRow(
                                item.page.title, item.page.url, pending = item.page.pending,
                                edits = if (editable) {
                                    listOf(
                                        renameItem(item.page.id, item.page.title),
                                        Edit("Unpin", unpinning, danger = true) { browser.change { it.remove(item.page.id) } },
                                    )
                                } else {
                                    emptyList()
                                },
                                onOpen = { browser.open(item.page, space.id) },
                            )
                        }
                    }
                }
            }
            here(browser, space)
            val sent = browser.sent.filter { it.space == space.id }
            if (sent.isNotEmpty()) sent(browser, sent)
            if (space.today.isNotEmpty()) {
                section("On your other devices")
                items(space.today, key = { "today-" + it.id }) { page ->
                    PageRow(page.title, page.url, onOpen = { browser.open(page, space.id) })
                }
            }
        }
    }
    if (onClose != null) {
        list()
    } else {
        PullToRefreshBox(isRefreshing = browser.sync.busy, onRefresh = browser::syncSoon) { list() }
    }
}

/**
 * The space's icon and name, which holding offers `edits` for, and the way
 * to Settings (and out of the sheet).
 */
@Composable
private fun Header(space: Space, edits: List<Edit>, onSettings: () -> Unit, onClose: (() -> Unit)?) {
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Holdable(edits, Modifier.weight(1f)) { hold ->
            Row(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    // Only held: a tap on the name does nothing.
                    .then(if (hold != null) Modifier.combinedClickable(onLongClick = hold, onClick = {}) else Modifier),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SpaceIcon(space, 26.dp)
                Text(
                    space.title, Modifier.weight(1f, fill = false).padding(start = 10.dp).semantics { heading() },
                    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (space.pending) Waiting(Modifier.padding(start = 8.dp))
            }
        }
        IconButton(onClick = onSettings) {
            Icon(Icons.Outlined.Settings, "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onClose != null) IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Done") }
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

/**
 * A part's small heading, maybe with an action at its end. TalkBack reads
 * the words as written, not the capitals.
 */
private fun LazyListScope.section(title: String, action: (@Composable () -> Unit)? = null) = item {
    Row(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 4.dp).height(32.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title.uppercase(),
            Modifier.weight(1f).semantics {
                heading()
                contentDescription = title
            },
            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        action?.invoke()
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text, Modifier.padding(vertical = 4.dp),
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The favorites, four to a row; holding one offers its `edits`. */
@Composable
private fun Favorites(pages: List<Page>, open: (Page) -> Unit, edits: (Page) -> List<Edit>) {
    Column(Modifier.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        for (row in pages.chunked(4)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (page in row) Holdable(edits(page), Modifier.weight(1f)) { hold ->
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .combinedClickable(onLongClick = hold) { open(page) }
                            // Room above the tile for the waiting mark on its corner.
                            .padding(top = 6.dp, bottom = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box {
                            SiteMark(page.title, page.url, 58.dp)
                            if (page.pending) Waiting(Modifier.align(Alignment.TopEnd).offset(5.dp, (-5).dp))
                        }
                        Text(
                            page.title.ifEmpty { Address.site(page.url) }, Modifier.padding(top = 7.dp),
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private fun LazyListScope.here(browser: Browser, space: Space) {
    val tabs = browser.tabs(space)
    section("Open here") {
        if (tabs.size > 1) {
            TextButton(onClick = { browser.closeAll(space) }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text("Close All", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
    items(tabs, key = Tab::id) { tab ->
        PageRow(
            tab.label, tab.url, marked = tab.id == browser.front,
            onOpen = { browser.show(tab) }, onClose = { browser.close(tab) },
        )
    }
    if (tabs.isEmpty()) item { Note("No tabs open in this space. Type an address below, or pick a page above.") }
}

private fun LazyListScope.sent(browser: Browser, tabs: List<Tab>) {
    section("Sent to your desktop")
    items(tabs, key = { "sent-" + it.id }) { tab ->
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
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(22.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
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
 * close it. `marked`: the tab on screen, on a wash of the space's colour
 * that reaches past the rows' edge; `pending`: a change no desktop made
 * yet. Holding it offers its `edits`.
 */
@Composable
private fun PageRow(
    title: String,
    url: String,
    marked: Boolean = false,
    pending: Boolean = false,
    edits: List<Edit> = emptyList(),
    onOpen: () -> Unit,
    onClose: (() -> Unit)? = null,
) = Holdable(edits) { hold ->
    val wash = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                if (marked) {
                    val reach = 8.dp.toPx()
                    drawRoundRect(wash, Offset(-reach, 0f), Size(size.width + 2 * reach, size.height), CornerRadius(12.dp.toPx()))
                }
            }
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(onLongClick = hold, onClick = onOpen)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SiteMark(title, url, 32.dp)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title.ifEmpty { Address.site(url) }, Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontWeight = if (marked) FontWeight.SemiBold else FontWeight.Normal,
                )
                if (pending) Waiting(Modifier.padding(start = 6.dp))
            }
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

/** A folder of the pinned tree; a tap folds it, holding it offers its `edits`. */
@Composable
private fun FolderRow(title: String, smart: Boolean, open: Boolean, pending: Boolean, edits: List<Edit>, toggle: () -> Unit) = Holdable(edits) { hold ->
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(onLongClick = hold, onClick = toggle)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(32.dp), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_folder), null, tint = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (pending) Waiting(Modifier.padding(start = 6.dp))
            }
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

/** Something holding an item offers: a change for a desktop of the chain to make. */
private class Edit(val label: String, val icon: Painter, val danger: Boolean = false, val run: () -> Unit)

/**
 * `content` with the menu of its `edits` below it. `content` gets what its
 * long press does: open the menu; none without edits.
 */
@Composable
private fun Holdable(edits: List<Edit>, modifier: Modifier = Modifier, content: @Composable (hold: (() -> Unit)?) -> Unit) {
    var shown by remember { mutableStateOf(false) }
    Box(modifier) {
        content(if (edits.isEmpty()) null else ({ shown = true }))
        DropdownMenu(expanded = shown, onDismissRequest = { shown = false }) {
            for (edit in edits) {
                val color = if (edit.danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                DropdownMenuItem(
                    text = { Text(edit.label, color = color) },
                    leadingIcon = { Icon(edit.icon, null, tint = color) },
                    onClick = {
                        shown = false
                        edit.run()
                    },
                )
            }
        }
    }
}

/** The mark of a change made here that no desktop of the chain made yet. */
@Composable
private fun Waiting(modifier: Modifier = Modifier) {
    Icon(
        painterResource(R.drawable.ic_waiting), "Waiting for your computer",
        modifier
            .size(18.dp)
            .background(MaterialTheme.colorScheme.background, CircleShape)
            .padding(2.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * A site's mark: its icon if one is kept (`Favicons`), else the first
 * letter of its name in a colour of its own, on a light wash of it.
 */
@Composable
fun SiteMark(title: String, url: String, size: Dp) {
    val shape = RoundedCornerShape(size * 0.28f)
    val icon = Favicons.image(url)
    if (icon != null) {
        Image(
            icon, null,
            // Icons are drawn for a light ground.
            Modifier
                .size(size)
                .background(Color.White, shape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                .padding(size * 0.16f),
        )
    } else {
        val name = Address.site(url)
        val letter = (title.firstOrNull() ?: name.firstOrNull())?.uppercase() ?: "?"
        // The same site gets the same colour at every start, and the one
        // the iOS app gives it.
        val hue = abs(name.codePoints().asLongStream().reduce(5381L) { sum, char -> sum * 33 + char } % 360).toFloat()
        val dark = MaterialTheme.colorScheme.background.isDark
        Box(
            Modifier
                .size(size)
                .background(Color.hsv(hue, if (dark) 0.3f else 0.12f, if (dark) 0.28f else 0.97f), shape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), shape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                letter, color = Color.hsv(hue, if (dark) 0.35f else 0.55f, if (dark) 0.9f else 0.5f),
                fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
