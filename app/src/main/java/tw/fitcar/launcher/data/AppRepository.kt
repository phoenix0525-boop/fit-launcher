package tw.fitcar.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.Build

data class AppEntry(
    val label: String,
    val component: ComponentName,
    val icon: Drawable?,
) {
    val key: String get() = component.flattenToString()
}

/**
 * 讀取車機上實際可啟動的 App。只用 MAIN/LAUNCHER 查詢（搭配 Manifest 的 <queries>），
 * 不猜測導航王、KKBOX、神盾的套件名稱。
 */
object AppRepository {

    fun launchableApps(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return query(pm, intent)
            .filter { it.activityInfo.packageName != context.packageName }
            .map { it.toEntry(pm) }
            .distinctBy { it.key }
            .sortedBy { it.label }
    }

    /** 其他桌面（含原廠桌面），供「返回原廠桌面」使用。 */
    fun otherHomeApps(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return query(pm, intent)
            .filter { it.activityInfo.packageName != context.packageName }
            // 排除 Android 的「選擇桌面」中繼畫面
            .filter { it.activityInfo.packageName != "android" }
            .map { it.toEntry(pm) }
            .distinctBy { it.key }
    }

    fun resolve(context: Context, key: String?): AppEntry? {
        if (key.isNullOrBlank()) return null
        val component = ComponentName.unflattenFromString(key) ?: return null
        val pm = context.packageManager
        return try {
            val info = if (Build.VERSION.SDK_INT >= 33) {
                pm.getActivityInfo(component, PackageManager.ComponentInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getActivityInfo(component, 0)
            }
            AppEntry(info.loadLabel(pm).toString(), component, info.loadIcon(pm))
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    fun launchIntent(component: ComponentName): Intent =
        Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

    private fun query(pm: PackageManager, intent: Intent): List<ResolveInfo> =
        if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }

    private fun ResolveInfo.toEntry(pm: PackageManager): AppEntry = AppEntry(
        label = loadLabel(pm).toString(),
        component = ComponentName(activityInfo.packageName, activityInfo.name),
        icon = try { loadIcon(pm) } catch (e: Exception) { null },
    )
}
