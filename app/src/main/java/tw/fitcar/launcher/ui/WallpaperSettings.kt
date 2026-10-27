package tw.fitcar.launcher.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import tw.fitcar.launcher.wallpaper.*

internal val LocalWallpaperActive = staticCompositionLocalOf { false }

@Composable
internal fun WallpaperLayer(state: WallpaperState, night: Boolean, modifier: Modifier = Modifier) {
    val bitmap = state.bitmap
    if (!state.visible || bitmap == null) return
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    Box(modifier.clipToBounds()) {
        Image(image, "自訂桌布", Modifier.fillMaxSize(),
            contentScale = if (state.options.crop) ContentScale.Crop else ContentScale.Fit,
            alignment = BiasAlignment(state.options.x, state.options.y))
        if (night) Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = state.options.nightDim)))
    }
}

@Composable
internal fun WallpaperSettings() {
    val context = LocalContext.current
    val store = remember(context) { WallpaperRepository.get(context) }
    val state by store.state.collectAsState()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reset by remember { mutableStateOf(false) }
    LaunchedEffect(store) { store.load() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true; error = null
            try { store.importImage(uri).onFailure { error = it.message ?: "無法匯入圖片，保留原桌布" } }
            finally { busy = false }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Hint("桌布與主題配色分開；地圖保留自己的底圖，歌詞與資訊卡保留清楚底色。圖片只存車機，不會上傳。", size = 14)
        PrimaryButton(if (busy) "圖片處理中…" else "選擇桌布圖片", {
            try { picker.launch(arrayOf("image/jpeg", "image/png", "image/webp")) }
            catch (_: android.content.ActivityNotFoundException) { error = "車機沒有系統選圖程式" }
            catch (_: SecurityException) { error = "系統不允許選取圖片" }
        }, enabled = state.loaded && !busy)
        error?.let { Text(it, color = LocalPalette.current.danger) }
        if (state.bitmap != null) {
            WallpaperLayer(state.copy(options = state.options.copy(enabled = true)), LocalPalette.current.isNight,
                Modifier.fillMaxWidth().height(180.dp).clip(RadiusMd).background(LocalPalette.current.bgDeep))
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("使用自訂桌布", Modifier.weight(1f), color = LocalPalette.current.text)
                Switch(state.options.enabled, { value -> store.update { it.copy(enabled = value) } }, enabled = !busy)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(if (state.options.crop) "✓ 填滿畫面" else "填滿畫面", { store.update { it.copy(crop = true) } }, enabled = !busy)
                SecondaryButton(if (!state.options.crop) "✓ 完整顯示" else "完整顯示", { store.update { it.copy(crop = false) } }, enabled = !busy)
            }
            Text("左右位置", color = LocalPalette.current.text)
            Slider(state.options.x, { value -> store.update { it.copy(x = value) } }, valueRange = -1f..1f, enabled = !busy,
                modifier = Modifier.semantics { contentDescription = "桌布左右位置" })
            Text("上下位置", color = LocalPalette.current.text)
            Slider(state.options.y, { value -> store.update { it.copy(y = value) } }, valueRange = -1f..1f, enabled = !busy,
                modifier = Modifier.semantics { contentDescription = "桌布上下位置" })
            Text("夜間圖片暗化 ${(state.options.nightDim * 100).toInt()}%", color = LocalPalette.current.text)
            Slider(state.options.nightDim, { value -> store.update { it.copy(nightDim = value) } }, valueRange = 0f..0.75f, enabled = !busy,
                modifier = Modifier.semantics { contentDescription = "桌布夜間暗化" })
            Hint("只暗化桌布圖片，不調整螢幕背光；主題仍依原本日夜設定切換。", size = 13)
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("顯示主畫面主題插畫", Modifier.weight(1f), color = LocalPalette.current.text)
                Switch(state.options.showArt, { value -> store.update { it.copy(showArt = value) } }, enabled = !busy)
            }
            Hint("關閉可避免照片與蜘蛛人／布丁狗插畫同時出現；主題配色不受影響。桌布主要顯示在留白與卡片後方，不覆蓋導航地圖。", size = 13)
            SecondaryButton("恢復預設桌布", { reset = true }, enabled = !busy)
        }
        Hint("支援JPG／PNG／WebP，20MB以內；匯入會縮圖並保存本機副本，原圖不會修改。", size = 13)
    }
    if (reset) AlertDialog(onDismissRequest = { reset = false }, title = { Text("恢復預設桌布？") },
        text = { Text("只移除本機桌布副本與桌布設定，不刪原圖，也不變更主題、導航或音樂設定。") },
        confirmButton = { TextButton(onClick = { reset = false; scope.launch { busy = true; try { store.reset() } finally { busy = false } } }) { Text("恢復預設") } },
        dismissButton = { TextButton(onClick = { reset = false }) { Text("取消") } })
}
