package tw.fitcar.launcher.system

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/** Android geocoding is best effort, not highway map matching. No public search server is polled. */
object RoadGeocoder {
    fun chineseLabel(parts: List<String?>, addressLine: String?): String? {
        val han = Regex("[\u3400-\u9fff]")
        val useful = parts.filterNotNull().map(String::trim).filter { it.isNotBlank() }.distinct()
        val label = useful.joinToString(" ").takeIf { han.containsMatchIn(it) }
            ?: addressLine?.takeIf { han.containsMatchIn(it) }
        return label?.take(160)
    }

    private fun label(entry: Address?): String? = entry?.let {
        chineseLabel(listOf(it.adminArea, it.subAdminArea, it.locality, it.subLocality, it.thoroughfare), it.getAddressLine(0))
    }

    suspend fun lookup(context: Context, latitude: Double, longitude: Double): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context.applicationContext, Locale.TAIWAN)
        return try {
            if (Build.VERSION.SDK_INT >= 33) withTimeoutOrNull(8_000) {
                suspendCancellableCoroutine { continuation ->
                    geocoder.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            if (continuation.isActive) continuation.resume(label(addresses.firstOrNull()))
                        }
                        override fun onError(errorMessage: String?) {
                            if (continuation.isActive) continuation.resume(null)
                        }
                    })
                }
            } else withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                label(geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull())
            }
        } catch (_: java.io.IOException) { null }
        catch (_: IllegalArgumentException) { null }
    }
}
