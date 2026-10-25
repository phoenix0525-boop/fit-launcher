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
 * 讀取車機上實際可啟動的 App。優先 MAIN/LAUNCHER，補上原廠 APK 中確認的車機入口，
 * 不猜測導航王、KKBOX、神盾的套件名稱。
 */
object AppRepository {

    // 來源：使用者提供的 Launcher3.apk 字串；每個入口仍須通過實機檢查。
    // 不呼叫 FYT/SYU 私有服務、不取得系統 UID、不自動綁定使用者的捷徑。
    private val factoryActivities = listOf(
        ComponentName("com.syu.radio", "com.syu.radio.Launch"),
        ComponentName("com.syu.bt", "com.syu.bt.BtAct"),
        ComponentName("com.syu.bt", "com.syu.bt.act.ActBtAvStart"),
        ComponentName("com.syu.music", "com.syu.music.MAct"),
        ComponentName("com.syu.settings", "com.syu.settings.MainActivity"),
    )
    private val factoryPackages = listOf("com.syu.eq", "com.syu.btav")

    fun launchableApps(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val standard = query(pm, intent)
            .filter { it.activityInfo.packageName != context.packageName }
            .map { it.toEntry(pm) }
            .distinctBy { it.key }
            .sortedBy { it.label }

        // 同一套件已有一般 App 入口時沿用它，避免重複列出。
        val standardPackages = standard.map { it.component.packageName }.toSet()
        val discovered = factoryActivities + factoryPackages.mapNotNull {
            pm.getLaunchIntentForPackage(it)?.component
        }
        val factory = discovered
            .filter { it.packageName !in standardPackages }
            .mapNotNull { resolve(context, it.flattenToString()) }
            .filter { entry ->
                try {
                    @Suppress("DEPRECATION")
                    val info = pm.getActivityInfo(entry.component, 0)
                    info.exported && info.enabled && info.applicationInfo.enabled &&
                        (info.permission == null ||
                            context.checkSelfPermission(info.permission) == PackageManager.PERMISSION_GRANTED)
                } catch (_: PackageManager.NameNotFoundException) {
                    false
                } catch (_: SecurityException) {
                    false
                }
            }
        return (standard + factory).distinctBy { it.key }.sortedBy { it.label }
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
