package tw.fitcar.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tw.fitcar.launcher.data.AppEntry
import tw.fitcar.launcher.data.DisplaySettings
import tw.fitcar.launcher.data.LauncherState
import tw.fitcar.launcher.data.NavOpenMode
import tw.fitcar.launcher.data.Period
import tw.fitcar.launcher.data.Schedule
import tw.fitcar.launcher.data.ThemeChoice

enum class BindingTarget(val label: String) { NAVIGATION("導航 App"), MUSIC("音樂 App"), SHIELD("測速 App") }

class SettingsData(
    val state: LauncherState,
    val nav: AppEntry?,
    val music: AppEntry?,
    val shield: AppEntry?,
    val canWriteBrightness: Boolean,
    val currentBrightness: Int?,
    val notificationGranted: Boolean,
    val locationGranted: Boolean,
    val bluetoothGranted: Boolean = true,
    val otherHomes: List<AppEntry>,
    val diagnostics: List<Pair<String, String>>,
    val versionName: String,
)

class SettingsActions(
    val back: () -> Unit,
    val requestBluetooth: (() -> Unit)? = null,
    val setShortcutPages: ((Int) -> Unit)? = null,
    val setAccent: ((AccentTheme) -> Unit)? = null,
    val setStyle: ((VisualStyle) -> Unit)? = null,
    val updateDisplay: ((DisplaySettings) -> DisplaySettings) -> Unit,
    val setManualBrightness: (Int) -> Unit,
    val restoreAuto: () -> Unit,
    val applyNow: () -> Unit,
    val pickTime: (periodIndex: Int, isStart: Boolean) -> Unit,
    val requestWriteSettings: () -> Unit,
    val requestNotificationAccess: () -> Unit,
    val requestLocation: () -> Unit,
    val bind: (BindingTarget) -> Unit,
    val clearBinding: (BindingTarget) -> Unit,
    val setNavMode: (NavOpenMode) -> Unit,
    val setMediaMode: (tw.fitcar.launcher.data.MediaMode) -> Unit,
    val testSplit: () -> Unit,
    val openOtherHome: (AppEntry) -> Unit,
    val openHomeSettings: () -> Unit,
    val openChecker: () -> Unit,
    /** 儲存回家／公司地址（null = 不提供此設定）。 */
    val savePlaces: ((String, String) -> Unit)? = null,
    val setSpeedAlert: ((Int) -> Unit)? = null,
)

private val SECTIONS = listOf("實機檢測", "顯示與亮度", "App 綁定", "導航", "權限", "返回原廠桌面", "實機資訊", "更新", "關於")

@Composable
fun SettingsScreen(data: SettingsData, actions: SettingsActions) {
    val p = LocalPalette.current
    var section by rememberSaveable { mutableStateOf(0) }
    ThemePageFrame(ThemePage.SETTINGS) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("設定", color = p.text, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            PrimaryButton("返回桌面", actions.back, Modifier.heightIn(min = 72.dp), icon = {
                GlyphIcon(Glyph.HOME, size = 26.dp, tint = p.onAccent)
            })
        }
        Spacer(Modifier.height(16.dp))
        SectionNavLayout(SECTIONS, section, { section = it }, footer = {
            if (p.characterArt && LocalThemeAppearance.current.smallArt) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { ThemeArtwork(112.dp, ThemeArtPose.MUSIC) }
        }) {
                when (section) {
                    0 -> CheckerEntrySection(actions)
                    1 -> DisplaySection(data, actions)
                    2 -> BindingSection(data, actions)
                    3 -> NavSection(data, actions)
                    4 -> PermissionSection(data, actions)
                    5 -> FactoryHomeSection(data, actions)
                    6 -> DiagnosticsSection(data)
                    7 -> UpdateSection()
                    else -> AboutSection(data)
                }
            }
    }
    }
}

@Composable
private fun ToggleRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = p.text, fontSize = 17.sp)
            Hint(desc, size = 13)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = p.accent, checkedThumbColor = p.surfaceStrong, uncheckedTrackColor = p.surfaceMuted),
        )
    }
}

@Composable
private fun StatusLine(ok: Boolean, okText: String, badText: String) {
    val p = LocalPalette.current
    Text(if (ok) "● $okText" else "● $badText", color = if (ok) p.success else p.danger, fontSize = 15.sp)
}

