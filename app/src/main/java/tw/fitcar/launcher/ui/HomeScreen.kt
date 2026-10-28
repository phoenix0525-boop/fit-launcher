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
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
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
    val goHome: (() -> Unit)? = null,
    val goWork: (() -> Unit)? = null,
    /** 音樂卡快速切換：開啟指定套件的播放器。 */
    val openPlayer: ((String) -> Unit)? = null,
    /** 狀態列「目前位置」在未授權時可直接請求定位。 */
    val requestLocation: (() -> Unit)? = null,
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
    /** 最近播放器（套件, 名稱），固定模式時傳空清單。 */
    recentPlayers: List<Pair<String, String>> = emptyList(),
    hasHome: Boolean = false,
    hasWork: Boolean = false,
    speedAlertKmh: Int = 0,
    /** Test seam for deterministic lyric layout; production uses DesktopLyrics. */
    lyricsContent: (@Composable (MediaUiState) -> Unit)? = null,
    wallpaperOverride: tw.fitcar.launcher.wallpaper.WallpaperState? = null,
    googleGuidanceOverride: tw.fitcar.launcher.nav.GoogleGuidanceState? = null,
    googleGuidanceNow: Long? = null,
) {
    val p = LocalPalette.current
    val driving = rememberDriving(gps)
    val context = androidx.compose.ui.platform.LocalContext.current
    val wallpaperStore = remember(context) { tw.fitcar.launcher.wallpaper.WallpaperRepository.get(context) }
    val wallpaperState by wallpaperStore.state.collectAsState()
    val wallpaper = wallpaperOverride ?: wallpaperState
    val mapPrefs = context.getSharedPreferences("map_preferences", android.content.Context.MODE_PRIVATE)
    val backupRevision by tw.fitcar.launcher.data.BackupStore.revision.collectAsState()
    val mapControls = remember(backupRevision) { MapPanelControls(mapPrefs.getBoolean("auto_zoom",true)) { mapPrefs.edit().putBoolean("auto_zoom",it).apply() } }
    LaunchedEffect(wallpaperStore) { if (wallpaperOverride == null) wallpaperStore.load() }
    BoxWithConstraints(Modifier.fillMaxSize().background(p.bg)) {
        val spec = remember(maxWidth, maxHeight) { AdaptiveLayout.of(maxWidth.value, maxHeight.value) }
        val gap = spec.gapDp.dp
        ThemeBackdrop(ThemePage.HOME, visible = !wallpaper.visible)
        WallpaperLayer(wallpaper, p.isNight, Modifier.matchParentSize())
        val contentPalette = if (wallpaper.visible && !wallpaper.options.showArt) p.copy(characterArt = false) else p
        CompositionLocalProvider(LocalLayoutSpec provides spec, LocalDriving provides driving,
            LocalMapControls provides mapControls, LocalWallpaperActive provides wallpaper.visible, LocalFullThemeActive provides (p.characterArt && LocalThemeAppearance.current.fullTheme), LocalPalette provides contentPalette) {
            Column(Modifier.fillMaxSize().padding(spec.outerDp.dp), verticalArrangement = Arrangement.spacedBy(gap)) {
                StatusBar(shield, gps, actions, speedAlertKmh)
                when (spec.arrangement) {
                    HomeArrangement.TWO_COLUMN -> Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        NavPanel(nav, gps, actions, mapContent, hasHome, hasWork, Modifier.weight(spec.mapWeight).fillMaxHeight(), media.permissionGranted, googleGuidanceOverride, googleGuidanceNow)
                        Column(Modifier.weight(spec.sideWeight).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                            MusicCard(music, media, actions, recentPlayers, Modifier.fillMaxWidth().weight(if (spec.shortHeight) 1.5f else 2.2f), lyricsContent)
                            ShortcutsCard(slots, actions, Modifier.fillMaxWidth().weight(1f))
                        }
                    }
                    // 超寬螢幕：三欄，音樂與捷徑各自有完整高度
                    HomeArrangement.THREE_COLUMN -> Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        NavPanel(nav, gps, actions, mapContent, hasHome, hasWork, Modifier.weight(spec.mapWeight).fillMaxHeight(), media.permissionGranted, googleGuidanceOverride, googleGuidanceNow)
                        MusicCard(music, media, actions, recentPlayers, Modifier.weight(spec.sideWeight).fillMaxHeight(), lyricsContent)
                        ShortcutsCard(slots, actions, Modifier.weight(spec.sideWeight * 0.8f).fillMaxHeight())
                    }
                    // 直向／正方形：地圖在上，音樂與捷徑並排在下
                    HomeArrangement.STACKED -> {
                        NavPanel(nav, gps, actions, mapContent, hasHome, hasWork, Modifier.fillMaxWidth().weight(spec.mapWeight), media.permissionGranted, googleGuidanceOverride, googleGuidanceNow)
                        Row(Modifier.fillMaxWidth().weight(spec.sideWeight), horizontalArrangement = Arrangement.spacedBy(gap)) {
                            MusicCard(music, media, actions, recentPlayers, Modifier.weight(1f).fillMaxHeight(), lyricsContent)
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
    val hasArt = p.characterArt && large
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(if (hasArt) Modifier.weight(1f) else Modifier) {
            Text(timeFormat.format(date), color = p.text, fontSize = if (large) 40.sp else 30.sp, fontWeight = FontWeight.Medium, lineHeight = if (large) 44.sp else 34.sp, maxLines = 1)
            Text(dateFormat.format(date), color = p.textSoft, fontSize = if (large) 15.sp else 13.sp, maxLines = 1)
        }
        if (hasArt && LocalThemeAppearance.current.smallArt) ThemeArtwork(64.dp, ThemeArtPose.CLOCK)
    }
}

@Composable
internal fun StatusBar(shield: AppEntry?, gps: GpsState, actions: HomeActions, speedAlertKmh: Int = 0) {
    val p = LocalPalette.current
    val spec = LocalLayoutSpec.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = if (spec.compactChrome) 76.dp else 104.dp).clip(RadiusLg).background(p.surface).border(1.dp, p.line, RadiusLg)
            .padding(horizontal = if (spec.compactChrome) 12.dp else 18.dp, vertical = if (spec.compactChrome) 6.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (spec.compactChrome) 8.dp else 12.dp),
    ) {
        // 時鐘下方放狀態小標籤，不另外佔用狀態列寬度
        Column(if (spec.compactChrome) Modifier.widthIn(min = 120.dp) else Modifier.width(264.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Clock()
            // 行車中隱藏狀態小標籤（離線、藍牙等），減少一瞥要看的東西
            if (!LocalDriving.current) StatusIndicatorRow()
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(if (spec.compactChrome) 8.dp else 12.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            if (spec.showLocationCard) CurrentLocationCard(gps, Modifier.weight(1f), actions.requestLocation) else Spacer(Modifier.weight(1f))
            SpeedChip(gps, speedAlertKmh)
        }
        WeatherChip()
        actions.voice?.let { SecondaryButton("語音", it) }
        SecondaryButton("所有 App", actions.openAllApps, icon = { GlyphIcon(Glyph.GRID, size = 22.dp) })
        SecondaryButton("設定", actions.openSettings, icon = { GlyphIcon(Glyph.GEAR, size = 22.dp) })
    }
}

/**
 * GPS 車速（啟動器自行計算，明確標示來源）。自己的 1 秒計時只重算這個小區塊，不再讓整個狀態列每秒重組。
 * 行車中數字放大、只留單位；超過自訂門檻時底色變警示色。
 */
@Composable
private fun SpeedChip(gps: GpsState, alertKmh: Int) {
    val p = LocalPalette.current
    val driving = LocalDriving.current
    val compact = LocalLayoutSpec.current.compactChrome
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) { delay(1000); tick++ } }
    val fresh = remember(tick, gps) { gps.isFresh() }
    var alert by remember { mutableStateOf(false) }
    LaunchedEffect(gps, fresh, alertKmh) { alert = tw.fitcar.launcher.system.SpeedAlert.next(alert, gps.speedKmh, fresh, alertKmh) }
    // 超速改用橘色警示，與品牌紅（主要按鈕、選取）區分
    val bg = if (alert) WarningOrange else p.surfaceStrong
    val fg = if (alert) contrastInk(WarningOrange) else p.text
    Row(
        Modifier.clip(RadiusMd).background(bg).border(1.dp, p.line, RadiusMd).padding(horizontal = 14.dp, vertical = 6.dp)
            .semantics { contentDescription = if (alert) "GPS 車速超過 $alertKmh 公里" else "GPS 車速" },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            if (gps.permission && fresh) "%.0f".format(gps.speedKmh ?: 0f) else "—",
            color = fg, fontSize = if (driving) 44.sp else if (compact) 28.sp else 34.sp, fontWeight = FontWeight.Medium,
            lineHeight = if (driving) 48.sp else if (compact) 32.sp else 38.sp, maxLines = 1,
        )
        CompassBadge(gps, fresh)
        Column {
            Text(if (driving) "km/h" else "GPS km/h", color = fg, fontSize = if (driving) 20.sp else if (compact) 13.sp else 15.sp, maxLines = 1)
            if (!driving) Text(
                when {
                    !gps.permission -> "未授權定位"
                    !gps.providerEnabled -> "GPS 已關閉"
                    fresh -> gps.accuracyM?.let { "精度 ±%.0f m".format(it) } ?: "已定位"
                    else -> "GPS 尚未定位"
                },
                color = if (alert) fg else p.textSoft, fontSize = TypeScale.NOTE.sp, maxLines = 1,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NavPanel(nav: AppEntry?, gps: GpsState, actions: HomeActions, mapContent: @Composable (GpsState, Modifier) -> Unit, hasHome: Boolean, hasWork: Boolean, modifier: Modifier, notificationAccess: Boolean, guidanceOverride: tw.fitcar.launcher.nav.GoogleGuidanceState?, guidanceNow: Long?) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val googleSelected = nav?.component?.packageName == tw.fitcar.launcher.nav.GoogleNavigation.PACKAGE
    CompositionLocalProvider(LocalFullThemeActive provides false, LocalWallpaperActive provides false) {
    FitCard(modifier, padding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
        if (googleSelected) {
            val guidance by tw.fitcar.launcher.nav.GoogleGuidanceRepository.state.collectAsState()
            var now by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }
            LaunchedEffect(Unit) { while (true) { now = android.os.SystemClock.elapsedRealtime(); delay(1000) } }
            GoogleGuidanceCard(guidanceOverride ?: guidance, notificationAccess, guidanceNow ?: now,
                actions.grantNotificationAccess, Modifier.weight(1f).fillMaxWidth())
        } else Box(Modifier.weight(1f).fillMaxWidth()) {
            mapContent(gps, Modifier.fillMaxSize())
            // 縮放與回到定位改成浮在地圖右下角，不再佔用下方工具列與地圖高度
            MapFloatingControls(Modifier.align(Alignment.BottomEnd).padding(10.dp))
        }
        Spacer(Modifier.height(10.dp))
        // 下方只留最常用的動作；其餘收進「更多」，避免換成兩排吃掉地圖高度
        Row(Modifier.fillMaxWidth().semantics { contentDescription = "地圖工具列" }, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PrimaryButton(if (nav == null) "選擇導航 App" else "開啟導航", if (nav == null) actions.bindNav else actions.openNavFullscreen)
            // 回家／去公司：設定好地址才顯示（自 0.3.2 線移植）；小螢幕放進「更多」避免擠壓
            val inline = !LocalLayoutSpec.current.compactChrome
            val home = actions.goHome.takeIf { hasHome }
            val work = actions.goWork.takeIf { hasWork }
            if (inline) home?.let { SecondaryButton("回家", it, icon = { GlyphIcon(Glyph.HOME, size = 22.dp) }) }
            if (inline) work?.let { SecondaryButton("去公司", it) }
            Spacer(Modifier.weight(1f))
            UpdateIndicator()
            MoreToolsButton(actions.openGoogleNav ?: { openGoogleNavigation(context) }, if (inline) null else home, if (inline) null else work)
        }
    }
}

}

/** 地圖右下角的浮動控制：行車中只留「回到目前位置」，停車時才可切換縮放模式。 */
@Composable
private fun MapFloatingControls(modifier: Modifier) {
    val controls = LocalMapControls.current
    val driving = LocalDriving.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!driving) MapPill(if (controls.autoZoom) "自動縮放" else "手動縮放") { controls.toggleZoom() }
        MapPill("回到目前位置") { controls.returnToPosition() }
    }
}

