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
    /** Short signed turn across north; two-degree dead band rejects GPS heading jitter. */
    fun bearingDelta(previous: Double, target: Double): Double =
        ((target - previous + 540.0) % 360.0 + 360.0) % 360.0 - 180.0

    fun bearing(previous: Double, fix: GpsState, now: Long): Double {
        val held = if (previous.isFinite()) ((previous % 360.0) + 360.0) % 360.0 else 0.0
        val speed = fix.speedKmh
        val raw = fix.bearing
        if (!fix.hasRecentPosition(now) || now - fix.fixElapsed !in 0 until 5_000 ||
            speed == null || !speed.isFinite() || speed < Compass.MIN_SPEED_KMH || raw == null || !raw.isFinite()) return held
        val target = Compass.normalize(raw).toDouble()
        val delta = bearingDelta(held, target)
        return if (kotlin.math.abs(delta) < 2.0) held else ((held + delta) % 360.0 + 360.0) % 360.0
    }

}
