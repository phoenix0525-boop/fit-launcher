package tw.fitcar.launcher.weather

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import tw.fitcar.launcher.system.GpsSpeed
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class WeatherUiState(
    val enabled: Boolean = false,
    val hasKey: Boolean = false,
    val current: NearestStation? = null,
    val updatedAtMillis: Long = 0L,
    val loading: Boolean = false,
    val error: String? = null,
    val history: List<DayRecord> = emptyList(),
)

/**
 * 中央氣象署開放資料「局屬氣象站現在天氣觀測」（O-A0003-001）。
 * - 使用者自行到 opendata.cwa.gov.tw 申請授權碼並填入；授權碼只存在本機。
 * - 只向氣象署下載全台測站資料，在車機端用 GPS 挑最近測站；不上傳位置。
 * - 前景時每 30 分鐘更新；失敗時保留上一筆並標示錯誤，不顯示猜測值。
 */
object WeatherRepository {
    private const val DATASET = "O-A0003-001"
    private const val REFRESH_MS = 30 * 60 * 1000L
    private const val MAX_BYTES = 4 * 1024 * 1024

    private val _state = MutableStateFlow(WeatherUiState())
    val state: StateFlow<WeatherUiState> = _state.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var lastFetchElapsed = 0L
    private var loaded = false

