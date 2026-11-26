package org.magnetitebrowser.android

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Assert.assertEquals
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
 * (`scripts/test.sh` sees to that). The phone test changes the chain's
 * sidebar: start the demo server again before the next run.
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

        // A space's icon below shows its page; swiping goes back to the one before.
        tap(By.res("space.space-work"))
        find(By.text("magnetite"))
        // Once the pages have stopped moving, the first space's is gone.
        assertTrue(device.wait(Until.gone(By.text("Wikipedia")), 5_000))
        keep("2-work-space")
        device.swipe(device.displayWidth / 5, device.displayHeight / 2, device.displayWidth * 4 / 5, device.displayHeight / 2, 10)
        find(By.text("Wikipedia"))

        tap(By.text("Example"))
        find(By.textContains("Learn more"), 30_000)
        assertTrue(has(By.text("example.org")))
        keep("3-page")

        // Holding a link offers to open it behind the page in front.
        find(By.textContains("Learn more")).longClick()
        find(By.text("Open in Background"))
        keep("3-link-menu")
        tap(By.text("Open in Background"))
        assertTrue(device.wait(Until.gone(By.text("Open in Background")), 5_000))
        assertTrue(has(By.text("example.org")))

        // Find on Page takes the bar's place and counts the matches.
        tap(By.desc("More"))
        tap(By.text("Find on Page"))
        find(By.res("find")).text = "Example"
        find(By.textStartsWith("1 of"))
        tap(By.desc("Done"))

        // New Tab: the start page, typing an address. A word offers the
        // chain's page that matches; picking it opens it and ends the typing.
        tap(By.desc("More"))
        tap(By.text("New Tab"))
        find(By.res("address").focused(true)).text = "rust"
        find(By.text("rust-lang.org · Home"))
        keep("4-suggestions")
        tap(By.text("rust-lang.org · Home"))
        assertTrue(device.wait(Until.gone(By.text("PINNED")), 5_000))
        find(By.text("rust-lang.org"), 30_000)

        // The tabs are listed as open here, in the space they were opened
        // from, the one opened behind too.
        tap(By.desc("Tabs"))
        find(By.text("OPEN HERE"))
        assertTrue(has(By.text("iana.org")))
        keep("5-sidebar-over-page")

        // The chain learns of the tab, a desktop takes it over, and at the
        // next sync it is closed here: the app is back on its start page.
        tap(By.desc("Settings"))
        tap(By.text("Sync Now"))
        Thread.sleep(2_000)
        assertTrue("the phone listed its tab", demo("take") >= 1)
        tap(By.text("Sync Now"))
        Thread.sleep(2_000)
        tap(By.desc("Done"))
        find(By.textStartsWith("No tabs open in this space."))
        keep("6-taken-over")

        // A site whose page was loaded here shows its icon from then on.
        tap(By.text("Wikipedia"))
        find(By.textContains("wikipedia.org"), 30_000)
        Thread.sleep(6_000)
        tap(By.desc("Tabs"))
        find(By.text("OPEN HERE"))
        keep("7-site-icon")

        // From the phone: pin the page from its menu, and rename a favorite
        // by holding it. Both show at once, and the mark that they wait
        // goes as soon as the phone wrote them into the chain.
        tap(By.desc("Done"))
        tap(By.desc("More"))
        find(By.text("Pin in “Home”"))
        keep("8-page-menu")
        tap(By.text("Pin in “Home”"))
        tap(By.desc("Tabs"))
        assertTrue(device.wait(Until.gone(By.desc("Waiting to sync")), 10_000))
        find(By.text("Wikipedia")).longClick()
        find(By.text("Rename…"))
        keep("9-holding")
        tap(By.text("Rename…"))
        find(By.res("name")).text = "Wiki"
        keep("10-renaming")
        // The button, not the dialog's title.
        tap(By.clickable(true).hasDescendant(By.text("Rename")))
        assertTrue(device.wait(Until.gone(By.res("name")), 5_000))
        find(By.text("Wiki"))
        assertTrue(device.wait(Until.gone(By.desc("Waiting to sync")), 10_000))
        keep("11-written")

        // The phone wrote both itself: no desktop is asked to make them,
        // and they are still there after a sync.
        tap(By.desc("Settings"))
        tap(By.text("Sync Now"))
        Thread.sleep(2_000)
        assertEquals("nothing left for a desktop", 0, demo("apply"))
        tap(By.text("Sync Now"))
        Thread.sleep(2_000)
        tap(By.desc("Done"))
        tap(By.desc("Tabs"))
        find(By.text("Wiki"))
        assertTrue(device.wait(Until.gone(By.desc("Waiting to sync")), 10_000))
        assertTrue(has(By.text("Wikipedia, the free encyclopedia")))
        keep("12-made")
    }

    /** On a tablet the sidebar stays beside the page. */
    @Test
    fun onATabletTheSidebarStaysBesideThePage() {
        assumeTrue(wide)
        join()
        keep("tablet-1-no-tab")

        // What a word typed could mean shows below the field.
        find(By.res("address")).click()
        find(By.res("address").focused(true)).text = "rust"
        find(By.text("rust-lang.org · Home"))
        keep("tablet-2-suggestions")

        // Picking a page from the sidebar ends the typing.
        tap(By.text("Example"))
        find(By.textContains("Learn more"), 30_000)
        assertTrue(device.wait(Until.gone(By.text("rust-lang.org · Home")), 5_000))
        // The page is open and the sidebar still there, listing it.
        assertTrue(has(By.text("Wikipedia")))
        assertTrue(has(By.text("OPEN HERE")))
        keep("tablet-3-page")

        tap(By.desc("Sidebar"))
        assertTrue(device.wait(Until.gone(By.text("Wikipedia")), 5_000))
        keep("tablet-4-sidebar-hidden")
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

    /**
     * The demo server plays a desktop: `take` takes over every listed tab,
     * `apply` makes the changes the phones asked for. Returns how many.
     */
    private fun demo(action: String): Int {
        val request = URL("$server/demo/$action").openConnection() as HttpURLConnection
        request.requestMethod = "POST"
        // More than this phone's when the server still holds an earlier run's phone.
        return request.inputStream.bufferedReader().readText().trim().toIntOrNull() ?: 0
    }

    /** A picture of the screen, kept in the app's files (`scripts/test.sh` fetches them). */
    private fun keep(name: String) {
        val dir = File(context.filesDir, "shots").apply { mkdirs() }
        device.waitForIdle()
        device.takeScreenshot(File(dir, "$name.png"))
    }
}
