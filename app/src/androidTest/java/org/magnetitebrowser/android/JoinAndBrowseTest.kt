package org.magnetitebrowser.android

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Joins the demo chain and opens a page from it. Needs the demo sync server
 * of magnetite-mobile-core on the machine the emulator runs on
 * (`cargo run --example demo_server`) and an app without data
 * (`scripts/test.sh` sees to that).
 */
@RunWith(AndroidJUnit4::class)
class JoinAndBrowseTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val context = instrumentation.targetContext

    /** The demo chain's sync code (32 zero bytes). */
    private val words = List(23) { "abandon" }.joinToString(" ") + " art"
    /** The demo server; `scripts/test.sh` makes the device's port lead to this machine's. */
    private val server = "http://localhost:8787"
    /** As wide as the app's layout with the sidebar beside the page needs. */
    private val wide get() = device.displayWidth / context.resources.displayMetrics.density >= 720

    @Before
    fun launch() {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)!!
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        device.wait(Until.hasObject(By.pkg(context.packageName).depth(0)), 10_000)
    }

    @Test
    fun joinShowsTheChainsSpacesAndOpensAPage() {
        // The steps are those of the narrow layout: the sidebar is a sheet.
        assumeTrue(!wide)
        join()
        // The chain's first space, with its favorites and pinned tree.
        assertTrue(has(By.text("MDN Web Docs")))
        keep("1-home-space")

        // A folded folder hides what is in it, at every depth.
        tap(By.text("Reading"))
        assertTrue(device.wait(Until.gone(By.text("MDN Web Docs")), 5_000))
        tap(By.text("Reading"))

        tap(By.res("space.space-work"))
        find(By.text("magnetite"))
        assertFalse(has(By.text("Wikipedia")))
        keep("2-work-space")

        tap(By.res("space.space-home"))
        tap(By.text("Example"))
        find(By.textContains("Learn more"), 30_000)
        assertTrue(has(By.text("example.org")))
        keep("3-page")

        // The tab is listed as open here, in the space it was opened from.
        tap(By.desc("Tabs"))
        find(By.text("OPEN HERE"))
        keep("4-sidebar-over-page")

        // The chain learns of the tab, a desktop takes it over, and at the
        // next sync it is closed here: the app is back on its start page.
        tap(By.desc("Settings"))
        tap(By.text("Sync Now"))
        Thread.sleep(2_000)
        assertTrue("the phone listed its tab", take() >= 1)
        tap(By.text("Sync Now"))
        Thread.sleep(2_000)
        tap(By.desc("Done"))
        find(By.textStartsWith("No tabs open in this space."))
        keep("5-taken-over")

        // A site whose page was loaded here shows its icon from then on.
        tap(By.text("Wikipedia"))
        find(By.textContains("wikipedia.org"), 30_000)
        Thread.sleep(6_000)
        tap(By.desc("Tabs"))
        find(By.text("OPEN HERE"))
        keep("6-site-icon")
    }

    /** On a tablet the sidebar stays beside the page. */
    @Test
    fun onATabletTheSidebarStaysBesideThePage() {
        assumeTrue(wide)
        join()
        keep("tablet-1-no-tab")

        tap(By.text("Example"))
        find(By.textContains("Learn more"), 30_000)
        // The page is open and the sidebar still there, listing it.
        assertTrue(has(By.text("Wikipedia")))
        assertTrue(has(By.text("OPEN HERE")))
        keep("tablet-2-page")

        tap(By.desc("Sidebar"))
        assertTrue(device.wait(Until.gone(By.text("Wikipedia")), 5_000))
        keep("tablet-3-sidebar-hidden")
    }

    private fun join() {
        tap(By.text("Join a Sync Chain"))
        tap(By.text("Your own server"))
        find(By.res("join.server")).text = server
        find(By.res("join.words")).text = words
        // The button takes a moment to see that the words are complete.
        find(By.res("join.go").enabled(true)).click()
        find(By.text("Wikipedia"), 15_000)
    }

    private fun has(what: BySelector) = device.hasObject(what)

    private fun find(what: BySelector, timeout: Long = 5_000): UiObject2 {
        val found = device.wait(Until.findObject(what), timeout)
        if (found == null) keep("failure")
        assertNotNull("not on screen: $what", found)
        return found
    }

    private fun tap(what: BySelector) = find(what).click()

    /** The demo server plays a desktop that takes over every listed tab; how many it took. */
    private fun take(): Int {
        val request = URL("$server/demo/take").openConnection() as HttpURLConnection
        request.requestMethod = "POST"
        // More than one when the server still holds an earlier run's phone.
        return request.inputStream.bufferedReader().readText().trim().toIntOrNull() ?: 0
    }

    /** A picture of the screen, kept in the app's files (`scripts/test.sh` fetches them). */
    private fun keep(name: String) {
        val dir = File(context.filesDir, "shots").apply { mkdirs() }
        device.waitForIdle()
        device.takeScreenshot(File(dir, "$name.png"))
    }
}
