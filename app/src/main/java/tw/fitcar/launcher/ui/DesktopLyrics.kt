package tw.fitcar.launcher.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import tw.fitcar.launcher.lyrics.Lrc
import tw.fitcar.launcher.lyrics.LyricsRepository
import tw.fitcar.launcher.lyrics.LyricsResult
import tw.fitcar.launcher.media.MediaUiState

@Composable
internal fun LyricsSetting() {
    val context = LocalContext.current
    val enabled by LyricsRepository.enabled.collectAsState()
    var consent by remember { mutableStateOf(false) }
    LaunchedEffect(context) { LyricsRepository.initialize(context) }
    Row(Modifier.fillMaxWidth()) {
        Hint("桌面歌詞（LRCLIB）", Modifier.weight(1f))
        Switch(checked = enabled, onCheckedChange = { if (it) consent = true else LyricsRepository.setEnabled(context, false) })
    }
    Hint("顯示於右上播放卡原歌詞區；未收錄或無法比對時保留歌曲資訊。", size = 13)
    if (consent) AlertDialog(onDismissRequest = { consent = false },
        title = { Text("啟用桌面歌詞") },
        text = { Text("啟用後會將目前歌曲的歌名、歌手、專輯與時長送到 LRCLIB 查詢。這是獨立歌詞來源，不是讀取 KKBOX／YT Music 內的歌詞。收錄與版本可能不同；有時間碼才同步，無時間碼則顯示文字。可隨時關閉。") },
        confirmButton = { TextButton(onClick = { LyricsRepository.setEnabled(context, true); consent = false }) { Text("同意並啟用") } },
        dismissButton = { TextButton(onClick = { consent = false }) { Text("取消") } })
}

@Composable
internal fun DesktopLyrics(media: MediaUiState) {
    var result by remember(media.packageName, media.title, media.artist, media.album, media.durationMs) { mutableStateOf(LyricsResult("正在查詢歌詞…")) }
    LaunchedEffect(media.packageName, media.title, media.artist, media.album, media.durationMs) {
        delay(600) // Let track metadata settle; do not query while it is changing.
        result = LyricsRepository.lookup(media.title, media.artist, media.album, media.durationMs)
    }
    LyricsLines(result, media)
}

@Composable
internal fun LyricsLines(result: LyricsResult, media: MediaUiState) {
    val p = LocalPalette.current
    var position by remember { mutableLongStateOf(media.livePosition()) }
    LaunchedEffect(media, result.lines) {
        if (result.lines.isNotEmpty()) while (true) { position = media.livePosition(); delay(if (media.isPlaying) 250 else 1000) }
    }
    val canSync = result.lines.isNotEmpty() && media.positionUpdateElapsed > 0
    Hint(if (result.lines.isNotEmpty() && !canSync) "播放進度未提供，顯示文字歌詞 · LRCLIB" else result.status, size = 12)
    if (canSync) {
        val index = Lrc.current(result.lines, position)
        Text(if (index < 0) "♪" else result.lines[index].text.ifBlank { "♪" }, color = p.text, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        result.lines.getOrNull(index + 1)?.let { Text(it.text, color = p.textSoft, fontSize = 16.sp) }
    } else (result.plain ?: result.lines.takeIf { it.isNotEmpty() }?.joinToString("\n") { it.text })?.let { Text(it, color = p.text, fontSize = 18.sp) }
}
