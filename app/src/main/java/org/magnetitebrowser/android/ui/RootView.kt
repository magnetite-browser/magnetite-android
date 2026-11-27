package org.magnetitebrowser.android.ui

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import org.magnetitebrowser.android.R
import org.magnetitebrowser.android.model.Browser
import org.magnetitebrowser.android.web.WebPage

/** What covers the whole app while it is open. */
private enum class Over { Nothing, Settings, Join, Archive }

/**
 * On a narrow screen (a phone, a tablet window beside another app): the page
 * in front, from the status bar down to the bar below it,
 * and the sidebar as the start page and as a sheet. On a wide one (a tablet):
 * the sidebar beside the page, as on the desktop, with the bar above the page.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun RootView(browser: Browser) {
    var over by remember { mutableStateOf(Over.Nothing) }
    val page = browser.frontTab?.let(browser::page)

    // A tab coming to the front (a link from another app) shows over Settings.
    LaunchedEffect(browser.front) {
        if (browser.front != null) over = Over.Nothing
    }
    BackHandler(enabled = browser.frontTab != null) {
        if (page != null && page.canGoBack) page.view.goBack() else browser.showSidebar()
    }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .semantics { testTagsAsResourceId = true },
    ) {
        val sidebar: Sidebar = { insets, close ->
            SidebarView(
                browser,
                onSettings = { close?.invoke(); over = Over.Settings },
                onJoin = { close?.invoke(); over = Over.Join },
                onClose = close, insets = insets,
            )
        }
        val wide = maxWidth >= 720.dp
        if (wide) Wide(browser, page, sidebar) else Narrow(browser, page, sidebar)
        // On a narrow screen the status bar is over the page's colour, else over the app's.
        SystemBarIcons(page?.topColor?.takeIf { !wide && over == Over.Nothing } ?: MaterialTheme.colorScheme.background)
        if (over != Over.Nothing) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).safeDrawingPadding()) {
                when (over) {
                    Over.Settings -> SettingsView(
                        browser,
                        onJoin = { over = Over.Join },
                        onArchive = { over = Over.Archive },
                        onDone = { over = Over.Nothing },
                    )
                    Over.Archive -> ArchiveView(browser, onDone = { over = Over.Nothing })
                    Over.Join -> JoinView(browser, onDone = { over = Over.Nothing })
                    Over.Nothing -> {}
                }
            }
        }
    }
}

/**
 * The sidebar as a start page or column (no `close`), or as a sheet that
 * `close` closes; it keeps clear of `insets`.
 */
private typealias Sidebar = @Composable (insets: WindowInsets, close: (() -> Unit)?) -> Unit

