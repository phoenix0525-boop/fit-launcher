package tw.fitcar.launcher.ui

import kotlin.math.pow

/** 強調色一組（ARGB）：按鈕底色、強調文字、淡底色。 */
data class AccentColors(val accent: Long, val accentStrong: Long, val accentSoft: Long)

/**
 * 主題強調色（參考 AGAMA 多主題，但維持日系原廠低飽和風格）。
 * 每組都通過按鈕文字 4.5:1 對比測試；底色、文字色仍沿用日／夜配色。
 */
enum class AccentTheme(val id: String, val label: String, val day: AccentColors, val night: AccentColors) {
    MIST_BLUE("mist", "霧藍（預設）",
        AccentColors(0xFF47768BL, 0xFF315F74L, 0xFFD7E4E8L), AccentColors(0xFF6E9DB0L, 0xFF85AFBFL, 0xFF263E48L)),
    GRAPHITE("graphite", "石墨灰",
        AccentColors(0xFF55595EL, 0xFF3C4045L, 0xFFDFE0E1L), AccentColors(0xFF9AA0A6L, 0xFFB4B9BEL, 0xFF33373BL)),
    WINE("wine", "酒紅",
        AccentColors(0xFF8E4A4FL, 0xFF733A3FL, 0xFFEFDCDDL), AccentColors(0xFFC4868BL, 0xFFD49EA2L, 0xFF4A2C2FL)),
    FOREST("forest", "森林綠",
        AccentColors(0xFF4F7360L, 0xFF3B5A4AL, 0xFFDCE7E0L), AccentColors(0xFF86AE98L, 0xFF9EC1ADL, 0xFF2C4237L)),
    CARAMEL("caramel", "焦糖棕",
        AccentColors(0xFF7A4234L, 0xFF602E24L, 0xFFF4DEC4L), AccentColors(0xFFE9BA75L, 0xFFF4D19AL, 0xFF4D3423L)),
    HERO_RED("hero_red", "英雄紅",
        AccentColors(0xFFC62828L, 0xFFA51920L, 0xFFFBE0E2L), AccentColors(0xFFFF7B7BL, 0xFFFFA2A2L, 0xFF43242FL)),
    SPORT("sport", "運動紅",
        AccentColors(0xFFB72E28L, 0xFF92251FL, 0xFFF5DEDBL), AccentColors(0xFFFF9386L, 0xFFFFB2A8L, 0xFF472C2BL)),
    DYNAMIC("dynamic", "動感紫",
        AccentColors(0xFF713FB5L, 0xFF58308FL, 0xFFE9DFF7L), AccentColors(0xFFC4A1FFL, 0xFFD8BEFFL, 0xFF392B50L)),
    SAND("sand", "沙棕",
        AccentColors(0xFF8A6A3FL, 0xFF6F5430L, 0xFFF0E5D5L), AccentColors(0xFFC9A877L, 0xFFD8BC92L, 0xFF4A3B26L));

    companion object {
        fun fromId(id: String?): AccentTheme = entries.firstOrNull { it.id == id } ?: MIST_BLUE
    }
}

/** WCAG 相對亮度與對比值。 */
object Contrast {
    private fun channel(v: Long): Double {
        val c = v / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    fun luminance(argb: Long): Double =
        0.2126 * channel((argb shr 16) and 0xFF) + 0.7152 * channel((argb shr 8) and 0xFF) + 0.0722 * channel(argb and 0xFF)

    fun ratio(a: Long, b: Long): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }
}
