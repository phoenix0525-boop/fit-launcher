package tw.fitcar.launcher.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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

/**
 * 歌詞大字顯示：目前行 28 sp（行車 30 sp），上一行與下一行淡色 18 sp。
 * 位置每 250 ms 取樣，但只有「目前行號」改變時才重組畫面（derivedStateOf）。
 * 無時間碼時只顯示前 4 行；點歌詞區可開全螢幕歌詞。
 */
@Composable
internal fun LyricsLines(result: LyricsResult, media: MediaUiState) {
    val p = LocalPalette.current
    val driving = LocalDriving.current
    var position by remember { mutableLongStateOf(media.livePosition()) }
    LaunchedEffect(media, result.lines) {
        if (result.lines.isNotEmpty()) while (true) { position = media.livePosition(); delay(if (media.isPlaying) 250 else 1000) }
    }
    val canSync = result.lines.isNotEmpty() && media.positionUpdateElapsed > 0
    val index by remember(result.lines) { derivedStateOf { Lrc.current(result.lines, position) } }
    var full by remember { mutableStateOf(false) }
    Hint(if (result.lines.isNotEmpty() && !canSync) "播放進度未提供，顯示文字歌詞 · LRCLIB" else result.status, size = 13)
    Column(
        Modifier.fillMaxWidth().clickable(enabled = canSync || result.plain != null || result.lines.isNotEmpty()) { full = true },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (canSync) {
            val i = index
            result.lines.getOrNull(i - 1)?.text?.takeIf { it.isNotBlank() && !driving }?.let {
                Text(it, modifier = Modifier.fillMaxWidth(), color = p.textSoft, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
            Text(if (i < 0) "♪" else result.lines[i].text.ifBlank { "♪" }, modifier = Modifier.fillMaxWidth(), color = p.text,
                fontSize = if (driving) 30.sp else 28.sp, lineHeight = if (driving) 36.sp else 34.sp,
                fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            result.lines.getOrNull(i + 1)?.let { Text(it.text, modifier = Modifier.fillMaxWidth(), color = p.textSoft, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center) }
        } else (result.plain ?: result.lines.takeIf { it.isNotEmpty() }?.joinToString("\n") { it.text })?.let { text ->
            val (shown, more) = Lrc.preview(text, if (driving) 2 else 4)
            Text(shown, modifier = Modifier.fillMaxWidth(), color = p.text, fontSize = if (driving) 22.sp else 20.sp, lineHeight = if (driving) 30.sp else 28.sp, textAlign = TextAlign.Center)
            if (more) Hint("無時間碼，只顯示前段；點一下看全文", size = 13)
        }
    }
    if (full) FullScreenLyrics(result, media, position, canSync) { full = false }
}

/** 全螢幕歌詞：目前行 40 sp，前後各兩行；無時間碼時可捲動全文。點任意處關閉。 */
@Composable
private fun FullScreenLyrics(result: LyricsResult, media: MediaUiState, position: Long, canSync: Boolean, onClose: () -> Unit) {
    val p = LocalPalette.current
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(p.bg).clickable(onClick = onClose).padding(32.dp)
                .then(if (canSync) Modifier else Modifier.verticalScroll(rememberScrollState())),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        ) {
            Text(listOfNotNull(media.title, media.artist).joinToString(" · "), color = p.textSoft, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (canSync) {
                val i = Lrc.current(result.lines, position)
                for (k in i - 2..i + 2) {
                    val line = result.lines.getOrNull(k) ?: continue
                    Text(line.text.ifBlank { "♪" }, color = if (k == i) p.text else p.textSoft,
                        fontSize = if (k == i) 40.sp else 24.sp, lineHeight = if (k == i) 48.sp else 30.sp,
                        fontWeight = if (k == i) FontWeight.Medium else FontWeight.Normal, textAlign = TextAlign.Center)
                }
            } else {
                Text(result.plain ?: result.lines.joinToString("\n") { it.text }, color = p.text, fontSize = 26.sp, lineHeight = 38.sp, textAlign = TextAlign.Center)
            }
            Hint("點任意處關閉 · LRCLIB", size = 15, essential = true)
        }
    }
}
