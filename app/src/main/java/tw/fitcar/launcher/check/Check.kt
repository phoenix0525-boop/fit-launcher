package tw.fitcar.launcher.check

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** 檢測結果等級。「可改進的地方」= WARN＋FAIL。 */
enum class Status(val mark: String, val label: String, val order: Int) {
    FAIL("✗", "有問題", 0),
    WARN("⚠", "可改進", 1),
    TODO("☐", "待測試", 2),
    PASS("✓", "正常", 3),
    INFO("ℹ", "資訊", 4),
}

/** 可在 FIT 桌面內一鍵套用的建議。 */
enum class FixKind { NAV_FULLSCREEN, NAV_SPLIT, DISABLE_BRIGHTNESS_SCHEDULE, BIND_NAV, BIND_MUSIC, BIND_SHIELD, OPEN_NOTIFICATION_ACCESS, OPEN_WRITE_SETTINGS, REQUEST_LOCATION, OPEN_OVERLAY_SETTINGS }

data class Fix(val label: String, val kind: FixKind, val arg: String? = null)

data class CheckResult(
    val id: String,
    val group: String,
    val title: String,
    val status: Status,
    val finding: String,
    val suggestion: String = "",
    val fix: Fix? = null,
    val time: Long = System.currentTimeMillis(),
)

/** 群組顯示順序。 */
val GROUPS = listOf("車機硬體", "顯示", "桌面", "導航", "音樂", "測速", "亮度", "定位", "操作", "電源", "手動檢查")

/** 檢測結果存放處；互動測試與手動檢查結果會保存，重開 App 仍在。 */
object CheckStore {
    private val _results = MutableStateFlow<Map<String, CheckResult>>(emptyMap())
    val results: StateFlow<Map<String, CheckResult>> = _results.asStateFlow()
    private var loaded = false

    private fun sp(context: Context) = context.applicationContext.getSharedPreferences("fit_checks", Context.MODE_PRIVATE)

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val text = sp(context).getString("saved", null) ?: return
        val map = LinkedHashMap<String, CheckResult>()
        try {
            val arr = JSONArray(text)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val fix = o.optJSONObject("fix")?.let { f ->
                    Fix(f.getString("label"), FixKind.valueOf(f.getString("kind")), f.optString("arg").ifEmpty { null })
                }
                val r = CheckResult(
                    id = o.getString("id"), group = o.getString("group"), title = o.getString("title"),
                    status = Status.valueOf(o.getString("status")), finding = o.getString("finding"),
                    suggestion = o.optString("suggestion"), fix = fix, time = o.optLong("time"),
                )
                map[r.id] = r
            }
        } catch (_: Exception) { }
        _results.value = _results.value + map
    }

    /** persist = true 用於互動測試與手動檢查（自動檢測每次重算，不必保存）。 */
    fun put(context: Context, results: List<CheckResult>, persist: Boolean) {
        val next = LinkedHashMap(_results.value)
        results.forEach { next[it.id] = it }
        _results.value = next
        if (persist) {
            val saved = savedIds(context) + results.map { it.id }
            sp(context).edit()
                .putStringSet("saved_ids", saved)
                .putString("saved", toJson(next.values.filter { it.id in saved }).toString())
                .apply()
        }
    }

    fun put(context: Context, result: CheckResult, persist: Boolean = true) = put(context, listOf(result), persist)

    private fun savedIds(context: Context): Set<String> = sp(context).getStringSet("saved_ids", emptySet()) ?: emptySet()

    fun clearSaved(context: Context) {
        val ids = savedIds(context)
        sp(context).edit().clear().apply()
        _results.value = _results.value.filterKeys { it !in ids }
    }

    private fun toJson(list: Collection<CheckResult>): JSONArray = JSONArray().apply {
        list.forEach { r ->
            put(JSONObject().apply {
                put("id", r.id); put("group", r.group); put("title", r.title)
                put("status", r.status.name); put("finding", r.finding); put("suggestion", r.suggestion)
                put("time", r.time)
                r.fix?.let { f -> put("fix", JSONObject().apply { put("label", f.label); put("kind", f.kind.name); put("arg", f.arg ?: "") }) }
            })
        }
    }

    fun sorted(map: Map<String, CheckResult>): List<CheckResult> =
        map.values.sortedWith(compareBy({ GROUPS.indexOf(it.group).let { i -> if (i < 0) 99 else i } }, { it.title }))

    fun improvements(map: Map<String, CheckResult>): List<CheckResult> =
        map.values.filter { it.status == Status.FAIL || it.status == Status.WARN }
            .sortedWith(compareBy({ it.status.order }, { GROUPS.indexOf(it.group) }))
}
