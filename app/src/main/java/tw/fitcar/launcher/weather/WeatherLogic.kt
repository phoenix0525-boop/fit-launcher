package tw.fitcar.launcher.weather

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** 中央氣象署測站觀測（O-A0003-001）整理後的一筆資料。 */
data class StationObs(
    val name: String,
    val county: String,
    val town: String,
    val latitude: Double,
    val longitude: Double,
    val obsTime: String,
    val weather: String?,
    val tempC: Double?,
    val highC: Double?,
    val lowC: Double?,
)

/** 一天的紀錄（保留 5 天，參考 AGAMA「保留 5 天天氣紀錄」）。 */
data class DayRecord(val date: String, val highC: Double?, val lowC: Double?, val weather: String?, val station: String)

data class NearestStation(val station: StationObs, val distanceKm: Double?)

/** 純邏輯：選最近測站、清除缺值、更新 5 天紀錄。可單元測試。 */
object WeatherLogic {
    const val KEEP_DAYS = 5

    /** 氣象署以 -99 等負值代表缺測。 */
    fun clean(v: Double?): Double? = v?.takeIf { it > -90.0 }

    fun cleanText(s: String?): String? = s?.trim()?.takeIf { it.isNotEmpty() && it != "-99" && it != "X" }

    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * r * asin(sqrt(a))
    }

    /** 有 GPS 時選最近且有氣溫的測站；沒有 GPS 時優先桃園市測站。 */
    fun nearest(stations: List<StationObs>, lat: Double?, lon: Double?): NearestStation? {
        val usable = stations.filter { clean(it.tempC) != null }
        if (usable.isEmpty()) return null
        if (lat == null || lon == null) {
            val s = usable.firstOrNull { it.county.contains("桃園") } ?: usable.first()
            return NearestStation(s, null)
        }
        return usable.map { NearestStation(it, distanceKm(lat, lon, it.latitude, it.longitude)) }.minByOrNull { it.distanceKm ?: Double.MAX_VALUE }
    }

    /** 同一天合併最高／最低溫（優先使用測站當日極值），保留最近 5 天，新日期在前。 */
    fun addToHistory(history: List<DayRecord>, obs: StationObs, date: String): List<DayRecord> {
        val t = clean(obs.tempC)
        val hi = clean(obs.highC) ?: t
        val lo = clean(obs.lowC) ?: t
        val old = history.firstOrNull { it.date == date }
        val merged = DayRecord(
            date = date,
            highC = listOfNotNull(old?.highC, hi).maxOrNull(),
            lowC = listOfNotNull(old?.lowC, lo).minOrNull(),
            weather = cleanText(obs.weather) ?: old?.weather,
            station = obs.name,
        )
        return (listOf(merged) + history.filter { it.date != date }).sortedByDescending { it.date }.take(KEEP_DAYS)
    }
}
