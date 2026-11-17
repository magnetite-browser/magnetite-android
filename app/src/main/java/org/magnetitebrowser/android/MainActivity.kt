package org.magnetitebrowser.android

import android.content.ComponentCallbacks2
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.webkit.WebChromeClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import org.magnetitebrowser.android.model.Address
import org.magnetitebrowser.android.model.Browser
import org.magnetitebrowser.android.ui.MagnetiteTheme
import org.magnetitebrowser.android.ui.RootView
import org.magnetitebrowser.android.ui.tint
import org.magnetitebrowser.android.web.WebPage

class MainActivity : ComponentActivity(), WebPage.Host {
    private lateinit var browser: Browser
    /** Gets the files of the picker that is open, if one is. */
    private var filesChosen: ((Array<Uri>?) -> Unit)? = null
    private val picker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        filesChosen?.invoke(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data))
        filesChosen = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        browser = Browser(this, this, lifecycleScope)
        if (savedInstanceState == null) openLink(intent)
        setContent {
            MagnetiteTheme(browser.space.tint) { RootView(browser) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openLink(intent)
    }

    /** A link another app handed over opens as a new tab. */
    private fun openLink(intent: Intent) {
        val url = intent.takeIf { it.action == Intent.ACTION_VIEW }?.dataString ?: return
        if (Address.isWeb(url)) browser.open(url)
    }

    override fun onStart() {
        super.onStart()
        browser.syncIfDue()
    }

    override fun onStop() {
        super.onStop()
        browser.shareTabsSoon()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // The app is out of sight and the system wants memory back.
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) browser.unloadHidden()
    }

    override fun chooseFiles(picker: Intent, done: (Array<Uri>?) -> Unit) {
        // A page that asks again while a picker is open gets no files for the first.
        filesChosen?.invoke(null)
        filesChosen = done
        this.picker.launch(picker)
    }

    /** Keys of a hardware keyboard, as on the desktop. */
    override fun onKeyShortcut(keyCode: Int, event: KeyEvent): Boolean {
        if (!event.isCtrlPressed) return super.onKeyShortcut(keyCode, event)
        val tab = browser.frontTab
        when (keyCode) {
            KeyEvent.KEYCODE_L -> browser.askAddress()
            KeyEvent.KEYCODE_T -> browser.newTab()
            KeyEvent.KEYCODE_W -> tab?.let(browser::close)
            KeyEvent.KEYCODE_R -> tab?.let { browser.page(it).reload() }
            else -> return super.onKeyShortcut(keyCode, event)
        }
        return true
    }
}
