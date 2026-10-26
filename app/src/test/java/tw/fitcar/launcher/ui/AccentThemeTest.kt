package tw.fitcar.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccentThemeTest {
    @Test fun unknownIdFallsBackToMistBlue() {
        assertEquals(AccentTheme.MIST_BLUE, AccentTheme.fromId(null))
        assertEquals(AccentTheme.MIST_BLUE, AccentTheme.fromId("neon"))
        assertEquals(AccentTheme.WINE, AccentTheme.fromId("wine"))
    }

    @Test fun defaultMatchesOriginalPrototypeColors() {
        assertEquals(0xFF47768BL, AccentTheme.MIST_BLUE.day.accent)
        assertEquals(0xFF6E9DB0L, AccentTheme.MIST_BLUE.night.accent)
    }

    /** 按鈕文字（日間白字、夜間深色字）與強調色對比至少 4.5:1（WCAG AA）。 */
    @Test fun everyAccentIsReadable() {
        AccentTheme.entries.forEach { t ->
            assertTrue("${t.label} 日間", Contrast.ratio(t.day.accent, 0xFFFFFFFFL) >= 4.5)
            assertTrue("${t.label} 日間強調字", Contrast.ratio(t.day.accentStrong, 0xFFE8E6DFL) >= 4.5)
            assertTrue("${t.label} 夜間", Contrast.ratio(t.night.accent, 0xFF131516L) >= 4.5)
            assertTrue("${t.label} 夜間強調字", Contrast.ratio(t.night.accentStrong, 0xFF1B1E20L) >= 4.5)
        }
    }

    @Test fun contrastMath() {
        assertEquals(21.0, Contrast.ratio(0xFF000000L, 0xFFFFFFFFL), 0.01)
        assertEquals(1.0, Contrast.ratio(0xFF777777L, 0xFF777777L), 0.001)
    }
}
