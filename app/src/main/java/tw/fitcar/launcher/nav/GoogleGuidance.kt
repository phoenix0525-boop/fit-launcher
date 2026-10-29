package tw.fitcar.launcher.nav

/** Notification text only; no screenshots, route inference or distance extrapolation. */
data class GoogleGuidance(val primary: String, val secondary: String?, val distance: String?, val eta: String?, val arrow: String) {
    companion object {
        private val turns = listOf(
            "↶" to Regex("迴轉|回轉|掉頭|u[- ]?turn", RegexOption.IGNORE_CASE),
            "◎" to Regex("已抵達|到達目的地|抵達目的地|目的地在|you have arrived", RegexOption.IGNORE_CASE),
            "↗" to Regex("靠右|右前方|keep right", RegexOption.IGNORE_CASE),
            "↖" to Regex("靠左|左前方|keep left", RegexOption.IGNORE_CASE),
            "→" to Regex("右轉|向右|turn right", RegexOption.IGNORE_CASE),
            "←" to Regex("左轉|向左|turn left", RegexOption.IGNORE_CASE),
            "↻" to Regex("圓環|圆环|roundabout", RegexOption.IGNORE_CASE),
            "↑" to Regex("直行|直走|繼續行駛|continue straight", RegexOption.IGNORE_CASE),
        )
        private fun clean(value: CharSequence?) = value?.toString()?.filterNot(Char::isISOControl)?.trim()?.take(256).orEmpty()
        fun parse(title: CharSequence?, text: CharSequence?, subText: CharSequence?, navigationCategory: Boolean = false): GoogleGuidance? {
            val t = clean(title); val x = clean(text); val sub = clean(subText)
            val main = "$t $x"
            val arrow = turns.firstOrNull { it.second.containsMatchIn(main) }?.first.orEmpty()
            val navigation = Regex("導航|导航|navigation|駛入匝道|take the exit", RegexOption.IGNORE_CASE).containsMatchIn(main)
            // Distance + road name is valid when the selected Google notification declares navigation.
            // Do not infer an arrow from a notification image or relax unrelated Maps notifications.
            if (t.isBlank() && x.isBlank() || arrow.isBlank() && !navigation && !navigationCategory) return null
            val distance = Regex("(\\d+(?:\\.\\d+)?)\\s*(公尺|公里|km|m)(?![a-zA-Z])", RegexOption.IGNORE_CASE)
                .find(main)?.let { "${it.groupValues[1]} ${it.groupValues[2]}" }
            val eta = Regex("(?<!\\d)([01]?\\d|2[0-3]):([0-5]\\d)(?!\\d)").find(sub)?.value
            return GoogleGuidance(t.ifBlank { x }, x.takeIf { t.isNotBlank() && it.isNotBlank() }, distance, eta, arrow)
        }
    }
}
/**
 * Google 地圖只在顯示文字改變時才更新通知：等紅燈或長直路時，舊通知本來就會停很久。
 * 因此不再用固定 8 秒判斷，而依「通知仍在、車是否在動、距離單位」決定可信時間：
 * - 停車（GPS 有效且 < 5 km/h）：通知仍在就有效，最多 10 分鐘。
 * - 行駛中、距離 < 1 公里：30 秒沒更新視為過期（50 公尺級距約數秒就會變）。
 * - 行駛中、距離 ≥ 1 公里：90 秒（0.1 公里級距在低速時要較久才變）。
 * - 速度未知（GPS 失效、隧道）或無距離：30 秒。
 * 通知被移除或監聽中斷仍立即結束，不推算距離。
 */
object GuidanceFreshness {
    /** 速度未知時的上限；UI 測試以此建立「已過期」狀態。 */
    const val MAX_AGE_MS = 30_000L
    const val NEAR_MOVING_MS = 30_000L
    const val FAR_MOVING_MS = 90_000L
    const val STOPPED_MS = 600_000L
    const val STOPPED_KMH = 5f

    fun distanceMeters(distance: String?): Int? {
        val m = Regex("(\\d+(?:\\.\\d+)?)\\s*(公尺|公里|km|m)", RegexOption.IGNORE_CASE).find(distance ?: return null) ?: return null
        val v = m.groupValues[1].toDoubleOrNull() ?: return null
        return (if (m.groupValues[2].equals("公里") || m.groupValues[2].equals("km", true)) v * 1000 else v).toInt()
    }

    fun limitMs(distanceMeters: Int?, speedKmh: Float?): Long = when {
        speedKmh == null -> MAX_AGE_MS
        speedKmh < STOPPED_KMH -> STOPPED_MS
        distanceMeters == null -> MAX_AGE_MS
        distanceMeters >= 1000 -> FAR_MOVING_MS
        else -> NEAR_MOVING_MS
    }

    fun isFresh(receivedElapsed: Long, now: Long, connected: Boolean, distanceMeters: Int? = null, speedKmh: Float? = null): Boolean =
        connected && receivedElapsed > 0 && now - receivedElapsed in 0 until limitMs(distanceMeters, speedKmh)
}
