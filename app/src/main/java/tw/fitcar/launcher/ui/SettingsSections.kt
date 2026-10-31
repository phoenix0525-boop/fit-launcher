package tw.fitcar.launcher.ui

/**
 * 0.4.34 設定分類：依使用目的命名，依頻率分四群（日常 → 初次設定 → 維護 → 診斷）。
 * 純資料，畫面與測試共用；分類名稱就是畫面文字，調整順序不會接錯頁。
 */
object SettingsSections {
    const val APPEARANCE = "外觀與配色"; const val DISPLAY = "亮度與日夜"; const val NAV = "導航"
    const val MUSIC = "音樂與音效"; const val APPS = "捷徑與 App"; const val WEATHER = "天氣"
    const val TAKEOVER = "桌面接管"; const val PERMISSIONS = "權限總覽"
    const val MAINTENANCE = "備份與更新"; const val CHECKER = "實機檢測"; const val ABOUT = "關於"

    data class Group(val title: String, val sections: List<String>)

    val GROUPS = listOf(
        Group("日常", listOf(APPEARANCE, DISPLAY, NAV, MUSIC, APPS, WEATHER)),
        Group("初次設定", listOf(TAKEOVER, PERMISSIONS)),
        Group("維護", listOf(MAINTENANCE)),
        Group("診斷", listOf(CHECKER, ABOUT)),
    )
    val ALL: List<String> = GROUPS.flatMap { it.sections }

    /** 側欄群組小標：索引 → 群組名稱（放在該群第一個分類上方）。 */
    val GROUP_LABELS: Map<Int, String> = buildMap {
        var i = 0
        GROUPS.forEach { g -> put(i, g.title); i += g.sections.size }
    }

    /** 0.4.33 以前的分類名稱 → 新分類，供舊入口或測試沿用。 */
    val LEGACY = mapOf(
        "App 綁定" to NAV, "音量與音效" to MUSIC, "啟動設定" to TAKEOVER, "返回原廠桌面" to TAKEOVER,
        "權限" to PERMISSIONS, "備份與還原" to MAINTENANCE, "更新" to MAINTENANCE, "實機資訊" to CHECKER,
    )

    fun resolve(name: String): String? = when (name) { in ALL -> name; else -> LEGACY[name] }

    /** 角色主題隱藏入口：連點「關於」五下（原本為「實機資訊」，已併入實機檢測）。 */
    const val CHARACTER_UNLOCK = ABOUT
}
