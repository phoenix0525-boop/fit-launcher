package tw.fitcar.launcher.check

data class ManualTestAction(val id: String, val label: String)

/** No hidden settings changes, binding changes, or automatic success judgments. */
object ManualTestActions {
    fun forItem(id: String): List<ManualTestAction> = when (id) {
        "m_voice" -> listOf(ManualTestAction("voice", "開啟語音測試"))
        "m_google_hint" -> listOf(ManualTestAction("google", "開啟 Google 地圖測試"))
        "m_nav_landscape" -> listOf(ManualTestAction("king", "開啟導航王測試"))
        "m_nav_overlay", "m_home", "m_wheel" -> listOf(ManualTestAction("navigation", "開啟已綁定導航測試"))
        "m_player_kkbox" -> listOf(ManualTestAction("music_kkbox", "開啟已綁定 KKBOX 測試"))
        "m_player_yt" -> listOf(ManualTestAction("music_yt", "開啟已綁定 YT Music 測試"))
        "m_player_morphe" -> listOf(ManualTestAction("music_morphe", "開啟已綁定 Morphe 測試"))
        "m_shield" -> listOf(ManualTestAction("shield", "開啟已綁定測速 App 測試"))
        "m_factory_home_return" -> listOf(ManualTestAction("system_settings", "開啟系統設定（確認不被切走）"))
        "m_reverse", "m_readability", "m_touch", "m_headlight", "m_desktop_volume" -> listOf(ManualTestAction("desktop", "回主畫面進行測試"))
        "m_widgets" -> listOf(ManualTestAction("all_apps", "開啟所有 App 測試小工具"))
        else -> emptyList() // Physical Home/ACC operations cannot be simulated by launching HOME.
    }
}
