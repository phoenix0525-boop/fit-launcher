package tw.fitcar.launcher.nav

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import tw.fitcar.launcher.CompanionActivity
import tw.fitcar.launcher.data.AppRepository
import tw.fitcar.launcher.data.NavOpenMode
import tw.fitcar.launcher.data.Prefs

sealed interface LaunchResult {
    data object Ok : LaunchResult
    data object NotBound : LaunchResult
    data class PlaceChoice(val address: String, val packageName: String?, val acceptsGeo: Boolean) : LaunchResult
    data class Failed(val reason: String) : LaunchResult
}

/**
 * 導航預設全螢幕；分割畫面僅為使用者主動啟用的實驗功能。
 *
 * 桌面（HOME）類型的 Activity 在 Android 上不能進入分割畫面，所以分割模式是：
 * 先開本 App 的一般 Activity「車機側欄」，再由側欄以 FLAG_ACTIVITY_LAUNCH_ADJACENT 開導航王。
 * 車機若不支援，系統會直接把導航王開成全螢幕，即自然退回全螢幕。
 * 70/30 比例由系統分割列決定，App 無法強制。
 */
object NavLauncher {

    fun open(context: Context, forceFullscreen: Boolean = false): LaunchResult {
        val state = Prefs.get(context).state.value
        val key = state.bindings.navigation ?: return LaunchResult.NotBound
        val entry = AppRepository.resolve(context, key)
            ?: return LaunchResult.Failed("已綁定的導航 App 找不到，可能已移除，請重新選擇。")

        val split = !forceFullscreen && state.navOpenMode == NavOpenMode.SPLIT_FIRST && state.lastSplitResult != false
        return try {
            if (split) {
                val intent = Intent(context, CompanionActivity::class.java)
                    .putExtra(CompanionActivity.EXTRA_LAUNCH_NAV, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } else {
                context.startActivity(AppRepository.launchIntent(entry.component))
            }
            LaunchResult.Ok
        } catch (e: ActivityNotFoundException) {
            LaunchResult.Failed("無法啟動 ${entry.label}：${e.message}")
        } catch (e: SecurityException) {
            LaunchResult.Failed("系統不允許啟動 ${entry.label}：${e.message}")
        }
    }

    /** Standard map search, never force a launcher Activity to consume an unsupported URI. */
    fun placeIntent(address: String, packageName: String? = null): Intent =
        Intent(Intent.ACTION_VIEW, android.net.Uri.parse(Places.geoUri(address)))
            .setPackage(packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun openPlace(context: Context, address: String?): LaunchResult {
        val clean = Places.clean(address) ?: return LaunchResult.Failed("還沒設定這個地點，請到 設定 → App 綁定 → 常用地點 填寫地址。")
        val navPkg = Prefs.get(context).state.value.bindings.navigation
            ?.let { android.content.ComponentName.unflattenFromString(it)?.packageName }
        val geo = placeIntent(clean, navPkg)
        val accepts = navPkg != null && geo.resolveActivity(context.packageManager) != null
        val route = PlaceRouting.route(navPkg, accepts)
        if (route == PlaceRoute.CONFIRM) return LaunchResult.PlaceChoice(clean, navPkg, accepts)
        return try {
            context.startActivity(if (route == PlaceRoute.GOOGLE) GoogleNavigation.destinationIntent(clean) else geo)
            LaunchResult.Ok
        } catch (_: ActivityNotFoundException) {
            LaunchResult.PlaceChoice(clean, navPkg, accepts)
        } catch (_: SecurityException) {
            LaunchResult.PlaceChoice(clean, navPkg, accepts)
        }
    }

    /** 由側欄呼叫：把導航王開在相鄰視窗。 */
    fun launchAdjacent(activity: Activity): LaunchResult {
        val key = Prefs.get(activity).state.value.bindings.navigation ?: return LaunchResult.NotBound
        val entry = AppRepository.resolve(activity, key) ?: return LaunchResult.Failed("導航 App 找不到")
        return try {
            val intent = AppRepository.launchIntent(entry.component)
                .addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
            activity.startActivity(intent)
            LaunchResult.Ok
        } catch (e: Exception) {
            LaunchResult.Failed(e.message ?: e.javaClass.simpleName)
        }
    }
}
