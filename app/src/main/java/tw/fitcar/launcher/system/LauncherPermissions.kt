package tw.fitcar.launcher.system

import android.app.AppOpsManager
import android.app.role.RoleManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

/** 主流車機桌面常用的「使用者自行開啟」權限；全部是公開介面，沒有自動授權。 */
object LauncherPermissions {
    // ---- 預設桌面角色（Android 10 以上）
    fun homeRoleAvailable(context: Context): Boolean = Build.VERSION.SDK_INT >= 29 &&
        (context.getSystemService(RoleManager::class.java)?.isRoleAvailable(RoleManager.ROLE_HOME) == true)
    fun homeRoleHeld(context: Context): Boolean = Build.VERSION.SDK_INT >= 29 &&
        (context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_HOME) == true)
    fun homeRoleIntent(context: Context): Intent? = if (Build.VERSION.SDK_INT >= 29)
        context.getSystemService(RoleManager::class.java)?.createRequestRoleIntent(RoleManager.ROLE_HOME) else null

    // ---- 無障礙服務（原廠桌面自動切回、喚醒、浮動鍵的前景判斷）
    fun accessibilityEnabled(context: Context): Boolean {
        val am = context.getSystemService(AccessibilityManager::class.java) ?: return false
        return am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo?.serviceInfo?.packageName == context.packageName }
    }
    fun accessibilityIntent() = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    // ---- 忽略電池最佳化
    fun ignoringBattery(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true
    @android.annotation.SuppressLint("BatteryLife")
    fun batteryIntent(context: Context) = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))

    // ---- 使用情況存取
    fun usageGranted(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= 29) ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            else @Suppress("DEPRECATION") ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }
    fun usageIntent() = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
    /** 近 3 天用過的套件，最近的在前；未授權時回傳空清單。 */
    fun recentPackages(context: Context, limit: Int = 16): List<String> {
        if (!usageGranted(context)) return emptyList()
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return emptyList()
        val now = System.currentTimeMillis()
        return try {
            usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, now - 3 * 86_400_000L, now)
                .filter { it.lastTimeUsed > 0 && it.totalTimeInForeground > 0 && it.packageName != context.packageName }
                .sortedByDescending { it.lastTimeUsed }.map { it.packageName }.distinct().take(limit)
        } catch (_: Exception) { emptyList() }
    }

    // ---- 浮動視窗
    fun canOverlay(context: Context) = Settings.canDrawOverlays(context)
    fun overlayIntent(context: Context) = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
}
