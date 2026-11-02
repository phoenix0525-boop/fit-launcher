package tw.fitcar.launcher.system

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * 0.4.35：已透過 Shizuku／adb 授予 WRITE_SECURE_SETTINGS 時，若使用者開啟了需要系統服務的功能
 * （原廠切回、喚醒開啟、導航浮動鍵），而系統在 App 更新或當掉後把服務關掉，就自動重新開啟。
 * 使用者可在設定關閉「自動恢復」；未開任何相關功能時不動作。
 */
object SecureSettingsHeal {
    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("secure_heal", Context.MODE_PRIVATE)
    fun enabled(context: Context) = prefs(context).getBoolean("auto_heal", true)
    fun setEnabled(context: Context, on: Boolean) = prefs(context).edit().putBoolean("auto_heal", on).apply()

    fun granted(context: Context) =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun needed(context: Context) = DesktopGuard.autoReturn(context) || DesktopGuard.wakeLaunch(context) || DesktopGuard.overlay(context)

    /** 回傳是否實際寫入。 */
    fun ensureAccessibility(context: Context): Boolean {
        if (!enabled(context) || !granted(context) || !needed(context)) return false
        return try {
            val cr = context.contentResolver
            val current = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            if (ShellPlan.containsService(current, context.packageName, ShellPlan.ACCESSIBILITY_CLASS) &&
                Settings.Secure.getInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1) return false
            Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                ShellPlan.mergeService(current, context.packageName, ShellPlan.ACCESSIBILITY_CLASS))
            Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            DesktopGuard.recordEvent(context, "系統服務被關閉，已自動重新開啟")
            true
        } catch (_: Exception) { false }
    }
}
