package tw.fitcar.launcher

/**
 * 「關於」頁的適用說明。三類資訊分開：
 * 1. 基本條件：可安裝、可當桌面的最低條件，不代表各項車機整合可用。
 * 2. 已回報實測機型：只列真的裝過並回報檢測的裝置。
 * 3. 待驗證功能：即使在已回報機型上也尚未通過實車驗收。
 * 同晶片（UIS7870SC）或同車款的其他車機，不因此被宣稱支援。
 */
object DeviceSupport {
    const val MIN_ANDROID = "Android 8.0（API 26）"

    data class TestedDevice(val name: String, val device: String, val sdk: Int, val detail: String)

    val TESTED = listOf(
        TestedDevice("愛客思 AKZ ZX09", "uis7870sc_2h10_nosec", 33,
            "UNISOC UIS7870SC／Android 13／2000×1200，裝於 Honda FIT4 2022 油電版"),
    )

    /** 本版仍待實車驗收或尚無公開可行方式的整合；不得標示為已支援。 */
    val PENDING = listOf(
        "原廠實際音量（方向盤／原廠鍵會改聲音，但桌面控制未證實有效）",
        "方向盤按鍵對應",
        "導航畫面固定嵌入桌面（目前用分割畫面或全螢幕）",
        "原廠 DSP／EQ 參數自動套用（目前只開啟原廠 App）",
        "熄火後喚醒自動回到桌面、實體 Home 接管",
    )

    fun basics(): String = "$MIN_ANDROID 以上、橫向畫面、允許選擇預設桌面的 Android 車機可安裝使用基本桌面。" +
        "正式版只提供 ARM64（64 位元）安裝包。符合基本條件只代表能安裝，不代表車機專屬整合可用。"

    fun current(manufacturer: String, model: String, device: String, sdk: Int, release: String): String {
        val match = TESTED.firstOrNull { it.device.equals(device, ignoreCase = true) && it.sdk == sdk }
        val head = "目前裝置：$manufacturer $model（$device）／Android $release"
        return head + if (match != null) "。與已回報的「${match.name}」相同，但各功能仍以本機實機檢測結果為準。"
        else "。不在已回報實測清單，車機整合功能請先用「實機檢測」確認。"
    }
}
