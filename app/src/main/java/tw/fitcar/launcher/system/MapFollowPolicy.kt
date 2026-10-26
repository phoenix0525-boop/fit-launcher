package tw.fitcar.launcher.system

/** Pure display policy. Holding a marker never makes an old GPS fix current. */
object MapFollowPolicy {
    const val HOLD_MS = 60_000L
    fun canHold(fix: GpsState, now: Long): Boolean =
        fix.permission && fix.providerEnabled && fix.latitude != null && fix.longitude != null &&
            fix.fixElapsed > 0 && now - fix.fixElapsed in 0 until HOLD_MS

    /** Continuous targets avoid threshold zoom oscillations at e.g. 60 km/h. */
    fun zoom(speedKmh: Float?): Double = when {
        speedKmh == null || !speedKmh.isFinite() -> 16.0
        else -> 17.0 - speedKmh.coerceIn(0f, 120f) / 40.0
    }
    fun smoothZoom(previous: Double, target: Double): Double =
        previous + (target - previous).coerceIn(-0.15, 0.15)
}
