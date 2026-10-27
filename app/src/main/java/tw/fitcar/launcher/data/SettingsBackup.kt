package tw.fitcar.launcher.data

import org.json.JSONObject

data class BackupPlan(val groups: Map<String, Map<String, Any>>)
/** Strict whitelist. Never serializes raw preference files or third-party credentials. */
object SettingsBackup {
    const val MAX_BYTES = 256 * 1024
    private val component = Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+/[A-Za-z0-9_.$]+")
    val groups = setOf("fit_launcher", "theme_appearance", "map_preferences", "desktop_dimming")
    fun known(group: String, key: String): Boolean = when (group) {
        "fit_launcher" -> key in setOf("auto_schedule", "auto_theme", "manual_theme", "manual_brightness", "smooth_transition", "bind_navigation", "bind_music", "bind_shield", "experimental_split", "media_mode", "manual_override", "shortcut_pages", "accent_theme", "visual_style", "home_address", "work_address", "speed_alert_kmh") || Regex("shortcut_([0-9]|1[0-9]|2[0-3])").matches(key) || Regex("period_(day|dusk|night)_(start|end|brightness)").matches(key)
        "theme_appearance" -> key in setOf("full", "small", "clarity", "keep_driving_transparency")
        "map_preferences" -> key == "auto_zoom"
        "desktop_dimming" -> key in setOf("auto", "percent")
        else -> false
    }
    private fun validate(g: String, k: String, v: Any) {
        fun integer(range: IntRange) { require(v is Int && v in range) { "$k 超出範圍或不是整數" } }
        fun choice(values: Set<String>) { require(v is String && v in values) { "$k 選項不支援" } }
        when {
            k == "shortcut_pages" -> integer(1..6)
            k == "speed_alert_kmh" -> { require(v is Int && (v == 0 || v in 30..200)) { "超速門檻須為0或30–200" } }
            k == "manual_brightness" || k.endsWith("_brightness") -> integer(1..100)
            k == "percent" -> integer(10..100)
            k.endsWith("_start") || k.endsWith("_end") -> integer(0..1439)
            k == "manual_theme" -> choice(setOf("day", "night"))
            k == "media_mode" -> choice(setOf("fixed", "follow"))
            k == "clarity" -> choice(setOf("clear", "balanced", "airy"))
            k == "accent_theme" -> choice(setOf("mist", "graphite", "wine", "forest", "caramel", "hero_red", "sand"))
            k == "visual_style" -> choice(setOf("original", "japanese", "white", "pompompurin", "spiderman"))
            k == "home_address" || k == "work_address" -> require(v is String && v.length <= 500 && !v.contains('\u0000')) { "地址長度或格式不正確" }
            k.startsWith("bind_") || k.startsWith("shortcut_") -> require(v is String && v.length <= 500 && component.matches(v)) { "$k 不是有效 App 元件" }
            else -> require(v is Boolean) { "$g/$k 應為開關值" }
        }
    }
    fun encode(values: Map<String, Map<String, Any>>, appVersion: String, exportedAt: String): String {
        val body = JSONObject()
        values.filterKeys { it in groups }.forEach { (g, entries) ->
            val o = JSONObject()
            entries.filterKeys { known(g, it) }.forEach { (k, v) -> validate(g, k, v); o.put(k, v) }
            body.put(g, o)
        }
        return JSONObject().put("schemaVersion", 1).put("appVersion", appVersion).put("exportedAt", exportedAt).put("groups", body).toString()
    }
    fun decode(raw: String): BackupPlan {
        require(raw.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "備份超過256KB" }
        val root = try { JSONObject(raw) } catch (_: Exception) { error("不是有效JSON備份") }
        val schema = root.opt("schemaVersion")
        require(schema is Int && schema in 0..1) { "不支援的schemaVersion（僅0、1）" }
        // Schema 0 has the same groups envelope; absent newer groups remain unchanged.
        val body = root.opt("groups") as? JSONObject ?: error("缺少groups物件")
        val result = linkedMapOf<String, Map<String, Any>>()
        body.keys().forEach { g -> if (g in groups) {
            val o = body.opt(g) as? JSONObject ?: error("$g 不是設定物件")
            val entries = linkedMapOf<String, Any>()
            o.keys().forEach { k -> if (known(g, k)) { val v = o.get(k); validate(g,k,v); entries[k]=v } }
            result[g]=entries
        } }
        require(result.isNotEmpty()) { "備份中沒有可還原的設定" }
        return BackupPlan(result)
    }
}
