package tw.fitcar.launcher.system

/**
 * 主畫面音量按鈕的說明。實車證據（0.4.22、0.4.24）：方向盤音量鍵與原廠語音助手可改變聲音；
 * 桌面以 Android 公開 API 調整（絕對、相對、系統目前音量）聲音都沒有變化，因此不再提供桌面音量滑桿。
 */
object VolumeGuidance {
    const val BUTTON_DESCRIPTION = "音量說明與原廠音效"
    const val TITLE = "調整音量"
    val WAYS = listOf("方向盤的音量鍵", "原廠語音助手（例如說「小聲一點」）", "車機面板的音量鍵或旋鈕")
    const val FACTORY_EQ = "開啟原廠音效（EQ／DSP）"
    const val SYSTEM_PANEL = "系統音量面板（效果依車機而定）"
    const val NOTE = "本車機的實際音量由原廠主機控制；車機桌面不送出音量指令，也不顯示 Android 音量數值，以免和實際聲音不一致。"
}