/** Status and navigation bar icons that read on `ground`: light on a dark colour, dark on a light one. */
@Composable
private fun SystemBarIcons(ground: Color) {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    val light = !ground.isDark
    LaunchedEffect(light) {
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun Narrow(browser: Browser, page: WebPage?, sidebar: Sidebar) {
    var sheetShown by remember { mutableStateOf(false) }
    LaunchedEffect(browser.front) { sheetShown = false }
    val address = rememberAddressState(browser)
    val above = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    val navigation = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // Where the keyboard is going: a field of the page or Find on Page has it, and
    // the page makes room for it once. The address is typed over the page.
    val keyboard = with(LocalDensity.current) { WindowInsets.imeAnimationTarget.getBottom(this).toDp() }
    // The page ends above the bar as it rests: the web view has no way to
    // let the end of a page scroll out from under it, short of changing the
    // page's own style, which pages can see and which breaks some layouts.
    val resting = PhoneBarHeight + navigation

    // What is above the page: its colour.
    Box(Modifier.fillMaxSize().background(page?.topColor ?: MaterialTheme.colorScheme.background)) {
        val tab = browser.frontTab
        if (tab != null && page != null) {
            key(tab.id) {
                // Find on Page's bar sits on the keyboard: the page ends above it.
                // In the order the bar picks what it shows.
                val bottom = when {
                    page.finding -> maxOf(keyboard + FindBarHeight, resting)
                    address.editing -> resting
                    else -> maxOf(keyboard, resting)
                }
                PageView(browser, page, Modifier.windowInsetsPadding(above).padding(bottom = bottom))
            }
        } else {
            // Its colour reaches under the status bar.
            Box(Modifier.padding(bottom = resting)) { sidebar(above, null) }
        }
        // One bar for the page and the start page, outside what switches
        // between them: with a bar each, switching would lose what it holds
        // (the keyboard, the text typed).
        PhoneBar(browser, page, address, onSidebar = { sheetShown = true }, Modifier.align(Alignment.BottomCenter))
    }
    if (sheetShown) {
        ModalBottomSheet(
            onDismissRequest = { sheetShown = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            sidebar(WindowInsets(0)) { sheetShown = false }
        }
    }
}

@Composable
private fun Wide(browser: Browser, page: WebPage?, sidebar: Sidebar) {
    var columnShown by remember { mutableStateOf(true) }
    val address = rememberAddressState(browser)

    Row(Modifier.fillMaxSize().safeDrawingPadding()) {
        AnimatedVisibility(columnShown) {
            Row {
                Box(Modifier.width(320.dp).fillMaxHeight()) { sidebar(WindowInsets(0), null) }
                VerticalDivider()
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            TopBar(browser, page, address, onSidebar = { columnShown = !columnShown })
            Box(Modifier.weight(1f)) {
                val tab = browser.frontTab
                if (tab != null && page != null) {
                    // The page ends above Find on Page's bar, so a match at
                    // its end is not under the bar.
                    key(tab.id) {
                        PageView(browser, page, Modifier.padding(bottom = if (page.finding) FindBarHeight + 16.dp else 0.dp))
                    }
                    if (page.finding) {
                        FindBar(page, Modifier.align(Alignment.BottomCenter).widthIn(max = 560.dp).padding(12.dp))
                    }
                } else {
                    Notice("No Tab Open", "Pick a page from the sidebar, or type an address above.")
                }
                // While typing: what the text could mean, over the page below the field.
                if (address.editing) {
                    SuggestionList(
                        browser, address.text.text, address::finish,
                        Modifier.align(Alignment.TopCenter).padding(8.dp).widthIn(max = 560.dp).heightIn(max = 420.dp),
                    )
                }
            }
        }
    }
}

/**
 * A page, and what is shown when it cannot be loaded; the menu of a link
 * held down, and a site's question for the camera, microphone or location.
 * How far it loaded shows in the address field.
 */
@Composable
private fun PageView(browser: Browser, page: WebPage, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            // The view outlives what showed it last.
            factory = { page.view.also { (it.parent as? ViewGroup)?.removeView(it) } },
        )
        page.failure?.let { failure ->
            Surface(Modifier.fillMaxSize()) {
                Notice("This page cannot be opened", failure) {
                    Button(onClick = page::reload) { Text("Try again") }
                }
            }
        }
        page.heldLink?.let { LinkMenu(browser, it) { page.heldLink = null } }
        page.asking?.let { AskDialog(it) }
    }
}

/**
 * What holding a link offers, where the finger is: open it in a new tab,
 * behind the one in front, or on the desktop (in a chain); copy or share it.
 * The page's web view has no menu of its own for links.
 */
@Composable
private fun LinkMenu(browser: Browser, link: WebPage.HeldLink, close: () -> Unit) {
    val context = LocalContext.current
    // The page shown is the tab in front's: its links go to its space, not
    // to the one the sidebar shows (a private page's links stay private).
    val space = browser.frontTab?.space ?: browser.space.id
    val private = browser.spaces.firstOrNull { it.id == space }?.ephemeral == true
    @Composable
    fun entry(label: String, icon: Painter, action: () -> Unit) = DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Icon(icon, null) },
        onClick = {
            close()
            action()
        },
    )

    Box(Modifier.offset { IntOffset(link.x, link.y) }) {
        DropdownMenu(expanded = true, onDismissRequest = close) {
            // Where the link goes, as the page may not say.
            Text(
                link.url, Modifier.widthIn(max = 280.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            HorizontalDivider()
            entry("Open in New Tab", painterResource(R.drawable.ic_open_new_tab)) { browser.open(link.url, space = space) }
            entry("Open in Background", painterResource(R.drawable.ic_open_background)) {
                browser.open(link.url, inBackground = true, space = space)
            }
            // Tabs of private spaces are never listed for the other devices.
            if (browser.sync.joined && !private) {
                entry("Send to Desktop", painterResource(R.drawable.ic_desktop)) { browser.sendToDesktop(link.url, space) }
            }
            HorizontalDivider()
            entry("Copy Link", painterResource(R.drawable.ic_link)) { copy(context, link.url) }
            entry("Share", rememberVectorPainter(Icons.Default.Share)) { share(context, link.url) }
        }
    }
}

/**
 * A site asks for the camera, the microphone or the location. The question
 * names the site, so a page cannot pass for the app.
 */
@Composable
private fun AskDialog(ask: WebPage.Ask) {
    AlertDialog(
        onDismissRequest = { ask.answer(false) },
        title = { Text("Allow ${ask.site} to ${ask.what}?") },
        text = { Text("Magnetite keeps and sends nothing of it.") },
        confirmButton = { TextButton(onClick = { ask.answer(true) }) { Text("Allow") } },
        dismissButton = { TextButton(onClick = { ask.answer(false) }) { Text("Don’t Allow") } },
    )
}

/** A title and a line in the middle of an empty place. */
@Composable
private fun Notice(title: String, text: String, action: @Composable () -> Unit = {}) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(
            text, textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        action()
    }
}
