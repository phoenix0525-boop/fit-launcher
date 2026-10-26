package tw.fitcar.launcher.check

import android.content.Context
import android.os.SystemClock
import android.provider.Settings

/** Only compares an explicitly started test; never infers sleep from routine checks. */
object BootComparison {
    fun describe(startCount: Int, endCount: Int, startUptime: Long, endUptime: Long): String = when {
        startCount >= 0 && endCount >= 0 && startCount != endCount -> "觀察到重新開機（開機計數已改變）。"
        startCount >= 0 && startCount == endCount -> "仍為同一次開機；無法單憑此判定曾休眠或恢復正常。"
        endUptime < startUptime -> "觀察到開機經過時間重置，可能曾重新開機。"
        else -> "無法取得開機計數；現有時間資料不足以確認是否重新開機。"
    }
}

data class IgnitionState(val startedAt: Long, val completedAt: Long, val description: String)
object IgnitionTest {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("fit_ignition_test", Context.MODE_PRIVATE)
    private fun bootCount(ctx: Context) = Settings.Global.getInt(ctx.contentResolver, Settings.Global.BOOT_COUNT, -1)
    fun read(ctx: Context): IgnitionState {
        val p = sp(ctx)
        return IgnitionState(p.getLong("start", 0), p.getLong("end", 0), p.getString("description", "尚未開始測試").orEmpty())
    }
    fun start(ctx: Context) {
        val wall = System.currentTimeMillis()
        val uptime = SystemClock.elapsedRealtime()
        // commit before power-off so the start record survives process/device restart.
        sp(ctx).edit().clear().putLong("start", wall).putLong("uptime", uptime)
            .putInt("boot", bootCount(ctx)).putString("version", CheckStore.version(ctx))
            .putString("context", CheckStore.scenario(ctx))
            .putString("description", "開始時間=${java.util.Date(wall)}；開始開機經過時間=${uptime}ms。請熄火後再發動。")
            .commit()
        CheckStore.put(ctx, CheckResult("ignition", "電源", "熄火後恢復", Status.TODO, read(ctx).description))
    }
    fun complete(ctx: Context) {
        val p = sp(ctx)
        if (p.getLong("start", 0) == 0L || p.getLong("end", 0) != 0L) return
        val end = System.currentTimeMillis()
        val uptime = SystemClock.elapsedRealtime()
        val description = "開始=${java.util.Date(p.getLong("start", 0))}；完成=${java.util.Date(end)}；" +
            "開始 uptime=${p.getLong("uptime", 0)}ms；完成 uptime=${uptime}ms。" +
            BootComparison.describe(p.getInt("boot", -1), bootCount(ctx), p.getLong("uptime", 0), uptime)
        p.edit().putLong("end", end).putString("description", description).commit()
        CheckStore.put(ctx, CheckResult("ignition_observation", "電源", "熄火測試觀察", Status.INFO, description,
            appVersion = p.getString("version", "").orEmpty(), contextKey = p.getString("context", "").orEmpty(),
            scenario = p.getString("context", "").orEmpty()))
    }
    fun confirm(ctx: Context, screen: Boolean?, music: Boolean?, gps: Boolean?, note: String) {
        val test = read(ctx)
        if (test.completedAt == 0L) return
        val checks = listOf(screen, music, gps)
        val status = when { false in checks -> Status.FAIL; null in checks -> Status.TODO; else -> Status.PASS }
        fun label(ok: Boolean?) = when(ok) { true -> "正常"; false -> "有問題"; null -> "待確認" }
        val p = sp(ctx)
        CheckStore.put(ctx, CheckResult("ignition", "電源", "熄火後恢復", status,
            "${test.description} 人工確認：畫面=${label(screen)}；音樂=${label(music)}；GPS=${label(gps)}。",
            appVersion = p.getString("version", "").orEmpty(), contextKey = p.getString("context", "").orEmpty(),
            scenario = p.getString("context", "").orEmpty(), notes = note))
    }
}
