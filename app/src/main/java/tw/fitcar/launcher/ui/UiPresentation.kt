package tw.fitcar.launcher.ui

/** 主畫面導航區的簡短操作說明；技術限制放在設定／README，不當成錯誤顯示。 */
object NavPanelCopy {
    fun supportMessage(splitResult: Boolean?): String =
        "導航會以分割畫面或全螢幕開啟；可隨時按 Home 返回 FIT 桌面。" +
            when (splitResult) {
                true -> "　最近一次：分割畫面成功。"
                false -> "　最近一次：已改用全螢幕。"
                null -> "　尚未測試分割畫面。"
            }
}

/** 過度透明、只剩小點的 Drawable 視為轉換失敗，改用一致的通用 App 圖示。 */
object IconQuality {
    private const val MIN_VISIBLE_RATIO = 0.04

    fun isUsable(visiblePixels: Int, totalPixels: Int): Boolean =
        totalPixels > 0 && visiblePixels.toDouble() / totalPixels >= MIN_VISIBLE_RATIO
}