@Composable
private fun WeatherSettings() {
    val p = LocalPalette.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val w by tw.fitcar.launcher.weather.WeatherRepository.state.collectAsState()
    var key by remember { mutableStateOf("") }
    Section("天氣（中央氣象署開放資料）") {
        ToggleRow("狀態列顯示天氣", "依 GPS 選最近的氣象署測站，30 分鐘更新一次，保留 5 天紀錄", w.enabled) { v ->
            tw.fitcar.launcher.weather.WeatherRepository.setEnabled(ctx, v)
        }
        Hint("需要自己到 opendata.cwa.gov.tw 註冊會員，在「取得授權碼」複製後貼在下方（免費）。授權碼只存在車機，不寫入任何專案檔案；只向氣象署下載測站資料，不上傳位置。", size = 13)
        StatusLine(w.hasKey, "已填寫授權碼", "尚未填寫授權碼")
        androidx.compose.material3.OutlinedTextField(
            key, { key = it }, Modifier.fillMaxWidth(),
            label = { Text(if (w.hasKey) "更換授權碼" else "授權碼") }, singleLine = true,
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton("儲存授權碼", { tw.fitcar.launcher.weather.WeatherRepository.setKey(ctx, key); key = "" }, enabled = key.isNotBlank())
            SecondaryButton("立即更新", { tw.fitcar.launcher.weather.WeatherRepository.refresh(ctx, force = true) }, enabled = w.enabled && w.hasKey && !w.loading)
        }
        w.error?.let { Text(it, color = p.danger, fontSize = 14.sp) }
        w.current?.station?.let { s -> Hint("目前：${s.name} ${s.tempC?.let { "%.1f°C".format(it) } ?: ""} ${s.weather ?: ""}", size = 14, color = p.text) }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun DisplaySection(data: SettingsData, actions: SettingsActions) {
    val p = LocalPalette.current
    val d = data.state.display
    Section("系統背光權限") {
        StatusLine(data.canWriteBrightness, "已允許修改系統亮度", "尚未允許：亮度排程不會生效")
        Hint("App 主題（日／夜配色）與系統背光是兩件事。背光只透過 Android「修改系統設定」授權調整，不用黑色遮罩冒充。若原廠車燈調光會覆蓋設定，請在實機診斷記錄。", size = 13)
        if (!data.canWriteBrightness) PrimaryButton("前往允許", actions.requestWriteSettings)
        data.currentBrightness?.let { Hint("目前系統亮度約 $it%", size = 14) }
    }
    Section("全版主題與插畫") { ThemeAppearanceSettings() }
    Section("自訂桌布") { WallpaperSettings() }
    actions.setStyle?.let { setStyle ->
        Section("畫面風格") {
            Hint("風格與強調色可分別搭配。布丁狗會先套用焦糖棕，之後仍可自訂；各風格沿用日夜排程。", size = 13)
            val current = VisualStyle.fromId(data.state.visualStyle)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                VisualStyle.entries.forEach { style ->
                    val preview = styledBase(p.isNight, style)
                    Column(
                        Modifier.clip(RadiusMd).background(preview.surface)
                            .border(if (style == current) 2.dp else 1.dp, if (style == current) p.accent else p.line, RadiusMd)
                            .clickable {
                                setStyle(style)
                                if (style == VisualStyle.POMPOMPURIN) actions.setAccent?.invoke(AccentTheme.CARAMEL)
                                if (style == VisualStyle.SPIDERMAN) actions.setAccent?.invoke(AccentTheme.HERO_RED)
                            }.padding(16.dp),
                    ) {
                        if (preview.characterArt) CompositionLocalProvider(LocalPalette provides preview) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                ThemeArtPose.entries.forEach { pose -> ThemeArtwork(56.dp, pose) }
                            }
                        }
                        Text((if (style == current) "✓ " else "") + style.label, color = preview.text, fontSize = 18.sp)
                        Text(style.description, color = preview.textSoft, fontSize = 14.sp)
                    }
                }
            }
        }
    }
    actions.setAccent?.let { setAccent ->
        Section("主題強調色") {
            Hint("按鈕、選取項目與進度條的顏色；日間與夜間會自動調整亮度，文字對比都符合 4.5:1。", size = 13)
            val current = AccentTheme.fromId(data.state.accentTheme)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AccentTheme.entries.forEach { t ->
                    val swatch = androidx.compose.ui.graphics.Color(if (p.isNight) t.night.accent else t.day.accent)
                    Row(
                        Modifier.clip(RadiusMd).background(if (t == current) p.accentSoft else p.surface)
                            .border(if (t == current) 2.dp else 1.dp, if (t == current) p.accent else p.line, RadiusMd)
                            .clickable { setAccent(t) }.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        androidx.compose.foundation.layout.Box(Modifier.size(28.dp).clip(RadiusSm).background(swatch))
                        Text(t.label, color = p.text, fontSize = 17.sp)
                    }
                }
            }
        }
    }
    WeatherSettings()
    Section("自動排程") {
        ToggleRow("亮度自動排程", "依下列時段調整系統背光", d.autoSchedule) { v -> actions.updateDisplay { it.copy(autoSchedule = v) } }
        ToggleRow("日夜主題自動切換", "夜間時段使用夜間配色", d.autoTheme) { v -> actions.updateDisplay { it.copy(autoTheme = v) } }
        if (!d.autoTheme) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ThemeChoice.entries.forEach { t ->
                    val selected = d.manualTheme == t
                    val label = if (t == ThemeChoice.DAY) "日間配色" else "夜間配色"
                    if (selected) PrimaryButton(label, {}) else SecondaryButton(label, { actions.updateDisplay { it.copy(manualTheme = t) } })
                }
            }
        }
        ToggleRow("平滑漸變", "切換亮度時約 1.5 秒漸變", d.smoothTransition) { v -> actions.updateDisplay { it.copy(smoothTransition = v) } }
    }
    Section("時段") {
        d.periods.forEachIndexed { index, period -> PeriodRow(index, period, actions) }
        Hint("時段可跨日（例如 18:30 → 07:00）。區間邊界、開機與系統時間變更時會重新計算。", size = 13)
    }
    Section("手動亮度") {
        val active = Schedule.activePeriod(d, tw.fitcar.launcher.system.BrightnessController.nowMinutes())
        Text(
            if (data.state.manualOverride) "目前：手動 ${d.manualBrightness}%（到下一個時段邊界自動恢復）"
            else if (d.autoSchedule) "目前：自動（${active?.label ?: "—"} ${active?.brightness ?: d.manualBrightness}%）"
            else "目前：排程關閉，使用手動 ${d.manualBrightness}%",
            color = p.text, fontSize = 16.sp,
        )
        var temp by remember(d.manualBrightness) { mutableFloatStateOf(d.manualBrightness.toFloat()) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("${temp.toInt()}%", color = p.text, fontSize = 18.sp, modifier = Modifier.width(80.dp))
            Slider(
                value = temp, onValueChange = { temp = it }, valueRange = 1f..100f,
                onValueChangeFinished = { actions.setManualBrightness(temp.toInt()) },
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(thumbColor = p.accent, activeTrackColor = p.accent, inactiveTrackColor = p.surfaceMuted),
            )
            SecondaryButton("恢復自動", actions.restoreAuto, enabled = data.state.manualOverride || !d.autoSchedule)
        }
        SecondaryButton("立即套用目前排程", actions.applyNow, enabled = data.canWriteBrightness)
    }
}

