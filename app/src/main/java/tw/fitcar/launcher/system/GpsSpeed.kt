package tw.fitcar.launcher.system

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
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
    val fixElapsed: Long = 0L,
) {
    /** 5 秒內有定位且有速度才算「已定位」。 */
    fun isFresh(now: Long = SystemClock.elapsedRealtime()) = speedKmh != null && now - fixElapsed < 5_000
}

/** 啟動器自己用 GPS 算的車速，畫面上一律標示「GPS 車速」，不冒充神盾資料。 */
object GpsSpeed {
    private const val FRESH_MILLIS = 5_000L
    private val _state = MutableStateFlow(GpsState())
    val state: StateFlow<GpsState> = _state.asStateFlow()
    private var manager: LocationManager? = null
    private var running = false
    private val main = Handler(Looper.getMainLooper())
    private var staleRunnable: Runnable? = null

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val fixAt = SystemClock.elapsedRealtime()
            _state.value = _state.value.copy(
                providerEnabled = true,
                speedKmh = if (location.hasSpeed()) location.speed * 3.6f else null,
                accuracyM = if (location.hasAccuracy()) location.accuracy else null,
                fixElapsed = fixAt,
            )
            staleRunnable?.let(main::removeCallbacks)
            staleRunnable = Runnable {
                if (_state.value.fixElapsed == fixAt) _state.value = _state.value.copy(speedKmh = null)
            }.also { main.postDelayed(it, FRESH_MILLIS) }
        }
        override fun onProviderEnabled(provider: String) { _state.value = _state.value.copy(providerEnabled = true) }
        override fun onProviderDisabled(provider: String) { _state.value = _state.value.copy(providerEnabled = false, speedKmh = null) }
        @Deprecated("Android 10 以下仍會呼叫")
        @Suppress("OVERRIDE_DEPRECATION")
        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
    }

    fun hasPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasApproximatePermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

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
        staleRunnable?.let(main::removeCallbacks)
        staleRunnable = null
        if (running) {
            manager?.removeUpdates(listener)
            running = false
        }
        _state.value = _state.value.copy(speedKmh = null, accuracyM = null, fixElapsed = 0L)
    }
}
