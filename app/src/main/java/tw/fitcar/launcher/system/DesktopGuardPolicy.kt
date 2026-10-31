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

/**
 * 0.4.34：開機廣播、系統服務補開、螢幕喚醒、原廠桌面切回四條路徑都會開啟車機桌面，原本各自判斷，
 * 可能同一次開機開兩次、在使用者已進入其他 App 後又被拉回，或在倒車影像出現時蓋過它。
 * 統一由這裡判斷；純邏輯，方便測試。
 */
object LaunchGate {
    enum class Reason { BOOT_BROADCAST, BOOT_SERVICE, SCREEN_ON, FACTORY_RETURN }

    /** 兩次自動開啟至少間隔（任何路徑合計）。 */
    const val MIN_GAP_MS = 5_000L
    /** 系統服務在開機後多久內可補開。 */
    const val BOOT_WINDOW_MS = 180_000L

    /** 倒車／環景／行車影像等原廠相機畫面：出現時一律不開啟車機桌面（固定決策：不得搶佔倒車畫面）。 */
    val CAMERA_PACKAGES = setOf(
        "com.syu.fourcamera2", "com.syu.doublecamera", "com.syu.rightcamera", "com.syu.frontvideo",
        "com.syu.camera360", "com.ivicar.avm", "com.baony.avm360",
    )

    fun isCamera(pkg: String?): Boolean = pkg != null &&
        (pkg in CAMERA_PACKAGES || pkg.contains("camera", ignoreCase = true) || pkg.contains("avm", ignoreCase = true))

    data class Verdict(val launch: Boolean, val note: String)

    /** 以「開機時刻」辨識同一次開機：牆上時間減去開機經過時間，誤差 60 秒內視為同一次。 */
    fun sameBoot(storedBootAt: Long?, wallNow: Long, elapsed: Long): Boolean =
        storedBootAt != null && kotlin.math.abs((wallNow - elapsed) - storedBootAt) < 60_000L

    /**
     * @param foreground 目前前景套件（由系統服務得知；未知時為 null）
     * @param lastLaunchAt 上次任何路徑自動開啟的 elapsedRealtime；從未開啟為 null
     * @param bootHandled 本次開機是否已由任一路徑開啟過
     */
    fun decide(reason: Reason, now: Long, foreground: String?, ownPackage: String, lastLaunchAt: Long?, bootHandled: Boolean,
               factoryHomePackages: Set<String> = emptySet()): Verdict {
        if (isCamera(foreground)) return Verdict(false, "相機／倒車畫面在前景，不開啟")
        if (foreground == ownPackage) return Verdict(false, "車機桌面已在前景")
        // 原廠切回有自己的 2 秒間隔與防迴圈；其餘路徑合計至少間隔 5 秒，避免同一次開機開兩次
        if (reason != Reason.FACTORY_RETURN && lastLaunchAt != null && now - lastLaunchAt in 0 until MIN_GAP_MS)
            return Verdict(false, "距離上次自動開啟不到 5 秒")
        return when (reason) {
            Reason.BOOT_BROADCAST -> if (bootHandled) Verdict(false, "本次開機已開啟過") else Verdict(true, "開機")
            Reason.BOOT_SERVICE -> when {
                now >= BOOT_WINDOW_MS -> Verdict(false, "已超過開機 3 分鐘，系統服務重新連線不再補開")
                bootHandled -> Verdict(false, "本次開機已開啟過")
                // 使用者在開機後已主動進入其他 App（非原廠桌面）時，不要把他拉回
                foreground != null && foreground !in factoryHomePackages -> Verdict(false, "開機後已在使用其他畫面（$foreground），不補開")
                else -> Verdict(true, "開機補開")
            }
            Reason.SCREEN_ON, Reason.FACTORY_RETURN -> Verdict(true, "")
        }
    }
}