@Composable
private fun PeriodRow(index: Int, period: Period, actions: SettingsActions) {
    val p = LocalPalette.current
    var temp by remember(period.brightness) { mutableFloatStateOf(period.brightness.toFloat()) }
    val times: @Composable () -> Unit = {
        Text(period.label, color = p.text, fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.width(80.dp))
        SecondaryButton(Schedule.formatMinutes(period.startMinutes), { actions.pickTime(index, true) })
        Text("→", color = p.textSoft, fontSize = 18.sp)
        SecondaryButton(Schedule.formatMinutes(period.endMinutes), { actions.pickTime(index, false) })
    }
    val slider: @Composable (Modifier) -> Unit = { m ->
        Slider(
            value = temp, onValueChange = { temp = it }, valueRange = 1f..100f,
            onValueChangeFinished = {
                val v = temp.toInt()
                actions.updateDisplay { ds -> ds.copy(periods = ds.periods.mapIndexed { i, pr -> if (i == index) pr.copy(brightness = v) else pr }) }
            },
            modifier = m,
            colors = SliderDefaults.colors(thumbColor = p.accent, activeTrackColor = p.accent, inactiveTrackColor = p.surfaceMuted),
        )
    }
    // 窄畫面分兩行，避免亮度滑桿被擠到無法操作
    BoxWithConstraints(Modifier.fillMaxWidth().clip(RadiusMd).background(p.surface).padding(12.dp)) {
        if (maxWidth >= 640.dp) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                times()
                Text("${temp.toInt()}%", color = p.text, fontSize = 17.sp, modifier = Modifier.width(80.dp))
                slider(Modifier.weight(1f))
            }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { times() }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${temp.toInt()}%", color = p.text, fontSize = 17.sp, modifier = Modifier.width(64.dp))
                    slider(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun BindingRow(target: BindingTarget, entry: AppEntry?, key: String?, actions: SettingsActions, note: String) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().clip(RadiusMd).background(p.surface).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppIcon(entry?.icon, 56.dp)
        Column(Modifier.weight(1f)) {
            Text(target.label, color = p.textSoft, fontSize = 14.sp)
            Text(
                when {
                    entry != null -> entry.label
                    key != null -> "已綁定的 App 找不到（可能已移除）"
                    else -> "尚未選擇"
                },
                color = if (key != null && entry == null) p.danger else p.text, fontSize = 18.sp,
            )
            Hint(note, size = 12)
        }
        PrimaryButton(if (key == null) "選擇" else "更換", { actions.bind(target) })
        if (key != null) SecondaryButton("清除", { actions.clearBinding(target) })
    }
}

