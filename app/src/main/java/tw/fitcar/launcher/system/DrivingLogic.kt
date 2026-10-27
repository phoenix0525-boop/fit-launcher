package tw.fitcar.launcher.system

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 行車大字模式：只依 GPS 車速判斷，含遲滯，避免在 10 km/h 附近反覆切換。
 * GPS 短暫失聯（隧道）時沿用前一狀態；失聯超過 [STALE_EXIT_MS] 就回到停車版面，
 * 避免停進地下室後一直卡在行車模式。純邏輯，可做 JVM 單元測試。
 */
object DrivingMode {
    const val ENTER_KMH = 10f
    const val EXIT_KMH = 3f
    const val STALE_EXIT_MS = 60_000L

    fun next(previous: Boolean, speedKmh: Float?, fresh: Boolean, staleForMs: Long): Boolean {
        if (!fresh || speedKmh == null) return previous && staleForMs < STALE_EXIT_MS
        return if (previous) speedKmh > EXIT_KMH else speedKmh >= ENTER_KMH
    }
}

/** 自訂超速提醒：只比對使用者自設門檻與 GPS 車速，不使用測速資料庫、不冒充神盾。 */
object SpeedAlert {
    const val OFF = 0
    const val MIN = 30
    const val MAX = 200
    const val HYSTERESIS_KMH = 3f

    fun clampThreshold(value: Int): Int = if (value <= 0) OFF else value.coerceIn(MIN, MAX)

    fun next(previous: Boolean, speedKmh: Float?, fresh: Boolean, thresholdKmh: Int): Boolean {
        if (thresholdKmh <= 0 || !fresh || speedKmh == null) return false
        return if (previous) speedKmh >= thresholdKmh - HYSTERESIS_KMH else speedKmh >= thresholdKmh
    }
}

/** 本次行程：GPS 估算的里程與行駛時間，只存在記憶體，不上傳。 */
data class TripState(
    val distanceM: Double = 0.0,
    val movingMs: Long = 0L,
    val lastLat: Double? = null,
    val lastLon: Double? = null,
    val lastElapsed: Long = 0L,
)

object Trip {
    /** 兩次定位間隔超過此值視為新的一趟（熄火久停後再出發）。 */
    const val NEW_TRIP_GAP_MS = 30 * 60_000L
    /** 中間斷訊超過此值，不把斷訊前後直線距離算進里程。 */
    const val MAX_SEGMENT_GAP_MS = 30_000L
    const val MAX_ACCURACY_M = 50f
    const val MAX_IMPLIED_KMH = 250.0

    fun add(s: TripState, lat: Double, lon: Double, accuracyM: Float?, speedKmh: Float?, elapsed: Long): TripState {
        if (accuracyM == null || accuracyM > MAX_ACCURACY_M) return s
        val lastLat = s.lastLat
        val lastLon = s.lastLon
        if (lastLat == null || lastLon == null || s.lastElapsed <= 0L) return s.copy(lastLat = lat, lastLon = lon, lastElapsed = elapsed)
        val dt = elapsed - s.lastElapsed
        if (dt <= 0L) return s
        if (dt >= NEW_TRIP_GAP_MS) return TripState(lastLat = lat, lastLon = lon, lastElapsed = elapsed)
        if (dt > MAX_SEGMENT_GAP_MS) return s.copy(lastLat = lat, lastLon = lon, lastElapsed = elapsed)
        val d = distanceM(lastLat, lastLon, lat, lon)
        if (d / dt * 3600.0 > MAX_IMPLIED_KMH) return s.copy(lastLat = lat, lastLon = lon, lastElapsed = elapsed)
        // 停車時的定位飄移：保留原點、不累加
        if ((speedKmh ?: 0f) < 2f && d < 15.0) return s.copy(lastElapsed = elapsed)
        return s.copy(distanceM = s.distanceM + d, movingMs = s.movingMs + dt, lastLat = lat, lastLon = lon, lastElapsed = elapsed)
    }

    fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = p2 - p1
        val dl = Math.toRadians(lon2 - lon1)
        val a = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
        return 2 * r * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    fun format(s: TripState): String {
        val km = s.distanceM / 1000.0
        val minutes = s.movingMs / 60_000L
        return "%.1f km · %d:%02d".format(km, minutes / 60, minutes % 60)
    }
}

/** 倒退／快進：不超出 0 與歌曲長度（保留最後 1 秒避免直接跳下一首）。 */
object SeekStep {
    const val STEP_MS = 15_000L
    fun target(positionMs: Long, deltaMs: Long, durationMs: Long): Long {
        val t = positionMs + deltaMs
        return if (durationMs > 0) t.coerceIn(0L, (durationMs - 1_000L).coerceAtLeast(0L)) else t.coerceAtLeast(0L)
    }
}

/** 底圖樣式跟著日夜主題；深色樣式載入失敗時退回亮色。 */
object MapStyles {
    const val DAY = "https://tiles.openfreemap.org/styles/liberty"
    const val NIGHT = "https://tiles.openfreemap.org/styles/dark"
    fun forTheme(night: Boolean, nightFailed: Boolean = false): String = if (night && !nightFailed) NIGHT else DAY
}

/** 最近使用的播放器（最多 3 個，新的在前），用於音樂卡快速切換。 */
object RecentPlayers {
    const val MAX = 3
    fun push(list: List<String>, packageName: String): List<String> =
        (listOf(packageName) + list.filter { it != packageName && it.isNotBlank() }).take(MAX)
    fun decode(raw: String?): List<String> = raw?.split('|')?.map { it.trim() }?.filter { it.isNotEmpty() }?.distinct()?.take(MAX).orEmpty()
    fun encode(list: List<String>): String = list.take(MAX).joinToString("|")
}

/** 捷徑頁數：超過 3 頁行車中滑動不便，設定頁提示。 */
object ShortcutPagesAdvice {
    const val COMFORT_MAX = 3
    fun warn(pages: Int): Boolean = pages > COMFORT_MAX
}