    private fun sp(context: Context) = context.applicationContext.getSharedPreferences("fit_weather", Context.MODE_PRIVATE)

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val p = sp(context)
        _state.value = _state.value.copy(
            enabled = p.getBoolean("enabled", false),
            hasKey = !p.getString("key", null).isNullOrBlank(),
            history = parseHistory(p.getString("history", null)),
        )
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        sp(context).edit().putBoolean("enabled", enabled).apply()
        _state.value = _state.value.copy(enabled = enabled, error = null)
        if (enabled) refresh(context, force = true)
    }

    fun setKey(context: Context, key: String) {
        val k = key.trim()
        sp(context).edit().putString("key", k.ifEmpty { null }).apply()
        _state.value = _state.value.copy(hasKey = k.isNotEmpty(), error = null)
        if (k.isNotEmpty() && _state.value.enabled) refresh(context, force = true)
    }

    fun refresh(context: Context, force: Boolean = false) {
        load(context)
        val s = _state.value
        if (!s.enabled) return
        val key = sp(context).getString("key", null)
        if (key.isNullOrBlank()) { _state.value = s.copy(error = "尚未填寫氣象署授權碼"); return }
        val now = SystemClock.elapsedRealtime()
        if (!force && lastFetchElapsed != 0L && now - lastFetchElapsed < REFRESH_MS) return
        if (job?.isActive == true) return
        lastFetchElapsed = now
        val app = context.applicationContext
        _state.value = s.copy(loading = true)
        job = scope.launch {
            val result = try {
                val stations = parseStations(download(key))
                if (stations.isEmpty()) throw IllegalStateException("資料格式不符或沒有測站資料")
                stations
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = "天氣更新失敗：${e.message ?: e.javaClass.simpleName}")
                return@launch
            }
            val gps = GpsSpeed.state.value
            val nearest = WeatherLogic.nearest(result, gps.latitude, gps.longitude)
            if (nearest == null) {
                _state.value = _state.value.copy(loading = false, error = "附近測站目前沒有氣溫資料")
                return@launch
            }
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.TAIWAN).format(Date())
            val history = WeatherLogic.addToHistory(_state.value.history, nearest.station, today)
            sp(app).edit().putString("history", historyJson(history)).apply()
            _state.value = _state.value.copy(
                current = nearest, updatedAtMillis = System.currentTimeMillis(),
                loading = false, error = null, history = history,
            )
        }
    }

    private fun download(key: String): String {
        val url = URL(
            "https://opendata.cwa.gov.tw/api/v1/rest/datastore/$DATASET?Authorization=" +
                URLEncoder.encode(key, "UTF-8") + "&format=JSON",
        )
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        try {
            val code = conn.responseCode
            if (code == 401 || code == 403) throw IllegalStateException("授權碼無效（HTTP $code）")
            if (code != 200) throw IllegalStateException("HTTP $code")
            conn.inputStream.use { input ->
                val bytes = input.readNBytesCompat(MAX_BYTES + 1)
                if (bytes.size > MAX_BYTES) throw IllegalStateException("資料過大")
                return String(bytes, Charsets.UTF_8)
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (out.size() < limit) {
            val n = read(buf, 0, minOf(buf.size, limit - out.size()))
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    /** 依氣象署 O-A0003-001 JSON：records.Station[].GeoInfo／ObsTime／WeatherElement。 */
    internal fun parseStations(json: String): List<StationObs> {
        val root = JSONObject(json)
        val list = root.optJSONObject("records")?.optJSONArray("Station") ?: return emptyList()
        return (0 until list.length()).mapNotNull { i ->
            val st = list.optJSONObject(i) ?: return@mapNotNull null
            val geo = st.optJSONObject("GeoInfo")
            val coord = pickCoordinate(geo?.optJSONArray("Coordinates")) ?: return@mapNotNull null
            val we = st.optJSONObject("WeatherElement")
            val daily = we?.optJSONObject("DailyExtreme")
            StationObs(
                name = st.optString("StationName"),
                county = geo?.optString("CountyName").orEmpty(),
                town = geo?.optString("TownName").orEmpty(),
                latitude = coord.first,
                longitude = coord.second,
                obsTime = st.optJSONObject("ObsTime")?.optString("DateTime").orEmpty(),
                weather = WeatherLogic.cleanText(we?.optString("Weather")),
                tempC = WeatherLogic.clean(we?.optNumber("AirTemperature")),
                highC = WeatherLogic.clean(daily?.optJSONObject("DailyHigh")?.optJSONObject("TemperatureInfo")?.optNumber("AirTemperature")),
                lowC = WeatherLogic.clean(daily?.optJSONObject("DailyLow")?.optJSONObject("TemperatureInfo")?.optNumber("AirTemperature")),
            )
        }
    }

    private fun JSONObject.optNumber(name: String): Double? {
        if (!has(name)) return null
        val v = optDouble(name)
        return if (v.isNaN()) null else v
    }

    /** 優先 WGS84 座標；沒有標示時取最後一組（氣象署慣例：TWD67 在前、WGS84 在後）。 */
    private fun pickCoordinate(arr: JSONArray?): Pair<Double, Double>? {
        if (arr == null || arr.length() == 0) return null
        val objs = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
        val c = objs.firstOrNull { it.optString("CoordinateName").equals("WGS84", ignoreCase = true) } ?: objs.lastOrNull() ?: return null
        val lat = c.optDouble("StationLatitude")
        val lon = c.optDouble("StationLongitude")
        if (lat.isNaN() || lon.isNaN() || lat !in 20.0..27.0 || lon !in 117.0..124.0) return null
        return lat to lon
    }

    private fun historyJson(list: List<DayRecord>): String = JSONArray().apply {
        list.forEach { d ->
            put(JSONObject().apply {
                put("date", d.date); put("station", d.station)
                d.highC?.let { put("high", it) }; d.lowC?.let { put("low", it) }; d.weather?.let { put("weather", it) }
            })
        }
    }.toString()

    private fun parseHistory(text: String?): List<DayRecord> = try {
        val arr = JSONArray(text ?: return emptyList())
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            DayRecord(
                date = o.optString("date"),
                highC = if (o.has("high")) o.optDouble("high") else null,
                lowC = if (o.has("low")) o.optDouble("low") else null,
                weather = if (o.has("weather")) o.optString("weather") else null,
                station = o.optString("station"),
            )
        }.take(WeatherLogic.KEEP_DAYS)
    } catch (_: Exception) { emptyList() }
}
