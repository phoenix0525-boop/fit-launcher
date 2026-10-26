package tw.fitcar.launcher.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import tw.fitcar.launcher.data.Prefs

object ReceiverActions {
    const val ACTION_BRIGHTNESS_BOUNDARY = "tw.fitcar.launcher.action.BRIGHTNESS_BOUNDARY"
    val BOOT_ACTIONS = setOf(
        Intent.ACTION_BOOT_COMPLETED,
        Intent.ACTION_TIME_CHANGED,
        Intent.ACTION_TIMEZONE_CHANGED,
        Intent.ACTION_MY_PACKAGE_REPLACED,
    )
}

/** 排程區間邊界：清除手動覆寫、套用新區間亮度、排定下一次。 */
class BrightnessReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReceiverActions.ACTION_BRIGHTNESS_BOUNDARY) return
        val pending = goAsync()
        try {
            Prefs.get(context).update { it.copy(manualOverride = false) }
            BrightnessController.applyScheduled(context) {
                try { BrightnessController.scheduleNext(context) } finally { pending.finish() }
            }
        } catch (_: Exception) {
            pending.finish()
        }
    }
}

/** 開機、系統時間或時區改變、App 更新後重新計算。 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ReceiverActions.BOOT_ACTIONS) return
        val pending = goAsync()
        try {
            BrightnessController.applyScheduled(context) {
                try { BrightnessController.scheduleNext(context) } finally { pending.finish() }
            }
        } catch (_: Exception) {
            pending.finish()
        }
    }
}
