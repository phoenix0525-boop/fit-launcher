package tw.fitcar.launcher.ui

import android.content.Context
import android.content.Intent
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import tw.fitcar.launcher.system.GpsState
import tw.fitcar.launcher.system.MapDiagnostics
import java.util.Locale

private fun GpsState.hasRecentPosition() = permission && providerEnabled &&
    latitude != null && longitude != null && SystemClock.elapsedRealtime() - fixElapsed < 10_000

/** Address lookup is independent of map billing. Never display a stale address as the current one. */
@Composable
internal fun CurrentLocationCard(gps: GpsState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val latest by rememberUpdatedState(gps)
    var address by remember { mutableStateOf<String?>(null) }
    var resolvedPosition by remember { mutableStateOf<LatLng?>(null) }
    var lookupPosition by remember { mutableStateOf<LatLng?>(null) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(context) {
        var requestedAt = 0L
        while (true) {
            now = SystemClock.elapsedRealtime()
            val fix = latest
            if (fix.hasRecentPosition()) {
                val point = LatLng(fix.latitude!!, fix.longitude!!)
                val old = lookupPosition
                val distances = FloatArray(1)
                if (old != null) Location.distanceBetween(old.latitude, old.longitude, point.latitude, point.longitude, distances)
                if (old == null || now - requestedAt >= 30_000 && (distances[0] >= 100 || address == null)) {
                    requestedAt = now
                    lookupPosition = point
                    address = null
                    resolvedPosition = null
                    MapDiagnostics.address("查詢中")
                    val result = withContext(Dispatchers.IO) {
                        try {
                            @Suppress("DEPRECATION")
                            val entry = if (Geocoder.isPresent()) Geocoder(context.applicationContext, Locale.TAIWAN)
                                .getFromLocation(point.latitude, point.longitude, 1)?.firstOrNull() else null
                            entry?.let {
                                listOfNotNull(it.adminArea, it.subAdminArea, it.locality, it.subLocality, it.thoroughfare)
                                    .filter(String::isNotBlank).distinct().joinToString(" ").ifBlank { null }
                            }
                        } catch (_: Exception) { null }
                    }
                    MapDiagnostics.address(if (result == null) "地址查詢暫不可用" else "地址查詢成功")
                    address = result
                    if (result != null) resolvedPosition = point
                }
            }
            if (!fix.hasRecentPosition()) MapDiagnostics.address("等待有效 GPS 定位")
            delay(2_000)
        }
    }
    val p = LocalPalette.current
    val current = gps.hasRecentPosition() && now - gps.fixElapsed < 10_000
    val distance = FloatArray(1)
    resolvedPosition?.let { old ->
        if (gps.latitude != null && gps.longitude != null)
            Location.distanceBetween(old.latitude, old.longitude, gps.latitude, gps.longitude, distance)
    }
    val label = when {
        !gps.permission -> "尚未授權定位"
        !gps.providerEnabled -> "GPS 已關閉"
        !current -> "定位中…"
        address != null && distance[0] < 100 -> address!!
        else -> String.format(Locale.US, "%.5f, %.5f（地址暫不可用）", gps.latitude, gps.longitude)
    }
    Column(modifier.clip(RadiusMd).background(p.surfaceStrong).border(1.dp, p.line, RadiusMd)
        .padding(horizontal = 14.dp, vertical = 8.dp)) {
        Text("目前位置", color = p.textSoft, fontSize = 12.sp)
        Text(label, color = p.text, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

internal fun openGoogleNavigation(context: Context) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/"))
            .setPackage("com.google.android.apps.maps").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        Toast.makeText(context, "無法開啟 Google 地圖，請確認已安裝並啟用", Toast.LENGTH_LONG).show()
    }
}

@Composable
internal fun FreeMapPanel(gps: GpsState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val view = remember(context) {
        MapLibre.getInstance(context.applicationContext)
        MapView(context).apply { onCreate(null) }
    }
    var map by remember(view) { mutableStateOf<MapLibreMap?>(null) }
    var marker by remember(view) { mutableStateOf<Marker?>(null) }
    var following by remember(view) { mutableStateOf(true) }
    var loaded by remember(view) { mutableStateOf(false) }
    var slow by remember(view) { mutableStateOf(false) }
    var failed by remember(view) { mutableStateOf(false) }
    var retry by remember(view) { mutableIntStateOf(0) }
    DisposableEffect(view, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> view.onStart()
                Lifecycle.Event.ON_RESUME -> view.onResume()
                Lifecycle.Event.ON_PAUSE -> view.onPause()
                Lifecycle.Event.ON_STOP -> view.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        var disposed = false
        val failureListener = MapView.OnDidFailLoadingMapListener { if (!disposed) { failed = true; MapDiagnostics.base("底圖載入失敗") } }
        view.addOnDidFailLoadingMapListener(failureListener)
        view.getMapAsync { ready ->
            if (!disposed) {
                map = ready
                ready.uiSettings.isAttributionEnabled = true
                ready.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(23.7, 121.0), 7.0))
                ready.addOnCameraMoveStartedListener { reason ->
                    if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) following = false
                }

            }
        }
        onDispose {
            disposed = true
            view.removeOnDidFailLoadingMapListener(failureListener)
            lifecycle.removeObserver(observer)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) view.onPause()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) view.onStop()
            view.onDestroy()
        }
    }
    LaunchedEffect(map, retry) {
        loaded = false
        failed = false
        slow = false
        MapDiagnostics.base("底圖載入中")
        map?.setStyle("https://tiles.openfreemap.org/styles/liberty") { loaded = true; failed = false; MapDiagnostics.base("底圖樣式已載入（圖磚顯示仍需目視確認）") }
        delay(20_000)
        if (!loaded) { slow = true; MapDiagnostics.base("底圖載入逾時") }
    }
    LaunchedEffect(map, loaded, gps.latitude, gps.longitude, gps.providerEnabled, gps.permission, following) {
        val ready = map
        if (ready != null && loaded && gps.hasRecentPosition()) {
            val point = LatLng(gps.latitude!!, gps.longitude!!)
            if (marker == null) marker = ready.addMarker(MarkerOptions().position(point).title("目前位置"))
            else marker?.position = point
            if (following) ready.moveCamera(CameraUpdateFactory.newLatLngZoom(point, 16.0))
        }
    }
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Hint(if (following) "跟隨目前位置" else "自由瀏覽", Modifier.weight(1f), size = 13)
            SecondaryButton("回到目前位置", { following = true })
        }
        AndroidView(factory = { view }, modifier = Modifier.weight(1f).fillMaxWidth())
        if (failed || slow && !loaded) {
            Hint("底圖暫時無法載入，請確認網路後重試；GPS 與外部導航仍可使用。", size = 13)
            SecondaryButton("重試載入", { marker = null; retry++ })
        }
        Hint("© OpenFreeMap · © OpenMapTiles · © OpenStreetMap contributors", size = 11)
    }
}