@Composable
private fun MapPill(text: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Text(text,
        Modifier.heightIn(min = 56.dp).clip(RadiusMd).background(p.surfaceStrong.copy(alpha = .94f)).border(1.dp, p.line, RadiusMd)
            .combinedClickableCompat(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        color = p.text, fontSize = if (LocalDriving.current) DriveText.BUTTON.sp else 17.sp, maxLines = 1)
}

/** 不常用的工具集中在「更多」：Google 導航、系統分割、桌面調暗。 */
@Composable
private fun MoreToolsButton(openGoogle: () -> Unit, goHome: (() -> Unit)? = null, goWork: (() -> Unit)? = null) {
    var open by remember { mutableStateOf(false) }
    var arrange by remember { mutableStateOf(false) }
    val p = LocalPalette.current
    SecondaryButton("更多", { open = true })
    if (arrange) SplitArrangementPrefsDialog { arrange = false }
    if (open) androidx.compose.material3.AlertDialog(
        onDismissRequest = { open = false }, containerColor = p.surface,
        title = { Text("更多工具", color = p.text, fontSize = 22.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                goHome?.let { go -> SecondaryButton("回家", { open = false; go() }, Modifier.fillMaxWidth()) }
                goWork?.let { go -> SecondaryButton("去公司", { open = false; go() }, Modifier.fillMaxWidth()) }
                SecondaryButton("Google 導航", { open = false; openGoogle() }, Modifier.fillMaxWidth())
                SystemSplitShortcut()
                // 先關閉「更多」再開排列指引，不讓對話框一層疊一層
                SecondaryButton("排列", { open = false; arrange = true }, Modifier.fillMaxWidth())
                DesktopDimShortcut()
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { open = false }) { Text("關閉", color = p.accent, fontSize = 18.sp) } },
    )
}

/** 天氣（中央氣象署最近測站），點擊看 5 天紀錄；未啟用時不顯示。 */
@Composable
private fun WeatherChip() {
    val p = LocalPalette.current
    val w by tw.fitcar.launcher.weather.WeatherRepository.state.collectAsState()
    if (!w.enabled) return
    var open by remember { mutableStateOf(false) }
    val cur = w.current?.station
    val compact = LocalLayoutSpec.current.compactChrome
    Column(
        Modifier.clip(RadiusMd).background(p.surfaceStrong).border(1.dp, p.line, RadiusMd)
            .combinedClickableCompat(onClick = { open = true }).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            cur?.tempC?.let { "%.0f°".format(it) } ?: "—°",
            color = if (w.error != null && cur == null) p.danger else p.text, fontSize = 24.sp, fontWeight = FontWeight.Medium, maxLines = 1,
        )
        if (!LocalDriving.current) Text(
            when {
                cur == null && w.error != null -> "天氣未更新"
                cur == null -> "讀取中…"
                compact -> cur.weather ?: cur.name
                else -> listOfNotNull(cur.weather, cur.name).joinToString(" · ")
            },
            color = p.textSoft, fontSize = TypeScale.NOTE.sp, maxLines = 1,
        )
    }
    if (open) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { open = false },
            containerColor = p.surface,
            title = { Text("天氣（中央氣象署）", color = p.text, fontSize = 22.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (cur != null) {
                        Text(
                            "${cur.name} 測站" + (w.current?.distanceKm?.let { "（距離約 %.0f 公里）".format(it) } ?: "（未定位，使用桃園測站）"),
                            color = p.text, fontSize = 16.sp,
                        )
                        Hint("觀測時間 ${cur.obsTime.replace('T', ' ').take(16)}", size = 14)
                    }
                    w.error?.let { Text(it, color = p.danger, fontSize = 14.sp) }
                    Text("近 5 天紀錄", color = p.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    if (w.history.isEmpty()) Hint("尚無紀錄", size = 14)
                    w.history.forEach { d ->
                        Text(
                            "${d.date.drop(5)}　" +
                                listOfNotNull(d.lowC?.let { "%.0f".format(it) }, d.highC?.let { "%.0f°".format(it) }).joinToString("～") +
                                (d.weather?.let { "　$it" } ?: "") + "　${d.station}",
                            color = p.text, fontSize = 15.sp,
                        )
                    }
                    Hint("資料來源：中央氣象署開放資料平臺", size = 14)
                }
            },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { open = false }) { Text("關閉", color = p.accent, fontSize = 17.sp) } },
        )
    }
}

