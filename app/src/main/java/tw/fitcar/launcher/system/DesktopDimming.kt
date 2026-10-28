package tw.fitcar.launcher.system
import tw.fitcar.launcher.data.DisplaySettings
import tw.fitcar.launcher.data.Schedule
/** Window-local only. -1 follows OEM/system brightness, never writes Settings.System. */
object DesktopDimming {
    fun night(display: DisplaySettings, minute: Int): Boolean = display.periods.firstOrNull { it.id=="night" }?.let { Schedule.periodContains(it.startMinutes,it.endMinutes,minute) } ?: false
    fun window(resumed: Boolean, auto: Boolean, manual: Boolean?, night: Boolean, percent: Int): Float =
        if (resumed && (manual ?: (auto && night))) percent.coerceIn(10,100)/100f else -1f
}
