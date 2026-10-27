package tw.fitcar.launcher.data

import android.content.Context
import android.content.SharedPreferences
import tw.fitcar.launcher.lyrics.LyricsRepository
import tw.fitcar.launcher.ui.ThemeAppearanceStore

object BackupStore {
    fun snapshot(context: Context): Map<String, Map<String, Any>> = SettingsBackup.groups.associateWith { g ->
        context.getSharedPreferences(g, Context.MODE_PRIVATE).all.filterKeys { SettingsBackup.known(g,it) }.mapValues { it.value as Any }
    }
    fun changes(context: Context, plan: BackupPlan): List<String> {
        val old = snapshot(context)
        return plan.groups.flatMap { (g, entries) -> entries.filter { (k,v) -> old[g]?.get(k) != v }.map { (k,v) ->
            val missing = if ((k.startsWith("bind_") || Regex("shortcut_\\d+").matches(k)) && AppRepository.resolve(context, v as String) == null) "（找不到，請重新選擇）" else ""
            "$g / ${label(k)} → $v$missing"
        } } + "歌詞：關閉，需重新同意"
    }
    private fun label(k: String) = mapOf("bind_navigation" to "導航App", "bind_music" to "播放器", "bind_shield" to "測速App", "home_address" to "回家地址", "work_address" to "公司地址", "shortcut_pages" to "捷徑頁數", "clarity" to "卡片透明度")[k] ?: k
    private fun SharedPreferences.Editor.putValue(k: String, v: Any) { when (v) {
        is Boolean -> putBoolean(k,v); is Int -> putInt(k,v); is String -> putString(k,v)
        else -> error("不支援的設定型別")
    } }
    /** Entire plan is validated before writes; on a storage error restore all previous values. */
    @Synchronized fun apply(context: Context, plan: BackupPlan) {
        val validated = SettingsBackup.decode(SettingsBackup.encode(plan.groups,"validation","now"))
        val names = validated.groups.keys + "desktop_lyrics"
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
    }
}
