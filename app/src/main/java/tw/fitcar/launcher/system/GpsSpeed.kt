package tw.fitcar.launcher.system

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class GpsState(
    val permission: Boolean = false,
    val providerEnabled: Boolean = false,
    val speedKmh: Float? = null,
    val accuracyM: Float? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val fixElapsed: Long = 0L,
    /** GPS 行進方向（度，0＝北）；只在移動中可信，顯示前用 Compass.heading 過濾。 */
    val bearing: Float? = null,
) {
    fun hasRecentPosition(now: Long = SystemClock.elapsedRealtime()): Boolean =
        permission && providerEnabled && latitude != null && longitude != null &&
            fixElapsed > 0 && now - fixElapsed in 0 until 10_000

    /** 5 秒內有定位且有速度才算「已定位」。 */
    fun isFresh(now: Long = SystemClock.elapsedRealtime()) = speedKmh != null && now - fixElapsed < 5_000
}

/** 啟動器自己用 GPS 算的車速，畫面上一律標示「GPS 車速」，不冒充神盾資料。 */
object GpsSpeed {
    private val _state = MutableStateFlow(GpsState())
    val state: StateFlow<GpsState> = _state.asStateFlow()
    private var manager: LocationManager? = null
    private var running = false

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            _state.value = _state.value.copy(
                providerEnabled = true,
                latitude = location.latitude,
                longitude = location.longitude,
                speedKmh = if (location.hasSpeed()) location.speed * 3.6f else null,
                accuracyM = if (location.hasAccuracy()) location.accuracy else null,
                bearing = if (location.hasBearing()) location.bearing else null,
                fixElapsed = location.elapsedRealtimeNanos / 1_000_000L,
            )
            TripMeter.onFix(location.latitude, location.longitude,
                if (location.hasAccuracy()) location.accuracy else null,
                if (location.hasSpeed()) location.speed * 3.6f else null,
                location.elapsedRealtimeNanos / 1_000_000L)
        }
        override fun onProviderEnabled(provider: String) { _state.value = _state.value.copy(providerEnabled = true) }
        override fun onProviderDisabled(provider: String) { _state.value = _state.value.copy(providerEnabled = false, speedKmh = null) }
        @Deprecated("Android 10 以下仍會呼叫")
        @Suppress("OVERRIDE_DEPRECATION")
        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
    }

    fun hasPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun start(context: Context) {
        val ctx = context.applicationContext
        val granted = hasPermission(ctx)
        val lm = manager ?: (ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager).also { manager = it }
        val enabled = try { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) } catch (e: Exception) { false }
        _state.value = _state.value.copy(permission = granted, providerEnabled = enabled)
        if (!granted || running) return
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener, Looper.getMainLooper())
            running = true
        } catch (e: Exception) {
            _state.value = _state.value.copy(providerEnabled = false)
        }
    }

    fun stop() {
        if (running) {
            manager?.removeUpdates(listener)
            running = false
        }
    }
}

/** 本次行程（GPS 估算），只存在記憶體；久停 30 分鐘後自動視為新的一趟。 */
object TripMeter {
    private val _state = MutableStateFlow(TripState())
    val state: StateFlow<TripState> = _state.asStateFlow()
    fun onFix(lat: Double, lon: Double, accuracyM: Float?, speedKmh: Float?, elapsed: Long) {
        _state.value = Trip.add(_state.value, lat, lon, accuracyM, speedKmh, elapsed)
    }
    fun reset() { _state.value = TripState() }
}
