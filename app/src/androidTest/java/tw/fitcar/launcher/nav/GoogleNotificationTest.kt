package tw.fitcar.launcher.nav

import android.app.Notification
import android.content.ComponentName
import android.os.Process
import android.service.notification.StatusBarNotification
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test
import tw.fitcar.launcher.data.Prefs

class GoogleNotificationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var oldBinding: String? = null
    @Before fun prepare() {
        oldBinding = Prefs.get(context).state.value.bindings.navigation
        Prefs.get(context).update { it.copy(bindings = it.bindings.copy(navigation = ComponentName(GoogleNavigation.PACKAGE, "MapsActivity").flattenToString())) }
        GoogleGuidanceRepository.connection(true)
    }
    @After fun restore() {
        Prefs.get(context).update { it.copy(bindings = it.bindings.copy(navigation = oldBinding)) }
        GoogleGuidanceRepository.connection(false)
    }
    private fun notification(pkg: String = GoogleNavigation.PACKAGE, age: Long = 0L): StatusBarNotification {
        val n = Notification.Builder(context, "navigation-test").setContentTitle("300 公尺後右轉")
            .setContentText("進入中山路").setCategory(Notification.CATEGORY_NAVIGATION).setOngoing(true).build()
        return StatusBarNotification(pkg, pkg, 1, null, Process.myUid(), Process.myPid(), 0, n, Process.myUserHandle(), System.currentTimeMillis() - age)
    }
    @Test fun selectedGoogleNotificationProvidesFreshHint() {
        GoogleGuidanceRepository.posted(context, notification())
        assertEquals("→", GoogleGuidanceRepository.state.value.guidance?.arrow)
        assertTrue(GoogleGuidanceRepository.state.value.fresh())
    }
    @Test fun unrelatedAppsAreIgnored() {
        GoogleGuidanceRepository.posted(context, notification("test.other.app"))
        assertNull(GoogleGuidanceRepository.state.value.guidance)
    }
    @Test fun googleIsIgnoredWhenAnotherNavigationIsSelected() {
        Prefs.get(context).update { it.copy(bindings = it.bindings.copy(navigation = "test.nav/Nav")) }
        GoogleGuidanceRepository.posted(context, notification())
        assertNull(GoogleGuidanceRepository.state.value.guidance)
    }
    @Test fun oldNotificationAndDisconnectCannotShowOldTurnAsFresh() {
        GoogleGuidanceRepository.posted(context, notification(age = 9000))
        assertFalse(GoogleGuidanceRepository.state.value.fresh())
        GoogleGuidanceRepository.connection(false)
        assertNull(GoogleGuidanceRepository.state.value.guidance)
    }
}