/** 指南針：GPS 行進方向箭頭＋方位；停車或低速時顯示「—」。 */
@Composable
private fun CompassBadge(gps: GpsState, fresh: Boolean) {
    val p = LocalPalette.current
    val heading = if (fresh) tw.fitcar.launcher.system.Compass.heading(gps.bearing, gps.speedKmh) else null
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "▲",
            Modifier.rotate(heading ?: 0f),
            color = if (heading != null) p.accent else p.disabledContent,
            fontSize = 20.sp, lineHeight = 22.sp,
        )
        Text(heading?.let { tw.fitcar.launcher.system.Compass.cardinal(it) } ?: "—", color = p.textSoft, fontSize = 14.sp, maxLines = 1)
    }
}

/** Wi-Fi／行動網路／藍牙／USB／電池（有電池時）小標籤，參考 AGAMA 狀態列。 */
@Composable
private fun StatusIndicatorRow() {
    val p = LocalPalette.current
    val status by tw.fitcar.launcher.system.SystemStatusMonitor.state.collectAsState()
    val items = remember(status) { tw.fitcar.launcher.system.StatusIndicators.build(status) }
    val compact = LocalLayoutSpec.current.compactChrome
    // 放在時鐘下方一列：完整版最多 3 個，精簡版只留網路與藍牙
    val shown = if (compact) items.filter { it.kind == tw.fitcar.launcher.system.IndicatorKind.NETWORK || it.kind == tw.fitcar.launcher.system.IndicatorKind.BLUETOOTH }.take(2) else items.take(3)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        shown.forEach { item ->
            Text(
                item.label,
                Modifier.clip(RadiusSm).background(if (item.active) p.accentSoft else p.surfaceMuted).padding(horizontal = 6.dp, vertical = 2.dp),
                color = when { item.warning -> p.danger; item.active -> p.accentStrong; else -> p.textSoft },
                fontSize = TypeScale.NOTE.sp, maxLines = 1,
            )
        }
    }
}

