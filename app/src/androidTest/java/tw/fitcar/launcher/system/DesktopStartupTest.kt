package tw.fitcar.launcher.system

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import tw.fitcar.launcher.MainActivity

class DesktopStartupTest {
    private class FixtureContext : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
        private val prefix = "startup-fixture-${UUID.randomUUID()}-"
        var launched: Intent? = null
        var reject = false
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            baseContext.getSharedPreferences(prefix + name, mode)
        override fun startActivity(intent: Intent) {
            if (reject) throw SecurityException("fixture rejection")
            launched = intent
        }
    }

    @Test fun disabledOrTimeBroadcastDoesNotOpenDesktop() {
        val c = FixtureContext()
        DesktopStartup.request(c, Intent.ACTION_BOOT_COMPLETED, false)
        DesktopStartup.request(c, Intent.ACTION_TIME_CHANGED, true)
        DesktopStartup.request(c, Intent.ACTION_MY_PACKAGE_REPLACED, true)
        assertNull(c.launched)
        assertEquals("none", DesktopStartup.status(c))
    }

    @Test fun requestIsExplicitAndNotMisreportedAsSuccessful() {
        val c = FixtureContext()
        DesktopStartup.request(c, Intent.ACTION_BOOT_COMPLETED, true)
        val intent = c.launched!!
        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertFalse(intent.categories.orEmpty().contains(Intent.CATEGORY_HOME))
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertEquals("requested", DesktopStartup.status(c))
        assertFalse(DesktopStartup.confirm(c, Intent(intent).putExtra(DesktopStartup.EXTRA_REQUEST, "wrong")))
        assertEquals("requested", DesktopStartup.status(c))
        assertTrue(DesktopStartup.confirm(c, intent))
        assertEquals("confirmed", DesktopStartup.status(c))
        assertFalse(DesktopStartup.confirm(c, intent))
    }

    @Test fun manualLaunchCannotConfirmAnUnacknowledgedBootRequest() {
        val c = FixtureContext()
        DesktopStartup.request(c, Intent.ACTION_BOOT_COMPLETED, true)
        assertFalse(DesktopStartup.confirm(c, Intent(c, MainActivity::class.java)))
        assertEquals("requested", DesktopStartup.status(c))
    }

    @Test fun rejectedRequestDoesNotCrashOrClaimSuccess() {
        val c = FixtureContext().apply { reject = true }
        DesktopStartup.request(c, Intent.ACTION_BOOT_COMPLETED, true)
        assertEquals("rejected", DesktopStartup.status(c))
    }
    @Test fun startupPreferencePersistsWithoutChangingOtherSettings() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = tw.fitcar.launcher.data.Prefs.get(context)
        val before = prefs.state.value
        try {
            prefs.update { it.copy(startOnBoot = true) }
            prefs.reload()
            assertTrue(prefs.state.value.startOnBoot)
            assertEquals(before.bindings, prefs.state.value.bindings)
            assertEquals(before.visualStyle, prefs.state.value.visualStyle)
            prefs.update { it.copy(startOnBoot = false) }
            prefs.reload()
            assertFalse(prefs.state.value.startOnBoot)
        } finally { prefs.update { before } }
    }
}
