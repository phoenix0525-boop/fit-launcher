package tw.fitcar.launcher.nav

import android.app.Notification
import android.content.Context
import android.os.Bundle
import android.os.Process
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import tw.fitcar.launcher.data.LauncherState
import tw.fitcar.launcher.data.Prefs

class GoogleGuidanceRepositoryTest {
    @Test fun authoritativeSnapshotNeverPublishesEmptyBetweenValidUpdates() {
        GoogleGuidanceRepository.posted(context, notification("260 公尺", "測試道路"))
        val observed = mutableListOf<Boolean>()
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
        val job = scope.launchSnapshot(observed)
        try {
            GoogleGuidanceRepository.replaceSnapshot(context, listOf(notification("200 公尺", "測試道路")))
            assertTrue(observed.isNotEmpty()); assertTrue(observed.all { it })
            assertEquals("200 公尺", GoogleGuidanceRepository.state.value.guidance?.primary)
        } finally { job.cancel() }
    }

    private fun kotlinx.coroutines.CoroutineScope.launchSnapshot(observed: MutableList<Boolean>) =
        launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            GoogleGuidanceRepository.state.collect { observed += it.activeNavigation }
        }

    @Test fun blankSameKeyUpdateKeepsPreviousTextWithoutRenewingAge() {
        GoogleGuidanceRepository.posted(context, notification("260 公尺", "測試道路"))
        val before = GoogleGuidanceRepository.state.value
        GoogleGuidanceRepository.posted(context, notification(null, null))
        val after = GoogleGuidanceRepository.state.value
        assertEquals(before.guidance, after.guidance)
        assertEquals(before.receivedElapsed, after.receivedElapsed)
    }

    @Test fun emptySnapshotRemovesTurnAndNavigationState() {
        GoogleGuidanceRepository.posted(context, notification("260 公尺", "測試道路"))
        GoogleGuidanceRepository.replaceSnapshot(context, emptyList())
        assertFalse(GoogleGuidanceRepository.state.value.activeNavigation)
        assertNull(GoogleGuidanceRepository.state.value.guidance)
    }
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

    @Suppress("DEPRECATION") // Public SDK constructor; no hidden overrideGroupKey API.
    private fun notification(title: String?, text: String?, category: String = Notification.CATEGORY_NAVIGATION,
        pkg: String = GoogleNavigation.PACKAGE, extras: Bundle = Bundle(), ageMs: Long = 0): StatusBarNotification {
        val n = Notification.Builder(context, "navigation-fixture")
            .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(text)
            .setCategory(category).setOngoing(true).addExtras(extras).build()
        return StatusBarNotification(pkg, pkg, 1, null, Process.myUid(), Process.myPid(), 0, n,
            Process.myUserHandle(), System.currentTimeMillis() - ageMs)
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
