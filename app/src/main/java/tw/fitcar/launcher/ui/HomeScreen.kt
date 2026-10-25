package tw.fitcar.launcher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import tw.fitcar.launcher.data.AppEntry
import tw.fitcar.launcher.data.ShortcutSlots
import tw.fitcar.launcher.media.MediaUiState
import tw.fitcar.launcher.system.GpsState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 捷徑格的解析結果：empty = 空格；missing = 已綁定但 App 找不到。 */
data class SlotView(val key: String?, val entry: AppEntry?) {
    val isEmpty get() = key == null
    val isMissing get() = key != null && entry == null
}

class HomeActions(
    val openAllApps: () -> Unit,
    val openSettings: () -> Unit,
    val openNav: () -> Unit,
    val openNavFullscreen: () -> Unit,
    val bindNav: () -> Unit,
    val openMusicApp: () -> Unit,
    val bindMusic: () -> Unit,
    val grantNotificationAccess: () -> Unit,
    val playPause: () -> Unit,
    val next: () -> Unit,
    val previous: () -> Unit,
    val seekTo: (Long) -> Unit,
    val openShield: () -> Unit,
    val launchSlot: (Int) -> Unit,
    val pickSlot: (Int) -> Unit,
    val editSlot: (Int) -> Unit,
)

@Composable
fun HomeScreen(
    nav: AppEntry?,
    navModeLabel: String,
    splitResult: Boolean?,
    music: AppEntry?,
    shield: AppEntry?,
    media: MediaUiState,
    gps: GpsState,
    slots: List<SlotView>,
    actions: HomeActions,
) {
    val p = LocalPalette.current
    Column(
        Modifier.fillMaxSize().background(p.bg).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        StatusBar(shield, gps, actions)
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            NavPanel(nav, navModeLabel, splitResult, actions, Modifier.weight(7f).fillMaxHeight())
            Column(Modifier.weight(3f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                MusicCard(music, media, actions, Modifier.fillMaxWidth().weight(1.22f))
                ShortcutsCard(slots, actions, Modifier.fillMaxWidth().weight(0.78f))
            }
        }
    }
}

@Composable
fun Clock(modifier: Modifier = Modifier, big: Boolean = true) {
    val p = LocalPalette.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000L - now % 1000L)
        }
    }
    val date = Date(now)
    Column(modifier) {
        Text(SimpleDateFormat("HH:mm", Locale.TAIWAN).format(date), color = p.text, fontSize = if (big) 40.sp else 30.sp, fontWeight = FontWeight.Medium, lineHeight = if (big) 44.sp else 34.sp)
        Text(SimpleDateFormat("M月d日 EEEE", Locale.TAIWAN).format(date), color = p.textSoft, fontSize = 15.sp)
    }
}

@Composable
private fun StatusBar(shield: AppEntry?, gps: GpsState, actions: HomeActions) {
    val p = LocalPalette.current
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) { delay(1000); tick++ } }
    Row(
        Modifier.fillMaxWidth().height(84.dp).clip(RadiusLg).background(p.surface).border(1.dp, p.line, RadiusLg).padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Clock(Modifier.width(220.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            // 神盾：沒有官方資料介面，只提供開啟入口，狀態維持「尚未連接」
            Row(
                Modifier.clip(RadiusMd).background(p.surfaceStrong).border(1.dp, p.line, RadiusMd)
                    .combinedClickableCompat(onClick = actions.openShield).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GlyphIcon(Glyph.SHIELD, size = 22.dp, tint = p.textSoft)
                Column {
                    Text(shield?.let { "${it.label}（已綁定）" } ?: "尚未選擇測速 App", color = p.text, fontSize = 15.sp, maxLines = 1)
                    Text(
                        if (shield == null) "點擊選擇神盾"
                        else "資料尚未連接 · 點擊開啟官方浮動窗",
                        color = p.textSoft, fontSize = 12.sp, maxLines = 1,
                    )
                }
            }
            // GPS 車速：啟動器自行計算，明確標示來源
            val fresh = remember(tick, gps) { gps.isFresh() }
            Row(
                Modifier.clip(RadiusMd).background(p.surfaceStrong).border(1.dp, p.line, RadiusMd).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    when {
                        !gps.permission -> "—"
                        fresh -> "%.0f".format(gps.speedKmh ?: 0f)
                        else -> "—"
                    },
                    color = p.text, fontSize = 28.sp, fontWeight = FontWeight.Medium,
                )
                Column {
                    Text("GPS 車速 km/h", color = p.text, fontSize = 13.sp)
                    Text(
                        when {
                            !gps.permission -> "未授權定位"
                            !gps.providerEnabled -> "GPS 已關閉"
                            fresh -> gps.accuracyM?.let { "精度 ±%.0f m".format(it) } ?: "已定位"
                            else -> "GPS 尚未定位"
                        },
                        color = p.textSoft, fontSize = 12.sp,
                    )
                }
            }
        }
        SecondaryButton("所有 App", actions.openAllApps, icon = { GlyphIcon(Glyph.GRID, size = 22.dp) })
        SecondaryButton("設定", actions.openSettings, icon = { GlyphIcon(Glyph.GEAR, size = 22.dp) })
    }
}