@Composable
private fun BindingSection(data: SettingsData, actions: SettingsActions) {
    val b = data.state.bindings
    Section("從車機實際安裝的 App 綁定") {
        Hint("請從實際安裝清單選擇。官方 YT Music 與 Morphe Music 視為不同 App。", size = 13)
        BindingRow(BindingTarget.NAVIGATION, data.nav, b.navigation, actions, "左側導航區的開啟對象")
        BindingRow(BindingTarget.MUSIC, data.music, b.music, actions, "固定模式只控制此 App；跟隨模式記住最後實際播放的 App")
        BindingRow(BindingTarget.SHIELD, data.shield, b.shield, actions, "狀態列入口；警示由神盾官方浮動窗顯示")
        tw.fitcar.launcher.data.MediaMode.entries.forEach { mode ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = data.state.mediaMode == mode, onClick = { actions.setMediaMode(mode) })
                Text(if (mode == tw.fitcar.launcher.data.MediaMode.FIXED) "固定播放器" else "跟隨目前播放", color = LocalPalette.current.text, fontSize = 17.sp)
            }
        }
        LyricsSetting()
        Hint("跟隨模式：記住最後實際播放的 App，喚醒或重開桌面時優先連接；其他 App 開始播放時可跟隨切換。若播放器已被系統關閉，可用「連接上次播放器」重新開啟。", size = 13)
    }
    actions.setShortcutPages?.let { setPages ->
        val pages = data.state.shortcutPages
        Section("主畫面捷徑") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton("－", { setPages(pages - 1) }, enabled = pages > tw.fitcar.launcher.data.ShortcutSlots.MIN_PAGES)
                Text("$pages 頁（${pages * tw.fitcar.launcher.data.ShortcutSlots.PER_PAGE} 格）", color = LocalPalette.current.text, fontSize = 18.sp)
                SecondaryButton("＋", { setPages(pages + 1) }, enabled = pages < tw.fitcar.launcher.data.ShortcutSlots.MAX_PAGES)
            }
            Hint("最多 6 頁 24 格，在主畫面左右滑動換頁。減少頁數時，後面的捷徑只是隱藏，不會刪除。長按捷徑可更換、移除或調整順序。", size = 13)
            if (tw.fitcar.launcher.system.ShortcutPagesAdvice.warn(pages))
                Hint("超過 3 頁時行車中要多次滑動，建議把常用 App 放前 2 頁。", color = LocalPalette.current.danger, size = 14)
        }
    }
}

