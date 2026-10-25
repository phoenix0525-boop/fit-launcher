package tw.fitcar.launcher.data

/**
 * 亮度／主題排程的純邏輯（不依賴 Android），可直接做 JVM 單元測試。
 * 邏輯移植自第一階段原型 state.mjs。
 */
data class Period(
    val id: String,
    val label: String,
    val startMinutes: Int,
    val endMinutes: Int,
    val brightness: Int,
)

enum class ThemeChoice { DAY, NIGHT }

data class DisplaySettings(
    val autoSchedule: Boolean = true,
    val autoTheme: Boolean = true,
    val manualTheme: ThemeChoice = ThemeChoice.DAY,
    val manualBrightness: Int = 48,
    val smoothTransition: Boolean = true,
    val periods: List<Period> = DEFAULT_PERIODS,
)

val DEFAULT_PERIODS = listOf(
    Period("day", "日間", 7 * 60, 17 * 60, 80),
    Period("dusk", "黃昏", 17 * 60, 18 * 60 + 30, 50),
    Period("night", "夜間", 18 * 60 + 30, 7 * 60, 20),
)

object Schedule {
    const val MINUTES_PER_DAY = 24 * 60

    fun clampBrightness(value: Int): Int = value.coerceIn(1, 100)

    /** 支援跨日區間（例如 18:30–07:00）；start == end 視為整天。 */
    fun periodContains(start: Int, end: Int, current: Int): Boolean {
        if (start == end) return true
        return if (start < end) current in start until end else current >= start || current < end
    }

    fun activePeriod(settings: DisplaySettings, current: Int): Period? =
        settings.periods.firstOrNull { periodContains(it.startMinutes, it.endMinutes, current) }

    fun brightnessFor(settings: DisplaySettings, current: Int): Int {
        if (!settings.autoSchedule) return clampBrightness(settings.manualBrightness)
        return clampBrightness(activePeriod(settings, current)?.brightness ?: settings.manualBrightness)
    }

    fun themeFor(settings: DisplaySettings, current: Int): ThemeChoice {
        if (!settings.autoTheme) return settings.manualTheme
        val night = settings.periods.firstOrNull { it.id == "night" } ?: return ThemeChoice.DAY
        return if (periodContains(night.startMinutes, night.endMinutes, current)) ThemeChoice.NIGHT else ThemeChoice.DAY
    }

    /** 從 current 起算，距離下一個區間邊界還有幾分鐘（至少 1 分鐘）。 */
    fun minutesUntilNextBoundary(settings: DisplaySettings, current: Int): Int {
        val boundaries = settings.periods.flatMap { listOf(it.startMinutes, it.endMinutes) }.distinct()
        if (boundaries.isEmpty()) return MINUTES_PER_DAY
        return boundaries.minOf { b ->
            val diff = ((b - current) % MINUTES_PER_DAY + MINUTES_PER_DAY) % MINUTES_PER_DAY
            if (diff == 0) MINUTES_PER_DAY else diff
        }
    }

    /** 百分比 (1–100) 轉為 Android 系統亮度 (1–255)。 */
    fun percentToSystem(percent: Int): Int =
        (clampBrightness(percent) * 255 + 50) / 100

    fun systemToPercent(value: Int): Int =
        clampBrightness((value.coerceIn(0, 255) * 100 + 127) / 255)

    fun formatMinutes(minutes: Int): String {
        val m = ((minutes % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
        return "%02d:%02d".format(m / 60, m % 60)
    }

    fun parseTime(text: String): Int? {
        val match = Regex("^([01]?\\d|2[0-3]):([0-5]\\d)$").matchEntire(text.trim()) ?: return null
        return match.groupValues[1].toInt() * 60 + match.groupValues[2].toInt()
    }
}

/** 捷徑：固定 8 格（2 頁 × 2×2），null 代表空格。 */
object ShortcutSlots {
    const val COUNT = 8
    const val PER_PAGE = 4

    fun normalize(list: List<String?>): List<String?> =
        List(COUNT) { index -> list.getOrNull(index)?.takeIf { it.isNotBlank() } }

    fun replace(list: List<String?>, index: Int, value: String?): List<String?> {
        val next = normalize(list).toMutableList()
        if (index in 0 until COUNT) next[index] = value
        return next
    }

    fun move(list: List<String?>, from: Int, to: Int): List<String?> {
        val next = normalize(list).toMutableList()
        if (from == to || from !in 0 until COUNT || to !in 0 until COUNT) return next
        val moved = next.removeAt(from)
        next.add(to, moved)
        return next
    }
}
