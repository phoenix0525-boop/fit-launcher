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
        fun parse(title: CharSequence?, text: CharSequence?, subText: CharSequence?): GoogleGuidance? {
            val t = clean(title); val x = clean(text); val sub = clean(subText)
            val main = "$t $x"
            val arrow = turns.firstOrNull { it.second.containsMatchIn(main) }?.first.orEmpty()
            val navigation = Regex("導航|导航|navigation|駛入匝道|take the exit", RegexOption.IGNORE_CASE).containsMatchIn(main)
            if (t.isBlank() && x.isBlank() || arrow.isBlank() && !navigation) return null
            val distance = Regex("(\\d+(?:\\.\\d+)?)\\s*(公尺|公里|km|m)(?![a-zA-Z])", RegexOption.IGNORE_CASE)
                .find(if (t.isNotBlank()) t else x)?.let { "${it.groupValues[1]} ${it.groupValues[2]}" }
            val eta = Regex("(?<!\\d)([01]?\\d|2[0-3]):([0-5]\\d)(?!\\d)").find(sub)?.value
            return GoogleGuidance(t.ifBlank { x }, x.takeIf { t.isNotBlank() && it.isNotBlank() }, distance, eta, arrow)
        }
    }
}
object GuidanceFreshness {
    const val MAX_AGE_MS = 8000L
    fun isFresh(receivedElapsed: Long, now: Long, connected: Boolean): Boolean =
        connected && receivedElapsed > 0 && now - receivedElapsed in 0 until MAX_AGE_MS
}
