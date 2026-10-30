package tw.fitcar.launcher.check

/**
 * 判定一筆已儲存結果是否仍適用目前版本與情境。
 * 情境字串格式為「欄位=值; 欄位=值」（見 CheckStore.scenario）。只比較會影響該檢測的欄位，
 * 例如切換播放器不會把已驗證的背光測試清成待測；未列出的檢測仍比較全部欄位（保守）。
 */
object ResultValidity {
    const val HOME = "預設桌面"; const val FIT_HOME = "FIT預設"; const val PLAYER = "播放器"; const val MEDIA_MODE = "模式"
    const val CONTROL = "控制來源"; const val NAV = "導航"; const val NAV_MODE = "導航模式"; const val FONT = "字體"

    private val DESKTOP = setOf(HOME, FIT_HOME)
    private val MUSIC = setOf(PLAYER, MEDIA_MODE)
    private val NAVIGATION = setOf(NAV, NAV_MODE)

    /** null 代表比較全部欄位。 */
    fun relevantFields(id: String): Set<String>? = when {
        id in setOf("brightness_test", "brightness_perm", "m_headlight", "gps_test", "gps_perm", "map_gps", "m_widgets", "m_shield", "oem_feasibility") -> emptySet()
        id.startsWith("gesture_") || id == "keys" -> setOf(FIT_HOME)
        id == "m_wheel" -> setOf(FIT_HOME, PLAYER)
        id.startsWith("m_player_") || id in setOf("media", "lyrics", "bind_music") -> MUSIC
        id.startsWith("audio_probe_") || id == "m_desktop_volume" -> setOf(PLAYER, CONTROL)
        id in setOf("m_google_hint", "google_hint", "m_nav_landscape", "m_nav_overlay") || id.startsWith("split") -> NAVIGATION
        id in setOf("m_reverse", "m_home", "m_factory_home_return", "m_wake_launch", "home", "boot") || id.startsWith("ignition") -> DESKTOP
        id in setOf("m_readability", "m_touch", "screen") -> setOf(FONT)
        id in setOf("m_voice", "voice_service") -> NAVIGATION + PLAYER
        else -> null
    }

    fun fields(context: String): Map<String, String> = context.split(';').mapNotNull { part ->
        val i = part.indexOf('=')
        if (i <= 0) null else part.substring(0, i).trim() to part.substring(i + 1).trim()
    }.toMap()

    fun matches(recordVersion: String, recordContext: String, version: String, context: String, id: String? = null): Boolean {
        if (recordVersion.isBlank() || recordContext.isBlank() || recordVersion != version) return false
        val keys = id?.let(::relevantFields) ?: return recordContext == context
        val old = fields(recordContext); val now = fields(context)
        return keys.all { k -> k in old && old[k] == now[k] }
    }
}
