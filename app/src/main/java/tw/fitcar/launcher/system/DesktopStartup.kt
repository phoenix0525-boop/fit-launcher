package tw.fitcar.launcher.system

import android.content.Context
import android.content.Intent
import java.util.UUID
import tw.fitcar.launcher.MainActivity

/** One opt-in request on a real BOOT_COMPLETED broadcast. No retry loop or HOME interception. */
object DesktopStartup {
    const val EXTRA_REQUEST = "tw.fitcar.launcher.STARTUP_REQUEST"
    private fun prefs(context: Context) = context.getSharedPreferences("desktop_startup_history", Context.MODE_PRIVATE)

    fun status(context: Context): String = prefs(context).getString("status", "none") ?: "none"
    fun statusText(context: Context): String = when (status(context)) {
        "requested" -> "上次已送出開啟請求，尚未確認進入桌面"
        "confirmed" -> "上次開機請求曾進入桌面前景"
        "rejected" -> "上次開啟請求失敗，請使用系統桌面設定"
        "skipped" -> "上次開機未開啟：${prefs(context).getString("note", "") ?: ""}"
        else -> "尚無開機啟動紀錄"
    }

    fun request(context: Context, action: String?, enabled: Boolean) {
        if (!enabled || action != Intent.ACTION_BOOT_COMPLETED) return
        // 與系統服務的開機補開共用判斷：倒車畫面在前景或本次開機已開過就不再開
        val verdict = DesktopGuard.gate(context, LaunchGate.Reason.BOOT_BROADCAST, SystemShortcutService.foreground)
        if (!verdict.launch) { prefs(context).edit().putString("status", "skipped").putString("note", verdict.note).commit(); return }
        val token = UUID.randomUUID().toString()
        prefs(context).edit().putString("token", token).putString("status", "requested").commit()
        val launch = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_REQUEST, token)
        try {
            context.startActivity(launch)
            DesktopGuard.markLaunched(context, LaunchGate.Reason.BOOT_BROADCAST)
            // A background launch can be silently blocked. Returning here is NOT success.
        } catch (_: Exception) {
            prefs(context).edit().putString("status", "rejected").commit()
        }
    }

    /** Called only when our Activity window actually gains focus, using the matching request's explicit Intent. */
    fun confirm(context: Context, intent: Intent?): Boolean {
        val token = intent?.getStringExtra(EXTRA_REQUEST) ?: return false
        val p = prefs(context)
        if (p.getString("status", "none") != "requested" || token != p.getString("token", null)) return false
        p.edit().putString("status", "confirmed").remove("token").commit()
        intent.removeExtra(EXTRA_REQUEST)
        return true
    }
}
