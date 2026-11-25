package org.magnetitebrowser.android.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.drop
import org.magnetitebrowser.android.R
import org.magnetitebrowser.android.model.Address
import org.magnetitebrowser.android.model.Browser
import org.magnetitebrowser.android.web.WebPage
import uniffi.magnetite_mobile_core.RowItem

// Narrow screens

/** The height the narrow bar keeps clear above the bottom edge (the navigation bar's top). */
val PhoneBarHeight = 64.dp

private enum class PhoneBarLook { Full, Small, Find, Aside }

/**
 * The bar of a narrow screen, below the page on a strip of its colour: back,
 * the address, the page's menu and the way to the sidebar (`onSidebar`).
 * While the reader scrolls down into a page it shrinks to the site's name,
 * and Find on Page's bar takes its place while finding. It sits on the
 * keyboard while the address is typed, with what the text could mean just
 * above it, and steps aside while a field of the page has the keyboard.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhoneBar(browser: Browser, page: WebPage?, address: AddressState, onSidebar: () -> Unit, modifier: Modifier = Modifier) {
    // Where the keyboard is going, so the bar does not wait for it to finish moving.
    val keyboard = WindowInsets.imeAnimationTarget.getBottom(LocalDensity.current) > 0
    val look = when {
        page?.finding == true -> PhoneBarLook.Find
        address.editing -> PhoneBarLook.Full
        keyboard -> PhoneBarLook.Aside
        page?.reading == true -> PhoneBarLook.Small
        else -> PhoneBarLook.Full
    }
    Column(
        modifier
            .windowInsetsPadding(
                WindowInsets.ime.union(WindowInsets.navigationBars).union(WindowInsets.displayCutout)
                    .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
            )
            // The suggestions end below the status bar.
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            .padding(start = 12.dp, end = 12.dp, bottom = 8.dp, top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (address.editing) {
            SuggestionList(browser, address.text.text, address::finish, Modifier.weight(1f, fill = false).fillMaxWidth())
        }
        Bar(browser, page, address, look, onSidebar)
    }
}

/** The narrow bar's row in its `look`, changing from one to the next. */
@Composable
private fun Bar(browser: Browser, page: WebPage?, address: AddressState, look: PhoneBarLook, onSidebar: () -> Unit) {
    AnimatedContent(
        look,
        transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.9f)) togetherWith fadeOut() },
        contentAlignment = Alignment.BottomCenter,
        label = "bar",
    ) { now ->
        when (now) {
            PhoneBarLook.Full -> Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (page != null && !address.editing) {
                    Floating {
                        IconButton(onClick = { page.view.goBack() }, enabled = page.canGoBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    }
                }
                Floating(Modifier.weight(1f)) { AddressField(browser, page, address) }
                if (address.editing) {
                    Floating { IconButton(onClick = address::stop) { Icon(Icons.Default.Close, "Cancel") } }
                } else if (page != null) {
                    PageMenu(browser, page, floating = true)
                    Floating { TabsButton(browser, onSidebar) }
                }
            }
            PhoneBarLook.Small -> page?.let { SiteLabel(it) }
            PhoneBarLook.Find -> page?.let { FindBar(it, Modifier.fillMaxWidth()) }
            PhoneBarLook.Aside -> Spacer(Modifier)
        }
    }
}

