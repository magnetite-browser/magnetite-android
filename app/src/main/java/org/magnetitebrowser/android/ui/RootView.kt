package org.magnetitebrowser.android.ui

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.magnetitebrowser.android.model.Browser
import org.magnetitebrowser.android.web.WebPage

/** What covers the whole app while it is open. */
private enum class Over { Nothing, Settings, Join }

/**
 * On a narrow screen (a phone, a tablet window beside another app): the page
 * in front with the bar below it, and the sidebar as the start page and as
 * a sheet. On a wide one (a tablet): the sidebar beside the page, as on the
 * desktop, with the bar above the page.
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
            .safeDrawingPadding()
            .semantics { testTagsAsResourceId = true },
    ) {
        val sidebar: @Composable (Boolean, () -> Unit) -> Unit = { inSheet, leave ->
            SidebarView(
                browser, inSheet,
                onSettings = { leave(); over = Over.Settings },
                onJoin = { leave(); over = Over.Join },
            )
        }
        if (maxWidth >= 720.dp) Wide(browser, page, sidebar) else Narrow(browser, page, sidebar)
        when (over) {
            Over.Settings -> SettingsView(browser, onJoin = { over = Over.Join }, onDone = { over = Over.Nothing })
            Over.Join -> JoinView(browser, onDone = { over = Over.Nothing })
            Over.Nothing -> {}
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Narrow(browser: Browser, page: WebPage?, sidebar: @Composable (Boolean, () -> Unit) -> Unit) {
    var sheetShown by remember { mutableStateOf(false) }
    LaunchedEffect(browser.front) { sheetShown = false }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val tab = browser.frontTab
            if (tab != null && page != null) key(tab.id) { PageView(page) } else sidebar(false) {}
        }
        AddressBar(browser, page, wide = false, onSidebar = { sheetShown = true })
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

    Row(Modifier.fillMaxSize()) {
        AnimatedVisibility(columnShown) {
            Row {
                Box(Modifier.width(320.dp).fillMaxHeight()) { sidebar(false) {} }
                VerticalDivider()
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            AddressBar(browser, page, wide = true, onSidebar = { columnShown = !columnShown })
            val tab = browser.frontTab
            if (tab != null && page != null) {
                key(tab.id) { PageView(page) }
            } else {
                Notice("No Tab Open", "Pick a page from the sidebar, or type an address above.")
            }
        }
    }
}

/** A page, its loading bar, and what is shown when it cannot be loaded. */
@Composable
private fun PageView(page: WebPage) {
    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            // The view outlives what showed it last.
            factory = { page.view.also { (it.parent as? ViewGroup)?.removeView(it) } },
        )
        if (page.loading) {
            LinearProgressIndicator(progress = { page.progress }, modifier = Modifier.fillMaxWidth())
        }
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
