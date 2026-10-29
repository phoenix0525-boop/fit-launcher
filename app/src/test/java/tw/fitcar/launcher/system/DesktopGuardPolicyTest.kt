package tw.fitcar.launcher.system

import org.junit.Assert.*
import org.junit.Test
import tw.fitcar.launcher.system.DesktopGuardPolicy.Action
import tw.fitcar.launcher.system.DesktopGuardPolicy.OverlayChange

class DesktopGuardPolicyTest {
    private val homes = setOf("com.android.launcher43/com.android.launcher43.Launcher")
    private fun d(now: Long, cls: String = "com.android.launcher43.Launcher", returns: List<Long> = emptyList(), paused: Long = 0, enabled: Boolean = true) =
        DesktopGuardPolicy.decide(enabled, now, "com.android.launcher43", cls, homes, paused, returns)

    @Test fun returnsOnlyFromFactoryHomeActivity() {
        assertEquals(Action.RETURN, d(10_000).action)
        assertEquals(Action.IGNORE, d(10_000, cls = "com.android.launcher43.RadioActivity").action)
        assertEquals(Action.IGNORE, d(10_000, enabled = false).action)
        assertEquals(Action.IGNORE, DesktopGuardPolicy.decide(true, 10_000, "com.other", "X", homes, 0, emptyList()).action)
    }
    @Test fun manualOrLoopPauseIsRespected() {
        assertEquals(Action.IGNORE, d(10_000, paused = 20_000).action)
        assertEquals(Action.RETURN, d(20_000, paused = 20_000).action)
    }
    @Test fun loopProtectionPausesAfterThreeReturns() {
        val r1 = d(10_000); val r2 = d(15_000, returns = r1.returns); val r3 = d(20_000, returns = r2.returns)
        assertEquals(listOf(10_000L, 15_000L, 20_000L), r3.returns)
        val r4 = d(25_000, returns = r3.returns)
        assertEquals(Action.PAUSE_LOOP, r4.action); assertEquals(25_000 + DesktopGuardPolicy.LOOP_PAUSE_MS, r4.pausedUntil)
        assertEquals(Action.IGNORE, d(26_000, returns = r4.returns, paused = r4.pausedUntil).action)
        // 30 秒前的紀錄不算
        assertEquals(Action.RETURN, d(60_000, returns = r3.returns).action)
    }
    @Test fun tooFrequentEventsAreIgnored() {
        assertEquals(Action.IGNORE, d(11_000, returns = listOf(10_000L)).action)
    }
    @Test fun overlayFollowsNavigationForeground() {
        val nav = setOf("com.google.android.apps.maps", "com.kingwaytek.a5i_3d.plus")
        assertEquals(OverlayChange.SHOW, DesktopGuardPolicy.overlay(true, "com.google.android.apps.maps", nav, "tw.fitcar.launcher"))
        assertEquals(OverlayChange.HIDE, DesktopGuardPolicy.overlay(true, "tw.fitcar.launcher", nav, "tw.fitcar.launcher"))
        assertEquals(OverlayChange.HIDE, DesktopGuardPolicy.overlay(true, "com.kkbox", nav, "tw.fitcar.launcher"))
        assertEquals(OverlayChange.KEEP, DesktopGuardPolicy.overlay(true, "com.android.systemui", nav, "tw.fitcar.launcher"))
        assertEquals(OverlayChange.KEEP, DesktopGuardPolicy.overlay(true, "com.google.android.inputmethod.latin", nav, "tw.fitcar.launcher"))
        assertEquals(OverlayChange.HIDE, DesktopGuardPolicy.overlay(false, "com.google.android.apps.maps", nav, "tw.fitcar.launcher"))
    }
    @Test fun adbCommandsAreUserRunTextOnly() {
        assertEquals("adb shell cmd package set-home-activity tw.fitcar.launcher/.MainActivity", AdbCommands.setHome("tw.fitcar.launcher/.MainActivity"))
        assertTrue(AdbCommands.addHomeRole("tw.fitcar.launcher").endsWith("android.app.role.HOME tw.fitcar.launcher"))
    }
}
