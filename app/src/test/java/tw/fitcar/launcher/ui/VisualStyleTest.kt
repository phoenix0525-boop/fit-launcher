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
        assertEquals(VisualStyle.POMPOMPURIN, VisualStyle.fromId("pompompurin"))
        assertTrue(styledBase(false, VisualStyle.POMPOMPURIN).characterArt)
        assertFalse(DayPalette.characterArt)
        assertEquals(DayPalette, paletteFor(ThemeChoice.DAY))
        assertEquals(NightPalette, paletteFor(ThemeChoice.NIGHT))
    }

    @Test fun stylesPreserveAccentAndNightSchedule() {
        VisualStyle.entries.forEach { style ->
            AccentTheme.entries.forEach { accent ->
                ThemeChoice.entries.forEach { theme ->
                    val p = paletteFor(theme, accent, style)
                    assertEquals(theme == ThemeChoice.NIGHT, p.isNight)
                    assertEquals(if (p.isNight && style != VisualStyle.SPIDERMAN) accent.night.accent else accent.day.accent, p.accent.toArgb().toLong() and 0xFFFFFFFFL)
                    if (p.isNight && style != VisualStyle.SPIDERMAN) assertTrue(Contrast.luminance(p.bg.toArgb().toLong()) < 0.05)
                    if (style == VisualStyle.SPIDERMAN) assertTrue("Spider surfaces must stay light", Contrast.luminance(p.bg.toArgb().toLong()) > .45)
                }
            }
        }
    }

    @Test fun newStylesKeepBodyAndButtonTextReadable() {
        VisualStyle.entries.forEach { style ->
            ThemeChoice.entries.forEach { theme ->
                AccentTheme.entries.forEach { accent ->
                    val p = paletteFor(theme, accent, style)
                    listOf(p.bg, p.surface, p.surfaceStrong, p.surfaceMuted).forEach { bg ->
                        listOf(p.text, p.textSoft).forEach { text ->
                            assertTrue("$style $theme body", Contrast.ratio(text.toArgb().toLong(), bg.toArgb().toLong()) >= 4.5)
                        }
                    }
                    val buttonText = p.onAccent.toArgb().toLong()
                    assertTrue("$style $theme $accent button", Contrast.ratio(p.accent.toArgb().toLong(), buttonText) >= 4.5)
                }
            }
        }
    }
    @Test fun spiderAndPompompurinRemainIndependentChoices() {
        assertEquals(VisualStyle.SPIDERMAN, VisualStyle.fromId("spiderman"))
        val spider = styledBase(false, VisualStyle.SPIDERMAN)
        val pudding = styledBase(false, VisualStyle.POMPOMPURIN)
        assertEquals(VisualStyle.SPIDERMAN, spider.artworkStyle)
        assertEquals(VisualStyle.POMPOMPURIN, pudding.artworkStyle)
        assertTrue(spider.characterArt && pudding.characterArt)
        assertNotEquals(spider.bg, pudding.bg)
        assertEquals(VisualStyle.ORIGINAL, VisualStyle.fromId(null))
    }

    @Test fun wallpaperCardsKeepContrastAgainstBrightAndDarkImages() {
        VisualStyle.entries.forEach { style -> ThemeChoice.entries.forEach { theme ->
            val p = paletteFor(theme, AccentTheme.HERO_RED, style)
            listOf(androidx.compose.ui.graphics.Color.Black, androidx.compose.ui.graphics.Color.White).forEach { image ->
                val surface = p.surface.copy(alpha = .96f).compositeOver(image)
                assertTrue("$style $theme wallpaper body", Contrast.ratio(p.text.toArgb().toLong(), surface.toArgb().toLong()) >= 4.5)
                assertTrue("$style $theme wallpaper disabled", Contrast.ratio(p.disabledContent.compositeOver(surface).toArgb().toLong(), surface.toArgb().toLong()) >= 3.0)
                if (style != VisualStyle.ORIGINAL) assertTrue("$style $theme wallpaper secondary", Contrast.ratio(p.textSoft.toArgb().toLong(), surface.toArgb().toLong()) >= 4.5)
            }
        } }
    }

    @Test fun albumCoverKeepsPrimaryTextReadable() {
        VisualStyle.entries.forEach { style -> ThemeChoice.entries.forEach { theme ->
            val p = paletteFor(theme, AccentTheme.HERO_RED, style)
            listOf(androidx.compose.ui.graphics.Color.Black, androidx.compose.ui.graphics.Color.White).forEach { image ->
                val surface = image.copy(alpha = PLAYER_COVER_ALPHA).compositeOver(p.surface)
                assertTrue("$style $theme album primary", Contrast.ratio(p.text.toArgb().toLong(), surface.toArgb().toLong()) >= 4.5)
                if (style != VisualStyle.ORIGINAL) assertTrue("$style $theme album secondary", Contrast.ratio(p.textSoft.toArgb().toLong(), surface.toArgb().toLong()) >= 4.5)
            }
        } }
    }

    @Test fun fullThemeCardsRemainReadableAtEveryTransparency() {
        listOf(VisualStyle.SPIDERMAN, VisualStyle.POMPOMPURIN).forEach { style -> ThemeChoice.entries.forEach { theme ->
            val p = paletteFor(theme, AccentTheme.HERO_RED, style)
            ThemeClarity.entries.forEach { clarity ->
                listOf(androidx.compose.ui.graphics.Color.Black, androidx.compose.ui.graphics.Color.White).forEach { image ->
                    val surface = p.surface.copy(alpha = clarity.alpha).compositeOver(image)
                    assertTrue("$style $theme $clarity primary", Contrast.ratio(p.text.toArgb().toLong(), surface.toArgb().toLong()) >= 4.5)
                    assertTrue("$style $theme $clarity secondary", Contrast.ratio(p.textSoft.toArgb().toLong(), surface.toArgb().toLong()) >= 4.5)
                    assertTrue("$style $theme $clarity disabled", Contrast.ratio(p.disabledContent.compositeOver(surface).toArgb().toLong(), surface.toArgb().toLong()) >= 3.0)
                }
            }
        } }
    }


    /** 狀態文字（● 已允許／✗ 有問題）畫在 Section 卡片（surfaceStrong）與一般卡片上，須 ≥ 4.5:1。 */
    @Test fun statusTextReadableOnCardsInEveryStyle() {
        VisualStyle.entries.forEach { style -> ThemeChoice.entries.forEach { theme ->
            val p = paletteFor(theme, AccentTheme.MIST_BLUE, style)
            listOf(p.surface, p.surfaceStrong).forEach { bg ->
                listOf("success" to p.success, "danger" to p.danger).forEach { (name, c) ->
                    assertTrue("$style $theme $name on $bg",
                        Contrast.ratio(c.toArgb().toLong(), bg.toArgb().toLong()) >= 4.5)
                }
            }
        } }
    }

    /** 選取中的設定分類（強調字在淡強調底）在每種風格、強調色都清楚。 */
    @Test fun selectedSectionLabelReadableForEveryAccent() {
        VisualStyle.entries.forEach { style -> ThemeChoice.entries.forEach { theme -> AccentTheme.entries.forEach { accent ->
            val p = paletteFor(theme, accent, style)
            assertTrue("$style $theme $accent selected",
                Contrast.ratio(p.accentStrong.toArgb().toLong(), p.accentSoft.toArgb().toLong()) >= 4.5)
        } } }
    }

    @Test fun sportAndDynamicHaveMatchingAccentsAndFullDayNightPalettes() {
        assertEquals(AccentTheme.SPORT, AccentTheme.fromId("sport"))
        assertEquals(AccentTheme.DYNAMIC, AccentTheme.fromId("dynamic"))
        listOf(VisualStyle.SPORT, VisualStyle.DYNAMIC).forEach { style ->
            val day = styledBase(false, style); val night = styledBase(true, style)
            assertNotEquals(DayPalette.bg, day.bg); assertNotEquals(NightPalette.bg, night.bg)
            assertNotEquals(day.surface, night.surface)
            assertFalse(day.characterArt || night.characterArt)
        }
    }
}
