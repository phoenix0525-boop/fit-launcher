package tw.fitcar.launcher.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrivingLogicTest {
    @Test fun drivingModeHasHysteresisAndTunnelHold() {
        assertFalse(DrivingMode.next(false, 9f, true, 0))
        assertTrue(DrivingMode.next(false, 10f, true, 0))
        // 8 km/h 塞車中不退出，避免版面跳動
        assertTrue(DrivingMode.next(true, 8f, true, 0))
        assertFalse(DrivingMode.next(true, 3f, true, 0))
        // 隧道短暫失聯：沿用前一狀態
        assertTrue(DrivingMode.next(true, null, false, 20_000))
        // 失聯超過 60 秒（停進地下室）回停車版面
        assertFalse(DrivingMode.next(true, null, false, 60_000))
        assertFalse(DrivingMode.next(false, null, false, 0))
    }

    @Test fun speedAlertUsesUserThresholdWithHysteresis() {
        assertFalse(SpeedAlert.next(false, 120f, true, 0))
        assertFalse(SpeedAlert.next(false, 109f, true, 110))
        assertTrue(SpeedAlert.next(false, 110f, true, 110))
        assertTrue(SpeedAlert.next(true, 108f, true, 110))
        assertFalse(SpeedAlert.next(true, 106f, true, 110))
        // 沒有新鮮 GPS 就不提醒，不猜測
        assertFalse(SpeedAlert.next(true, 130f, false, 110))
        assertEquals(0, SpeedAlert.clampThreshold(-5))
        assertEquals(30, SpeedAlert.clampThreshold(10))
        assertEquals(200, SpeedAlert.clampThreshold(260))
    }

    @Test fun tripAddsRealMovementOnly() {
        var t = TripState()
        t = Trip.add(t, 25.0, 121.0, 5f, 50f, 1_000)
        assertEquals(0.0, t.distanceM, 0.001)
        // 約 111 m 北移，1 秒後（400 km/h 不合理）→ 視為跳點，不累加
        t = Trip.add(t, 25.001, 121.0, 5f, 50f, 2_000)
        assertEquals(0.0, t.distanceM, 0.001)
        // 10 秒後再北移約 111 m（40 km/h）→ 累加
        t = Trip.add(t, 25.002, 121.0, 5f, 40f, 12_000)
        assertEquals(111.2, t.distanceM, 1.0)
        assertEquals(10_000L, t.movingMs)
        // 精度太差忽略
        val same = Trip.add(t, 25.01, 121.0, 80f, 40f, 13_000)
        assertEquals(t, same)
        // 停車飄移不累加
        val drift = Trip.add(t, 25.00205, 121.0, 5f, 0f, 14_000)
        assertEquals(t.distanceM, drift.distanceM, 0.001)
        // 斷訊 40 秒：不算直線距離
        val gap = Trip.add(t, 25.01, 121.0, 5f, 40f, 52_000)
        assertEquals(t.distanceM, gap.distanceM, 0.001)
        // 久停 30 分鐘後：新的一趟
        val fresh = Trip.add(t, 25.002, 121.0, 5f, 0f, 12_000 + Trip.NEW_TRIP_GAP_MS)
        assertEquals(0.0, fresh.distanceM, 0.001)
        assertEquals("0.1 km · 0:00", Trip.format(t))
    }

    @Test fun seekStepStaysInsideSong() {
        assertEquals(0L, SeekStep.target(5_000, -15_000, 240_000))
        assertEquals(35_000L, SeekStep.target(20_000, 15_000, 240_000))
        assertEquals(239_000L, SeekStep.target(235_000, 15_000, 240_000))
        assertEquals(15_000L, SeekStep.target(0, 15_000, 0))
    }

    @Test fun mapStyleFollowsThemeAndFallsBack() {
        assertEquals(MapStyles.DAY, MapStyles.forTheme(false))
        assertEquals(MapStyles.NIGHT, MapStyles.forTheme(true))
        assertEquals(MapStyles.DAY, MapStyles.forTheme(true, nightFailed = true))
    }

    @Test fun recentPlayersKeepThreeNewestDistinct() {
        var list = emptyList<String>()
        listOf("kkbox", "yt", "morphe", "yt", "spotify").forEach { list = RecentPlayers.push(list, it) }
        assertEquals(listOf("spotify", "yt", "morphe"), list)
        assertEquals(list, RecentPlayers.decode(RecentPlayers.encode(list)))
        assertEquals(emptyList<String>(), RecentPlayers.decode(null))
        assertEquals(listOf("a", "b"), RecentPlayers.decode(" a | |b|a"))
    }

    @Test fun shortcutPagesAdvice() {
        assertFalse(ShortcutPagesAdvice.warn(3))
        assertTrue(ShortcutPagesAdvice.warn(4))
    }
}
