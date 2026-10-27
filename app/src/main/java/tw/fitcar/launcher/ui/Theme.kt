package tw.fitcar.launcher.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.*
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
    val characterArt: Boolean = false,
    val artworkStyle: VisualStyle = VisualStyle.ORIGINAL,
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
    WHITE("white", "白色", "清爽白底、灰白分層與深灰文字"),
    POMPOMPURIN("pompompurin", "布丁狗", "奶油黃、焦糖棕與布丁狗插畫"),
    SPIDERMAN("spiderman", "蜘蛛人", "霧白紅藍、淺暖灰夜間與全版插畫");

    companion object {
        fun fromId(id: String?): VisualStyle = entries.firstOrNull { it.id == id } ?: ORIGINAL
    }
}

fun styledBase(night: Boolean, style: VisualStyle): Palette {
    val base = if (night) NightPalette else DayPalette
    return when (style) {
        VisualStyle.ORIGINAL -> base
        VisualStyle.POMPOMPURIN -> if (night) base.copy(
            bg = Color(0xFF241A14), bgDeep = Color(0xFF17110D),
            surface = Color(0xFF30231A), surfaceStrong = Color(0xFF3B2A1F),
            surfaceMuted = Color(0xFF211812), text = Color(0xFFFFF0CB),
            textSoft = Color(0xFFD9BE8D), line = Color(0xFF65482D), characterArt = true, artworkStyle = VisualStyle.POMPOMPURIN,
        ) else base.copy(
            bg = Color(0xFFFFF6D7), bgDeep = Color(0xFFEAD49F),
            surface = Color(0xFFFFF9E5), surfaceStrong = Color(0xFFFFFEF6),
            surfaceMuted = Color(0xFFEFDEB5), text = Color(0xFF4B2C20),
            textSoft = Color(0xFF6E5437), line = Color(0xFFDCC28C), characterArt = true, artworkStyle = VisualStyle.POMPOMPURIN,
        )
        VisualStyle.SPIDERMAN -> if (night) base.copy(
            bg = Color(0xFFD5DCE4), bgDeep = Color(0xFFC5CFDB),
            surface = Color(0xFFE9EDF3), surfaceStrong = Color(0xFFF2F4F7),
            surfaceMuted = Color(0xFFD7DEE7), text = Color(0xFF182B44),
            textSoft = Color(0xFF3B506B), line = Color(0xFF8196AD),
            characterArt = true, artworkStyle = VisualStyle.SPIDERMAN,
        ) else base.copy(
            bg = Color(0xFFF1F5FA), bgDeep = Color(0xFFDCE6F2),
            surface = Color(0xFFFAFCFF), surfaceStrong = Color(0xFFFFFFFF),
            surfaceMuted = Color(0xFFE2EBF5), text = Color(0xFF13263E),
            textSoft = Color(0xFF435D7B), line = Color(0xFFA8BAD2),
            characterArt = true, artworkStyle = VisualStyle.SPIDERMAN,
        )
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
 * 原本用分隔線色，對比只有 1.3–1.7；改為次要文字色 85%，在卡片背景合成後至少 3:1。
 */
val Palette.disabledContent: Color get() = textSoft.copy(alpha = 0.85f)

fun paletteFor(theme: ThemeChoice, accent: AccentTheme = AccentTheme.MIST_BLUE, style: VisualStyle = VisualStyle.ORIGINAL): Palette {
    val base = styledBase(theme == ThemeChoice.NIGHT, style)
    val c = if (theme == ThemeChoice.NIGHT && style != VisualStyle.SPIDERMAN) accent.night else accent.day
    return base.copy(accent = Color(c.accent), accentStrong = Color(c.accentStrong), accentSoft = Color(c.accentSoft))
}

fun contrastInk(background: Color): Color {
    val dark = Color(0xFF172333)
    return if (Contrast.ratio(Color.White.toArgb().toLong(), background.toArgb().toLong()) >=
        Contrast.ratio(dark.toArgb().toLong(), background.toArgb().toLong())) Color.White else dark
}
val Palette.onAccent: Color get() = contrastInk(accent)

@Composable
fun FitTheme(palette: Palette, appearanceOverride: ThemeAppearanceOptions? = null, content: @Composable () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val appearance by ThemeAppearanceStore.state.collectAsState()
    LaunchedEffect(context) { ThemeAppearanceStore.initialize(context) }
    val scheme = if (palette.isNight && palette.artworkStyle != VisualStyle.SPIDERMAN) {
        darkColorScheme(
            primary = palette.accent, onPrimary = palette.onAccent,
            background = palette.bg, onBackground = palette.text,
            surface = palette.surface, onSurface = palette.text,
            surfaceVariant = palette.surfaceMuted, onSurfaceVariant = palette.textSoft,
            outline = palette.line, error = palette.danger,
        )
    } else {
        lightColorScheme(
            primary = palette.accent, onPrimary = palette.onAccent,
            background = palette.bg, onBackground = palette.text,
            surface = palette.surface, onSurface = palette.text,
            surfaceVariant = palette.surfaceMuted, onSurfaceVariant = palette.textSoft,
            outline = palette.line, error = palette.danger,
        )
    }
    CompositionLocalProvider(LocalPalette provides palette, LocalThemeAppearance provides (appearanceOverride ?: appearance)) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
