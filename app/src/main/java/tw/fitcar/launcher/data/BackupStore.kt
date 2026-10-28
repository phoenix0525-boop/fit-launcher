package tw.fitcar.launcher.data

import android.content.Context
import android.content.SharedPreferences
import tw.fitcar.launcher.lyrics.LyricsRepository
import tw.fitcar.launcher.ui.ThemeAppearanceStore

object BackupStore {
    val revision = kotlinx.coroutines.flow.MutableStateFlow(0)
    fun snapshot(context: Context): Map<String, Map<String, Any>> {
        val s=Prefs.get(context).state.value
        val main=linkedMapOf<String,Any>(
            "auto_schedule" to s.display.autoSchedule, "auto_theme" to s.display.autoTheme,
            "manual_theme" to (if(s.display.manualTheme==ThemeChoice.NIGHT) "night" else "day"),
            "manual_brightness" to s.display.manualBrightness, "smooth_transition" to s.display.smoothTransition,
            "experimental_split" to (s.navOpenMode==NavOpenMode.SPLIT_FIRST), "media_mode" to (if(s.mediaMode==MediaMode.FIXED) "fixed" else "follow"),
            "shortcut_pages" to s.shortcutPages,
            "accent_theme" to s.accentTheme, "visual_style" to s.visualStyle, "speed_alert_kmh" to s.speedAlertKmh)
        listOf("bind_navigation" to s.bindings.navigation,"bind_music" to s.bindings.music,"bind_shield" to s.bindings.shield,"home_address" to s.homeAddress,"work_address" to s.workAddress).forEach { (k,v) -> if(v!=null) main[k]=v }
        s.shortcuts.forEachIndexed { i,v -> if(v!=null) main["shortcut_$i"]=v }
        s.display.periods.forEach { p -> main["period_${p.id}_start"]=p.startMinutes; main["period_${p.id}_end"]=p.endMinutes; main["period_${p.id}_brightness"]=p.brightness }
        fun sp(name: String)=context.getSharedPreferences(name,Context.MODE_PRIVATE)
        val theme=sp("theme_appearance")
        val dim=sp("desktop_dimming")
        return mapOf("fit_launcher" to main,
            "theme_appearance" to mapOf("full" to theme.getBoolean("full",true),"small" to theme.getBoolean("small",true),"clarity" to (theme.getString("clarity","balanced") ?: "balanced"),"keep_driving_transparency" to theme.getBoolean("keep_driving_transparency",false)),
            "map_preferences" to mapOf("auto_zoom" to sp("map_preferences").getBoolean("auto_zoom",true)),
            "desktop_dimming" to mapOf("auto" to dim.getBoolean("auto",true),"percent" to dim.getInt("percent",35)))
    }
    fun changes(context: Context, plan: BackupPlan): List<String> {
        val old = snapshot(context)
        return plan.groups.flatMap { (g, entries) ->
            (old[g].orEmpty().keys + entries.keys).filter { old[g]?.get(it) != entries[it] }.map { k ->
                val v=entries[k]
                val missing = if (v is String && (k.startsWith("bind_") || Regex("shortcut_\\d+").matches(k)) && AppRepository.resolve(context, v) == null) "（找不到，請重新選擇）" else ""
                "$g / ${label(k)} → ${v ?: "還原預設／清除"}$missing"
            }
        } + "歌詞：關閉，需重新同意" + "亮度：回到依時段排程"
    }
    private fun label(k: String) = mapOf("bind_navigation" to "導航App", "bind_music" to "播放器", "bind_shield" to "測速App", "home_address" to "回家地址", "work_address" to "公司地址", "shortcut_pages" to "捷徑頁數", "clarity" to "卡片透明度")[k] ?: k
    private fun SharedPreferences.Editor.putValue(k: String, v: Any) { when (v) {
        is Boolean -> putBoolean(k,v); is Int -> putInt(k,v); is String -> putString(k,v)
        else -> error("不支援的設定型別")
    } }
    /** Entire plan is validated before writes; on a storage error restore all previous values. */
    @Synchronized fun apply(context: Context, plan: BackupPlan) {
        val validated = SettingsBackup.decode(SettingsBackup.encode(plan.groups,"validation","now"))
        val names = validated.groups.keys + "desktop_lyrics" + "fit_launcher"
        val before = names.associateWith { context.getSharedPreferences(it, Context.MODE_PRIVATE).all.toMap() }
        try {
            validated.groups.forEach { (g, entries) ->
                val sp = context.getSharedPreferences(g, Context.MODE_PRIVATE)
                check(sp.edit().apply {
                    // Remove old exportable keys absent from a present group; preserve all unrelated data.
                    sp.all.keys.filter { SettingsBackup.known(g,it) }.forEach(::remove)
                    entries.forEach { (k,v) -> putValue(k,v) }
                }.commit()) { "儲存失敗，已還原原設定" }
            }
            check(context.getSharedPreferences("desktop_lyrics", Context.MODE_PRIVATE).edit().putBoolean("lrclib",false).commit())
            // 手動亮度覆寫是暫時狀態，不從備份還原；匯入後回到依時段排程
            check(context.getSharedPreferences("fit_launcher", Context.MODE_PRIVATE).edit().putBoolean("manual_override",false).commit())
        } catch (e: Exception) {
            before.forEach { (g, entries) -> context.getSharedPreferences(g,Context.MODE_PRIVATE).edit().apply {
                clear(); entries.forEach { (k,v) -> when(v) { is Float -> putFloat(k,v); is Long -> putLong(k,v); is Set<*> -> @Suppress("UNCHECKED_CAST") putStringSet(k,v as Set<String>); null -> {}; else -> putValue(k,v) } }
            }.commit() }
            throw e
        }
        Prefs.get(context).reload()
        ThemeAppearanceStore.initialize(context)
        tw.fitcar.launcher.ui.DesktopDimStore.initialize(context)
        tw.fitcar.launcher.ui.DesktopDimStore.manual.value=null
        LyricsRepository.initialize(context)
        revision.value++
    }
}
