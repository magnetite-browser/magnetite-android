package org.magnetitebrowser.android.web

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.view.MotionEvent
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.net.toUri
import androidx.webkit.WebViewCompat
import org.json.JSONArray
import org.magnetitebrowser.android.model.Favicons

/**
 * One tab's page: the web view and what the bars show of it. `profile` names
 * the engine's profile its cookies and logins live in (none: the default
 * one); a `private` page leaves no site icon.
 */
// A `profile` is only given where the web engine has profiles (see `Browser`).
@SuppressLint("SetJavaScriptEnabled", "RequiresFeature")
class WebPage(
    private val activity: Activity,
    url: String,
    private val profile: String?,
    private val private: Boolean,
    private val host: Host,
) {
    /** What a page needs of the activity it is shown in. */
    interface Host {
        /** Let the user pick files with `picker`; `done` gets them, or null. */
        fun chooseFiles(picker: Intent, done: (Array<Uri>?) -> Unit)
        /** Ask the system for Android `permissions`; `done` gets those the app has. */
        fun askPermissions(permissions: List<String>, done: (Set<String>) -> Unit)
    }

    var url by mutableStateOf(url)
        private set
    var title by mutableStateOf("")
        private set
    var progress by mutableFloatStateOf(0f)
        private set
    var loading by mutableStateOf(true)
        private set
    var canGoBack by mutableStateOf(false)
        private set
    var canGoForward by mutableStateOf(false)
        private set
    /** Why the page could not be loaded, while that is what is shown. */
    var failure by mutableStateOf<String?>(null)
        private set
    /**
     * The colour at the page's top edge: its `theme-color`, else its
     * background. Fills what is above the page (the status bar). The web
     * view tells neither, so the page is asked when it loads or changes.
     */
    var topColor by mutableStateOf<Color?>(null)
        private set
    /**
     * The reader scrolled down into the page: the narrow bar makes room.
     * Scrolling up, reaching an end of the page or a tap brings it back.
     */
    var reading by mutableStateOf(false)
    /** Asks sites for their desktop pages. Kept while this page is loaded. */
    var desktopSite by mutableStateOf(false)
        private set
    /** Find on Page is open: its bar shows in place of the address bar. */
    var finding by mutableStateOf(false)
        private set
    /** What Find on Page looks for. */
    var query by mutableStateOf("")
        private set
    /** Where Find on Page is; null while nothing was looked for. */
    var matches by mutableStateOf<Matches?>(null)
        private set

    /** The match shown (from 1; 0 when there is none) and how many there are. */
    data class Matches(val current: Int, val count: Int)

    /** A link held down, at `x`, `y` in the view's pixels: its menu shows there. */
    var heldLink by mutableStateOf<HeldLink?>(null)

    data class HeldLink(val url: String, val x: Int, val y: Int)

    /** The site asks for the camera, the microphone or the location, and waits for the reader. */
    var asking by mutableStateOf<Ask?>(null)
        private set

    /**
     * What `site` asks to do (`what`: "use your camera"…); `answer` takes the
     * reader's choice. Android asks once more for the app itself.
     */
    class Ask(val site: String, val what: String, val answer: (Boolean) -> Unit)

    /** Called when the address or title changed. */
    var changed: ((url: String, title: String) -> Unit)? = null
    /** Called when the page wants `url` in a new tab. */
    var opensTab: ((url: String) -> Unit)? = null
    /** Called when the engine's process for this page died: the view is of no use any more. */
    var died: (() -> Unit)? = null

    val view: WebView = inProfile(Viewer()).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.setSupportMultipleWindows(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        // With the marks of a page inside another app, sites hold features back.
        settings.userAgentString = settings.userAgentString.replace("; wv", "").replace(Regex("Version/\\S+ "), "")
        webViewClient = Navigation()
        webChromeClient = Chrome()
        setFindListener { current, count, _ -> matches = Matches(if (count > 0) current + 1 else 0, count) }
        setOnLongClickListener { held() }
        loadUrl(url)
    }
    private val mobileAgent = view.settings.userAgentString
    /** What Chrome on a computer says, as Chrome's own desktop site does. */
    private val desktopAgent = mobileAgent.replaceFirst(Regex("\\([^)]*\\)"), "(X11; Linux x86_64)").replace(" Mobile", "")
    /** The text size the page started with, in percent; a reset goes back to it. */
    private val siteTextSize = view.settings.textZoom
    /** The size of the page's text, in percent. */
    var textSize by mutableIntStateOf(siteTextSize)
        private set

    /** `view` in this page's profile; the profile must be set before anything else. */
    private fun <V : WebView> inProfile(view: V) = view.also {
        profile?.let { WebViewCompat.setProfile(view, it) }
    }

    fun reload() {
        failure = null
        view.reload()
    }

    fun askDesktopSite(on: Boolean) {
        if (on == desktopSite) return
        desktopSite = on
        view.settings.userAgentString = if (on) desktopAgent else mobileAgent
        // Loaded anew: a reload would keep the zoom of the other layout.
        failure = null
        view.loadUrl(url)
    }

    /** Make the page's text larger (`steps` > 0) or smaller, by tenths, from 50 to 300 percent. */
    fun sizeText(steps: Int) {
        textSize = ((textSize + 5) / 10 * 10 + steps * 10).coerceIn(50, 300)
        view.settings.textZoom = textSize
    }

    fun resetTextSize() {
        textSize = siteTextSize
        view.settings.textZoom = textSize
    }

    fun startFinding() {
        finding = true
    }

    /** Look for `text` in the page and show the first match. */
    fun find(text: String) {
        query = text
        if (text.isEmpty()) {
            view.clearMatches()
            matches = null
        } else {
            view.findAllAsync(text)
        }
    }

    fun findNext(forward: Boolean) {
        if (query.isNotEmpty()) view.findNext(forward)
    }

    fun endFinding() {
        find("")
        finding = false
    }

    private fun readTopColor() = view.evaluateJavascript(TOP_COLOR) { result ->
        // The page can change what the script reads: anything but three
        // numbers is no colour, and never an error.
        val color = runCatching {
            val rgb = JSONArray(result)
            Color(rgb.getInt(0).coerceIn(0, 255), rgb.getInt(1).coerceIn(0, 255), rgb.getInt(2).coerceIn(0, 255))
        }.getOrNull() ?: return@evaluateJavascript
        topColor = color
    }

    /** The page was loaded or changed: what the bars take from it. */
    private fun loaded() {
        readTopColor()
    }

    /**
     * A long press: on a web link, its menu (`heldLink`) in place of the
     * engine's text selection. Elsewhere the engine handles it.
     */
    private fun held(): Boolean {
        val hit = view.hitTestResult
        fun hold(url: String?) {
            if (url != null && url.toUri().scheme?.lowercase() in setOf("http", "https")) heldLink = HeldLink(url, pressedX, pressedY)
        }
        when (hit.type) {
            WebView.HitTestResult.SRC_ANCHOR_TYPE -> hold(hit.extra)
            // The hit names the image; the engine tells the link's address.
            WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE ->
                view.requestFocusNodeHref(Handler(Looper.getMainLooper()) { hold(it.data.getString("url")); true }.obtainMessage())
            else -> return false
        }
        return true
    }

    /** Where the last finger went down, in the view's pixels. */
    private var pressedX = 0
    private var pressedY = 0

    /** A finger is on the page. */
    private var touching = false
    /** When the reader's own scrolling last moved the page. */
    private var readerScrolled = 0L

    /**
     * The page moved down by `delta` px, and is `above` px below its top
     * and `below` px above its end. Only the reader's own scrolling counts,
     * a fling after the finger left included, not a page that scrolls itself.
     */
    private fun scrolled(delta: Int, above: Int, below: Int) {
        val now = SystemClock.uptimeMillis()
        if (!touching && now - readerScrolled > 120) return
        readerScrolled = now
        val dp = view.resources.displayMetrics.density
        reading = when {
            above < 24 * dp || below < 2 * dp -> false
            delta > 4 * dp -> true
            delta < -8 * dp -> false
            else -> reading
        }
    }

    /** The web view of the page, telling it how the reader scrolls. */
    private inner class Viewer : WebView(activity) {
        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touching = true
                    pressedX = event.x.toInt()
                    pressedY = event.y.toInt()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> touching = false
            }
            return super.dispatchTouchEvent(event)
        }

        override fun onScrollChanged(x: Int, y: Int, oldX: Int, oldY: Int) {
            super.onScrollChanged(x, y, oldX, oldY)
            scrolled(y - oldY, y, computeVerticalScrollRange() - computeVerticalScrollExtent() - y)
        }
    }

    private fun history() {
        canGoBack = view.canGoBack()
        canGoForward = view.canGoForward()
    }

    private inner class Navigation : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (request.url.scheme?.lowercase() in setOf("http", "https", "about", "blob", "data")) return false
            // Mail, phone, app links: the system's business, and only when
            // the page itself goes there, not one of its frames.
            if (request.isForMainFrame) {
                val intent = Intent(Intent.ACTION_VIEW, request.url).addCategory(Intent.CATEGORY_BROWSABLE)
                try {
                    activity.startActivity(intent)
                } catch (_: ActivityNotFoundException) {
                    // No app for it: the link does nothing.
                }
            }
            return true
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            loading = true
            failure = null
            reading = false
        }

        override fun onPageCommitVisible(view: WebView, url: String) = loaded()

        override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
            this@WebPage.url = url
            history()
            changed?.invoke(url, title)
            // A loaded page that changed its address itself may have changed its colour too.
            if (!loading) readTopColor()
        }

        override fun onPageFinished(view: WebView, url: String) {
            loading = false
            history()
            loaded()
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) failure = error.description.toString()
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            died?.invoke()
            return true
        }
    }

    private inner class Chrome : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            progress = newProgress / 100f
        }

        override fun onReceivedTitle(view: WebView, title: String?) {
            this@WebPage.title = title.orEmpty()
            changed?.invoke(url, this@WebPage.title)
        }

        override fun onReceivedIcon(view: WebView, icon: Bitmap?) {
            val page = view.url ?: return
            if (!private && icon != null) Favicons.received(page, icon)
        }

        /** A link that asks for a new window opens as a new tab. */
        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
            if (!isUserGesture) return false
            // The engine only tells the address to a web view it may load
            // it in: this one hands it on and goes.
            val probe = inProfile(WebView(activity))
            probe.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    if (request.url.scheme?.lowercase() in setOf("http", "https")) opensTab?.invoke(request.url.toString())
                    view.destroy()
                    return true
                }

                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    view.destroy()
                    return true
                }
            }
            (resultMsg.obj as WebView.WebViewTransport).webView = probe
            resultMsg.sendToTarget()
            return true
        }

        override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
            host.chooseFiles(params.createIntent()) { callback.onReceiveValue(it) }
            return true
        }

        /** The camera or the microphone, for a call for example; nothing else a page may ask for. */
        override fun onPermissionRequest(request: PermissionRequest) {
            val wanted = mapOf(
                PermissionRequest.RESOURCE_VIDEO_CAPTURE to Manifest.permission.CAMERA,
                PermissionRequest.RESOURCE_AUDIO_CAPTURE to Manifest.permission.RECORD_AUDIO,
            ).filterKeys { it in request.resources }
            if (wanted.isEmpty()) return request.deny()
            val what = when (wanted.size) {
                2 -> "use your camera and microphone"
                else -> if (PermissionRequest.RESOURCE_VIDEO_CAPTURE in wanted) "use your camera" else "use your microphone"
            }
            ask(request.origin.host ?: request.origin.toString(), what, wanted.values.toList()) { granted ->
                val allowed = wanted.filterValues { it in granted }.keys
                if (allowed.isEmpty()) request.deny() else request.grant(allowed.toTypedArray())
            }
        }

        override fun onPermissionRequestCanceled(request: PermissionRequest) {
            asking = null
        }

        /** The location, for a map for example. Asked again at the next request. */
        override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
            val location = listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
            ask(origin.toUri().host ?: origin, "know where you are", location) { granted ->
                callback.invoke(origin, granted.isNotEmpty(), false)
            }
        }

        override fun onGeolocationPermissionsHidePrompt() {
            asking = null
        }
    }

    /**
     * Ask the reader whether `site` may `what`, then the system for the app's
     * `permissions`; `done` gets those granted, none for a no. A question
     * still open is answered no.
     */
    private fun ask(site: String, what: String, permissions: List<String>, done: (Set<String>) -> Unit) {
        asking?.answer(false)
        asking = Ask(site, what) { allowed ->
            asking = null
            if (allowed) host.askPermissions(permissions, done) else done(emptySet())
        }
    }
}