@Composable
private fun NavPanel(nav: AppEntry?, modeLabel: String, splitResult: Boolean?, actions: HomeActions, modifier: Modifier) {
    val p = LocalPalette.current
    FitCard(modifier, padding = androidx.compose.foundation.layout.PaddingValues(26.dp)) {
        CardTitle("導航") { Pill(modeLabel) }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                if (nav == null) {
                    GlyphIcon(Glyph.NAV, size = 72.dp, tint = p.accent)
                    Text("尚未選擇導航 App", color = p.text, fontSize = 26.sp, fontWeight = FontWeight.Medium)
                    Hint("請從車機實際安裝的 App 中選擇導航王", size = 16)
                    PrimaryButton("選擇導航 App", actions.bindNav)
                } else {
                    AppIcon(nav.icon, 112.dp)
                    Text(nav.label, color = p.text, fontSize = 30.sp, fontWeight = FontWeight.Medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        PrimaryButton("開始導航", actions.openNav, Modifier.height(72.dp).width(260.dp), icon = {
                            GlyphIcon(Glyph.NAV, size = 26.dp, tint = if (p.isNight) p.bgDeep else androidx.compose.ui.graphics.Color.White)
                        })
                        SecondaryButton("全螢幕開啟", actions.openNavFullscreen, Modifier.height(72.dp), icon = { GlyphIcon(Glyph.EXPAND, size = 24.dp) })
                    }
                }
            }
        }
        Hint(
            "導航王地圖無法嵌入桌面（Android 不允許嵌入其他 App 畫面）。" +
                "「開始導航」會先嘗試分割畫面，車機不支援時自動改為全螢幕；分割比例請拖曳中間分隔線。" +
                when (splitResult) {
                    true -> "　最近一次：分割畫面成功。"
                    false -> "　最近一次：車機未進入分割畫面，已改全螢幕。"
                    null -> "　尚未實測分割畫面。"
                },
            size = 14,
        )
    }
}

