package tw.fitcar.launcher.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import tw.fitcar.launcher.data.ThemeChoice

/** 色票沿用第一階段原型 styles.css（日系原廠風：暖白／炭灰＋低飽和霧藍）。 */
@Immutable
data class Palette(
    val isNight: Boolean,
    val bg: Color,
    val bgDeep: Color,
    val surface: Color,
    val surfaceStrong: Color,
    val surfaceMuted: Color,
    val text: Color,
    val textSoft: Color,
    val line: Color,
    val accent: Color,
    val accentStrong: Color,
    val accentSoft: Color,
    val success: Color,
    val danger: Color,
)

val DayPalette = Palette(
    isNight = false,
    bg = Color(0xFFE8E6DF),
    bgDeep = Color(0xFFDAD8D1),
    surface = Color(0xFFF3F1EB),
    surfaceStrong = Color(0xFFFBFAF6),
    surfaceMuted = Color(0xFFE3E1DA),
    text = Color(0xFF282B2F),
    textSoft = Color(0xFF686D72),
    line = Color(0x212C3136),
    accent = Color(0xFF47768B),
    accentStrong = Color(0xFF315F74),
    accentSoft = Color(0xFFD7E4E8),
    success = Color(0xFF5B796C),
    danger = Color(0xFFA55D58),
)

val NightPalette = Palette(
    isNight = true,
    bg = Color(0xFF1B1E20),
    bgDeep = Color(0xFF131516),
    surface = Color(0xFF25292C),
    surfaceStrong = Color(0xFF2D3236),
    surfaceMuted = Color(0xFF202427),
    text = Color(0xFFEEF0EE),
    textSoft = Color(0xFFAEB4B7),
    line = Color(0x1CEBF1F3),
    accent = Color(0xFF6E9DB0),
    accentStrong = Color(0xFF85AFBF),
    accentSoft = Color(0xFF263E48),
    success = Color(0xFF88AA9A),
    danger = Color(0xFFD18B83),
)

val LocalPalette = staticCompositionLocalOf { DayPalette }

fun paletteFor(theme: ThemeChoice, accent: AccentTheme = AccentTheme.MIST_BLUE): Palette {
    val base = if (theme == ThemeChoice.NIGHT) NightPalette else DayPalette
    val c = if (theme == ThemeChoice.NIGHT) accent.night else accent.day
    return base.copy(accent = Color(c.accent), accentStrong = Color(c.accentStrong), accentSoft = Color(c.accentSoft))
}

@Composable
fun FitTheme(palette: Palette, content: @Composable () -> Unit) {
    val scheme = if (palette.isNight) {
        darkColorScheme(
            primary = palette.accent, onPrimary = palette.bgDeep,
            background = palette.bg, onBackground = palette.text,
            surface = palette.surface, onSurface = palette.text,
            surfaceVariant = palette.surfaceMuted, onSurfaceVariant = palette.textSoft,
            outline = palette.line, error = palette.danger,
        )
    } else {
        lightColorScheme(
            primary = palette.accent, onPrimary = Color.White,
            background = palette.bg, onBackground = palette.text,
            surface = palette.surface, onSurface = palette.text,
            surfaceVariant = palette.surfaceMuted, onSurfaceVariant = palette.textSoft,
            outline = palette.line, error = palette.danger,
        )
    }
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
