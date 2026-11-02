package tw.fitcar.launcher.system

import org.junit.Assert.*
import org.junit.Test
import tw.fitcar.launcher.system.LaunchGate.Reason

class LaunchGateTest {
    private val own = "tw.fitcar.launcher"
    private val factory = setOf("com.syu.launcher")
    private val nav = setOf("com.google.android.apps.maps", "com.kingwaytek.a5i_3d.plus")
    private fun d(reason: Reason, now: Long = 20_000, fg: String? = null, last: Long? = null, boot: Boolean = false) =
        LaunchGate.decide(reason, now, fg, own, last, boot, factory, nav)

    @Test fun neverCoversReverseCameraOnAnyPath() {
        Reason.entries.forEach { r ->
            assertFalse(r.name, d(r, fg = "com.syu.fourcamera2").launch)
            assertFalse(r.name, d(r, fg = "com.baony.avm360").launch)
        }
        assertTrue(LaunchGate.isCamera("com.vendor.RearCamera"))
        assertFalse(LaunchGate.isCamera("com.syu.launcher"))
    }
    @Test fun bootLaunchesOnlyOncePerBoot() {
        assertTrue(d(Reason.BOOT_BROADCAST).launch)
        assertFalse(d(Reason.BOOT_SERVICE, boot = true).launch)
        assertFalse(d(Reason.BOOT_BROADCAST, boot = true).launch)
    }
    @Test fun serviceReconnectAfterBootWindowOrInOtherAppDoesNotPullUserBack() {
        assertFalse(d(Reason.BOOT_SERVICE, now = LaunchGate.BOOT_WINDOW_MS + 1).launch)
        assertFalse(d(Reason.BOOT_SERVICE, fg = "com.kingwaytek.naviking").launch)
        assertTrue(d(Reason.BOOT_SERVICE, fg = "com.syu.launcher").launch)
        assertTrue(d(Reason.BOOT_SERVICE, fg = null).launch)
    }
    @Test fun minimumGapAppliesExceptFactoryReturn() {
        assertFalse(d(Reason.SCREEN_ON, now = 20_000, last = 17_000).launch)
        assertTrue(d(Reason.SCREEN_ON, now = 20_000, last = 14_000).launch)
        // 原廠剛搶回桌面時仍要能切回（原廠切回自有 2 秒間隔與防迴圈）
        assertTrue(d(Reason.FACTORY_RETURN, now = 20_000, last = 19_000, fg = "com.syu.launcher").launch)
    }
    @Test fun alreadyForegroundIsSkipped() {
        Reason.entries.forEach { assertFalse(d(it, fg = own).launch) }
    }
    @Test fun sameBootDetection() {
        assertTrue(LaunchGate.sameBoot(1_000_000, 1_100_000, 100_000))
        assertTrue(LaunchGate.sameBoot(1_000_000, 1_130_000, 100_000))
        assertFalse(LaunchGate.sameBoot(1_000_000, 5_000_000, 100_000))
        assertFalse(LaunchGate.sameBoot(null, 1_100_000, 100_000))
    }
    // 0.4.35
    @Test fun screenOnSkipsWhileNavigationForeground() {
        assertFalse(d(Reason.SCREEN_ON, fg = "com.kingwaytek.a5i_3d.plus").launch)
        assertFalse(d(Reason.SCREEN_ON, fg = "com.google.android.apps.maps").launch)
        assertTrue(d(Reason.SCREEN_ON, fg = "com.syu.launcher").launch)
        assertTrue(d(Reason.SCREEN_ON, fg = "com.kkbox.android").launch)
    }
    @Test fun bootServiceTreatsSystemOverlaysAsNeutral() {
        assertTrue(d(Reason.BOOT_SERVICE, fg = "com.android.systemui").launch)
        assertTrue(d(Reason.BOOT_SERVICE, fg = "com.syu.ms").launch)
        assertTrue(d(Reason.BOOT_SERVICE, fg = "com.google.android.inputmethod.latin").launch)
        assertFalse(d(Reason.BOOT_SERVICE, fg = "com.kkbox.android").launch)
    }
    @Test fun bootCountWinsOverJumpingWallClock() {
        // 開機後校時：牆上時間跳了 2 小時，但開機次數相同 → 仍是同一次開機
        assertTrue(LaunchGate.sameBootId(57, 57, 1_000_000, 1_000_000 + 7_200_000 + 100_000, 100_000))
        assertFalse(LaunchGate.sameBootId(56, 57, 1_000_000, 1_100_000, 100_000))
        // 讀不到開機次數才退回牆上時間
        assertTrue(LaunchGate.sameBootId(null, -1, 1_000_000, 1_100_000, 100_000))
        assertTrue(LaunchGate.sameBootId(57, -1, 1_000_000, 1_100_000, 100_000))
    }
}
