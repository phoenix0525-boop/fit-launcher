package tw.fitcar.launcher.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.collectAsState
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
    val voice: (() -> Unit)? = null,
    val openGoogleNav: (() -> Unit)? = null,
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
    mapContent: @Composable (GpsState, Modifier) -> Unit = { fix, modifier -> FreeMapPanel(fix, modifier) },
) {
    val p = LocalPalette.current
    BoxWithConstraints(Modifier.fillMaxSize().background(p.bg)) {
        val spec = remember(maxWidth, maxHeight) { AdaptiveLayout.of(maxWidth.value, maxHeight.value) }
        val gap = spec.gapDp.dp
        CompositionLocalProvider(LocalLayoutSpec provides spec) {
            Column(Modifier.fillMaxSize().padding(spec.outerDp.dp), verticalArrangement = Arrangement.spacedBy(gap)) {
                StatusBar(shield, gps, actions)
                when (spec.arrangement) {
                    HomeArrangement.TWO_COLUMN -> Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        NavPanel(nav, gps, actions, mapContent, Modifier.weight(spec.mapWeight).fillMaxHeight())
                        Column(Modifier.weight(spec.sideWeight).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                            MusicCard(music, media, actions, Modifier.fillMaxWidth().weight(1.5f))
                            ShortcutsCard(slots, actions, Modifier.fillMaxWidth().weight(1f))
                        }
                    }
                    // 超寬螢幕：三欄，音樂與捷徑各自有完整高度
                    HomeArrangement.THREE_COLUMN -> Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        NavPanel(nav, gps, actions, mapContent, Modifier.weight(spec.mapWeight).fillMaxHeight())
                        MusicCard(music, media, actions, Modifier.weight(spec.sideWeight).fillMaxHeight())
                        ShortcutsCard(slots, actions, Modifier.weight(spec.sideWeight * 0.8f).fillMaxHeight())
                    }
                    // 直向／正方形：地圖在上，音樂與捷徑並排在下
                    HomeArrangement.STACKED -> {
                        NavPanel(nav, gps, actions, mapContent, Modifier.fillMaxWidth().weight(spec.mapWeight))
                        Row(Modifier.fillMaxWidth().weight(spec.sideWeight), horizontalArrangement = Arrangement.spacedBy(gap)) {
                            MusicCard(music, media, actions, Modifier.weight(1f).fillMaxHeight())
                            ShortcutsCard(slots, actions, Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Clock(modifier: Modifier = Modifier, big: Boolean = true) {
    val p = LocalPalette.current
    // 只顯示到分鐘：對齊整分更新，不必每秒重繪狀態列
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000L - now % 60_000L + 50L)
        }
    }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.TAIWAN) }
    val dateFormat = remember { SimpleDateFormat("M月d日 EEEE", Locale.TAIWAN) }
    val date = Date(now)
    val large = big && !LocalLayoutSpec.current.compactChrome
    Column(modifier) {
        Text(timeFormat.format(date), color = p.text, fontSize = if (large) 40.sp else 30.sp, fontWeight = FontWeight.Medium, lineHeight = if (large) 44.sp else 34.sp, maxLines = 1)
        Text(dateFormat.format(date), color = p.textSoft, fontSize = if (large) 15.sp else 13.sp, maxLines = 1)
    }
}

@Composable
internal fun StatusBar(shield: AppEntry?, gps: GpsState, actions: HomeActions) {
    val p = LocalPalette.current
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) { delay(1000); tick++ } }
    val spec = LocalLayoutSpec.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = if (spec.compactChrome) 76.dp else 104.dp).clip(RadiusLg).background(p.surface).border(1.dp, p.line, RadiusLg)
            .padding(horizontal = if (spec.compactChrome) 12.dp else 18.dp, vertical = if (spec.compactChrome) 6.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (spec.compactChrome) 8.dp else 12.dp),
    ) {
        Clock(if (spec.compactChrome) Modifier.widthIn(min = 120.dp) else Modifier.width(240.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(if (spec.compactChrome) 8.dp else 12.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            if (spec.showLocationCard) CurrentLocationCard(gps, Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
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
        StatusIndicatorRow()
        actions.voice?.let { SecondaryButton("語音", it) }
        SecondaryButton("所有 App", actions.openAllApps, icon = { GlyphIcon(Glyph.GRID, size = 22.dp) })
        SecondaryButton("設定", actions.openSettings, icon = { GlyphIcon(Glyph.GEAR, size = 22.dp) })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NavPanel(nav: AppEntry?, gps: GpsState, actions: HomeActions, mapContent: @Composable (GpsState, Modifier) -> Unit, modifier: Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    FitCard(modifier, padding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
        CardTitle("即時地圖") { Pill("OpenFreeMap") }
        if (nav?.component?.packageName == tw.fitcar.launcher.nav.GoogleNavigation.PACKAGE) {
            val guidance by tw.fitcar.launcher.nav.GoogleGuidanceRepository.state.collectAsState()
            val mediaState by tw.fitcar.launcher.media.MediaRepository.state.collectAsState()
            var now by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }
            LaunchedEffect(Unit) { while (true) { now = android.os.SystemClock.elapsedRealtime(); delay(1000) } }
            GoogleGuidanceCard(guidance, mediaState.permissionGranted, now, actions.grantNotificationAccess)
        }
        Spacer(Modifier.height(10.dp))
        mapContent(gps, Modifier.weight(1f).fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("Google 導航", actions.openGoogleNav ?: { openGoogleNavigation(context) })
            PrimaryButton(if (nav == null) "選擇導航 App" else "開啟導航", if (nav == null) actions.bindNav else actions.openNavFullscreen)
            SecondaryButton("更換", actions.bindNav)
        }
        Hint("地圖顯示目前位置；開始導航會全螢幕開啟選定 App。", size = 13)
    }
}

/** Wi-Fi／行動網路／藍牙／USB／電池（有電池時）小標籤，參考 AGAMA 狀態列。 */
@Composable
private fun StatusIndicatorRow() {
    val p = LocalPalette.current
    val status by tw.fitcar.launcher.system.SystemStatusMonitor.state.collectAsState()
    val items = remember(status) { tw.fitcar.launcher.system.StatusIndicators.build(status) }
    val compact = LocalLayoutSpec.current.compactChrome
    // 精簡版只留網路與藍牙，避免擠掉主要按鈕
    val shown = if (compact) items.filter { it.kind == tw.fitcar.launcher.system.IndicatorKind.NETWORK || it.kind == tw.fitcar.launcher.system.IndicatorKind.BLUETOOTH } else items
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.End) {
        shown.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { item ->
                    Text(
                        item.label,
                        Modifier.clip(RadiusSm).background(if (item.active) p.accentSoft else p.surfaceMuted).padding(horizontal = 8.dp, vertical = 3.dp),
                        color = when { item.warning -> p.danger; item.active -> p.accentStrong; else -> p.textSoft },
                        fontSize = 12.sp, maxLines = 1,
                    )
                }
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
internal fun MusicCard(music: AppEntry?, media: MediaUiState, actions: HomeActions, modifier: Modifier) {
    val p = LocalPalette.current
    FitCard(modifier, padding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
        CardTitle(media.playerName ?: music?.label ?: "音樂") {
            if (music != null || media.hasSession) SecondaryButton("開啟", actions.openMusicApp)
            else SecondaryButton("選擇音樂 App", actions.bindMusic)
        }
        Spacer(Modifier.height(8.dp))
        // 主要區：原本規劃為歌詞；目前沒有合法歌詞來源，依決策改顯示曲目並註明
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
        val spacious = maxHeight >= 240.dp && !LocalLayoutSpec.current.shortHeight
        Column(
            Modifier.fillMaxSize().clip(RadiusMd).background(p.surfaceStrong).border(1.dp, p.line, RadiusMd).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                !media.permissionGranted -> {
                    GlyphIcon(Glyph.MUSIC, size = 32.dp, tint = p.textSoft)
                    Text("尚未授權讀取播放資訊", color = p.text, fontSize = 20.sp, textAlign = TextAlign.Center)
                    Hint("請開啟 FIT 桌面的「通知存取」，以顯示歌曲與播放控制。", size = 14)
                    PrimaryButton("前往授權", actions.grantNotificationAccess)
                }
                !media.hasSession -> {
                    GlyphIcon(Glyph.MUSIC, size = 32.dp, tint = p.textSoft)
                    Text("尚未播放", color = p.text, fontSize = 22.sp)
                    Hint("開啟選定播放器播放後，這裡會顯示歌曲資訊。", size = 14)
                }
                else -> {
                    val art = media.art
                    if (art != null && spacious) {
                        val img = remember(art) { art.asImageBitmap() }
                        Image(img, null, Modifier.size(88.dp).clip(RadiusMd), contentScale = ContentScale.Crop)
                    }
                    Text(media.title ?: "（App 未提供歌名）", color = p.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = if (spacious) 2 else 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, lineHeight = 29.sp, modifier = Modifier.fillMaxWidth())
                    Text(listOfNotNull(media.artist, if (spacious) media.album else null).joinToString(" · ").ifEmpty { "（App 未提供歌手）" }, color = p.textSoft, fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (spacious) Hint("歌詞功能尚未提供", size = 13)
                }
            }
        }
        }
        if (media.hasSession) {
            Spacer(Modifier.height(8.dp))
            Progress(media, actions)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                val prev = media.supports(android.media.session.PlaybackState.ACTION_SKIP_TO_PREVIOUS)
                val next = media.supports(android.media.session.PlaybackState.ACTION_SKIP_TO_NEXT)
                ControlButton(Glyph.PREV, prev, actions.previous)
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(p.accent).combinedClickableCompat(onClick = actions.playPause),
                    contentAlignment = Alignment.Center,
                ) {
                    GlyphIcon(if (media.isPlaying) Glyph.PAUSE else Glyph.PLAY, size = 30.dp, tint = if (p.isNight) p.bgDeep else androidx.compose.ui.graphics.Color.White)
                }
                ControlButton(Glyph.NEXT, next, actions.next)
            }
        }
    }
}

@Composable
private fun ControlButton(glyph: Glyph, enabled: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(56.dp)) {
        GlyphIcon(glyph, size = 30.dp, tint = if (enabled) p.text else p.line)
    }
}

@Composable
private fun Progress(media: MediaUiState, actions: HomeActions) {
    val p = LocalPalette.current
    var position by remember { mutableLongStateOf(media.livePosition()) }
    LaunchedEffect(media) {
        // 只顯示到秒：播放中對齊下一秒更新，暫停時每秒檢查一次即可
        while (true) {
            position = media.livePosition()
            delay(if (media.isPlaying) (1000L - position % 1000L).coerceAtLeast(200L) else 1000L)
        }
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
    BoxWithConstraints(modifier) {
    val compact = maxHeight < 110.dp
    Column(
        Modifier.fillMaxSize().clip(RadiusMd).background(p.surfaceStrong).border(1.dp, p.line, RadiusMd)
            .combinedClickableCompat(
                onClick = {
                    when {
                        slot.isEmpty || slot.isMissing -> actions.pickSlot(index)
                        else -> actions.launchSlot(index)
                    }
                },
                onLongClick = { if (!slot.isEmpty) actions.editSlot(index) },
            )
            .padding(6.dp),
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
                AppIcon(slot.entry?.icon, if (compact) 32.dp else 48.dp)
                Spacer(Modifier.height(4.dp))
                Text(slot.entry?.label ?: "", color = p.text, fontSize = 15.sp, maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
}

/** 統一的點擊／長按修飾子。 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.combinedClickableCompat(onClick: () -> Unit, onLongClick: (() -> Unit)? = null): Modifier =
    this.combinedClickable(onClick = onClick, onLongClick = onLongClick)