@Composable
private fun NavSection(data: SettingsData, actions: SettingsActions) {
    val p = LocalPalette.current
    actions.savePlaces?.let { save ->
        Section("常用地點（回家／去公司）") {
            Hint("常用地址只存在車機。Google 地圖可直接導航；導航王的住家／公司需另外在導航王內設定，兩邊不會自動同步。不支援時可複製地址開啟導航王，或改用其他地圖。", size = 13)
            var home by remember(data.state.homeAddress) { mutableStateOf(data.state.homeAddress.orEmpty()) }
            var work by remember(data.state.workAddress) { mutableStateOf(data.state.workAddress.orEmpty()) }
            OutlinedTextField(home, { home = it }, Modifier.fillMaxWidth(), label = { Text("住家地址") }, singleLine = true)
            OutlinedTextField(work, { work = it }, Modifier.fillMaxWidth(), label = { Text("公司地址") }, singleLine = true)
            PrimaryButton("儲存", { save(home, work) })
        }
    }
    actions.setSpeedAlert?.let { setAlert ->
        val kmh = data.state.speedAlertKmh
        Section("自訂超速提醒") {
            Hint("只比對 GPS 車速與你自訂的門檻，超過時主畫面車速變警示色。不是測速照相或道路速限資料。", size = 13)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton("－10", { setAlert(if (kmh <= tw.fitcar.launcher.system.SpeedAlert.MIN) 0 else kmh - 10) }, enabled = kmh > 0)
                Text(if (kmh <= 0) "關閉" else "$kmh km/h", color = p.text, fontSize = 18.sp)
                SecondaryButton("＋10", { setAlert(if (kmh <= 0) 100 else kmh + 10) }, enabled = kmh < tw.fitcar.launcher.system.SpeedAlert.MAX)
            }
        }
    }
    Section("本次行程") {
        val trip by tw.fitcar.launcher.system.TripMeter.state.collectAsState()
        Text(tw.fitcar.launcher.system.Trip.format(trip), color = p.text, fontSize = 18.sp)
        Hint("以 GPS 估算行駛里程與時間，只存在記憶體；停車超過 30 分鐘後再出發會自動重新計算。主畫面地圖上方在里程超過 0.1 km 後顯示。", size = 13)
        SecondaryButton("歸零", { tw.fitcar.launcher.system.TripMeter.reset() })
    }
    Section("導航全螢幕／實驗功能") {
        NavOpenMode.entries.forEach { mode ->
            Row(
                Modifier.fillMaxWidth().clip(RadiusMd).clickable { actions.setNavMode(mode) }.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = data.state.navOpenMode == mode, onClick = { actions.setNavMode(mode) }, colors = RadioButtonDefaults.colors(selectedColor = p.accent))
                Column {
                    Text(if (mode == NavOpenMode.SPLIT_FIRST) "實驗功能：嘗試左右視窗" else "一律全螢幕", color = p.text, fontSize = 17.sp)
                    Hint(
                        if (mode == NavOpenMode.SPLIT_FIRST) "目標為左側導航、右側音樂與捷徑；系統決定視窗位置，可能需在系統分割列交換左右。"
                        else "直接以全螢幕開啟導航王，按 Home 回到本桌面。",
                        size = 13,
                    )
                }
            }
        }
    }
    Section("實驗功能：左右視窗實測") {
        Text(
            when (data.state.lastSplitResult) {
                true -> "最近一次：偵測到左右側欄尺寸，仍需目視確認"
                false -> "最近一次：未進入分割畫面（已退回全螢幕）"
                null -> "尚未測試"
            },
            color = p.text, fontSize = 16.sp,
        )
        Hint("需先綁定導航 App。測試會開啟側欄並嘗試把導航王開在旁邊，約 2 秒後記錄結果。", size = 13)
        PrimaryButton("測試分割畫面", actions.testSplit, enabled = data.state.bindings.navigation != null)
    }
}

@Composable
private fun PermissionSection(data: SettingsData, actions: SettingsActions) {
    Section("通知存取（音樂資訊）") {
        StatusLine(data.notificationGranted, "已開啟", "未開啟：無法讀取 KKBOX 播放資訊")
        Hint("用於 Android MediaSession 讀取歌曲與控制播放；選定 Google 地圖時也會讀取導航通知文字，只留在記憶體，不保存或上傳。", size = 13)
        SecondaryButton("開啟系統設定", actions.requestNotificationAccess)
    }
    Section("語音控制") {
        Hint("主畫面點「語音」後，使用車機的 Android 語音辨識。支援開啟導航／音樂／神盾、播放／暫停、切歌及亮度調整。", size = 13)
        Hint("Google 地圖可用「導航到某地」，辨識後先確認目的地。語音服務可能需要網路與麥克風權限；本程式不會持續監聽或保存辨識內容。", size = 13)
    }
    Section("定位（GPS 車速）") {
        StatusLine(data.locationGranted, "已允許", "未允許：狀態列不顯示車速")
        Hint("車速由本桌面以 GPS 計算並標示「GPS 車速」，不是神盾的資料。", size = 13)
        if (!data.locationGranted) SecondaryButton("允許定位", actions.requestLocation)
    }
    Section("附近裝置（藍牙連線狀態）") {
        StatusLine(data.bluetoothGranted, "已允許", "未允許：狀態列只顯示藍牙開關")
        Hint("只用來判斷手機是否已用藍牙連上車機，顯示在狀態列；不掃描、不連線其他裝置。", size = 13)
        if (!data.bluetoothGranted) actions.requestBluetooth?.let { SecondaryButton("允許", it) }
    }
    Section("修改系統設定（背光）") {
        StatusLine(data.canWriteBrightness, "已允許", "未允許：亮度排程不會生效")
        SecondaryButton("開啟系統設定", actions.requestWriteSettings)
    }
}

