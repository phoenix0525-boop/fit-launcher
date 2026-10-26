package tw.fitcar.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.session.PlaybackState
import android.os.Build
import tw.fitcar.launcher.data.LauncherState
import tw.fitcar.launcher.media.MediaUiState
import tw.fitcar.launcher.system.BrightnessController
import tw.fitcar.launcher.system.GpsState

object BuildInfo {
    fun versionName(context: Context): String = try {
        val info = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        info.versionName ?: "?"
    } catch (e: Exception) { "?" }
}

/** 實機驗證用的資訊：直接顯示在設定 → 實機診斷，方便拍照回報。 */
object Diagnostics {

    private val ACTION_NAMES = listOf(
        PlaybackState.ACTION_PLAY to "PLAY",
        PlaybackState.ACTION_PAUSE to "PAUSE",
        PlaybackState.ACTION_PLAY_PAUSE to "PLAY_PAUSE",
        PlaybackState.ACTION_SKIP_TO_PREVIOUS to "PREV",
        PlaybackState.ACTION_SKIP_TO_NEXT to "NEXT",
        PlaybackState.ACTION_SEEK_TO to "SEEK_TO",
        PlaybackState.ACTION_STOP to "STOP",
    )

    fun actionsText(actions: Long): String =
        ACTION_NAMES.filter { actions and it.first != 0L }.joinToString(" ") { it.second }.ifEmpty { "（無）" }

    private fun stateText(state: Int) = when (state) {
        PlaybackState.STATE_PLAYING -> "播放中"
        PlaybackState.STATE_PAUSED -> "暫停"
        PlaybackState.STATE_STOPPED -> "停止"
        PlaybackState.STATE_BUFFERING -> "緩衝"
        PlaybackState.STATE_NONE -> "無"
        else -> "狀態 $state"
    }

    fun collect(context: Context, state: LauncherState, media: MediaUiState, gps: GpsState): List<Pair<String, String>> {
        val dm = context.resources.displayMetrics
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val defaultHome = try {
            @Suppress("DEPRECATION")
            context.packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        } catch (e: Exception) { null }
        val rows = mutableListOf(
            "裝置" to "${Build.MANUFACTURER} ${Build.MODEL}（${Build.DEVICE}）",
            "主板／平台" to "${Build.BOARD} / ${Build.HARDWARE}",
            "Android" to "${Build.VERSION.RELEASE}（API ${Build.VERSION.SDK_INT}）",
            "螢幕" to "${dm.widthPixels}×${dm.heightPixels} px，${dm.densityDpi} dpi，約 ${(dm.widthPixels / dm.density).toInt()}×${(dm.heightPixels / dm.density).toInt()} dp",
            "預設桌面" to when (defaultHome) {
                null -> "無法判斷"
                context.packageName -> "本 App（車機桌面）"
                "android" -> "尚未選擇（每次詢問）"
                else -> defaultHome
            },
            "系統亮度權限" to if (BrightnessController.canWrite(context)) "已允許" else "未允許",
            "分割畫面實測" to when (state.lastSplitResult) { true -> "成功"; false -> "未進入分割"; null -> "尚未測試" },
            "通知存取" to if (media.permissionGranted) "已開啟" else "未開啟",
            "GPS" to when {
                !gps.permission -> "未授權"
                !gps.providerEnabled -> "已關閉"
                gps.speedKmh != null -> "有定位（%.1f km/h）".format(gps.speedKmh)
                else -> "尚未定位"
            },
        )
        if (media.sessions.isEmpty()) {
            rows += "媒體工作階段" to if (media.permissionGranted) "目前沒有任何 App 在播放" else "需先開啟通知存取"
        } else {
            media.sessions.forEachIndexed { i, s ->
                rows += "媒體工作階段 ${i + 1}" to "${s.packageName}｜${stateText(s.state)}｜控制：${actionsText(s.actions)}｜Metadata：${if (s.hasMetadata) "有" else "無"}"
            }
        }
        return rows
    }
}
