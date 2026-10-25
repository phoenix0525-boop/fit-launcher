package tw.fitcar.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleTest {
    private val defaults = DisplaySettings()
    private fun t(h: Int, m: Int) = h * 60 + m

    @Test fun periodContains_sameDay() {
        assertTrue(Schedule.periodContains(t(7, 0), t(17, 0), t(7, 0)))
        assertTrue(Schedule.periodContains(t(7, 0), t(17, 0), t(16, 59)))
        assertFalse(Schedule.periodContains(t(7, 0), t(17, 0), t(17, 0)))
    }

    @Test fun periodContains_crossMidnight() {
        assertTrue(Schedule.periodContains(t(18, 30), t(7, 0), t(23, 59)))
        assertTrue(Schedule.periodContains(t(18, 30), t(7, 0), t(0, 0)))
        assertTrue(Schedule.periodContains(t(18, 30), t(7, 0), t(6, 59)))
        assertFalse(Schedule.periodContains(t(18, 30), t(7, 0), t(7, 0)))
    }

    @Test fun defaultBrightnessSchedule() {
        assertEquals(80, Schedule.brightnessFor(defaults, t(12, 0)))
        assertEquals(50, Schedule.brightnessFor(defaults, t(17, 30)))
        assertEquals(20, Schedule.brightnessFor(defaults, t(22, 0)))
        assertEquals(20, Schedule.brightnessFor(defaults, t(3, 0)))
    }

    @Test fun manualWhenScheduleOff() {
        assertEquals(48, Schedule.brightnessFor(defaults.copy(autoSchedule = false), t(12, 0)))
    }

    @Test fun themeFollowsNightPeriod() {
        assertEquals(ThemeChoice.DAY, Schedule.themeFor(defaults, t(17, 45)))
        assertEquals(ThemeChoice.NIGHT, Schedule.themeFor(defaults, t(18, 30)))
        assertEquals(ThemeChoice.NIGHT, Schedule.themeFor(defaults, t(6, 0)))
        assertEquals(ThemeChoice.NIGHT, Schedule.themeFor(defaults.copy(autoTheme = false, manualTheme = ThemeChoice.NIGHT), t(12, 0)))
    }

    @Test fun nextBoundary() {
        assertEquals(60, Schedule.minutesUntilNextBoundary(defaults, t(16, 0)))
        assertEquals(30, Schedule.minutesUntilNextBoundary(defaults, t(18, 0)))
        // 22:00 → 翌日 07:00
        assertEquals(9 * 60, Schedule.minutesUntilNextBoundary(defaults, t(22, 0)))
        // 正好在邊界上 → 下一個邊界而非 0
        assertEquals(10 * 60, Schedule.minutesUntilNextBoundary(defaults, t(7, 0)))
    }

    @Test fun brightnessConversion() {
        assertEquals(255, Schedule.percentToSystem(100))
        assertEquals(3, Schedule.percentToSystem(1))
        assertEquals(100, Schedule.systemToPercent(255))
        assertEquals(1, Schedule.systemToPercent(0))
    }

    @Test fun timeParsing() {
        assertEquals(t(18, 30), Schedule.parseTime("18:30"))
        assertEquals("07:05", Schedule.formatMinutes(t(7, 5)))
        assertNull(Schedule.parseTime("24:00"))
    }

    @Test fun shortcutsMoveAndReplace() {
        val base = ShortcutSlots.normalize(listOf("a", "b", null, "d"))
        assertEquals(8, base.size)
        assertEquals(listOf("b", "a", null, "d", null, null, null, null), ShortcutSlots.move(base, 0, 1))
        assertEquals(null, ShortcutSlots.replace(base, 1, null)[1])
        assertEquals(base, ShortcutSlots.move(base, 0, 9))
    }
}
