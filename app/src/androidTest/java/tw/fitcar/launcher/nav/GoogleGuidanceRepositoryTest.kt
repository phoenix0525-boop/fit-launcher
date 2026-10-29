package tw.fitcar.launcher.nav

import android.app.Notification
import android.content.Context
import android.os.Bundle
import android.os.Process
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import tw.fitcar.launcher.data.LauncherState
import tw.fitcar.launcher.data.Prefs

class GoogleGuidanceRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var original: LauncherState

    @Before fun selectGoogle() {
        val prefs = Prefs.get(context)
        original = prefs.state.value
        prefs.update { it.copy(bindings = it.bindings.copy(navigation = "${GoogleNavigation.PACKAGE}/FixtureActivity")) }
        GoogleGuidanceRepository.connection(true)
    }

    @After fun restoreSettings() {
        Prefs.get(context).update { original }
        GoogleGuidanceRepository.connection(false)
    }

    private fun notification(title: String?, text: String?, category: String = Notification.CATEGORY_NAVIGATION,
        pkg: String = GoogleNavigation.PACKAGE, extras: Bundle = Bundle(), ageMs: Long = 0): StatusBarNotification {
        val n = Notification.Builder(context, "navigation-fixture")
            .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(text)
            .setCategory(category).setOngoing(true).addExtras(extras).build()
        return StatusBarNotification(pkg, pkg, 1, null, Process.myUid(), Process.myPid(), n,
            Process.myUserHandle(), null, System.currentTimeMillis() - ageMs)
    }

    @Test fun navigationCategoryAcceptsDistanceAndRoadWithoutInventingArrow() {
        GoogleGuidanceRepository.posted(context, notification("260 公尺", "測試道路"))
        val state = GoogleGuidanceRepository.state.value
        assertTrue(state.activeNavigation)
        assertTrue(state.fresh())
        assertEquals("260 公尺", state.guidance?.primary)
        assertEquals("測試道路", state.guidance?.secondary)
        assertEquals("260 公尺", state.guidance?.distance)
        assertEquals("", state.guidance?.arrow)
    }

    @Test fun blankBigTextFallsBackToTextLines() {
        val extras = Bundle().apply {
            putCharSequence(Notification.EXTRA_BIG_TEXT, "  ")
            putCharSequenceArray(Notification.EXTRA_TEXT_LINES, arrayOf("測試道路"))
        }
        GoogleGuidanceRepository.posted(context, notification("260 公尺", "", extras = extras))
        assertEquals("測試道路", GoogleGuidanceRepository.state.value.guidance?.secondary)
    }

    @Test fun unrelatedMapsNotificationIsNotNavigation() {
        GoogleGuidanceRepository.posted(context, notification("位置分享", "已在地圖上與你分享", Notification.CATEGORY_SOCIAL))
        assertFalse(GoogleGuidanceRepository.state.value.activeNavigation)
        assertNull(GoogleGuidanceRepository.state.value.guidance)
    }

    @Test fun anotherPackageCannotUseTrustedNavigationCategory() {
        GoogleGuidanceRepository.posted(context, notification("260 公尺", "測試道路", pkg = "test.other"))
        assertFalse(GoogleGuidanceRepository.state.value.activeNavigation)
    }

    @Test fun recoveredOldNavigationTextDoesNotBecomeFresh() {
        GoogleGuidanceRepository.posted(context, notification("260 公尺", "測試道路", ageMs = 9_000))
        assertNotNull(GoogleGuidanceRepository.state.value.guidance)
        assertFalse(GoogleGuidanceRepository.state.value.fresh())
    }
}
