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
        val standard = query(pm, intent)
            .filter { it.activityInfo.packageName != context.packageName }
            .map { it.toEntry(pm) }
            .distinctBy { it.key }
        val byKey = standard.associateBy { it.key }.toMutableMap()
        // 原廠車機 App（收音機、藍牙…）在部分車機沒有一般圖示，經實機驗證後補列
        val factoryKeys = FactoryApps.ACTIVITY_CANDIDATES + FactoryApps.PACKAGE_CANDIDATES.mapNotNull {
            pm.getLaunchIntentForPackage(it)?.component?.flattenToString()
        }
        val merged = FactoryApps.mergeKeys(standard.map { it.key }, factoryKeys)
        return merged.mapNotNull { key -> byKey[key] ?: resolveLaunchable(context, key) }
            .sortedBy { it.label }
    }

    /** 只在元件存在、已啟用、允許外部開啟且不需特殊權限時回傳。 */
    private fun resolveLaunchable(context: Context, key: String): AppEntry? {
        val component = ComponentName.unflattenFromString(key) ?: return null
        val pm = context.packageManager
        return try {
            val info = if (Build.VERSION.SDK_INT >= 33) {
                pm.getActivityInfo(component, PackageManager.ComponentInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getActivityInfo(component, 0)
            }
            val usable = info.exported && info.enabled && info.applicationInfo.enabled &&
                (info.permission == null || context.checkSelfPermission(info.permission) == PackageManager.PERMISSION_GRANTED)
            if (usable) AppEntry(info.loadLabel(pm).toString(), component, info.loadIcon(pm)) else null
        } catch (e: PackageManager.NameNotFoundException) {
            null
        } catch (e: SecurityException) {
            null
        }
    }

    /** 回傳清單中實際安裝在車機上的套件（需在 Manifest <queries> 宣告才看得到）。 */
    fun installedPackages(context: Context, packages: List<String>): Set<String> {
        val pm = context.packageManager
        return packages.filter { pkg ->
            try {
                if (Build.VERSION.SDK_INT >= 33) {
                    pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(pkg, 0)
                }
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
        }.toSet()
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
