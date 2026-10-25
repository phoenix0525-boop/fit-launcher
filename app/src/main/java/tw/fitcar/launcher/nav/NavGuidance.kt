package tw.fitcar.launcher.nav

enum class Turn { LEFT, RIGHT, STRAIGHT, KEEP_LEFT, KEEP_RIGHT, U_TURN, ROUNDABOUT, ARRIVE, UNKNOWN }

/**
 * 導航 App 進行中通知的文字整理（參考開車趣「首頁顯示導航指示」的做法，自行實作）。
 * 只整理通知本來就顯示的文字：轉彎方向、距離、路名、到達時間；不另外計算路線。
 */
data class NavGuidance(
    val primary: String,
    val secondary: String?,
    val distance: String?,
    val eta: String?,
    val turn: Turn,
) {
    val arrow: String get() = when (turn) {
        Turn.LEFT -> "←"
        Turn.RIGHT -> "→"
        Turn.STRAIGHT -> "↑"
        Turn.KEEP_LEFT -> "↖"
        Turn.KEEP_RIGHT -> "↗"
        Turn.U_TURN -> "↶"
        Turn.ROUNDABOUT -> "↻"
        Turn.ARRIVE -> "◎"
        Turn.UNKNOWN -> "↑"
    }

    companion object {
        private val DISTANCE = Regex("""(\d+(?:\.\d+)?)\s*(公尺|公里|km|KM|Km|m|M)(?![a-zA-Z])""")
        private val ETA = Regex("""(?:預計|約|抵達|到達)?\s*(\d{1,2}:\d{2})\s*(?:抵達|到達)?""")

        // 順序重要：「靠左」要比「左」先判斷，「迴轉」要比「轉」先判斷
        private val TURNS = listOf(
            Turn.U_TURN to listOf("迴轉", "掉頭", "U 形", "U形"),
            Turn.ROUNDABOUT to listOf("圓環"),
            Turn.ARRIVE to listOf("抵達目的地", "到達目的地", "已抵達", "目的地在"),
            Turn.KEEP_LEFT to listOf("靠左", "左前方", "左側車道"),
            Turn.KEEP_RIGHT to listOf("靠右", "右前方", "右側車道", "匝道"),
            Turn.LEFT to listOf("左轉", "向左"),
            Turn.RIGHT to listOf("右轉", "向右"),
            Turn.STRAIGHT to listOf("直走", "直行", "繼續行駛", "沿著"),
        )

        fun parse(title: CharSequence?, text: CharSequence?, subText: CharSequence?): NavGuidance? {
            val t = title?.toString()?.trim().orEmpty()
            val x = text?.toString()?.trim().orEmpty()
            val s = subText?.toString()?.trim().orEmpty()
            if (t.isEmpty() && x.isEmpty()) return null
            val primary = t.ifEmpty { x }
            val secondary = if (t.isEmpty()) null else x.ifEmpty { null }
            val all = listOf(t, x, s).joinToString(" ")
            val distance = DISTANCE.find("$t $x")?.let { "${it.groupValues[1]} ${it.groupValues[2]}" }
            val eta = ETA.find(s)?.groupValues?.get(1) ?: ETA.find("$t $x")?.groupValues?.get(1)
            val turn = TURNS.firstOrNull { (_, words) -> words.any { all.contains(it) } }?.first ?: Turn.UNKNOWN
            return NavGuidance(primary, secondary, distance, eta, turn)
        }
    }
}