private fun formatMs(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
internal fun MusicCard(music: AppEntry?, media: MediaUiState, actions: HomeActions, recentPlayers: List<Pair<String, String>>, modifier: Modifier, lyricsContent: (@Composable (MediaUiState) -> Unit)? = null, lyricsResultOverride: tw.fitcar.launcher.lyrics.LyricsResult? = null) {
    val p = LocalPalette.current
    val driving = LocalDriving.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val lyricsEnabled by tw.fitcar.launcher.lyrics.LyricsRepository.enabled.collectAsState()
    LaunchedEffect(context) { tw.fitcar.launcher.lyrics.LyricsRepository.initialize(context) }
    val coverFallback by tw.fitcar.launcher.lyrics.LyricsRepository.coverFallback.collectAsState()
    val lyricResult = lyricsResultOverride ?: if (lyricsEnabled && media.hasSession && media.permissionGranted && lyricsContent == null) rememberLyricsResult(media) else null
    val showCover = tw.fitcar.launcher.lyrics.shouldShowLyricsCover(coverFallback, lyricsEnabled,
        media.hasSession && media.permissionGranted, media.art != null, lyricResult)
    FitCard(modifier, padding = androidx.compose.foundation.layout.PaddingValues(14.dp),
        backgroundContent = if (showCover) ({ PlayerCoverBackground(media.art!!) }) else null) {
        // A short two-column pane can leave zero height for weighted metadata.
        // Keep player identity and song in the existing header height on these screens.
        val compactTitle = (LocalLayoutSpec.current.shortHeight || lyricsEnabled) && media.hasSession && media.permissionGranted
        if (compactTitle) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    if (!lyricsEnabled) Text(media.playerName ?: music?.label ?: "音樂", color = p.textSoft, fontSize = TypeScale.NOTE.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(media.title ?: "（App 未提供歌名）", color = p.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (lyricsEnabled) Text(media.artist ?: "（App 未提供歌手）", color = p.textSoft, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (!lyricsEnabled) LyricsEnableButton()
                SecondaryButton("開啟", actions.openMusicApp)
            }
        } else CardTitle(media.playerName ?: music?.label ?: "音樂") {
            if (p.characterArt && !LocalFullThemeActive.current && LocalThemeAppearance.current.smallArt && !LocalLayoutSpec.current.shortHeight) ThemeArtwork(64.dp, ThemeArtPose.MUSIC)
            if (!lyricsEnabled) LyricsEnableButton()
            if (music != null || media.hasSession) SecondaryButton("開啟", actions.openMusicApp)
            else SecondaryButton("選擇音樂 App", actions.bindMusic)
        }
        // 快速切換最近播放器（跟隨模式，至少兩個時才顯示）
        val switchable = recentPlayers.filter { it.first != media.packageName }
        if (recentPlayers.size >= 2 && switchable.isNotEmpty() && !LocalLayoutSpec.current.shortHeight && !lyricsEnabled) actions.openPlayer?.let { open ->
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                switchable.take(2).forEach { (pkg, label) ->
                    Text(
                        label,
                        Modifier.weight(1f, fill = false).heightIn(min = 48.dp).clip(RadiusSm).background(p.surfaceMuted)
                            .combinedClickableCompat(onClick = { open(pkg) }).padding(horizontal = 12.dp, vertical = 12.dp)
                            .semantics { contentDescription = "切換到 $label" },
                        color = p.text, fontSize = if (driving) 20.sp else 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        // Original lyrics area: opt-in independent lookup, with honest metadata fallback.
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
        val spacious = maxHeight >= 240.dp && !LocalLayoutSpec.current.shortHeight
        Column(
            Modifier.fillMaxSize().clip(RadiusMd).background(p.surfaceStrong.copy(alpha = if (showCover || LocalFullThemeActive.current) 0f else 1f)).border(1.dp, p.line, RadiusMd).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = if (lyricsEnabled) 3.dp else 6.dp),
            verticalArrangement = Arrangement.spacedBy(if (lyricsEnabled) 4.dp else 6.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                !media.permissionGranted -> {
                    GlyphIcon(Glyph.MUSIC, size = 32.dp, tint = p.textSoft)
                    Text("尚未授權讀取播放資訊", color = p.text, fontSize = 20.sp, textAlign = TextAlign.Center)
                    Hint("請開啟 車機桌面的「通知存取」，以顯示歌曲與播放控制。", size = 14)
                    PrimaryButton("前往授權", actions.grantNotificationAccess)
                }
                !media.hasSession -> {
                    GlyphIcon(Glyph.MUSIC, size = 32.dp, tint = p.textSoft)
                    Text("播放器待連接", color = p.text, fontSize = 22.sp)
                    Hint("已記住上次播放器；按下方按鈕重新開啟。播放進度由播放器保留。", size = 14)
                    PrimaryButton("連接上次播放器", actions.playPause)
                }
                else -> {
                    val art = media.art
                    if (art != null && spacious && !lyricsEnabled) {
                        val img = remember(art) { art.asImageBitmap() }
                        Image(img, null, Modifier.size(88.dp).clip(RadiusMd), contentScale = ContentScale.Crop)
                    }
                    if (!compactTitle) Text(media.title ?: "（App 未提供歌名）", color = p.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = if (spacious) 2 else 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, lineHeight = 29.sp, modifier = Modifier.fillMaxWidth())
                    if (!lyricsEnabled) Text(listOfNotNull(media.artist, if (spacious && !lyricsEnabled && !driving) media.album else null).joinToString(" · ").ifEmpty { "（App 未提供歌手）" }, color = p.textSoft, fontSize = if (driving) 20.sp else 18.sp, maxLines = if (driving || lyricsEnabled) 1 else 2, overflow = TextOverflow.Ellipsis)
                    if (lyricsEnabled) { if (lyricsContent != null) lyricsContent(media) else LyricsLines(requireNotNull(lyricResult), media) }
                    else if (spacious) Hint("可在設定 → App 綁定啟用桌面歌詞", size = 14)
                }
            }
        }
        }
        if (media.hasSession) {
            Spacer(Modifier.height(8.dp))
            Progress(media, actions, compact = lyricsEnabled)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                val prev = media.supports(android.media.session.PlaybackState.ACTION_SKIP_TO_PREVIOUS)
                val next = media.supports(android.media.session.PlaybackState.ACTION_SKIP_TO_NEXT)
                // 車用觸控目標：一般畫面播放鍵 64 dp、其他 56 dp；高度不足的小螢幕維持 48 dp
                val roomy = !LocalLayoutSpec.current.shortHeight
                val ctl = if (roomy) 56.dp else 48.dp
                ControlButton(Glyph.PREV, prev, actions.previous, ctl)
                if (media.canSeek) SeekButton("−15", -tw.fitcar.launcher.system.SeekStep.STEP_MS, media, actions, ctl)
                Box(
                    Modifier.size(if (roomy) 64.dp else 48.dp).clip(CircleShape).background(p.accent).combinedClickableCompat(onClick = actions.playPause)
                        .semantics { contentDescription = "播放／暫停" },
                    contentAlignment = Alignment.Center,
                ) {
                    GlyphIcon(if (media.isPlaying) Glyph.PAUSE else Glyph.PLAY, size = if (roomy) 32.dp else 24.dp, tint = p.onAccent)
                }
                if (media.canSeek) SeekButton("+15", tw.fitcar.launcher.system.SeekStep.STEP_MS, media, actions, ctl)
                ControlButton(Glyph.NEXT, next, actions.next, ctl)
                VolumeControl(ctl)
            }
        } else VolumeControl()
    }
}

@Composable
private fun ControlButton(glyph: Glyph, enabled: Boolean, onClick: () -> Unit, size: androidx.compose.ui.unit.Dp = 48.dp) {
    val p = LocalPalette.current
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(size).background(p.surface, CircleShape)) {
        GlyphIcon(glyph, size = if (size > 48.dp) 28.dp else 24.dp, tint = if (enabled) p.text else p.disabledContent)
    }
}

/** 倒退／快進 15 秒：行車中比拖曳進度條好按。 */
@Composable
private fun SeekButton(label: String, deltaMs: Long, media: MediaUiState, actions: HomeActions, size: androidx.compose.ui.unit.Dp = 48.dp) {
    val p = LocalPalette.current
    IconButton(
        onClick = { actions.seekTo(tw.fitcar.launcher.system.SeekStep.target(media.livePosition(), deltaMs, media.durationMs)) },
        modifier = Modifier.size(size).semantics { contentDescription = if (deltaMs < 0) "倒退 15 秒" else "快進 15 秒" },
    ) {
        Text(label, color = p.text, fontSize = if (LocalDriving.current) DriveText.BUTTON.sp else 18.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun Progress(media: MediaUiState, actions: HomeActions, compact: Boolean = false) {
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
    var adjust by remember { mutableStateOf(false) }
    val driving = LocalDriving.current
    // 進度條改成單純細條：不再出現 Material 滑桿的直條把手與尾端紅點（容易被看成錯誤標記）。
    // 可拖曳的播放器停車時點一下進度條開「調整播放進度」；行車中用 ±15 秒按鈕。
    val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val adjustable = media.canSeek && !driving
    if (duration > 0) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .combinedClickableCompat(onClick = { if (adjustable) adjust = true })
            .semantics { contentDescription = if (adjustable) "播放進度，點擊調整" else "播放進度" },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!compact) Hint(formatMs(position), size = 14)
            SimpleTrack(fraction, Modifier.weight(1f))
            if (!compact) Hint(if (media.canSeek) formatMs(duration) else "${formatMs(duration)}（不支援拖曳）", size = 14)
        }
    }
    if (adjust) androidx.compose.material3.AlertDialog(
        onDismissRequest = { adjust = false }, title = { Text("調整播放進度") },
        text = { Slider(value = if (dragging) dragValue else (position.toFloat() / duration).coerceIn(0f, 1f),
            onValueChange = { dragging = true; dragValue = it },
            onValueChangeFinished = { actions.seekTo((dragValue * duration).toLong()); dragging = false },
            modifier = Modifier.semantics { contentDescription = "調整播放進度滑桿" }) },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { adjust = false }) { Text("關閉") } })

}

