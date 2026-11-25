package org.magnetitebrowser.android.model

import android.annotation.SuppressLint
import android.app.Activity
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.magnetitebrowser.android.web.WebPage
import uniffi.magnetite_mobile_core.DeviceTab
import uniffi.magnetite_mobile_core.Page
import uniffi.magnetite_mobile_core.Space
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/**
 * A tab open on this device. Tabs are not synced: the chain's sidebar is
 * what the desktop wrote, and a tab opened from it lives here only.
 */
data class Tab(
    val id: String = UUID.randomUUID().toString(),
    /** The space it was opened in; its pages use that space's profile. */
    val space: String,
    val url: String,
    val title: String = "",
    /** The sidebar item it was opened from, so that item shows it again. */
    val origin: String? = null,
) {
    /** What a row shows: the page's title, or its site while it has none. */
    val label get() = title.ifEmpty { Address.site(url) }

    fun json(): JSONObject = JSONObject().put("id", id).put("space", space).put("url", url)
        .put("title", title).putOpt("origin", origin)

    companion object {
        fun of(json: JSONObject) = Tab(
            json.getString("id"), json.getString("space"), json.getString("url"),
            json.optString("title"), json.optString("origin").ifEmpty { null },
        )
    }
}

/** The tabs of this device, the space in front, and the pages behind them. */
// Profiles are only touched where `profiles` says the web engine has them.
@SuppressLint("RequiresFeature")
class Browser(
    private val activity: Activity,
    private val host: WebPage.Host,
    private val scope: CoroutineScope,
) {
    val sync = Sync(activity)
    var tabs by mutableStateOf(listOf<Tab>())
        private set
    /** Tabs sent to the desktop that no desktop has opened yet. */
    var sent by mutableStateOf(listOf<Tab>())
        private set
    /** The id of the tab on screen; none: the sidebar is the start page. */
    var front by mutableStateOf<String?>(null)
        private set
    var spaceId by mutableStateOf(HOME.id)
        private set
    /** Goes up when the address field should take the keyboard (a key of a hardware keyboard). */
    var addressAsked by mutableIntStateOf(0)
        private set

    /** The pages loaded right now, by tab. A tab without one loads when shown. */
    private val pages = HashMap<String, WebPage>()
    /** Goes up when a loaded page was dropped, so what shows it asks for a new one. */
    private var dropped by mutableIntStateOf(0)
    private val file = File(activity.filesDir, "tabs.json")
    /** Whether spaces can have cookies and logins of their own on this device. */
    private val profiles = WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)

    init {
        Favicons.start(activity)
        // Profiles of private spaces are not meant to outlive the app.
        if (profiles) {
            val store = ProfileStore.getInstance()
            store.allProfileNames.filter { it.startsWith(PRIVATE) }.forEach { store.deleteProfile(it) }
        }
        runCatching {
            val saved = JSONObject(file.readText())
            tabs = saved.getJSONArray("tabs").tabs()
            sent = saved.optJSONArray("sent")?.tabs().orEmpty()
            front = saved.optString("front").ifEmpty { null }
            spaceId = saved.getString("space")
        }
        settle()
    }

    // Spaces

    val spaces: List<Space> get() = sync.sidebar?.spaces?.takeIf { it.isNotEmpty() } ?: listOf(HOME)
    val space: Space get() = spaces.firstOrNull { it.id == spaceId } ?: spaces[0]
    val frontTab: Tab? get() = tabs.firstOrNull { it.id == front }

    fun tabs(space: Space) = tabs.filter { it.space == space.id }

    fun show(space: Space) {
        spaceId = space.id
        save()
    }

    /**
     * After the sidebar changed: tabs of a space that is gone move to the
     * first one, and the space in front is one that exists.
     */
    fun settle() {
        val known = spaces.map { it.id }.toSet()
        val first = spaces[0].id
        tabs = tabs.map { tab ->
            if (tab.space in known) tab else tab.copy(space = first).also { drop(it.id) }
        }
        if (spaceId !in known) spaceId = first
        if (frontTab == null) front = null
        save()
    }

    // Tabs

    /**
     * Open `url` in a new tab of the space in front and show it; with
     * `inBackground`, behind the tab in front.
     */
    fun open(url: String, origin: String? = null, inBackground: Boolean = false, space: String = this.space.id) {
        val tab = Tab(space = space, url = url, origin = origin)
        tabs = listOf(tab) + tabs
        if (!inBackground) {
            front = tab.id
            spaceId = tab.space
        }
        save()
    }

    /** Show the tab of sidebar item `page` in `space`, opening one if there is none. */
    fun open(page: Page, space: String = this.space.id) {
        val tab = tabs.firstOrNull { it.origin == page.id && it.space == space }
        if (tab != null) show(tab) else open(page.url, page.id, space = space)
    }

    /** The search engine Settings chose, as a template (`Address.engines`). */
    val searchTemplate: String get() = sync.prefs.getString(SEARCH, null) ?: Address.engines[0].second

    /** Go to what the address field holds: in the tab in front, else a new one. */
    fun go(text: String) {
        Address.url(text, searchTemplate)?.let(::load)
    }

    /** Load `url` in the tab in front, else in a new one. */
    fun load(url: String) {
        val tab = frontTab
        if (tab != null) page(tab).view.loadUrl(url) else open(url)
    }

    fun show(tab: Tab) {
        spaceId = tab.space
        front = tab.id
        save()
    }

    /** Back to the sidebar; the tab stays open. */
    fun showSidebar() {
        front = null
        save()
    }

    /** The start page with the keyboard in its address field. */
    fun newTab() {
        showSidebar()
        addressAsked++
    }

    fun askAddress() {
        addressAsked++
    }

    fun close(tab: Tab) {
        tabs = tabs.filter { it.id != tab.id }
        drop(tab.id)
        if (front == tab.id) front = null
        save()
    }

    fun closeAll(space: Space) = tabs(space).forEach(::close)

    // Other devices

    /** Hand `url` to the desktop without opening it here. */
    fun sendToDesktop(url: String, space: String = this.space.id) {
        sent = listOf(Tab(space = space, url = url)) + sent
        save()
        scope.launch { shareTabs() }
    }

    /**
     * Hand `tab` to the desktop: it closes here and waits in `sent` until a
     * desktop opened it.
     */
    fun sendToDesktop(tab: Tab) {
        close(tab)
        sent = listOf(tab) + sent
        save()
        scope.launch { shareTabs() }
    }

    /** Open a sent tab here again. */
    fun takeBack(tab: Tab) {
        forgetSent(tab)
        tabs = listOf(tab) + tabs
        show(tab)
    }

    fun forgetSent(tab: Tab) {
        sent = sent.filter { it.id != tab.id }
        save()
    }

    /**
     * Make a change to the chain's sidebar (`Sync`), then tell the chain at
     * once, so a desktop makes it soon.
     */
    fun change(make: suspend (Sync) -> Unit) {
        scope.launch {
            make(sync)
            shareTabs()
        }
    }

    /** Read the chain's sidebar, then tell the chain about the tabs here. */
    suspend fun syncNow() {
        sync.refresh()
        settle()
        shareTabs()
    }

    fun syncSoon() {
        scope.launch { syncNow() }
    }

    fun syncIfDue() {
        if (sync.due) syncSoon()
    }

    fun shareTabsSoon() {
        scope.launch { shareTabs() }
    }

    /**
     * List the tabs open here in the chain, and close those another device
     * took over. Tabs of private spaces are never listed.
     */
    suspend fun shareTabs() {
        if (!sync.joined) return
        // Twice at most: after closing what was taken, the list is sent
        // again without it, so the other device can forget the handover.
        repeat(2) {
            val taken = sync.exchange(listed()).toSet()
            if (taken.isEmpty()) return
            tabs.filter { it.id in taken }.forEach(::close)
            sent = sent.filter { it.id !in taken }
            save()
        }
    }

    /** What the chain is told: the sent tabs, and the open ones unless Settings says no. */
    private fun listed(): List<DeviceTab> {
        val known = sync.sidebar?.spaces.orEmpty().associateBy { it.id }
        fun entry(tab: Tab, later: Boolean): DeviceTab? {
            if (known[tab.space]?.ephemeral == true) return null
            return DeviceTab(tab.id, tab.url, tab.title, known[tab.space]?.id, later)
        }
        val open = if (sync.sharesTabs) tabs.mapNotNull { entry(it, false) } else emptyList()
        return sent.mapNotNull { entry(it, true) } + open
    }

    // Pages

    /** The page of `tab`, loaded now if it was not. */
    fun page(tab: Tab): WebPage {
        dropped // Read, so what shows the page asks again after it was dropped.
        return pages.getOrPut(tab.id) {
            val space = spaces.firstOrNull { it.id == tab.space } ?: spaces[0]
            WebPage(activity, tab.url, profile(space), space.ephemeral, host).also { page ->
                page.changed = { url, title -> note(tab.id, url, title) }
                page.opensTab = { open(it) }
                page.died = {
                    drop(tab.id)
                    dropped++
                }
            }
        }
    }

    private fun drop(id: String) {
        pages.remove(id)?.view?.destroy()
    }

    private fun note(id: String, url: String, title: String) {
        val before = tabs.firstOrNull { it.id == id } ?: return
        val now = before.copy(url = if (Address.isWeb(url)) url else before.url, title = title)
        if (now == before) return
        tabs = tabs.map { if (it.id == id) now else it }
        save()
    }

    /**
     * Cookies and logins of a space: one profile of the web engine per
     * profile of the chain, so spaces with the same profile share them, as
     * on the desktop. Null (the engine's one profile for all) where the
     * device's web engine is too old for profiles.
     */
    private fun profile(space: Space): String? {
        if (!profiles) return null
        val hash = MessageDigest.getInstance("SHA-256").digest(space.profileId.toByteArray())
        val name = (if (space.ephemeral) PRIVATE else "space-") + hash.take(16).joinToString("") { "%02x".format(it) }
        ProfileStore.getInstance().getOrCreateProfile(name)
        return name
    }

    /** Erase cookies, logins and site storage of every space. */
    fun clearWebsiteData() {
        if (profiles) {
            val store = ProfileStore.getInstance()
            for (profile in store.allProfileNames.mapNotNull(store::getProfile)) {
                profile.cookieManager.removeAllCookies(null)
                profile.webStorage.deleteAllData()
            }
        } else {
            CookieManager.getInstance().removeAllCookies(null)
            WebStorage.getInstance().deleteAllData()
        }
        pages.values.forEach {
            it.view.clearCache(true)
            it.reload()
        }
    }

    /** When the system wants memory back, only the page in front stays loaded. */
    fun unloadHidden() {
        pages.keys.filter { it != front }.forEach(::drop)
    }

    private fun save() {
        val saved = JSONObject()
            .put("tabs", JSONArray(tabs.map(Tab::json)))
            .put("sent", JSONArray(sent.map(Tab::json)))
            .putOpt("front", front)
            .put("space", spaceId)
        runCatching {
            val temp = File(file.path + ".part")
            temp.writeText(saved.toString())
            temp.renameTo(file)
        }
    }

    companion object {
        /** The key of the search engine's template in the app's settings. */
        const val SEARCH = "search.template"
        /** How profiles of private spaces are named. */
        private const val PRIVATE = "private-"

        /** The space of a device that is in no chain. */
        val HOME = Space("home", "Home", "home", null, "home", false, emptyList(), emptyList(), emptyList(), false)

        private fun JSONArray.tabs() = (0 until length()).map { Tab.of(getJSONObject(it)) }
    }
}
