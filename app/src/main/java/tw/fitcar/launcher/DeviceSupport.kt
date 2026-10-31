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

    data class TestedDevice(val name: String, val device: String, val sdk: Int, val detail: String,
                            val chip: String, val platform: String, val androidRelease: String)

    val TESTED = listOf(
        TestedDevice("愛客思 AKZ ZX09", "uis7870sc_2h10_nosec", 33,
            "UNISOC UIS7870SC／Android 13／2000×1200，裝於 Honda FIT4 2022 油電版",
            chip = "UNISOC UIS7870SC", platform = "FYT／SYU 原廠系統", androidRelease = "13"),
    )

    /** 一句話標示測試機型與晶片；其他機種可能不相容。關於頁、桌面接管頁、發布說明共用。 */
    val COMPATIBILITY_NOTE: String get() = TESTED.joinToString("、") { "${it.name}（${it.chip}，${it.platform}，Android ${it.androidRelease}）" }
        .let { "本程式只在 $it 上測試。其他機種、晶片或原廠系統（例如非 FYT 平台、不同 Android 版本或解析度）可能有相容性問題：" +
            "桌面接管、倒車畫面判斷、原廠 App 入口與版面都可能不同，請先用「實機檢測」確認，不要直接設為預設桌面。" }

    /** 目前裝置是否為已回報實測機型（裝置代號與 Android 版本都相同）。 */
    fun isTested(device: String, sdk: Int): Boolean = TESTED.any { it.device.equals(device, ignoreCase = true) && it.sdk == sdk }

    /** 本版仍待實車驗收的整合；不得標示為已支援。 */
    val PENDING = listOf(
        "實體 Home 鍵、開機與熄火喚醒後回到車機桌面",
        "導航王／Google 地圖切換與 Google 轉彎提示",
        "播放器控制、亮度排程、分割畫面",
    )

    /** 經實車或權限分析確認做不到，改用原廠功能（0.4.34 決定）。 */
    val NOT_PROVIDED = listOf(
        "原廠實際音量：請用方向盤音量鍵或原廠語音助手（桌面三種公開方法實車無效）",
        "導航畫面嵌入桌面：需系統權限，改用分割畫面與浮動回桌面鍵",
        "原廠 DSP／EQ 參數自動套用：無讀回方法，只提供開啟原廠 App",
    )

    fun basics(): String = "$MIN_ANDROID 以上、橫向畫面、允許選擇預設桌面的 Android 車機可安裝使用基本桌面。" +
        "正式版只提供 ARM64（64 位元）安裝包。符合基本條件只代表能安裝，不代表車機專屬整合可用。"

    fun current(manufacturer: String, model: String, device: String, sdk: Int, release: String): String {
        val match = TESTED.firstOrNull { it.device.equals(device, ignoreCase = true) && it.sdk == sdk }
        val head = "目前裝置：$manufacturer $model（$device）／Android $release"
        return head + if (match != null) "。與已回報的「${match.name}」相同，但各功能仍以本機實機檢測結果為準。"
        else "。不在已回報實測清單，可能有相容性問題；車機整合功能請先用「實機檢測」確認。"
    }
}
