package tw.fitcar.launcher.ui

import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.*
import org.junit.Test
import tw.fitcar.launcher.data.ThemeChoice

class MechaThemeTest {
    @Test fun themeIsPubliclySelectableAndKeepsIdentity() {
        val style = VisualStyle.fromId("mecha")
        assertEquals("mecha", style.id)
        assertTrue(CharacterThemeAccess.visibleStyles(false, "original").contains(style))
        assertEquals("機甲", style.label)
    }
    @Test fun graphiteSurfacesAndTextRemainReadableInDayAndNight() {
        val style = VisualStyle.fromId("mecha")
        ThemeChoice.entries.forEach { theme ->
            val p = paletteFor(theme, AccentTheme.DYNAMIC, style)
            assertEquals("mecha", p.artworkStyle.id)
            assertTrue(p.characterArt)
            assertTrue(Contrast.luminance(p.bg.toArgb().toLong()) < .05)
            assertTrue(Contrast.ratio(p.text.toArgb().toLong(), p.surface.toArgb().toLong()) >= 4.5)
        }
    }
    @Test fun orangeAndRedPanelsKeepContrastAndDoNotChangeOtherThemes() {
        assertEquals(DayPalette, mechaPanelPalette(DayPalette, MechaPanel.MUSIC))
        MechaPanel.entries.forEach { panel ->
            val p = mechaPanelPalette(paletteFor(ThemeChoice.NIGHT, AccentTheme.DYNAMIC, VisualStyle.MECHA), panel)
            assertTrue(Contrast.ratio(p.onAccent.toArgb().toLong(), p.accent.toArgb().toLong()) >= 4.5)
            assertTrue(Contrast.ratio(p.accentStrong.toArgb().toLong(), p.accentSoft.toArgb().toLong()) >= 4.5)
            assertTrue(Contrast.ratio(p.textSoft.toArgb().toLong(), p.surfaceStrong.toArgb().toLong()) >= 4.5)
        }
    }
}
