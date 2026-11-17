package org.magnetitebrowser.android.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.magnetitebrowser.android.R
import org.magnetitebrowser.android.model.Address
import org.magnetitebrowser.android.model.Browser
import org.magnetitebrowser.android.web.WebPage

/**
 * Back and forward, the address field, and the way to the sidebar. Below
 * the page on a narrow screen, above it on a wide one (`wide`), where its
 * sidebar button shows and hides the sidebar column. `page` is the page in
 * front, if a tab is.
 */
@Composable
fun AddressBar(browser: Browser, page: WebPage?, wide: Boolean, onSidebar: () -> Unit) {
    var text by remember { mutableStateOf(TextFieldValue()) }
    var editing by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val field = remember { FocusRequester() }

    // A key of a hardware keyboard asked for the address field.
    LaunchedEffect(browser.addressAsked) {
        if (browser.addressAsked > 0) field.requestFocus()
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (wide) {
            IconButton(onClick = onSidebar) { Icon(painterResource(R.drawable.ic_sidebar), "Sidebar") }
        }
        if (page != null && !editing) {
            IconButton(onClick = { page.view.goBack() }, enabled = page.canGoBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
            }
            IconButton(onClick = { page.view.goForward() }, enabled = page.canGoForward) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, "Forward")
            }
        }
        Row(
            Modifier
                .weight(1f)
                .padding(horizontal = 6.dp)
                .height(44.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp))
                .padding(start = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("address")
                        .focusRequester(field)
                        .onFocusChanged { state ->
                            // Typing starts from the page's address, all of it marked.
                            if (state.isFocused && !editing) {
                                val url = page?.url.orEmpty()
                                text = TextFieldValue(url, TextRange(0, url.length))
                            }
                            editing = state.isFocused
                        }
                        // While not typing, the field shows the site in its place.
                        .alpha(if (editing || page == null) 1f else 0f),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go,
                    ),
                    keyboardActions = KeyboardActions(onGo = {
                        browser.go(text.text)
                        text = TextFieldValue()
                        focus.clearFocus()
                    }),
                )
                if (page != null && !editing) {
                    Text(Address.site(page.url), Modifier.fillMaxWidth(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else if (text.text.isEmpty()) {
                    Text("Search or enter address", color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            if (page != null && !editing) {
                if (page.loading) {
                    IconButton(onClick = { page.view.stopLoading() }) { Icon(Icons.Default.Close, "Stop") }
                } else {
                    IconButton(onClick = page::reload) { Icon(Icons.Default.Refresh, "Reload") }
                }
            }
        }
        if (editing) {
            TextButton(onClick = { focus.clearFocus() }) { Text("Cancel") }
        } else if (page != null) {
            PageMenu(browser, page)
            if (!wide) {
                IconButton(onClick = onSidebar) { Icon(painterResource(R.drawable.ic_sidebar), "Tabs") }
            }
        }
    }
}

@Composable
private fun PageMenu(browser: Browser, page: WebPage) {
    var shown by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val tab = browser.frontTab

    Box {
        IconButton(onClick = { shown = true }) { Icon(Icons.Default.MoreVert, "More") }
        DropdownMenu(expanded = shown, onDismissRequest = { shown = false }) {
            @Composable
            fun entry(label: String, icon: @Composable () -> Unit, action: () -> Unit) = DropdownMenuItem(
                text = { Text(label) }, leadingIcon = icon,
                onClick = {
                    shown = false
                    action()
                },
            )
            // The start page: its address field opens a new tab.
            entry("New Tab", { Icon(Icons.Default.Add, null) }) { browser.showSidebar() }
            entry("Share", { Icon(Icons.Default.Share, null) }) {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, page.url)
                context.startActivity(Intent.createChooser(send, null))
            }
            entry("Copy Link", { Icon(painterResource(R.drawable.ic_link), null) }) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Link", page.url))
            }
            if (tab != null && browser.sync.joined) {
                entry("Send to Desktop", { Icon(painterResource(R.drawable.ic_desktop), null) }) { browser.sendToDesktop(tab) }
            }
            if (tab != null) {
                entry("Close Tab", { Icon(Icons.Default.Close, null, tint = LocalContentColor.current) }) { browser.close(tab) }
            }
        }
    }
}
