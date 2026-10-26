package tw.fitcar.launcher.system

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import tw.fitcar.launcher.data.Prefs
import tw.fitcar.launcher.data.BrightnessSchedulePolicy
import tw.fitcar.launcher.data.Schedule
import java.util.Calendar

/**
 * 系統背光控制。只走 Android 正式的 WRITE_SETTINGS 授權（使用者在系統頁面手動允許），
 * 不用黑色遮罩冒充背光。為避免與原廠車燈調光反覆搶控制，只在下列時機寫入：
 * 排程區間邊界、開機／時間變更、使用者在設定頁操作。
 */
object BrightnessController {

    private val main = Handler(Looper.getMainLooper())
    private var animation: Runnable? = null
    private var animationDone: (() -> Unit)? = null

    fun canWrite(context: Context): Boolean = Settings.System.canWrite(context)

    fun permissionIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun currentPercent(context: Context): Int? = try {
        Schedule.systemToPercent(Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS))
    } catch (e: Settings.SettingNotFoundException) {
        null
    }

    fun nowMinutes(): Int {
        val c = Calendar.getInstance()
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    }

    /** 依目前設定算出應有亮度（手動覆寫優先）。 */
    fun targetPercent(context: Context): Int {
        val s = Prefs.get(context).state.value
        return if (s.manualOverride) Schedule.clampBrightness(s.display.manualBrightness)
        else Schedule.brightnessFor(s.display, nowMinutes())
    }

    /**
     * 套用亮度。回傳 false 代表沒有權限。
     * [onDone] 在漸變結束後呼叫（給 BroadcastReceiver 的 goAsync 用）。
     */
    fun apply(context: Context, percent: Int, smooth: Boolean, onDone: (() -> Unit)? = null): Boolean {
        val ctx = context.applicationContext
        if (!canWrite(ctx)) { onDone?.invoke(); return false }
        val resolver = ctx.contentResolver
        val modeChanged = try {
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        } catch (_: SecurityException) {
            false
        }
        if (!modeChanged) { onDone?.invoke(); return false }
        val target = Schedule.percentToSystem(percent)
        animation?.let { main.removeCallbacks(it) }
        animation = null
        animationDone?.also { animationDone = null }?.invoke()
        val start = try { Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS) } catch (e: Exception) { target }
        if (!smooth || start == target) {
            val written = try { Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, target) }
            catch (_: SecurityException) { false }
            onDone?.invoke()
            return written
        }
        val steps = 20
        var i = 0
        val r = object : Runnable {
            override fun run() {
                i++
                val v = start + (target - start) * i / steps
                val written = try { Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, v) }
                catch (_: SecurityException) { false }
                if (!written) {
                    animation = null
                    animationDone?.also { animationDone = null }?.invoke()
                } else if (i < steps) {
                    main.postDelayed(this, 75)
                } else if (animation === this) {
                    animation = null
                    animationDone?.also { animationDone = null }?.invoke()
                }
            }
        }
        animation = r
        animationDone = onDone
        main.post(r)
        return true
    }

    fun applyScheduled(context: Context, onDone: (() -> Unit)? = null): Boolean {
        val s = Prefs.get(context).state.value
        if (!s.display.autoSchedule && !s.manualOverride) { onDone?.invoke(); return false }
        return apply(context, targetPercent(context), s.display.smoothTransition, onDone)
    }

    /** 排定下一個區間邊界的鬧鐘（非精確、不喚醒，避免需要 SCHEDULE_EXACT_ALARM）。 */
    fun scheduleNext(context: Context) {
        val ctx = context.applicationContext
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            ctx, 1001, Intent(ctx, BrightnessReceiver::class.java).setAction(ReceiverActions.ACTION_BRIGHTNESS_BOUNDARY),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        am.cancel(pi)
        val s = Prefs.get(ctx).state.value
        if (!s.display.autoSchedule && !s.manualOverride) return
        val minutes = Schedule.minutesUntilNextBoundary(s.display, nowMinutes())
        val cal = Calendar.getInstance().apply {
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MINUTE, minutes)
        }
        am.setWindow(AlarmManager.RTC, cal.timeInMillis, BrightnessSchedulePolicy.WINDOW_MILLIS, pi)
    }
}