/** 無把手、無尾端停止點的細進度條。 */
@Composable
private fun SimpleTrack(fraction: Float, modifier: Modifier) {
    val p = LocalPalette.current
    Box(modifier.height(6.dp).clip(CircleShape).background(p.surfaceMuted)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).clip(CircleShape).background(p.accent))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ShortcutsCard(slots: List<SlotView>, actions: HomeActions, modifier: Modifier) {
    val p = LocalPalette.current
    val pages = (slots.size / ShortcutSlots.PER_PAGE).coerceIn(1, ShortcutSlots.MAX_PAGES)
    val pager = rememberPagerState { pages }
    FitCard(modifier, padding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
        CardTitle("捷徑") {
            if (p.characterArt && !LocalFullThemeActive.current && LocalThemeAppearance.current.smallArt && !LocalLayoutSpec.current.shortHeight) ThemeArtwork(64.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(pages) { i ->
                    Box(Modifier.size(12.dp).clip(CircleShape).background(if (pager.currentPage == i) p.accent else p.disabledContent))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val oneRow = maxHeight < 106.dp && maxWidth >= 240.dp
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val columns = if (oneRow) 4 else 2
                    val rows = if (oneRow) 1 else 2
                    for (row in 0 until rows) {
                        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            for (col in 0 until columns) {
                                val index = page * ShortcutSlots.PER_PAGE + row * columns + col
                                ShortcutTile(slots.getOrNull(index) ?: SlotView(null, null), index, actions, Modifier.weight(1f).fillMaxHeight())
                            }
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
    val iconSize = (maxHeight - 12.dp).coerceIn(24.dp, 52.dp)
    Column(
        Modifier.fillMaxSize().clip(RadiusMd).background(if (slot.isEmpty) p.surfaceStrong.copy(alpha = .6f) else p.surfaceStrong)
            .then(if (slot.isEmpty) Modifier.dashedBorder(p.disabledContent, 16.dp) else Modifier.border(1.dp, p.line, RadiusMd))
            .semantics { contentDescription = when { slot.isEmpty -> "新增捷徑"; slot.isMissing -> "App 已移除，點擊重新選擇"; else -> slot.entry?.label.orEmpty() } }
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
                // 空格：虛線框＋「＋」，一看就知道可以新增
                GlyphIcon(Glyph.PLUS, size = (iconSize * 0.7f), tint = p.disabledContent)
            }
            slot.isMissing -> {
                GlyphIcon(Glyph.PLUS, size = iconSize, tint = p.danger)
            }
            else -> {
                AppIcon(slot.entry?.icon, iconSize)
            }
        }
    }
}
}

/** 統一的點擊／長按修飾子。 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.combinedClickableCompat(onClick: () -> Unit, onLongClick: (() -> Unit)? = null): Modifier =
    this.combinedClickable(onClick = onClick, onLongClick = onLongClick)

/** 虛線外框（空捷徑格用）。 */
private fun Modifier.dashedBorder(color: androidx.compose.ui.graphics.Color, radius: androidx.compose.ui.unit.Dp): Modifier = this.drawBehind {
    val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx(),
        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())))
    drawRoundRect(color = color, style = stroke, cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius.toPx(), radius.toPx()))
}