@Composable
private fun FactoryHomeSection(data: SettingsData, actions: SettingsActions) {
    Section("返回原廠桌面") {
        Hint("下列為車機上的其他桌面。開啟後若要永久改回，請到「預設桌面」設定選擇原廠桌面。", size = 13)
        if (data.otherHomes.isEmpty()) Hint("找不到其他桌面。", size = 15)
        data.otherHomes.forEach { home ->
            SecondaryButton("開啟 ${home.label}", { actions.openOtherHome(home) }, Modifier.fillMaxWidth(), icon = { AppIcon(home.icon, 32.dp) })
        }
        PrimaryButton("變更預設桌面", actions.openHomeSettings)
    }
}

@Composable
private fun CheckerEntrySection(actions: SettingsActions) {
    Section("實機檢測") {
        Hint("自動檢查車機硬體、螢幕、權限、KKBOX、分割畫面、亮度與 GPS，並提供手勢、按鍵、背光等互動測試與手動檢查清單，整理出「可改進的地方」。部分建議可一鍵套用，也可匯出報告交給 AI。", size = 14)
        Hint("檢測不會修改系統設定；只有背光測試會暫時調整亮度 4 秒並自動恢復。", size = 13)
        PrimaryButton("開啟實機檢測", actions.openChecker)
    }
}

@Composable
private fun DiagnosticsSection(data: SettingsData) {
    val p = LocalPalette.current
    Section("實機資訊") {
        data.diagnostics.forEach { (k, v) ->
            Row(Modifier.fillMaxWidth()) {
                Text(k, color = p.textSoft, fontSize = 14.sp, modifier = Modifier.width(220.dp))
                Text(v, color = p.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun AboutSection(data: SettingsData) {
    Section("關於") {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Hint("個人化啟動器", size = 20, color = LocalPalette.current.text)
                Hint("版本 ${data.versionName}", size = 16)
            }
            if (LocalPalette.current.characterArt && LocalThemeAppearance.current.smallArt) ThemeArtwork(120.dp, ThemeArtPose.SHORTCUTS)
        }
        Hint("• 0.4.14：淺色蜘蛛人全版風格、各頁插畫、卡片透明度與夜間中文路名可讀性。", size = 14)
        Hint("• 0.4.13：無歌詞時可用淡化專輯封面作為整張播放器底圖；獨立開關；Google 導航通知取代地圖欄位，免費地圖行進方向朝上。", size = 14)
        Hint("• 0.4.12：自訂本機桌布、夜間圖片暗化與獨立插畫開關；地圖控制整合至下方工具列。", size = 14)
        Hint("• 0.4.11：新增蜘蛛人紅藍日夜主題與四款透明插畫，保留布丁狗與歌詞版面。", size = 14)
        Hint("• 0.4.10：放大布丁狗、增加歌詞預覽空間、精簡控制鈕，加入導航地址接收檢查與替代入口。", size = 14)
        Hint("• 0.4.9：四款布丁狗插畫分別搭配時鐘、播放卡、App清單與捷徑。", size = 14)
        Hint("• 0.4.8：新增布丁狗日夜主題與焦糖棕強調色；保留125%可讀性。", size = 14)
        Hint("改版內容", size = 18, color = LocalPalette.current.text)
        Hint("• 夜間深色地圖；行車中自動大字並隱藏次要說明。", size = 14)
        Hint("• 歌詞大字與全螢幕歌詞；倒退／快進 15 秒。", size = 14)
        Hint("• 回家／去公司、最近播放器切換、自訂超速提醒、本次行程。", size = 14)
        Hint("• 減少畫面重算與耗電，首次開啟更順。", size = 14)
        Hint("• 延續 0.4.6：自動檢查新版、附近門牌、記住播放器、桌面歌詞。", size = 14)
    }
}
