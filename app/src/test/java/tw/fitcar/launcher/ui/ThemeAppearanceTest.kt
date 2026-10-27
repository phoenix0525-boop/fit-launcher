package tw.fitcar.launcher.ui
import org.junit.Assert.*
import org.junit.Test
class ThemeAppearanceTest {
    @Test fun oldSettingsHaveSafeDefaults() {
        assertTrue(ThemeAppearanceOptions().fullTheme)
        assertTrue(ThemeAppearanceOptions().smallArt)
        assertEquals(ThemeClarity.BALANCED, ThemeClarity.fromId(null))
        assertEquals(ThemeClarity.BALANCED, ThemeClarity.fromId("unknown"))
        ThemeClarity.entries.forEach { assertTrue(it.alpha in .90f..1f) }
    }
}
