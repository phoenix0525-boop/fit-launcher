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
    fun refresh() {
        volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        maximum = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    }
    fun setVolume(value: Int) {
        error = null
        try {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, value.coerceIn(0, maximum), 0)
            refresh()
            if (volume != value.coerceIn(0, maximum)) error = "系統未套用音量，請使用車機音量鍵確認。"
        } catch (_: SecurityException) { error = "車機限制音量控制，請使用車機音量鍵。" }
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
    IconButton(onClick = { open = true }, modifier = Modifier.size(56.dp)
        .semantics { contentDescription = "媒體音量 $volume／$maximum" }) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (!LocalDriving.current) Text("音量", color = p.text, fontSize = 12.sp, maxLines = 1)
            Text("$volume", color = p.text, fontSize = if (LocalDriving.current) DriveText.BODY.sp else 18.sp, maxLines = 1)
        }
    }
    if (open) AlertDialog(
        onDismissRequest = { open = false }, containerColor = p.surface,
        title = { Text("媒體音量 $volume／$maximum", color = p.text) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Slider(value = if (dragging) draft else volume.toFloat(),
                    onValueChange = { dragging = true; draft = it },
                    valueRange = 0f..maximum.toFloat(),
                    enabled = !audio.isVolumeFixed,
                    onValueChangeFinished = { setVolume(draft.toInt()); dragging = false },
                    modifier = Modifier.semantics { contentDescription = "調整媒體音量" })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    SecondaryButton("降低", { setVolume(volume - 1) }, enabled = volume > 0 && !audio.isVolumeFixed)
                    SecondaryButton("提高", { setVolume(volume + 1) }, enabled = volume < maximum && !audio.isVolumeFixed)
                }
                if (audio.isVolumeFixed) Hint("系統音量固定，請使用車機音量鍵。")
                error?.let { Hint(it, color = p.danger) }
                Hint("調整 Android 媒體音量；原廠擴大機音量需實機確認。", size = 13)
            }
        },
        confirmButton = { TextButton(onClick = { open = false }) { Text("關閉") } },
    )
}
