package tw.fitcar.launcher.system

/**
 * 0.4.35 經 Shizuku 執行的 shell 指令（純邏輯，可做 JVM 測試）。
 * 指令以參數陣列執行、不經 sh 解析；只操作車機桌面自己的權限，以及使用者選定的其他桌面啟用狀態。
 */
object ShellPlan {
    data class Step(val label: String, val argv: List<String>)
    data class Result(val label: String, val exitCode: Int, val output: String) {
        val ok: Boolean get() = exitCode == 0 && !FAILURE.containsMatchIn(output)
    }
    private val FAILURE = Regex("Exception|Error:|Failure|Unknown command|not found|Security", RegexOption.IGNORE_CASE)

    const val ACCESSIBILITY_CLASS = "tw.fitcar.launcher.system.SystemShortcutService"
    const val LISTENER_CLASS = "tw.fitcar.launcher.media.MediaListenerService"
    const val HOME_CLASS = "tw.fitcar.launcher.MainActivity"

    /** 一鍵授權：等同使用者到各系統頁逐一允許；不開啟任何桌面接管開關（那些仍由使用者決定）。 */
    fun setup(pkg: String, sdk: Int): List<Step> = buildList {
        add(Step("允許自動恢復系統服務（WRITE_SECURE_SETTINGS）", listOf("pm", "grant", pkg, "android.permission.WRITE_SECURE_SETTINGS")))
        add(Step("通知存取（音樂資訊、Google 提示）", listOf("cmd", "notification", "allow_listener", "$pkg/$LISTENER_CLASS")))
        add(Step("浮動視窗", listOf("appops", "set", pkg, "SYSTEM_ALERT_WINDOW", "allow")))
        add(Step("使用情況存取", listOf("appops", "set", pkg, "GET_USAGE_STATS", "allow")))
        add(Step("修改系統設定（背光）", listOf("appops", "set", pkg, "WRITE_SETTINGS", "allow")))
        add(Step("背景不被關閉", listOf("cmd", "deviceidle", "whitelist", "+$pkg")))
        add(Step("定位（精確）", listOf("pm", "grant", pkg, "android.permission.ACCESS_FINE_LOCATION")))
        add(Step("定位（概略）", listOf("pm", "grant", pkg, "android.permission.ACCESS_COARSE_LOCATION")))
        if (sdk >= 31) add(Step("附近裝置（藍牙連線狀態）", listOf("pm", "grant", pkg, "android.permission.BLUETOOTH_CONNECT")))
        if (sdk >= 29) add(Step("設為預設桌面", listOf("cmd", "role", "add-role-holder", "android.app.role.HOME", pkg)))
        else add(Step("設為預設桌面", listOf("cmd", "package", "set-home-activity", "$pkg/$HOME_CLASS")))
    }

    /** 讀取目前已啟用的無障礙服務（回傳文字可能是 "null"）。 */
    fun readAccessibility() = Step("讀取已啟用的系統服務", listOf("settings", "get", "secure", "enabled_accessibility_services"))

    fun enableAccessibility(current: String?, pkg: String): List<Step> = listOf(
        Step("開啟系統服務（無障礙）", listOf("settings", "put", "secure", "enabled_accessibility_services", mergeService(current, pkg, ACCESSIBILITY_CLASS))),
        Step("無障礙總開關", listOf("settings", "put", "secure", "accessibility_enabled", "1")),
    )

    /** 把服務加入以冒號分隔的清單；保留其他 App 的服務，已存在（含短格式）就不重複。 */
    fun mergeService(current: String?, pkg: String, cls: String): String {
        val full = "$pkg/$cls"
        val items = current?.trim()?.takeIf { it.isNotEmpty() && it != "null" }?.split(':')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        return if (items.any { normalize(it, pkg) == full }) items.joinToString(":") else (items + full).joinToString(":")
    }

    fun containsService(current: String?, pkg: String, cls: String): Boolean =
        current?.split(':')?.any { normalize(it.trim(), pkg) == "$pkg/$cls" } == true

    private fun normalize(item: String, pkg: String): String =
        if (item.startsWith("$pkg/.")) "$pkg/$pkg${item.substringAfter('/')}" else item

    /** 絕不停用：系統本身、設定（含開機用的 FallbackHome）、系統介面、最近使用畫面的提供者、車機桌面自己。 */
    val PROTECTED = setOf("android", "com.android.settings", "com.android.systemui", "com.android.provision")

    fun disableCandidates(homePackages: Collection<String>, own: String, recentsPackage: String?): List<String> =
        homePackages.filter { it != own && it !in PROTECTED && it != recentsPackage }.distinct().sorted()

    /** 停用前的條件：車機桌面必須已是預設桌面，避免停用後沒有可用桌面。 */
    fun canDisable(isDefaultHome: Boolean, candidate: String, candidates: List<String>): Boolean = isDefaultHome && candidate in candidates

    fun disable(pkg: String) = Step("停用 $pkg", listOf("pm", "disable-user", "--user", "0", pkg))
    fun enable(pkg: String) = Step("還原 $pkg", listOf("pm", "enable", pkg))
    fun restoreByAdb(pkg: String) = "adb shell pm enable $pkg"

    /** `config_recentsComponentName`（例如 com.android.launcher3/com.android.quickstep.RecentsActivity）的套件名。 */
    fun recentsPackage(component: String?): String? = component?.substringBefore('/')?.takeIf { it.isNotBlank() }
}
