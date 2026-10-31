package tw.fitcar.launcher.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import tw.fitcar.launcher.MainActivity

/** 桌面接管的使用者設定與狀態（只存在車機，不備份、不上傳）。所有功能預設關閉。 */
object DesktopGuard {
    private const val PREFS = "desktop_guard"
    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun autoReturn(context: Context) = prefs(context).getBoolean("auto_return", false)
    fun setAutoReturn(context: Context, on: Boolean) = prefs(context).edit().putBoolean("auto_return", on).apply()
    fun wakeLaunch(context: Context) = prefs(context).getBoolean("wake_launch", false)
    fun setWakeLaunch(context: Context, on: Boolean) = prefs(context).edit().putBoolean("wake_launch", on).apply()
    fun overlay(context: Context) = prefs(context).getBoolean("nav_overlay", false)
    fun setOverlay(context: Context, on: Boolean) = prefs(context).edit().putBoolean("nav_overlay", on).apply()

    /** 以 elapsedRealtime 計時，重開機後自動失效。 */
    fun pausedUntil(context: Context): Long = prefs(context).getLong("paused_until", 0L).takeIf { it <= SystemClock.elapsedRealtime() + DesktopGuardPolicy.LOOP_PAUSE_MS } ?: 0L
    fun pause(context: Context, ms: Long = DesktopGuardPolicy.MANUAL_PAUSE_MS) =
        prefs(context).edit().putLong("paused_until", SystemClock.elapsedRealtime() + ms).apply()
    fun resume(context: Context) = prefs(context).edit().putLong("paused_until", 0L).apply()
    internal fun setPausedUntil(context: Context, value: Long) = prefs(context).edit().putLong("paused_until", value).apply()

    fun lastEvent(context: Context): String = prefs(context).getString("last_event", null) ?: "尚無紀錄"
    internal fun recordEvent(context: Context, text: String) {
        val time = java.text.SimpleDateFormat("M/d HH:mm:ss", java.util.Locale.TAIWAN).format(java.util.Date())
        prefs(context).edit().putString("last_event", "$time $text").apply()
    }

    /** 其他桌面（例如 launcher43）的主畫面 Activity，含 activity-alias 的目標名稱。 */
    fun factoryHomes(context: Context): Set<String> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val list = try { pm.queryIntentActivities(intent, PackageManager.MATCH_ALL) } catch (_: Exception) { emptyList() }
        return list.mapNotNull { it.activityInfo }.filter { it.packageName != context.packageName }
            .flatMap { info -> listOfNotNull(info.name, info.targetActivity).map { "${info.packageName}/$it" } }.toSet()
    }

    /** 系統目前的預設桌面套件（沒有預設時為 null，會出現選擇視窗）。 */
    fun defaultHomePackage(context: Context): String? = try {
        context.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName?.takeIf { it != "android" }
    } catch (_: Exception) { null }

    fun ownHomeComponent(context: Context) = ComponentName(context, MainActivity::class.java)

    /** 以公開 HOME Intent 開啟車機桌面；呼叫端必須是使用者已開啟的系統服務或前景畫面。 */
    private fun gatePrefs(context: Context) = context.getSharedPreferences("desktop_launch_gate", Context.MODE_PRIVATE)

    /** 依 [LaunchGate] 判斷這次自動開啟是否可以進行（不開啟）。 */
    fun gate(context: Context, reason: LaunchGate.Reason, foreground: String?): LaunchGate.Verdict {
        val p = gatePrefs(context)
        val now = SystemClock.elapsedRealtime()
        val wall = System.currentTimeMillis()
        fun stored(key: String) = if (p.contains(key)) p.getLong(key, 0) else null
        val bootHandled = LaunchGate.sameBoot(stored("boot_at"), wall, now)
        // last_at 是開機後經過時間，只有同一次開機才有意義
        val last = if (LaunchGate.sameBoot(stored("last_boot"), wall, now)) stored("last_at") else null
        val homes = factoryHomes(context).map { it.substringBefore('/') }.toSet()
        return LaunchGate.decide(reason, now, foreground, context.packageName, last, bootHandled, homes)
    }

    /** 記錄一次成功的自動開啟，供下一次判斷間隔與「本次開機已開過」。 */
    fun markLaunched(context: Context, reason: LaunchGate.Reason) {
        val now = SystemClock.elapsedRealtime()
        val bootAt = System.currentTimeMillis() - now
        val e = gatePrefs(context).edit().putLong("last_at", now).putLong("last_boot", bootAt)
        if (reason == LaunchGate.Reason.BOOT_BROADCAST || reason == LaunchGate.Reason.BOOT_SERVICE) e.putLong("boot_at", bootAt)
        e.apply()
    }

    /**
     * 自動開啟車機桌面（系統服務的開機補開、螢幕喚醒、原廠切回共用）。
     * 不在倒車／相機畫面時開啟，同一次開機只開一次；回傳是否開啟與原因。
     */
    fun autoLaunch(context: Context, reason: LaunchGate.Reason, foreground: String?): Pair<Boolean, String> {
        val v = gate(context, reason, foreground)
        if (!v.launch) return false to v.note
        val ok = launchDesktop(context)
        if (ok) markLaunched(context, reason)
        return ok to (if (ok) v.note else "開啟失敗")
    }

    fun launchDesktop(context: Context): Boolean = try {
        context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            .setComponent(ownHomeComponent(context))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
        true
    } catch (_: Exception) { false }
}