/** The bar while reading: the site's name, small. A tap brings the bar back. */
@Composable
private fun SiteLabel(page: WebPage) {
    Floating {
        Text(
            Address.site(page.url),
            Modifier
                .clickable(onClickLabel = "Show the bar") { page.reading = false }
                .padding(horizontal = 16.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The way to the sidebar: the number of tabs open in the space in front, in its colour. */
@Composable
private fun TabsButton(browser: Browser, onClick: () -> Unit) {
    val count = browser.tabs(browser.space).size
    IconButton(
        onClick,
        Modifier.semantics {
            contentDescription = "Tabs"
            stateDescription = "$count"
        },
    ) {
        val tint = MaterialTheme.colorScheme.primary
        Box(
            Modifier.size(22.dp).border(2.dp, tint, RoundedCornerShape(7.dp)).clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (count < 100) "$count" else "99+", color = tint,
                fontSize = if (count < 10) 13.sp else 10.sp, fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * A piece of the narrow bar, or something floating with it: a tonal surface
 * with a soft shadow, so that it stands out over any page.
 */
@Composable
fun Floating(modifier: Modifier = Modifier, shape: Shape = CircleShape, content: @Composable () -> Unit) {
    Surface(
        modifier, shape = shape, color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 3.dp, border = BorderStroke(Dp.Hairline, MaterialTheme.colorScheme.outlineVariant),
        content = content,
    )
}

// Wide screens

/**
 * The bar above the page on a wide screen (a tablet), beside the sidebar
 * column, which its first button shows and hides (`onSidebar`). What the
 * typed text could mean shows below it, over the page (`RootView`).
 */
@Composable
fun TopBar(browser: Browser, page: WebPage?, address: AddressState, onSidebar: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onSidebar) { Icon(painterResource(R.drawable.ic_sidebar), "Sidebar") }
        if (page != null) {
            IconButton(onClick = { page.view.goBack() }, enabled = page.canGoBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
            }
            IconButton(onClick = { page.view.goForward() }, enabled = page.canGoForward) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, "Forward")
            }
        }
        Box(Modifier.weight(1f).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
            AddressField(
                browser, page, address,
                Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
            )
        }
        if (page != null) PageMenu(browser, page, floating = false)
    }
}

// Shared

/** What the address field holds: the text typed, and whether it is being typed. */
@Stable
class AddressState {
    var text by mutableStateOf(TextFieldValue())
    var editing by mutableStateOf(false)
        private set

    /** Start typing, from `url` (the page's address), all of it selected so that typing replaces it. */
    fun edit(url: String?) {
        val start = url.orEmpty()
        text = TextFieldValue(start, TextRange(0, start.length))
        editing = true
    }

    fun stop() {
        editing = false
    }

    /** Typing ended with a page gone to: the text goes too. */
    fun finish() {
        text = TextFieldValue()
        editing = false
    }
}

/**
 * The state of a bar's address field. It starts typing when asked
 * (`Browser.addressAsked`: a key, New Tab), and a page picked while
 * typing ends the typing.
 */
@Composable
fun rememberAddressState(browser: Browser): AddressState {
    val address = remember { AddressState() }
    LaunchedEffect(browser) {
        snapshotFlow { browser.addressAsked }.drop(1).collect {
            address.edit(browser.frontTab?.let(browser::page)?.url)
        }
    }
    LaunchedEffect(browser.front) {
        if (browser.front != null) address.stop()
    }
    return address
}

/**
 * The address of the page in front: its site while browsing (reloading is
 * in the page's menu), a text field while typing. A tap on the site starts
 * typing. Along its bottom edge, how far the page loaded.
 */
@Composable
fun AddressField(browser: Browser, page: WebPage?, address: AddressState, modifier: Modifier = Modifier) {
    val typing = address.editing || page == null
    val field = remember { FocusRequester() }
    val focus = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(address.editing) {
        if (address.editing) field.requestFocus() else if (focused) focus.clearFocus()
    }
    BackHandler(address.editing) { address.stop() }

    Box(modifier.clip(CircleShape).height(48.dp)) {
        Row(Modifier.fillMaxSize().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (typing) {
                Icon(
                    Icons.Default.Search, null, Modifier.padding(end = 10.dp).size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                BasicTextField(
                    value = address.text,
                    onValueChange = { address.text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("address")
                        .focusRequester(field)
                        .onFocusChanged { state ->
                            // Tapped itself (on the start page), or reached with a key.
                            if (state.isFocused && !address.editing) address.edit(page?.url)
                            if (focused && !state.isFocused) address.stop()
                            focused = state.isFocused
                        }
                        // While not typing, the site shows in its place.
                        .alpha(if (typing) 1f else 0f),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go,
                    ),
                    keyboardActions = KeyboardActions(onGo = {
                        browser.go(address.text.text)
                        address.finish()
                    }),
                )
                if (!typing && page != null) {
                    // Over the field, so that a tap does not place the cursor in it.
                    SiteName(page, Modifier.matchParentSize().clickable(onClickLabel = "Edit the address") { address.edit(page.url) })
                } else if (address.text.text.isEmpty()) {
                    Text("Search or enter address", color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            if (page != null && !typing && page.loading) {
                IconButton(onClick = { page.view.stopLoading() }) { Icon(Icons.Default.Close, "Stop") }
            } else if (address.editing && address.text.text.isNotEmpty()) {
                IconButton(onClick = { address.text = TextFieldValue() }) {
                    Icon(Icons.Default.Clear, "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (page != null && page.loading) {
            LinearProgressIndicator(
                progress = { page.progress },
                modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 22.dp).fillMaxWidth().height(2.dp),
                trackColor = Color.Transparent, gapSize = 0.dp, drawStopIndicator = {},
            )
        }
    }
}

/** The site's name, in the middle; a page without https says so. */
@Composable
private fun SiteName(page: WebPage, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        if (page.url.startsWith("http:")) {
            Icon(
                painterResource(R.drawable.ic_not_secure), "Not secure", Modifier.padding(end = 4.dp).size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            Address.site(page.url), fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * What can be done with the page in front, from a button of the narrow bar
 * (`floating`), whose menu has the page's way back nearest to it, or of the
 * wide one.
 */
@Composable
fun PageMenu(browser: Browser, page: WebPage, floating: Boolean) {
    var shown by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val tab = browser.frontTab

    fun closing(action: () -> Unit): () -> Unit = {
        shown = false
        action()
    }

    @Composable
    fun entry(label: String, icon: Painter, danger: Boolean = false, action: () -> Unit) = DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Icon(icon, null) },
        colors = if (danger) {
            MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.error, leadingIconColor = MaterialTheme.colorScheme.error)
        } else {
            MenuDefaults.itemColors()
        },
        onClick = closing(action),
    )

    // In a chain: pinning the page in its tab's space, and making it a favorite there, unless it is already.
    val space = browser.spaces.firstOrNull { it.id == tab?.space }?.takeIf { browser.sync.joined && Address.isWeb(page.url) }
    val canPin = space != null && space.pinned.none { (it.item as? RowItem.Page)?.page?.url == page.url }
    val canFavor = space != null && space.favorites.none { it.url == page.url }

    // As the wide bar's menu shows them; the narrow one's is the other way up.
    val sections: List<@Composable () -> Unit> = listOfNotNull(
        {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = closing { page.view.goBack() }, enabled = page.canGoBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
                IconButton(onClick = closing { page.view.goForward() }, enabled = page.canGoForward) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, "Forward")
                }
                IconButton(onClick = closing(page::reload)) { Icon(Icons.Default.Refresh, "Reload") }
            }
        },
        {
            entry("Find on Page", painterResource(R.drawable.ic_find), action = page::startFinding)
            TextSize(page)
            DropdownMenuItem(
                text = { Text("Desktop Site") },
                leadingIcon = { Icon(painterResource(R.drawable.ic_desktop_site), null) },
                trailingIcon = { Checkbox(checked = page.desktopSite, onCheckedChange = null) },
                onClick = closing { page.askDesktopSite(!page.desktopSite) },
            )
        },
        // A desktop of the chain makes these (`Sync`).
        if (space != null && (canPin || canFavor)) {
            {
                if (canPin) {
                    entry("Pin in “${space.title}”", painterResource(R.drawable.ic_pin)) {
                        browser.change { it.pin(page.url, page.title, space) }
                    }
                }
                if (canFavor) {
                    entry("Add to Favorites", painterResource(R.drawable.ic_space_star)) {
                        browser.change { it.addFavorite(page.url, page.title, space) }
                    }
                }
            }
        } else {
            null
        },
        {
            entry("Share", rememberVectorPainter(Icons.Default.Share)) { share(context, page.url) }
            entry("Copy Link", painterResource(R.drawable.ic_link)) { copy(context, page.url) }
            if (tab != null && browser.sync.joined) {
                entry("Send to Desktop", painterResource(R.drawable.ic_desktop)) { browser.sendToDesktop(tab) }
            }
        },
        {
            entry("New Tab", rememberVectorPainter(Icons.Default.Add), action = browser::newTab)
            if (tab != null) entry("Close Tab", rememberVectorPainter(Icons.Default.Close), danger = true) { browser.close(tab) }
        },
    )

    Box {
        val button = @Composable { IconButton(onClick = { shown = true }) { Icon(Icons.Default.MoreVert, "More") } }
        if (floating) Floating(content = button) else button()
        DropdownMenu(expanded = shown, onDismissRequest = { shown = false }) {
            (if (floating) sections.asReversed() else sections).forEachIndexed { i, section ->
                if (i > 0) HorizontalDivider()
                section()
            }
        }
    }
}

/** Hand `url` to an app the reader picks. */
fun share(context: Context, url: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
    context.startActivity(Intent.createChooser(send, null))
}

fun copy(context: Context, url: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Link", url))
}

/** Smaller and larger text, and the size now, which a tap resets. */
@Composable
private fun TextSize(page: WebPage) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { page.sizeText(-1) }, enabled = page.textSize > 50) {
            Icon(painterResource(R.drawable.ic_text_smaller), "Smaller Text")
        }
        TextButton(onClick = page::resetTextSize, Modifier.weight(1f)) {
            Text("${page.textSize} %", color = MaterialTheme.colorScheme.onSurface)
        }
        IconButton(onClick = { page.sizeText(1) }, enabled = page.textSize < 300) {
            Icon(painterResource(R.drawable.ic_text_larger), "Larger Text")
        }
    }
}

/**
 * Find on Page: what to look for, which match shows of how many, the way
 * to the previous and the next one, and Done. In the narrow bar's place,
 * or over the bottom of the page on a wide screen.
 */
/** How high Find on Page's bar is in the narrow bar's place: its row and the gap below. */
val FindBarHeight = 56.dp

@Composable
fun FindBar(page: WebPage, modifier: Modifier = Modifier) {
    val field = remember { FocusRequester() }
    LaunchedEffect(Unit) { field.requestFocus() }
    BackHandler { page.endFinding() }
    val found = page.matches?.takeIf { page.query.isNotEmpty() }

    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Floating(Modifier.weight(1f)) {
            Row(Modifier.height(48.dp).padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    BasicTextField(
                        value = page.query,
                        onValueChange = page::find,
                        modifier = Modifier.fillMaxWidth().focusRequester(field).testTag("find"),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { page.findNext(true) }),
                    )
                    if (page.query.isEmpty()) {
                        Text("Find on page", color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
                if (found != null) {
                    Text(
                        "${found.current} of ${found.count}", Modifier.padding(horizontal = 4.dp),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val some = (found?.count ?: 0) > 0
                IconButton(onClick = { page.findNext(false) }, enabled = some) { Icon(Icons.Default.KeyboardArrowUp, "Previous") }
                IconButton(onClick = { page.findNext(true) }, enabled = some) { Icon(Icons.Default.KeyboardArrowDown, "Next") }
            }
        }
        Floating {
            IconButton(onClick = page::endFinding) { Icon(Icons.Default.Check, "Done", tint = MaterialTheme.colorScheme.primary) }
        }
    }
}
