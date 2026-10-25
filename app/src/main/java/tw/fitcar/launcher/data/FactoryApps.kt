package tw.fitcar.launcher.data

enum class Platform { FYT_SYU, UNKNOWN }

/**
 * 原廠車機 App 入口（收音機、藍牙電話、藍牙音樂、EQ、車機設定）。
 *
 * 來源：使用者提供的原廠桌面 Launcher3.apk 內出現的元件名稱（只參考名稱，不複製程式）。
 * 這些 App 在部分車機上沒有一般「App 圖示」，所以「所有 App」看不到；這裡只在
 * PackageManager 確認存在、啟用、可被外部開啟時補列，不呼叫任何 FYT／SYU 私有服務。
 */
object FactoryApps {

    /** 精確的 Activity 入口（套件/類別）。 */
    val ACTIVITY_CANDIDATES = listOf(
        "com.syu.radio/com.syu.radio.Launch",
        "com.syu.bt/com.syu.bt.BtAct",
        "com.syu.bt/com.syu.bt.act.ActBtAvStart",
        "com.syu.music/com.syu.music.MAct",
        "com.syu.settings/com.syu.settings.MainActivity",
    )

    /** 只知道套件、由系統回報啟動入口的 App。 */
    val PACKAGE_CANDIDATES = listOf("com.syu.eq", "com.syu.btav")

    /** 判斷平台用的套件（只檢查是否存在）。 */
    val PLATFORM_MARKERS = listOf("com.syu.ms", "com.android.launcher30") + (ACTIVITY_CANDIDATES.map { it.substringBefore('/') } + PACKAGE_CANDIDATES).distinct()

    /**
     * 合併一般 App 與原廠入口：同一套件已有一般圖示時沿用一般入口，不重複列出。
     * key 格式為 ComponentName.flattenToString()（套件/類別）。
     */
    fun mergeKeys(standard: List<String>, factory: List<String>): List<String> {
        val valid = { k: String -> k.contains('/') && k.substringBefore('/').isNotBlank() && k.substringAfter('/').isNotBlank() }
        val standardPackages = standard.map { it.substringBefore('/') }.toSet()
        val extra = factory.filter(valid).filter { it.substringBefore('/') !in standardPackages }.distinct()
        return (standard + extra).distinct()
    }

    fun detectPlatform(installedPackages: Set<String>): Platform =
        if (installedPackages.any { it == "com.syu.ms" || it.startsWith("com.syu.") }) Platform.FYT_SYU else Platform.UNKNOWN
}
