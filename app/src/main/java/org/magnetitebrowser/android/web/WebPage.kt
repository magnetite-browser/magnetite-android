package org.magnetitebrowser.android.web

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.webkit.WebViewCompat
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

    /** Called when the address or title changed. */
    var changed: ((url: String, title: String) -> Unit)? = null
    /** Called when the page wants `url` in a new tab. */
    var opensTab: ((url: String) -> Unit)? = null
    /** Called when the engine's process for this page died: the view is of no use any more. */
    var died: (() -> Unit)? = null

    val view = webView().apply {
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
        loadUrl(url)
    }

    /** A web view in this page's profile; the profile must be set before anything else. */
    private fun webView() = WebView(activity).also { view ->
        profile?.let { WebViewCompat.setProfile(view, it) }
    }

    fun reload() {
        failure = null
        view.reload()
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
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
            this@WebPage.url = url
            history()
            changed?.invoke(url, title)
        }

        override fun onPageFinished(view: WebView, url: String) {
            loading = false
            history()
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
            val probe = webView()
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
    }
}
