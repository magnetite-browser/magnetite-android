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
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import org.magnetitebrowser.android.model.Browser
import org.magnetitebrowser.android.web.WebPage

/** What covers the whole app while it is open. */
private enum class Over { Nothing, Settings, Join }

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

    BackHandler(enabled = browser.frontTab != null) {
        if (page != null && page.canGoBack) page.view.goBack() else browser.showSidebar()
    }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .semantics { testTagsAsResourceId = true },
    ) {
        val sidebar: @Composable (Boolean, () -> Unit) -> Unit = { inSheet, leave ->
            SidebarView(
                browser, inSheet,
                onSettings = { leave(); over = Over.Settings },
                onJoin = { leave(); over = Over.Join },
            )
        }
        val wide = maxWidth >= 720.dp
        if (wide) Wide(browser, page, sidebar) else Narrow(browser, page, sidebar)
        // On a narrow screen the status bar is over the page's colour, else over the app's.
        SystemBarIcons(page?.topColor?.takeIf { !wide && over == Over.Nothing } ?: MaterialTheme.colorScheme.background)
        if (over != Over.Nothing) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).safeDrawingPadding()) {
                when (over) {
                    Over.Settings -> SettingsView(browser, onJoin = { over = Over.Join }, onDone = { over = Over.Nothing })
                    Over.Join -> JoinView(browser, onDone = { over = Over.Nothing })
                    Over.Nothing -> {}
                }
            }
        }
    }
}

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
private fun Narrow(browser: Browser, page: WebPage?, sidebar: @Composable (Boolean, () -> Unit) -> Unit) {
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
                val bottom = if (address.editing) resting else maxOf(keyboard, resting)
                PageView(page, Modifier.windowInsetsPadding(above).padding(bottom = bottom))
            }
        } else {
            Box(Modifier.windowInsetsPadding(above).padding(bottom = resting)) { sidebar(false) {} }
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
            sidebar(true) { sheetShown = false }
        }
    }
}

@Composable
private fun Wide(browser: Browser, page: WebPage?, sidebar: @Composable (Boolean, () -> Unit) -> Unit) {
    var columnShown by remember { mutableStateOf(true) }

    Row(Modifier.fillMaxSize().safeDrawingPadding()) {
        AnimatedVisibility(columnShown) {
            Row {
                Box(Modifier.width(320.dp).fillMaxHeight()) { sidebar(false) {} }
                VerticalDivider()
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            TopBar(browser, page, onSidebar = { columnShown = !columnShown })
            val tab = browser.frontTab
            if (tab != null && page != null) {
                Box {
                    key(tab.id) { PageView(page) }
                    if (page.finding) {
                        FindBar(page, Modifier.align(Alignment.BottomCenter).widthIn(max = 560.dp).padding(12.dp))
                    }
                }
            } else {
                Notice("No Tab Open", "Pick a page from the sidebar, or type an address above.")
            }
        }
    }
}

/**
 * A page, and what is shown when it cannot be loaded. How far it loaded
 * shows in the address field.
 */
@Composable
private fun PageView(page: WebPage, modifier: Modifier = Modifier) {
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
    }
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
