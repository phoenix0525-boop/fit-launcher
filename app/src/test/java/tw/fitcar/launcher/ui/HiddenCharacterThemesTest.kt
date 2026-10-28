package tw.fitcar.launcher.ui

import org.junit.Assert.*
import org.junit.Test

class HiddenCharacterThemesTest {
    @Test fun fiveConsecutiveTapsUnlockExactlyOnce() {
        val taps = HiddenCharacterThemeTaps()
        repeat(4) { assertFalse(taps.onTap(100L + it * 500L)) }
        assertTrue(taps.onTap(2100L))
        assertFalse(taps.onTap(2200L))
    }

    @Test fun anotherSectionAndLongGapResetSequence() {
        val taps = HiddenCharacterThemeTaps()
        repeat(3) { taps.onTap(it * 400L) }
        taps.reset()
        repeat(4) { assertFalse(taps.onTap(2000L + it * 400L)) }
        assertTrue(taps.onTap(3600L))
        taps.reset()
        assertFalse(taps.onTap(5000L))
        assertFalse(taps.onTap(7001L))
    }

    @Test fun activeLegacyThemeRemainsAvailableAndOrdinaryThemesStayVisible() {
        assertFalse(CharacterThemeAccess.isUnlocked(false, "original"))
        assertTrue(CharacterThemeAccess.isUnlocked(false, "spiderman"))
        assertTrue(CharacterThemeAccess.isUnlocked(false, "pompompurin"))
        assertTrue(CharacterThemeAccess.isUnlocked(true, "original"))
        assertEquals(3, CharacterThemeAccess.visibleStyles(false, "original").size)
        assertTrue(CharacterThemeAccess.visibleStyles(false, "original").contains(VisualStyle.WHITE))
        assertEquals(5, CharacterThemeAccess.visibleStyles(false, "pompompurin").size)
        assertEquals(5, CharacterThemeAccess.visibleStyles(true, "original").size)
    }
}
