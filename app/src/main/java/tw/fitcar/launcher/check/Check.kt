package tw.fitcar.launcher.check

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** 檢測結果等級。「可改進的地方」只列有問題；歷史結果獨立保留。 */
enum class Status(val mark: String, val label: String, val order: Int) {
    FAIL("✗", "有問題", 0),
    NA("—", "不適用", 5),
    HISTORY("◷", "歷史結果", 6),
    TODO("☐", "待測試", 2),
    PASS("✓", "正常", 3),
    INFO("ℹ", "資訊", 4),
}

/** 可在 車機桌面內一鍵套用的建議。 */
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
    val appVersion: String = "",
    val scenario: String = "",
    val notes: String = "",
    val contextKey: String = "",
)

/** 群組顯示順序。 */
val GROUPS = listOf("車機硬體", "顯示", "桌面", "導航", "音樂", "測速", "亮度", "定位", "操作", "電源", "手動檢查")

/** 檢測結果存放處；互動測試與手動檢查結果會保存，重開 App 仍在。 */
object CheckStore {
    private val _results = MutableStateFlow<Map<String, CheckResult>>(emptyMap())
    val results: StateFlow<Map<String, CheckResult>> = _results.asStateFlow()
    private var records: MutableList<CheckResult>? = null
    private var projectedKeys = emptySet<String>()
    private fun sp(context: Context) = context.applicationContext.getSharedPreferences("fit_checks", Context.MODE_PRIVATE)

    fun version(context: Context): String {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val code = if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        return "${info.versionName} ($code)"
    }

    fun scenario(context: Context): String {
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_HOME)
        val home = context.packageManager.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        val state = tw.fitcar.launcher.data.Prefs.get(context).state.value
        val media = tw.fitcar.launcher.media.MediaRepository.state.value
        return "預設桌面=${home ?: "未指定"}; FIT預設=${home == context.packageName}; 播放器=${state.bindings.music ?: "未指定"}; 模式=${state.mediaMode}; 控制來源=${media.packageName ?: "無"}; 導航=${state.bindings.navigation ?: "未指定"}; 導航模式=${state.navOpenMode}; 字體=${context.resources.configuration.fontScale}"
    }

    fun load(context: Context) {
        if (records == null) {
            val list = mutableListOf<CheckResult>()
            runCatching {
                val arr = JSONArray(sp(context).getString("saved", "[]"))
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    runCatching {
                        list += CheckResult(o.getString("id"), o.getString("group"), o.getString("title"),
                            if (o.getString("status") == "WARN") Status.INFO else Status.valueOf(o.getString("status")),
                            o.getString("finding"), o.optString("suggestion"), time = o.optLong("time"),
                            appVersion = o.optString("appVersion"), scenario = o.optString("scenario"),
                            notes = o.optString("notes"), contextKey = o.optString("contextKey"))
                    }
                }
            }
            records = list
        }
        project(context)
    }

    internal fun reload(context: Context) { records = null; load(context) }

    private fun project(context: Context) {
        val next = LinkedHashMap(_results.value.filterKeys { it !in projectedKeys })
        val keys = mutableSetOf<String>()
        records.orEmpty().groupBy { it.id }.forEach { (id, history) ->
            val latest = history.lastOrNull { ResultValidity.matches(it.appVersion, it.contextKey, version(context), scenario(context), id) &&
                (id != "m_reverse" || "FIT預設=true" in it.contextKey) }
            history.forEachIndexed { index, result ->
                if (result !== latest) {
                    val key = "history:$id:$index"
                    next[key] = result.copy(id = key, status = Status.HISTORY, fix = null,
                        finding = "原結果：${result.status.label}。${result.finding}")
                    keys += key
                }
            }
            next[id] = latest ?: history.last().copy(status = Status.TODO, fix = null,
                finding = "本情境待測試：既有結果的版本或影響此項的情境（${ResultValidity.relevantFields(id)?.joinToString("、")?.ifEmpty { "僅版本" } ?: "全部情境"}）不符。", suggestion = "舊結果保留於歷史結果。",
                appVersion = version(context), scenario = scenario(context), notes = "", time = 0L)
            keys += id
        }
        projectedKeys = keys
        _results.value = next
    }

    fun put(context: Context, results: List<CheckResult>, persist: Boolean) {
        load(context)
        if (persist) {
            val scenario = scenario(context)
            records!!.addAll(results.map { it.copy(appVersion = it.appVersion.ifBlank { version(context) },
                scenario = it.scenario.ifBlank { scenario }, contextKey = it.contextKey.ifBlank { scenario },
                status = if (it.id == "m_reverse" && "FIT預設=false" in it.contextKey.ifBlank { scenario }) Status.TODO else it.status,
                finding = if (it.id == "m_reverse" && "FIT預設=false" in it.contextKey.ifBlank { scenario }) "本情境待測試：測試時本程式並非預設桌面。${it.finding}" else it.finding) })
            save(context)
            project(context)
        } else _results.value = _results.value + results.associateBy { it.id }
    }
    fun put(context: Context, result: CheckResult, persist: Boolean = true) = put(context, listOf(result), persist)

    private fun save(context: Context) {
        val arr = JSONArray()
        records.orEmpty().forEach { r -> arr.put(JSONObject().apply {
            put("id", r.id); put("group", r.group); put("title", r.title); put("status", r.status.name)
            put("finding", r.finding); put("suggestion", r.suggestion); put("time", r.time)
            put("appVersion", r.appVersion); put("scenario", r.scenario); put("notes", r.notes); put("contextKey", r.contextKey)
        }) }
        sp(context).edit().putString("saved", arr.toString()).apply()
    }
    fun clearSaved(context: Context) {
        records = mutableListOf()
        sp(context).edit().clear().apply()
        _results.value = _results.value.filterKeys { it !in projectedKeys }
        projectedKeys = emptySet()
    }
    fun sorted(map: Map<String, CheckResult>): List<CheckResult> = map.values.sortedWith(
        compareBy({ it.status == Status.HISTORY }, { GROUPS.indexOf(it.group).let { i -> if (i < 0) 99 else i } }, { it.title }))
    fun improvements(map: Map<String, CheckResult>): List<CheckResult> =
        map.values.filter { it.status == Status.FAIL }.sortedBy { GROUPS.indexOf(it.group) }
}
