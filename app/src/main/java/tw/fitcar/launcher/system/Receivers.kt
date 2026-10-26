package tw.fitcar.launcher.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import tw.fitcar.launcher.data.Prefs

/** 排程區間邊界：清除手動覆寫、套用新區間亮度、排定下一次。 */
class BrightnessReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Prefs.get(context).update { it.copy(manualOverride = false) }
        BrightnessController.applyScheduled(context) {
            BrightnessController.scheduleNext(context)
            pending.finish()
        }
    }
}

/** 開機、系統時間或時區改變、App 更新後重新計算。 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ALLOWED_ACTIONS) return
        val pending = goAsync()
        BrightnessController.applyScheduled(context) {
            BrightnessController.scheduleNext(context)
            pending.finish()
        }
    }

    companion object {
        internal val ALLOWED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}