/**
 * The colour at the top of the page as `[r, g, b]`: its `theme-color` (for
 * the colour scheme in use), else the background of its body or root, else
 * the engine's own; a colour that is not opaque is laid on white.
 */
private const val TOP_COLOR = """(() => {
  const clear = c => !c || !CSS.supports('color', c) || /^(transparent|rgba\(.*,\s*0\))$/.test(c.trim());
  const meta = [...document.querySelectorAll('meta[name="theme-color" i]')]
    .find(m => !m.media || matchMedia(m.media).matches);
  let color = meta?.content;
  if (clear(color)) color = [document.body, document.documentElement]
    .map(e => e && getComputedStyle(e).backgroundColor).find(c => !clear(c));
  if (!color) {
    const scheme = document.documentElement ? getComputedStyle(document.documentElement).colorScheme : '';
    const dark = /dark/.test(scheme) && (!/light/.test(scheme) || matchMedia('(prefers-color-scheme: dark)').matches);
    color = dark ? '#121212' : '#fff';
  }
  const pen = document.createElement('canvas').getContext('2d');
  pen.fillStyle = '#fff';
  pen.fillRect(0, 0, 1, 1);
  pen.fillStyle = color;
  pen.fillRect(0, 0, 1, 1);
  return [...pen.getImageData(0, 0, 1, 1).data.slice(0, 3)];
})()"""
