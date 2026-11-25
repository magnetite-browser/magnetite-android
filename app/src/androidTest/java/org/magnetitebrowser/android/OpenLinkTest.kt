package org.magnetitebrowser.android

import android.content.Intent
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Web links another app hands over (Magnetite as the default browser) open
 * as new tabs, at launch and while the app runs. Needs no chain, and leaves
 * no tab open for the other tests.
 */
@RunWith(AndroidJUnit4::class)
class OpenLinkTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val context = instrumentation.targetContext

    @Test
    fun aLinkFromAnotherAppOpensAsATab() {
        // The app starts with the link.
        open("https://example.net/", Intent.FLAG_ACTIVITY_CLEAR_TASK)
        find(By.text("example.net"), 30_000)

        // A link while Settings is open opens over it.
        if (!device.hasObject(By.desc("Settings"))) tap(By.desc("Tabs"))
        tap(By.desc("Settings"))
        find(By.text("Default Browser"))
        open("https://iana.org/")
        assertTrue(device.wait(Until.gone(By.text("Default Browser")), 5_000))
        find(By.text("iana.org"), 30_000)

        // Both are open: close them, and any tab an earlier test left.
        tap(By.desc("More"))
        tap(By.text("Close Tab"))
        find(By.desc("Close"))
        if (device.hasObject(By.text("Close All"))) tap(By.text("Close All")) else tap(By.desc("Close"))
        find(By.textStartsWith("No tabs open in this space."))
    }

    /** Hand `url` to the app as another app would. */
    private fun open(url: String, flags: Int = 0) {
        val link = Intent(Intent.ACTION_VIEW, url.toUri())
            .setPackage(context.packageName)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or flags)
        context.startActivity(link)
    }

    private fun find(what: BySelector, timeout: Long = 5_000): UiObject2 {
        val found = device.wait(Until.findObject(what), timeout)
        assertNotNull("not on screen: $what", found)
        return found
    }

    private fun tap(what: BySelector) = find(what).click()
}
