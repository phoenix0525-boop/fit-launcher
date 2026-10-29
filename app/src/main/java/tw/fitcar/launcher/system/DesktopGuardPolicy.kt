package tw.fitcar.launcher.system

/**
 * 原廠桌面出現時是否切回車機桌面（純邏輯，可做 JVM 測試）。
 * 只比對「原廠桌面的主畫面 Activity」，原廠桌面內的其他畫面（收音機、設定等）不處理。
 * 有防迴圈：30 秒內切回 3 次即暫停 10 分鐘，避免與原廠強制桌面互搶。
 */
object DesktopGuardPolicy {
    const val MIN_INTERVAL_MS = 2_000L
    const val LOOP_WINDOW_MS = 30_000L
    const val LOOP_LIMIT = 3
    const val LOOP_PAUSE_MS = 10 * 60_000L
    const val MANUAL_PAUSE_MS = 10 * 60_000L

    enum class Action { RETURN, IGNORE, PAUSE_LOOP }
    data class Decision(val action: Action, val reason: String, val returns: List<Long>, val pausedUntil: Long)

    fun decide(enabled: Boolean, now: Long, packageName: String?, className: String?, factoryHomes: Set<String>,
        pausedUntil: Long, returns: List<Long>): Decision {
        fun ignore(reason: String) = Decision(Action.IGNORE, reason, returns, pausedUntil)
        if (!enabled) return ignore("未開啟自動切回")
        if (packageName.isNullOrBlank() || className.isNullOrBlank()) return ignore("事件沒有畫面名稱")
        if ("$packageName/$className" !in factoryHomes) return ignore("不是原廠桌面主畫面")
        if (now < pausedUntil) return ignore("暫停中")
        val recent = returns.filter { now - it in 0 until LOOP_WINDOW_MS }
        recent.lastOrNull()?.let { if (now - it < MIN_INTERVAL_MS) return Decision(Action.IGNORE, "間隔太短", recent, pausedUntil) }
        if (recent.size >= LOOP_LIMIT) return Decision(Action.PAUSE_LOOP, "30 秒內切回 $LOOP_LIMIT 次，暫停 10 分鐘", emptyList(), now + LOOP_PAUSE_MS)
        return Decision(Action.RETURN, "已切回車機桌面", recent + now, pausedUntil)
    }

    /** 導航中顯示浮動回桌面鍵：只在前景是導航 App 時顯示；系統介面與輸入法不改變狀態。 */
    enum class OverlayChange { SHOW, HIDE, KEEP }
    fun overlay(enabled: Boolean, foreground: String?, navigationPackages: Set<String>, ownPackage: String): OverlayChange = when {
        !enabled -> OverlayChange.HIDE
        foreground.isNullOrBlank() -> OverlayChange.KEEP
        foreground == "com.android.systemui" || foreground.contains("inputmethod", ignoreCase = true) -> OverlayChange.KEEP
        foreground == ownPackage -> OverlayChange.HIDE
        foreground in navigationPackages -> OverlayChange.SHOW
        else -> OverlayChange.HIDE
    }
}

/** adb 指令（使用者自行在電腦執行；App 不會執行任何 shell）。 */
object AdbCommands {
    fun setHome(component: String) = "adb shell cmd package set-home-activity $component"
    fun addHomeRole(packageName: String) = "adb shell cmd role add-role-holder android.app.role.HOME $packageName"
    fun showHome() = "adb shell cmd role get-role-holders android.app.role.HOME"
    fun dumpAudio() = "adb shell dumpsys audio > audio.txt"
}
