package tw.fitcar.launcher.ui

import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

/** Control Android media volume, never claim to control a vendor's separate amplifier. */
@Composable
internal fun VolumeControl() {
    val context = LocalContext.current
    val audio = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var open by remember { mutableStateOf(false) }
    var volume by remember { mutableIntStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    var maximum by remember { mutableIntStateOf(audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)) }
    var error by remember { mutableStateOf<String?>(null) }
    var dragging by remember { mutableStateOf(false) }
    var draft by remember { mutableFloatStateOf(0f) }
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var verification by remember { mutableStateOf<String?>(null) }
    fun refresh() {
        volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        maximum = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    }
    fun changeVolume(target: Int, relative: Int? = null) {
        if (busy) return
        error = null
        val before = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val requested = target.coerceIn(0, maximum)
        busy = true
        scope.launch {
            try {
                if (relative == null) audio.setStreamVolume(AudioManager.STREAM_MUSIC, requested, AudioManager.FLAG_SHOW_UI)
                else audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, relative, AudioManager.FLAG_SHOW_UI)
                refresh()
                delay(600)
                refresh()
                val early = volume
                delay(900)
                refresh()
                val result = tw.fitcar.launcher.system.VolumeVerification.check(before, requested, early, volume, audio.isVolumeFixed)
                verification = result.message
                context.getSharedPreferences("volume_diagnostics", Context.MODE_PRIVATE).edit()
                    .putString("last_readback", "${if (relative == null) "絕對設定" else "相對增減"}：$before → $early → $volume；目標=$requested；fixed=${audio.isVolumeFixed}；${result.message}")
                    .putLong("observed_at", System.currentTimeMillis()).apply()
            } catch (_: SecurityException) { error = "車機限制這種音量控制，請使用系統音量或車機音量鍵。" }
            finally { busy = false }
        }
    }
    fun openSystemVolume() {
        try {
            val intent = if (android.os.Build.VERSION.SDK_INT >= 29) android.content.Intent(android.provider.Settings.Panel.ACTION_VOLUME)
                else android.content.Intent(android.provider.Settings.ACTION_SOUND_SETTINGS)
            context.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: android.content.ActivityNotFoundException) {
            try { audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI) }
            catch (_: SecurityException) { error = "系統未提供音量面板，請使用車機音量鍵。" }
        } catch (_: SecurityException) { error = "系統不允許開啟音量面板，請使用車機音量鍵。" }
    }
    // 音量改變時系統會送 VOLUME_CHANGED_ACTION；部分車機不送，保留 3 秒輪詢備援（原本每 0.5 秒）
    DisposableEffect(context) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context?, i: android.content.Intent?) { refresh() }
        }
        val registered = runCatching {
            androidx.core.content.ContextCompat.registerReceiver(context, receiver,
                android.content.IntentFilter("android.media.VOLUME_CHANGED_ACTION"), androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        }.isSuccess
        onDispose { if (registered) runCatching { context.unregisterReceiver(receiver) } }
    }
    LaunchedEffect(audio, open) { while (true) { refresh(); delay(if (open) 500 else 3_000) } }
    IconButton(onClick = { open = true }, modifier = Modifier.size(48.dp)
        .semantics { contentDescription = "媒體音量 $volume／$maximum" }) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            GlyphIcon(Glyph.VOLUME, size = 16.dp, tint = p.text)
            Text("$volume", color = p.text, fontSize = DriveText.BODY.sp, lineHeight = 22.sp, maxLines = 1)
        }
    }
    if (open) AlertDialog(
        onDismissRequest = { open = false }, containerColor = p.surface,
        title = { Text("媒體音量 $volume／$maximum", color = p.text) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Slider(value = if (dragging) draft else volume.toFloat(),
                    onValueChange = { dragging = true; draft = it },
                    valueRange = 0f..maximum.toFloat(),
                    enabled = !audio.isVolumeFixed && !busy,
                    onValueChangeFinished = { changeVolume(draft.toInt()); dragging = false },
                    modifier = Modifier.semantics { contentDescription = "調整媒體音量" })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    SecondaryButton("降低", { changeVolume(volume - 1, AudioManager.ADJUST_LOWER) }, enabled = volume > 0 && !audio.isVolumeFixed && !busy)
                    SecondaryButton("提高", { changeVolume(volume + 1, AudioManager.ADJUST_RAISE) }, enabled = volume < maximum && !audio.isVolumeFixed && !busy)
                }
                Spacer(Modifier.height(8.dp))
                SecondaryButton("系統音量", { openSystemVolume() }, Modifier.fillMaxWidth())
                if (busy) Hint("等待系統回讀…", size = 13, essential = true)
                verification?.let { Hint(it, size = 13, essential = true) }
                if (audio.isVolumeFixed) Hint("系統音量固定，請使用車機音量鍵。", essential = true)
                error?.let { Hint(it, color = p.danger, essential = true) }
                Hint("顯示 Android 數值，不代表原廠主機音量已改變；若聲音沒變，請用系統音量或車機音量鍵。", size = 13, essential = true)
            }
        },
        confirmButton = { TextButton(onClick = { open = false }) { Text("關閉") } },
    )
}
