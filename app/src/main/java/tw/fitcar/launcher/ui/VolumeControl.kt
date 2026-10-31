package tw.fitcar.launcher.ui

import android.content.Context
import android.media.AudioManager
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import tw.fitcar.launcher.system.OemIntegrationProbe
import tw.fitcar.launcher.system.VolumeGuidance

/**
 * 0.4.34：本車機的實際音量由原廠主機控制（0.4.22／0.4.24 實車：桌面三種公開音量方法都沒有改變聲音）。
 * 主畫面不再顯示 Android 音量數值、也不送出音量指令；改為說明與原廠入口。
 * 只讀的音量對照仍在「實機檢測 → 音量檢測」。
 */
@Composable
internal fun VolumeControl(size: androidx.compose.ui.unit.Dp = 48.dp) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    val p = LocalPalette.current
    IconButton(onClick = { open = true }, modifier = Modifier.size(size)
        .semantics { contentDescription = VolumeGuidance.BUTTON_DESCRIPTION }) {
        GlyphIcon(Glyph.VOLUME, size = (size.value * 0.5f).dp, tint = p.text)
    }
    if (open) AlertDialog(
        onDismissRequest = { open = false }, containerColor = p.surface,
        title = { Text(VolumeGuidance.TITLE, color = p.text) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                VolumeGuidance.WAYS.forEach { Hint("• $it", size = 18, essential = true) }
                SecondaryButton(VolumeGuidance.FACTORY_EQ, {
                    OemIntegrationProbe.openFactoryApp(context)?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                        ?: run { open = false }
                }, Modifier.fillMaxWidth())
                SecondaryButton(VolumeGuidance.SYSTEM_PANEL, { if (openSystemVolume(context)) open = false }, Modifier.fillMaxWidth())
                Hint(VolumeGuidance.NOTE, size = 14, essential = true)
            }
        },
        confirmButton = { TextButton(onClick = { open = false }) { Text("關閉") } },
    )
}

/** 開啟系統音量面板；效果依車機而定（可能只改 Android 數值）。回傳是否成功開啟。 */
private fun openSystemVolume(context: Context): Boolean {
    val flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
    return try {
        val intent = if (android.os.Build.VERSION.SDK_INT >= 29) android.content.Intent(android.provider.Settings.Panel.ACTION_VOLUME)
            else android.content.Intent(android.provider.Settings.ACTION_SOUND_SETTINGS)
        context.startActivity(intent.addFlags(flags)); true
    } catch (_: Exception) {
        try {
            (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
                .adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI); true
        } catch (_: Exception) {
            Toast.makeText(context, "系統未提供音量面板，請使用方向盤音量鍵。", Toast.LENGTH_LONG).show(); false
        }
    }
}
