package tw.fitcar.launcher.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NavOpenMode { FULLSCREEN, SPLIT_FIRST }
enum class MediaMode { FIXED, FOLLOW_CURRENT }

/** 導航／音樂／測速三個綁定，存的是使用者從實際清單選的 ComponentName 字串。 */
data class Bindings(
    val navigation: String? = null,
    val music: String? = null,
    val shield: String? = null,
)

data class LauncherState(
    val display: DisplaySettings = DisplaySettings(),
    val shortcuts: List<String?> = List(ShortcutSlots.COUNT) { null },
    val bindings: Bindings = Bindings(),
    val navOpenMode: NavOpenMode = NavOpenMode.FULLSCREEN,
    /** A manual arrangement goal, never a promise to move other apps. */
    val splitNavigationSide: tw.fitcar.launcher.nav.NavigationSide = tw.fitcar.launcher.nav.NavigationSide.LEFT,
    val mediaMode: MediaMode = MediaMode.FOLLOW_CURRENT,
    /** 最近一次分割畫面測試結果：null = 尚未測試。 */
    val lastSplitResult: Boolean? = null,
    val manualOverride: Boolean = false,
    /** 主畫面捷徑頁數（每頁 4 格，1–6 頁）。 */
    val shortcutPages: Int = ShortcutSlots.DEFAULT_PAGES,
    /** 主題強調色 id（見 ui.AccentTheme）。 */
    val accentTheme: String = "mist",
    val visualStyle: String = "original",
    /** Local discovery state; not part of exported settings. Existing character selections unlock on load. */
    val characterThemesUnlocked: Boolean = false,
    /** 常用地點（回家／去公司），交給導航 App 的地址文字。 */
    val homeAddress: String? = null,
    val workAddress: String? = null,
    /** 自訂超速提醒門檻（km/h），0 = 關閉。只比對 GPS 車速。 */
    val speedAlertKmh: Int = 0,
    val startOnBoot: Boolean = false,
)

/**
 * 以 SharedPreferences 保存設定，重新開機後保留。
 * 單例，所有 Activity／Receiver 共用同一份狀態。
 */
class Prefs private constructor(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("fit_launcher", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<LauncherState> = _state.asStateFlow()

    fun update(transform: (LauncherState) -> LauncherState) {
        val next = transform(_state.value)
        _state.value = next
        save(next)
    }

    fun reload() { _state.value = load() }

    private fun load(): LauncherState {
        val periods = DEFAULT_PERIODS.map { p ->
            Period(
                id = p.id,
                label = p.label,
                startMinutes = sp.getInt("period_${p.id}_start", p.startMinutes).coerceIn(0, 1439),
                endMinutes = sp.getInt("period_${p.id}_end", p.endMinutes).coerceIn(0, 1439),
                brightness = Schedule.clampBrightness(sp.getInt("period_${p.id}_brightness", p.brightness)),
            )
        }
        val display = DisplaySettings(
            autoSchedule = sp.getBoolean("auto_schedule", true),
            autoTheme = sp.getBoolean("auto_theme", true),
            manualTheme = if (sp.getString("manual_theme", "day") == "night") ThemeChoice.NIGHT else ThemeChoice.DAY,
            manualBrightness = Schedule.clampBrightness(sp.getInt("manual_brightness", 48)),
            smoothTransition = sp.getBoolean("smooth_transition", true),
            periods = periods,
        )
        val shortcuts = ShortcutSlots.normalize(
            List(ShortcutSlots.COUNT) { i -> sp.getString("shortcut_$i", null) }
        )
        val split = if (sp.contains("last_split_result")) sp.getBoolean("last_split_result", false) else null
        return LauncherState(
            startOnBoot = sp.getBoolean("start_desktop_on_boot", false),
            display = display,
            shortcuts = shortcuts,
            bindings = Bindings(
                navigation = sp.getString("bind_navigation", null),
                music = sp.getString("bind_music", null),
                shield = sp.getString("bind_shield", null),
            ),
            navOpenMode = if (sp.getBoolean("experimental_split", false)) NavOpenMode.SPLIT_FIRST else NavOpenMode.FULLSCREEN,
            mediaMode = if (sp.getString("media_mode", "follow") == "fixed") MediaMode.FIXED else MediaMode.FOLLOW_CURRENT,
            lastSplitResult = split,
            splitNavigationSide = tw.fitcar.launcher.nav.NavigationSide.fromId(sp.getString("split_navigation_side",null)),
            manualOverride = sp.getBoolean("manual_override", false),
            shortcutPages = ShortcutSlots.clampPages(sp.getInt("shortcut_pages", ShortcutSlots.DEFAULT_PAGES)),
            accentTheme = sp.getString("accent_theme", "mist") ?: "mist",
            visualStyle = sp.getString("visual_style", "original") ?: "original",
            characterThemesUnlocked = tw.fitcar.launcher.ui.CharacterThemeAccess.isUnlocked(
                sp.getBoolean("character_themes_unlocked", false), sp.getString("visual_style", "original") ?: "original",
            ),
            homeAddress = sp.getString("home_address", null),
            workAddress = sp.getString("work_address", null),
            speedAlertKmh = tw.fitcar.launcher.system.SpeedAlert.clampThreshold(sp.getInt("speed_alert_kmh", 0)),
        )
    }

    private fun save(s: LauncherState) {
        sp.edit().apply {
            putBoolean("start_desktop_on_boot", s.startOnBoot)
            putBoolean("auto_schedule", s.display.autoSchedule)
            putBoolean("auto_theme", s.display.autoTheme)
            putString("manual_theme", if (s.display.manualTheme == ThemeChoice.NIGHT) "night" else "day")
            putInt("manual_brightness", s.display.manualBrightness)
            putBoolean("smooth_transition", s.display.smoothTransition)
            s.display.periods.forEach { p ->
                putInt("period_${p.id}_start", p.startMinutes)
                putInt("period_${p.id}_end", p.endMinutes)
                putInt("period_${p.id}_brightness", p.brightness)
            }
            s.shortcuts.forEachIndexed { i, v -> if (v == null) remove("shortcut_$i") else putString("shortcut_$i", v) }
            putOrRemove("bind_navigation", s.bindings.navigation)
            putOrRemove("bind_music", s.bindings.music)
            putOrRemove("bind_shield", s.bindings.shield)
            putBoolean("experimental_split", s.navOpenMode == NavOpenMode.SPLIT_FIRST)
            putString("split_navigation_side",s.splitNavigationSide.id)
            putString("media_mode", if (s.mediaMode == MediaMode.FIXED) "fixed" else "follow")
            if (s.lastSplitResult == null) remove("last_split_result") else putBoolean("last_split_result", s.lastSplitResult)
            putBoolean("manual_override", s.manualOverride)
            putInt("shortcut_pages", ShortcutSlots.clampPages(s.shortcutPages))
            putString("accent_theme", s.accentTheme)
            putString("visual_style", s.visualStyle)
            putBoolean("character_themes_unlocked", s.characterThemesUnlocked)
            putOrRemove("home_address", s.homeAddress)
            putOrRemove("work_address", s.workAddress)
            putInt("speed_alert_kmh", tw.fitcar.launcher.system.SpeedAlert.clampThreshold(s.speedAlertKmh))
        }.apply()
    }

    private fun SharedPreferences.Editor.putOrRemove(key: String, value: String?) {
        if (value == null) remove(key) else putString(key, value)
    }

    companion object {
        @Volatile private var instance: Prefs? = null
        fun get(context: Context): Prefs =
            instance ?: synchronized(this) { instance ?: Prefs(context).also { instance = it } }
    }
}
