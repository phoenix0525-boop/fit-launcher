package tw.fitcar.launcher.ui

import android.content.Context
import android.content.Intent
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
import kotlinx.coroutines.delay
import tw.fitcar.launcher.system.GpsState
import tw.fitcar.launcher.system.MapDiagnostics

/** Keep useful Chinese text during lookups; label cached text instead of claiming an old road is current. */
@Composable
internal fun CurrentLocationCard(gps: GpsState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val latest by rememberUpdatedState(gps)
    var address by remember { mutableStateOf<String?>(null) }
    var resolvedPosition by remember { mutableStateOf<LatLng?>(null) }
    var resolvedAt by remember { mutableLongStateOf(0L) }
    var querying by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    // This clock must continue even while the geocoder is waiting for a network response.
    LaunchedEffect(Unit) { while (true) { now = SystemClock.elapsedRealtime(); delay(1_000) } }
    LaunchedEffect(context) {
        var requestedAt = 0L
        var lookupPosition: LatLng? = null
        var lastFailed = false
        while (true) {
            val fix = latest
            val elapsed = SystemClock.elapsedRealtime()
            if (fix.hasRecentPosition(elapsed)) {
                val point = LatLng(fix.latitude!!, fix.longitude!!)
                val old = lookupPosition
                val moved = FloatArray(1)
                if (old != null) Location.distanceBetween(old.latitude, old.longitude, point.latitude, point.longitude, moved)
                val interval = if (lastFailed) 30_000L else 15_000L
                if (old == null || elapsed - requestedAt >= interval && (moved[0] >= 100 || lastFailed)) {
                    requestedAt = elapsed
                    lookupPosition = point
                    querying = true
                    MapDiagnostics.address("查詢中（保留上次中文位置）")
                    val result = tw.fitcar.launcher.system.RoadGeocoder.lookup(context, point.latitude, point.longitude)
                    querying = false
                    lastFailed = result == null
                    if (result != null) {
                        address = result
                        resolvedPosition = point
                        resolvedAt = SystemClock.elapsedRealtime()
                    }
                    MapDiagnostics.address(if (result == null) "地址查詢暫不可用，保留上次位置供參考" else "地址查詢成功（道路名稱不等於導航道路比對）")
                }
            }
            delay(2_000)
        }
    }
    val p = LocalPalette.current
    val moved = FloatArray(1)
    resolvedPosition?.let { old ->
        if (gps.latitude != null && gps.longitude != null)
            Location.distanceBetween(old.latitude, old.longitude, gps.latitude, gps.longitude, moved)
    }
    val observedNow = maxOf(now, SystemClock.elapsedRealtime())
    val freshAddress = gps.hasRecentPosition(observedNow) && observedNow - resolvedAt in 0 until 120_000 && moved[0] < 300
    val label = when {
        !gps.permission -> "尚未授權定位"
        !gps.providerEnabled -> "GPS 已關閉"
        address != null && freshAddress -> address!!
        address != null -> "上次位置：$address"
        gps.hasRecentPosition(observedNow) -> if (querying) "正在查詢中文位置…" else "中文路段暫不可用"
        else -> "等待 GPS 定位…"
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
    var autoZoom by remember(view) { mutableStateOf(true) }
    var zoom by remember(view) { mutableStateOf(16.0) }
    var centered by remember(view) { mutableStateOf(false) }
    var recenter by remember(view) { mutableIntStateOf(0) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(view) { while (true) { now = SystemClock.elapsedRealtime(); delay(2_000) } }
    // A GPS callback can be newer than the last UI timer tick. Never compare it to that older tick.
    val observedNow = maxOf(now, SystemClock.elapsedRealtime())
    val hasPosition = gps.hasRecentPosition(observedNow)
    val canHold = tw.fitcar.launcher.system.MapFollowPolicy.canHold(gps, observedNow)
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
    LaunchedEffect(map, loaded, gps.fixElapsed, gps.providerEnabled, gps.permission, following, hasPosition, canHold, autoZoom, recenter) {
        val ready = map
        if (ready != null && loaded && canHold) {
            val point = LatLng(gps.latitude!!, gps.longitude!!)
            if (marker == null) marker = ready.addMarker(MarkerOptions().position(point).title("目前位置"))
            else marker?.position = point
            marker?.title = if (hasPosition) "目前位置" else "上次定位（訊號較弱）"
            if (following && hasPosition) {
                val target = if (autoZoom) tw.fitcar.launcher.system.MapFollowPolicy.zoom(gps.speedKmh.takeIf { gps.isFresh() }) else ready.cameraPosition.zoom
                zoom = if (centered && autoZoom) tw.fitcar.launcher.system.MapFollowPolicy.smoothZoom(zoom, target) else target
                val update = CameraUpdateFactory.newLatLngZoom(point, zoom)
                if (!centered) { ready.moveCamera(update); centered = true }
                else ready.animateCamera(update, 750, null)
            }
        } else {
            marker?.let { old -> ready?.removeMarker(old) }
            marker = null
        }
    }
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Hint(if (!gps.permission) "請先允許定位" else if (!gps.providerEnabled) "GPS 已關閉" else if (!hasPosition && canHold) "訊號較弱 · 上次位置" else if (!hasPosition) "等待 GPS 定位…" else if (following) "跟隨目前位置" else "自由瀏覽", Modifier.weight(1f), size = 13)
            SecondaryButton(if (autoZoom) "自動縮放" else "手動縮放", { autoZoom = !autoZoom; if (autoZoom) following = true })
            SecondaryButton("回到目前位置", { centered = false; following = true; recenter++ })
        }
        AndroidView(factory = { view }, modifier = Modifier.weight(1f).fillMaxWidth())
        if (failed || slow && !loaded) {
            Hint("底圖暫時無法載入，請確認網路後重試；GPS 與外部導航仍可使用。", size = 13)
            SecondaryButton("重試載入", { marker = null; retry++ })
        }
        Hint("© OpenFreeMap · © OpenMapTiles · © OpenStreetMap contributors", size = 11)
    }
}
