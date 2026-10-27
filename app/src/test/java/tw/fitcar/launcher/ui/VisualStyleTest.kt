package tw.fitcar.launcher.ui

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.compositeOver
import org.junit.Assert.*
import org.junit.Test
import tw.fitcar.launcher.data.ThemeChoice

class VisualStyleTest {
    @Test fun disabledContentKeepsThreeToOneContrastOnCardSurfaces() {
        VisualStyle.entries.forEach { style ->
            ThemeChoice.entries.forEach { theme ->
                AccentTheme.entries.forEach { accent ->
                    val p = paletteFor(theme, accent, style)
                    listOf(p.surface, p.surfaceStrong, p.surfaceMuted).forEach { background ->
                        val painted = p.disabledContent.compositeOver(background)
                        assertTrue("$style $theme disabled content on $background",
                            Contrast.ratio(painted.toArgb().toLong(), background.toArgb().toLong()) >= 3.0)
                    }
                }
            }
        }
    }

    @Test fun oldOrUnknownSettingsKeepOriginalStyle() {
        assertEquals(VisualStyle.ORIGINAL, VisualStyle.fromId(null))
        assertEquals(VisualStyle.ORIGINAL, VisualStyle.fromId("future"))
        assertEquals(VisualStyle.JAPANESE, VisualStyle.fromId("japanese"))
        assertEquals(VisualStyle.WHITE, VisualStyle.fromId("white"))
        assertEquals(DayPalette, paletteFor(ThemeChoice.DAY))
        assertEquals(NightPalette, paletteFor(ThemeChoice.NIGHT))
    }

    @Test fun stylesPreserveAccentAndNightSchedule() {
        VisualStyle.entries.forEach { style ->
            AccentTheme.entries.forEach { accent ->
                ThemeChoice.entries.forEach { theme ->
                    val p = paletteFor(theme, accent, style)
                    assertEquals(theme == ThemeChoice.NIGHT, p.isNight)
                    assertEquals(if (p.isNight) accent.night.accent else accent.day.accent, p.accent.toArgb().toLong() and 0xFFFFFFFFL)
                    if (p.isNight) assertTrue(Contrast.luminance(p.bg.toArgb().toLong()) < 0.05)
                }
            }
        }
    }

    @Test fun newStylesKeepBodyAndButtonTextReadable() {
        listOf(VisualStyle.JAPANESE, VisualStyle.WHITE).forEach { style ->
            ThemeChoice.entries.forEach { theme ->
                AccentTheme.entries.forEach { accent ->
                    val p = paletteFor(theme, accent, style)
                    listOf(p.bg, p.surface, p.surfaceStrong, p.surfaceMuted).forEach { bg ->
                        listOf(p.text, p.textSoft).forEach { text ->
                            assertTrue("$style $theme body", Contrast.ratio(text.toArgb().toLong(), bg.toArgb().toLong()) >= 4.5)
                        }
                    }
                    val buttonText = if (p.isNight) p.bgDeep.toArgb().toLong() else 0xFFFFFFFFL
                    assertTrue("$style $theme $accent button", Contrast.ratio(p.accent.toArgb().toLong(), buttonText) >= 4.5)
                }
            }
        }
    }
}