private fun formatMs(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
internal fun MusicCard(music: AppEntry?, media: MediaUiState, actions: HomeActions, modifier: Modifier) {
    val p = LocalPalette.current
    FitCard(modifier) {
        CardTitle(music?.label ?: "音樂") {
            if (music != null) SecondaryButton("開啟", actions.openMusicApp)
            else SecondaryButton("選擇音樂 App", actions.bindMusic)
        }
        Spacer(Modifier.height(10.dp))
        // 主要區：原本規劃為歌詞；目前沒有合法歌詞來源，依決策改顯示曲目並註明
        Column(
            Modifier.weight(1f).fillMaxWidth().clip(RadiusMd).background(p.surfaceStrong).border(1.dp, p.line, RadiusMd).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                !media.permissionGranted -> {
                    GlyphIcon(Glyph.MUSIC, size = 44.dp, tint = p.textSoft)
                    Text("尚未授權讀取播放資訊", color = p.text, fontSize = 20.sp, textAlign = TextAlign.Center)
                    Hint("需在系統開啟本 App 的「通知存取」，才能透過 Android MediaSession 讀取 KKBOX 播放中的歌曲。", size = 14)
                    PrimaryButton("前往授權", actions.grantNotificationAccess)
                }
                !media.hasSession -> {
                    GlyphIcon(Glyph.MUSIC, size = 44.dp, tint = p.textSoft)
                    Text("尚未播放", color = p.text, fontSize = 22.sp)
                    Hint("開啟 KKBOX 並播放後，這裡會顯示歌曲資訊。", size = 14)
                }
                else -> {
                    val art = media.art
                    if (art != null) {
                        val img = remember(art) { art.asImageBitmap() }
                        Image(img, null, Modifier.weight(1f, fill = false).size(150.dp).clip(RadiusMd), contentScale = ContentScale.Crop)
                    }
                    Text(media.title ?: "（App 未提供歌名）", color = p.text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, lineHeight = 32.sp)
                    Text(listOfNotNull(media.artist, media.album).joinToString(" · ").ifEmpty { "（App 未提供歌手）" }, color = p.textSoft, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Hint("歌詞資料尚未連接", size = 13)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        if (media.hasSession) {
            Progress(media, actions)
        } else {
            Hint(
                if (media.permissionGranted) "開啟 KKBOX 並開始播放後，下方控制鍵會自動啟用。"
                else "開啟通知存取後，下方控制鍵才能連接 KKBOX。",
                size = 12,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            ControlButton(Glyph.PREV, "上一首", media.canSkipPrevious, actions.previous)
            ControlButton(
                if (media.isPlaying) Glyph.PAUSE else Glyph.PLAY,
                if (media.isPlaying) "暫停" else "播放",
                media.canPlayPause,
                actions.playPause,
                emphasized = true,
            )
            ControlButton(Glyph.NEXT, "下一首", media.canSkipNext, actions.next)
        }
    }
}

@Composable
private fun ControlButton(glyph: Glyph, label: String, enabled: Boolean, onClick: () -> Unit, emphasized: Boolean = false) {
    val p = LocalPalette.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(if (emphasized) 68.dp else 60.dp)
                .clip(CircleShape)
                .background(if (emphasized && enabled) p.accent else p.surfaceStrong)
                .border(1.dp, if (enabled) p.line else p.surfaceStrong, CircleShape),
        ) {
            GlyphIcon(
                glyph,
                size = if (emphasized) 30.dp else 28.dp,
                tint = when {
                    !enabled -> p.line
                    emphasized && p.isNight -> p.bgDeep
                    emphasized -> androidx.compose.ui.graphics.Color.White
                    else -> p.text
                },
            )
        }
        Text(label, color = if (enabled) p.textSoft else p.line, fontSize = 12.sp)
    }
}

@Composable
private fun Progress(media: MediaUiState, actions: HomeActions) {
    val p = LocalPalette.current
    var position by remember { mutableLongStateOf(media.livePosition()) }
    LaunchedEffect(media) {
        while (true) { position = media.livePosition(); delay(500) }
    }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val duration = media.durationMs
    Column(Modifier.fillMaxWidth()) {
        if (media.canSeek) {
            Slider(
                value = if (dragging) dragValue else (position.toFloat() / duration).coerceIn(0f, 1f),
                onValueChange = { dragging = true; dragValue = it },
                onValueChangeFinished = { actions.seekTo((dragValue * duration).toLong()); dragging = false },
                colors = SliderDefaults.colors(thumbColor = p.accent, activeTrackColor = p.accent, inactiveTrackColor = p.surfaceMuted),
            )
        } else if (duration > 0) {
            LinearProgressIndicator(
                progress = { (position.toFloat() / duration).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp).height(6.dp).clip(CircleShape),
                color = p.accent, trackColor = p.surfaceMuted,
            )
        }
        if (duration > 0) {
            Row(Modifier.fillMaxWidth()) {
                Hint(formatMs(position), size = 13)
                Spacer(Modifier.weight(1f))
                Hint(if (media.canSeek) formatMs(duration) else "${formatMs(duration)}（不支援拖曳）", size = 13)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ShortcutsCard(slots: List<SlotView>, actions: HomeActions, modifier: Modifier) {
    val p = LocalPalette.current
    val pages = ShortcutSlots.COUNT / ShortcutSlots.PER_PAGE
    val pager = rememberPagerState { pages }
    FitCard(modifier, padding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
        CardTitle("捷徑") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(pages) { i ->
                    Box(Modifier.size(9.dp).clip(CircleShape).background(if (pager.currentPage == i) p.accent else p.line))
                }
            }
            Hint("長按可更換", size = 12)
        }
        Spacer(Modifier.height(8.dp))
        HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (row in 0 until 2) {
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (col in 0 until 2) {
                            val index = page * ShortcutSlots.PER_PAGE + row * 2 + col
                            ShortcutTile(slots.getOrNull(index) ?: SlotView(null, null), index, actions, Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShortcutTile(slot: SlotView, index: Int, actions: HomeActions, modifier: Modifier) {
    val p = LocalPalette.current
    Column(
        modifier.clip(RadiusMd).background(p.surfaceStrong).border(1.dp, p.line, RadiusMd)
            .combinedClickableCompat(
                onClick = {
                    when {
                        slot.isEmpty || slot.isMissing -> actions.pickSlot(index)
                        else -> actions.launchSlot(index)
                    }
                },
                onLongClick = { if (!slot.isEmpty) actions.editSlot(index) },
            )
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when {
            slot.isEmpty -> {
                GlyphIcon(Glyph.PLUS, size = 34.dp, tint = p.textSoft)
                Hint("新增", size = 13)
            }
            slot.isMissing -> {
                Text("App 已移除", color = p.danger, fontSize = 14.sp)
                Hint("點擊重新選擇", size = 12)
            }
            else -> {
                AppIcon(slot.entry?.icon, 52.dp)
                Spacer(Modifier.height(6.dp))
                Text(slot.entry?.label ?: "", color = p.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** 統一的點擊／長按修飾子。 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.combinedClickableCompat(onClick: () -> Unit, onLongClick: (() -> Unit)? = null): Modifier =
    this.combinedClickable(onClick = onClick, onLongClick = onLongClick)
