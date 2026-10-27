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

/** 畫面風格與日夜／強調色分開保存，切換風格不改亮度排程。 */
enum class VisualStyle(val id: String, val label: String, val description: String) {
    ORIGINAL("original", "原有", "暖灰底色，保留目前配色"),
    JAPANESE("japanese", "日系", "和紙米白、暖褐文字與柔和卡片"),
    WHITE("white", "白色", "清爽白底、灰白分層與深灰文字");

    companion object {
        fun fromId(id: String?): VisualStyle = entries.firstOrNull { it.id == id } ?: ORIGINAL
    }
}

fun styledBase(night: Boolean, style: VisualStyle): Palette {
    val base = if (night) NightPalette else DayPalette
    return when (style) {
        VisualStyle.ORIGINAL -> base
        VisualStyle.JAPANESE -> if (night) base.copy(
            bg = Color(0xFF24221F), bgDeep = Color(0xFF191815),
            surface = Color(0xFF302D28), surfaceStrong = Color(0xFF3B3731),
            surfaceMuted = Color(0xFF292621), text = Color(0xFFF3EEE4),
            textSoft = Color(0xFFC0B7A8), line = Color(0xFF524C42),
        ) else base.copy(
            bg = Color(0xFFEDE8DE), bgDeep = Color(0xFFE1DACD),
            surface = Color(0xFFF7F3EA), surfaceStrong = Color(0xFFFFFCF5),
            surfaceMuted = Color(0xFFE7E0D3), text = Color(0xFF38342E),
            textSoft = Color(0xFF665D52), line = Color(0xFFC9C0B1),
        )
        VisualStyle.WHITE -> if (night) base.copy(
            bg = Color(0xFF1C1E21), bgDeep = Color(0xFF121416),
            surface = Color(0xFF272A2E), surfaceStrong = Color(0xFF33373C),
            surfaceMuted = Color(0xFF222529), text = Color(0xFFF4F5F6),
            textSoft = Color(0xFFBAC0C7), line = Color(0xFF494F57),
        ) else base.copy(
            bg = Color(0xFFF4F5F7), bgDeep = Color(0xFFE7E9ED),
            surface = Color(0xFFFFFFFF), surfaceStrong = Color(0xFFFFFFFF),
            surfaceMuted = Color(0xFFEDEFF2), text = Color(0xFF252A31),
            textSoft = Color(0xFF59616B), line = Color(0xFFCCD1D8),
        )
    }
}

val LocalPalette = staticCompositionLocalOf { DayPalette }

/**
 * 停用但仍需看得見的圖示／文字（例如播放器不支援的上一首）。
 * 原本用分隔線色，對比只有 1.3–1.7；改為次要文字色 80%，各風格對比約 3.2–5.6（啟用文字約 11–14，停用仍明顯較淡）。
 */
val Palette.disabledContent: Color get() = textSoft.copy(alpha = 0.8f)

fun paletteFor(theme: ThemeChoice, accent: AccentTheme = AccentTheme.MIST_BLUE, style: VisualStyle = VisualStyle.ORIGINAL): Palette {
    val base = styledBase(theme == ThemeChoice.NIGHT, style)
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
