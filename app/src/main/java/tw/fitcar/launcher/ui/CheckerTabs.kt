package tw.fitcar.launcher.ui

/** 檢測分頁名稱；其他畫面以名稱取得索引，避免調整順序後開錯分頁。 */
object CheckerTabs {
    const val IMPROVEMENTS = "可改進的地方"; const val BRIGHTNESS = "亮度測試"; const val MANUAL = "手動檢查"; const val VOLUME = "音量檢測"
    val ALL = listOf(IMPROVEMENTS, "全部結果", "手勢測試", "按鍵測試", BRIGHTNESS, "分割畫面", "GPS 測試", MANUAL, VOLUME, "匯出報告")
    fun index(name: String): Int = ALL.indexOf(name).coerceAtLeast(0)
}
