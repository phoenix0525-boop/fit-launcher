package tw.fitcar.launcher

import android.Manifest
import android.app.TimePickerDialog
import android.speech.RecognizerIntent
import tw.fitcar.launcher.voice.VoiceCommand
import tw.fitcar.launcher.voice.VoiceAction
import tw.fitcar.launcher.nav.GoogleNavigation
import tw.fitcar.launcher.media.MediaListenerService
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.net.Uri
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import tw.fitcar.launcher.check.AutoChecks
import tw.fitcar.launcher.check.CheckStore
import tw.fitcar.launcher.check.Fix
import tw.fitcar.launcher.check.FixKind
import tw.fitcar.launcher.check.KeyLog
import tw.fitcar.launcher.data.AppEntry
import tw.fitcar.launcher.data.NavOpenMode
import tw.fitcar.launcher.data.AppCatalog
import tw.fitcar.launcher.data.AppRepository
import tw.fitcar.launcher.data.Prefs
import tw.fitcar.launcher.data.Schedule
import tw.fitcar.launcher.data.ShortcutSlots
import tw.fitcar.launcher.media.MediaRepository
import tw.fitcar.launcher.nav.LaunchResult
import tw.fitcar.launcher.nav.NavLauncher
import tw.fitcar.launcher.system.BrightnessController
import tw.fitcar.launcher.system.GpsSpeed
import tw.fitcar.launcher.system.SystemStatusMonitor
import tw.fitcar.launcher.ui.BindingTarget
import tw.fitcar.launcher.ui.CheckerActions
import tw.fitcar.launcher.ui.CheckerScreen
import tw.fitcar.launcher.ui.FitTheme
import tw.fitcar.launcher.ui.FullScreenAppList
import tw.fitcar.launcher.ui.HomeActions
import tw.fitcar.launcher.ui.HomeScreen
import tw.fitcar.launcher.ui.SettingsActions
import tw.fitcar.launcher.ui.SettingsData
import tw.fitcar.launcher.ui.SettingsScreen
import tw.fitcar.launcher.ui.ShortcutEditDialog
import tw.fitcar.launcher.ui.SlotView
import tw.fitcar.launcher.ui.paletteFor

/** 畫面層級：桌面 → 所有 App／設定／選擇 App（皆為全螢幕）。 */
sealed interface Screen {
    data object Home : Screen
    data object AllApps : Screen
    data object Settings : Screen
    data object Checker : Screen
    data class PickForSlot(val index: Int) : Screen
    data class PickForBinding(val target: BindingTarget, val returnToSettings: Boolean) : Screen
}

class MainActivity : ComponentActivity() {

    private val prefs by lazy { Prefs.get(this) }
    /** 每次 onResume 遞增，讓畫面重新讀取 App 清單與權限狀態。 */
    private var resumeTick by mutableIntStateOf(0)
    private var screen by mutableStateOf<Screen>(Screen.Home)

    private val voiceLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            val command = text?.let(VoiceCommand::parse)
            if (command == null) toast("未辨識到支援的指令，請再試一次。") else executeVoice(command)
        } else toast("語音辨識已取消或未取得結果。")
    }

    private fun startVoice() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-TW")
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "開啟導航、導航到某地、播放音樂、暫停、下一首、開神盾、提高亮度")
        try { voiceLauncher.launch(intent) }
        catch (_: ActivityNotFoundException) { toast("車機未提供語音辨識，請先安裝或啟用支援中文的語音服務。") }
        catch (_: SecurityException) { toast("系統未允許語音辨識，請檢查語音服務的麥克風權限。") }
    }

    private fun selectGoogleNavigation() {
        val app = AppCatalog.apps.value.ifEmpty { AppRepository.launchableApps(this) }
            .firstOrNull { it.component.packageName == GoogleNavigation.PACKAGE }
        if (app == null) { toast("找不到可啟動的 Google 地圖，請先安裝並啟用。 "); return }
        setBinding(BindingTarget.NAVIGATION, app.key)
        handleNav(NavLauncher.open(this, forceFullscreen = true))
    }

    private fun executeVoice(command: VoiceCommand) {
        fun unsupported() = toast("目前播放器沒有提供這項控制，請先播放歌曲並開啟通知存取。")
        when (command.action) {
            VoiceAction.OPEN_NAV -> {
                val label = AppRepository.resolve(this, prefs.state.value.bindings.navigation)?.label.orEmpty()
                if (command.requiredNavigationName != null && !label.contains(command.requiredNavigationName)) {
                    toast("目前選定的導航不是${command.requiredNavigationName}，請先更換導航 App。")
                } else handleNav(NavLauncher.open(this, forceFullscreen = true))
            }
            VoiceAction.OPEN_GOOGLE -> selectGoogleNavigation()
            VoiceAction.OPEN_MUSIC -> {
                val active = MediaRepository.resumePackage(this@MainActivity)?.let { packageManager.getLaunchIntentForPackage(it) }
                if (active != null) safeStart(active, "無法開啟目前播放器")
                else launchKey(prefs.state.value.bindings.music) { screen = Screen.PickForBinding(BindingTarget.MUSIC, false) }
            }
            VoiceAction.OPEN_SHIELD -> launchKey(prefs.state.value.bindings.shield) { screen = Screen.PickForBinding(BindingTarget.SHIELD, false) }
            VoiceAction.PLAY -> if (!MediaRepository.play()) unsupported()
            VoiceAction.PAUSE -> if (!MediaRepository.pause()) unsupported()
            VoiceAction.NEXT -> if (MediaRepository.state.value.supports(android.media.session.PlaybackState.ACTION_SKIP_TO_NEXT)) MediaRepository.next() else unsupported()
            VoiceAction.PREVIOUS -> if (MediaRepository.state.value.supports(android.media.session.PlaybackState.ACTION_SKIP_TO_PREVIOUS)) MediaRepository.previous() else unsupported()
            VoiceAction.BRIGHTER, VoiceAction.DIMMER -> {
                if (!BrightnessController.canWrite(this)) {
                    toast("請先允許修改系統設定，再使用語音調整亮度。")
                    safeStart(BrightnessController.permissionIntent(this), "無法開啟亮度權限設定")
                } else {
                    val step = if (command.action == VoiceAction.BRIGHTER) 10 else -10
                    val value = Schedule.clampBrightness((BrightnessController.currentPercent(this) ?: BrightnessController.targetPercent(this)) + step)
                    if (BrightnessController.apply(this, value, smooth = false)) {
                        prefs.update { it.copy(display = it.display.copy(manualBrightness = value), manualOverride = true) }
                        toast("亮度 $value%，已切換為手動亮度；可在設定恢復排程。")
                    }
                }
            }
            VoiceAction.NAVIGATE_TO -> {
                if (!GoogleNavigation.isSelected(this)) {
                    toast("語音目的地目前支援 Google 地圖，請先在導航選擇 Google 地圖。")
                    return
                }
                val destination = command.destination ?: return
                android.app.AlertDialog.Builder(this).setTitle("確認導航目的地")
                    .setMessage("使用 Google 地圖導航到：$destination？")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("開始導航") { _, _ ->
                        safeStart(GoogleNavigation.destinationIntent(destination), "無法開啟 Google 地圖導航")
                    }.show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BrightnessController.scheduleNext(this)
        setContent { Root() }
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
        AppCatalog.start(this)
        MediaRepository.refresh(this)
        GpsSpeed.start(this)
        SystemStatusMonitor.start(this)
        tw.fitcar.launcher.weather.WeatherRepository.refresh(this)
        CheckStore.load(this)
        MediaListenerService.rescanNavigation()
    }

    override fun onStop() {
        super.onStop()
        GpsSpeed.stop()
        SystemStatusMonitor.stop()
    }

    /** 已在桌面時再按 Home：關閉所有覆蓋畫面回到主畫面。 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) screen = Screen.Home
    }

    /** 實機檢測的按鍵測試：記錄後照常交給系統處理。 */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        KeyLog.onKey(event)
        return super.dispatchKeyEvent(event)
    }

    private fun runChecks() {
        CheckStore.load(this)
        CheckStore.put(this, AutoChecks.run(this), persist = false)
    }

    private fun applyFix(fix: Fix) {
        when (fix.kind) {
            FixKind.NAV_FULLSCREEN -> prefs.update { it.copy(navOpenMode = NavOpenMode.FULLSCREEN) }
            FixKind.NAV_SPLIT -> prefs.update { it.copy(navOpenMode = NavOpenMode.SPLIT_FIRST) }
            FixKind.DISABLE_BRIGHTNESS_SCHEDULE -> {
                prefs.update { it.copy(display = it.display.copy(autoSchedule = false), manualOverride = false) }
                BrightnessController.scheduleNext(this)
            }
            FixKind.BIND_NAV -> setBinding(BindingTarget.NAVIGATION, fix.arg)
            FixKind.BIND_MUSIC -> setBinding(BindingTarget.MUSIC, fix.arg)
            FixKind.BIND_SHIELD -> setBinding(BindingTarget.SHIELD, fix.arg)
            FixKind.OPEN_NOTIFICATION_ACCESS -> openNotificationAccess()
            FixKind.OPEN_WRITE_SETTINGS -> safeStart(BrightnessController.permissionIntent(this), "車機沒有提供修改系統設定頁")
            FixKind.REQUEST_LOCATION -> locationRequest?.invoke()
            FixKind.OPEN_OVERLAY_SETTINGS -> {
                val pkg = fix.arg
                if (pkg == null) toast("找不到神盾") else
                    safeStart(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$pkg")), "車機沒有提供浮動視窗設定頁")
            }
        }
        if (fix.kind != FixKind.OPEN_NOTIFICATION_ACCESS && fix.kind != FixKind.OPEN_WRITE_SETTINGS &&
            fix.kind != FixKind.REQUEST_LOCATION && fix.kind != FixKind.OPEN_OVERLAY_SETTINGS) {
            toast("已套用：${fix.label}")
        }
        runChecks()
    }

    /** 由 Compose 設定的定位權限請求（ActivityResult launcher 只能在 Compose 內建立）。 */
    private var locationRequest: (() -> Unit)? = null

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    private fun safeStart(intent: Intent, fail: String) {
        try { startActivity(intent) } catch (e: ActivityNotFoundException) { toast(fail) } catch (e: SecurityException) { toast(fail) }
    }

    private fun launchKey(key: String?, missing: () -> Unit) {
        val entry = AppRepository.resolve(this, key)
        if (entry == null) { missing(); return }
        safeStart(AppRepository.launchIntent(entry.component), "無法啟動 ${entry.label}")
    }

    private fun handleNav(result: LaunchResult) {
        when (result) {
            LaunchResult.Ok -> Unit
            LaunchResult.NotBound -> screen = Screen.PickForBinding(BindingTarget.NAVIGATION, false)
            is LaunchResult.Failed -> toast(result.reason)
        }
    }

    private fun notificationAccessIntent(): Intent =
        if (Build.VERSION.SDK_INT >= 30) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, MediaRepository.listenerComponent(this).flattenToString())
        } else {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }

    private fun openNotificationAccess() {
        try { startActivity(notificationAccessIntent()) }
        catch (e: Exception) { safeStart(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS), "車機沒有提供通知存取設定頁") }
    }

    private fun afterDisplayChange() {
        BrightnessController.scheduleNext(this)
        BrightnessController.applyScheduled(this)
    }

    private fun pickTime(periodIndex: Int, isStart: Boolean) {
        val period = prefs.state.value.display.periods.getOrNull(periodIndex) ?: return
        val current = if (isStart) period.startMinutes else period.endMinutes
        TimePickerDialog(this, { _, h, m ->
            val v = h * 60 + m
            prefs.update { s ->
                s.copy(display = s.display.copy(periods = s.display.periods.mapIndexed { i, p ->
                    if (i != periodIndex) p else if (isStart) p.copy(startMinutes = v) else p.copy(endMinutes = v)
                }))
            }
            afterDisplayChange()
        }, current / 60, current % 60, true).show()
    }

    private fun setBinding(target: BindingTarget, key: String?) {
        prefs.update { s ->
            s.copy(bindings = when (target) {
                BindingTarget.NAVIGATION -> s.bindings.copy(navigation = key)
                BindingTarget.MUSIC -> s.bindings.copy(music = key)
                BindingTarget.SHIELD -> s.bindings.copy(shield = key)
            })
        }
        if (target == BindingTarget.MUSIC) MediaRepository.refresh(this)
        if (target == BindingTarget.NAVIGATION) MediaListenerService.rescanNavigation()
    }

    @Composable
    private fun Root() {
        val state by prefs.state.collectAsState()
        val media by MediaRepository.state.collectAsState()
        val gps by GpsSpeed.state.collectAsState()
        val tick = resumeTick

        // 每 30 秒重新計算日／夜主題（跨日與系統時間變更都會反映）
        var minute by remember { mutableIntStateOf(BrightnessController.nowMinutes()) }
        LaunchedEffect(Unit) { while (true) { minute = BrightnessController.nowMinutes(); delay(30_000) } }
        val palette = paletteFor(Schedule.themeFor(state.display, minute), tw.fitcar.launcher.ui.AccentTheme.fromId(state.accentTheme), tw.fitcar.launcher.ui.VisualStyle.fromId(state.visualStyle))
        LaunchedEffect(palette) {
            window.statusBarColor = palette.bg.toArgb()
            window.navigationBarColor = palette.bg.toArgb()
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !palette.isNight
                isAppearanceLightNavigationBars = !palette.isNight
            }
        }

        // App 清單在背景載入並快取（AppCatalog），切換畫面不再於主執行緒重讀
        val apps by AppCatalog.apps.collectAsState()
        val nav = remember(tick, apps, state.bindings.navigation) { AppCatalog.resolve(this, state.bindings.navigation) }
        val music = remember(tick, apps, state.bindings.music) { AppCatalog.resolve(this, state.bindings.music) }
        val shield = remember(tick, apps, state.bindings.shield) { AppCatalog.resolve(this, state.bindings.shield) }
        val slots = remember(tick, apps, state.shortcuts, state.shortcutPages) {
            ShortcutSlots.visible(state.shortcuts, state.shortcutPages).map { SlotView(it, AppCatalog.resolve(this, it)) }
        }
        var editing by remember { mutableStateOf<Int?>(null) }

        val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            GpsSpeed.start(this)
            resumeTick++
            if (screen == Screen.Checker) runChecks()
        }
        // 天氣：前景時每 5 分鐘檢查一次是否超過 30 分鐘需要更新
        LaunchedEffect(Unit) { while (true) { delay(5 * 60_000L); tw.fitcar.launcher.weather.WeatherRepository.refresh(this@MainActivity) } }
        val bluetoothPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            SystemStatusMonitor.start(this)
            resumeTick++
        }
        locationRequest = {
            locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
        LaunchedEffect(screen, tick) { if (screen == Screen.Checker) runChecks() }

        BackHandler(enabled = screen != Screen.Home) {
            screen = when (val s = screen) {
                is Screen.PickForBinding -> if (s.returnToSettings) Screen.Settings else Screen.Home
                Screen.Checker -> Screen.Settings
                else -> Screen.Home
            }
        }

        FitTheme(palette) {
            tw.fitcar.launcher.ui.AutoUpdateNotice()
            Box(Modifier.fillMaxSize()) {
                val overlayOpen = screen != Screen.Home
                // 主畫面常駐：切到設定／所有 App 時不銷毀地圖與播放卡，返回時不必重新載入
                Box(if (overlayOpen) Modifier.fillMaxSize().clearAndSetSemantics {} else Modifier.fillMaxSize()) {
                    HomeScreen(
                        nav = nav,
                        navModeLabel = if (state.navOpenMode == tw.fitcar.launcher.data.NavOpenMode.SPLIT_FIRST) "分割畫面優先" else "全螢幕",
                        splitResult = state.lastSplitResult,
                        music = music, shield = shield, media = media, gps = gps, slots = slots,
                        actions = HomeActions(
                            openAllApps = { screen = Screen.AllApps },
                            openSettings = { screen = Screen.Settings },
                            openNav = { handleNav(NavLauncher.open(this@MainActivity)) },
                            openNavFullscreen = { handleNav(NavLauncher.open(this@MainActivity, forceFullscreen = true)) },
                            bindNav = { screen = Screen.PickForBinding(BindingTarget.NAVIGATION, false) },
                            openMusicApp = {
                                val active = MediaRepository.resumePackage(this@MainActivity)
                                val intent = active?.let { packageManager.getLaunchIntentForPackage(it) }
                                if (intent != null) startActivity(intent)
                                else launchKey(state.bindings.music) { screen = Screen.PickForBinding(BindingTarget.MUSIC, false) }
                            },
                            bindMusic = { screen = Screen.PickForBinding(BindingTarget.MUSIC, false) },
                            grantNotificationAccess = { openNotificationAccess() },
                            playPause = {
                                if (MediaRepository.state.value.hasSession) MediaRepository.playPause()
                                else {
                                    val target = MediaRepository.resumePackage(this@MainActivity)
                                    val intent = target?.let { packageManager.getLaunchIntentForPackage(it) }
                                    if (intent != null) safeStart(intent, "無法開啟上次播放器")
                                    else screen = Screen.PickForBinding(BindingTarget.MUSIC, false)
                                }
                            },
                            next = { MediaRepository.next() },
                            previous = { MediaRepository.previous() },
                            seekTo = { MediaRepository.seekTo(it) },
                            openShield = { launchKey(state.bindings.shield) { screen = Screen.PickForBinding(BindingTarget.SHIELD, false) } },
                            launchSlot = { i -> launchKey(state.shortcuts.getOrNull(i)) { screen = Screen.PickForSlot(i) } },
                            pickSlot = { i -> screen = Screen.PickForSlot(i) },
                            editSlot = { i -> editing = i },
                            voice = { startVoice() },
                            openGoogleNav = { selectGoogleNavigation() },
                        ),
                    )
                }
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
                    contentKey = { it::class },
                    label = "screen",
                ) { target ->
                // 覆蓋畫面攔下所有觸控，避免點到底下的主畫面
                if (target != Screen.Home) Box(
                    Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                ) {
                when (val s = target) {
                    Screen.Home -> Unit
                    Screen.AllApps -> FullScreenAppList(
                        title = "所有 App",
                        subtitle = "共 ${apps.size} 個可啟動的 App",
                        apps = apps,
                        backLabel = "返回桌面",
                        onBack = { screen = Screen.Home },
                        onSelect = { app -> safeStart(AppRepository.launchIntent(app.component), "無法啟動 ${app.label}") },
                    )
                    is Screen.PickForSlot -> FullScreenAppList(
                        title = "選擇捷徑 App",
                        subtitle = "第 ${s.index / ShortcutSlots.PER_PAGE + 1} 頁・第 ${s.index % ShortcutSlots.PER_PAGE + 1} 格",
                        apps = apps,
                        backLabel = "取消",
                        selectedKey = state.shortcuts.getOrNull(s.index),
                        onBack = { screen = Screen.Home },
                        onSelect = { app ->
                            prefs.update { st -> st.copy(shortcuts = ShortcutSlots.replace(st.shortcuts, s.index, app.key)) }
                            screen = Screen.Home
                        },
                    )
                    is Screen.PickForBinding -> FullScreenAppList(
                        title = "選擇${s.target.label}",
                        subtitle = "從車機實際安裝的 App 中選擇",
                        apps = apps,
                        backLabel = "取消",
                        selectedKey = when (s.target) {
                            BindingTarget.NAVIGATION -> state.bindings.navigation
                            BindingTarget.MUSIC -> state.bindings.music
                            BindingTarget.SHIELD -> state.bindings.shield
                        },
                        onBack = { screen = if (s.returnToSettings) Screen.Settings else Screen.Home },
                        onSelect = { app: AppEntry ->
                            setBinding(s.target, app.key)
                            screen = if (s.returnToSettings) Screen.Settings else Screen.Home
                        },
                    )
                    Screen.Checker -> CheckerScreen(
                        CheckerActions(
                            back = { screen = Screen.Settings },
                            rerun = { runChecks() },
                            applyFix = { applyFix(it) },
                            startSplitTest = {
                                if (state.bindings.navigation == null) {
                                    toast("請先綁定導航 App")
                                } else {
                                    prefs.update { st -> st.copy(navOpenMode = NavOpenMode.SPLIT_FIRST) }
                                    handleNav(NavLauncher.open(this@MainActivity))
                                }
                            },
                            requestLocation = { locationRequest?.invoke() },
                            requestWriteSettings = { safeStart(BrightnessController.permissionIntent(this@MainActivity), "車機沒有提供修改系統設定頁") },
                        ),
                    )
                    Screen.Settings -> {
                        val data = remember(tick, state, media.permissionGranted) {
                            SettingsData(
                                state = state, nav = nav, music = music, shield = shield,
                                canWriteBrightness = BrightnessController.canWrite(this@MainActivity),
                                currentBrightness = BrightnessController.currentPercent(this@MainActivity),
                                notificationGranted = MediaRepository.isPermissionGranted(this@MainActivity),
                                locationGranted = GpsSpeed.hasPermission(this@MainActivity),
                                bluetoothGranted = SystemStatusMonitor.canReadBluetoothConnection(this@MainActivity),
                                otherHomes = AppRepository.otherHomeApps(this@MainActivity),
                                diagnostics = Diagnostics.collect(this@MainActivity, state, media, gps),
                                versionName = BuildInfo.versionName(this@MainActivity),
                            )
                        }
                        SettingsScreen(
                            data,
                            SettingsActions(
                                back = { screen = Screen.Home },
                                setAccent = { t -> prefs.update { st -> st.copy(accentTheme = t.id) } },
                                setStyle = { t -> prefs.update { st -> st.copy(visualStyle = t.id) } },
                                setShortcutPages = { n -> prefs.update { st -> st.copy(shortcutPages = ShortcutSlots.clampPages(n)) } },
                                requestBluetooth = if (android.os.Build.VERSION.SDK_INT >= 31) {
                                    { bluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT) }
                                } else null,
                                updateDisplay = { f -> prefs.update { st -> st.copy(display = f(st.display)) }; afterDisplayChange() },
                                setManualBrightness = { v ->
                                    prefs.update { st -> st.copy(display = st.display.copy(manualBrightness = v), manualOverride = true) }
                                    BrightnessController.apply(this@MainActivity, v, smooth = false)
                                    BrightnessController.scheduleNext(this@MainActivity)
                                },
                                restoreAuto = {
                                    prefs.update { st -> st.copy(manualOverride = false, display = st.display.copy(autoSchedule = true)) }
                                    afterDisplayChange()
                                },
                                applyNow = { afterDisplayChange() },
                                pickTime = { i, start -> pickTime(i, start) },
                                requestWriteSettings = { safeStart(BrightnessController.permissionIntent(this@MainActivity), "車機沒有提供修改系統設定頁") },
                                requestNotificationAccess = { openNotificationAccess() },
                                requestLocation = {
                                    locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                                },
                                bind = { t -> screen = Screen.PickForBinding(t, true) },
                                clearBinding = { t -> setBinding(t, null) },
                                setNavMode = { m -> prefs.update { st -> st.copy(navOpenMode = m) } },
                                setMediaMode = { m -> prefs.update { st -> st.copy(mediaMode = m) }; MediaRepository.refresh(this@MainActivity) },
                                testSplit = {
                                    prefs.update { st -> st.copy(navOpenMode = tw.fitcar.launcher.data.NavOpenMode.SPLIT_FIRST, lastSplitResult = null) }
                                    handleNav(NavLauncher.open(this@MainActivity))
                                },
                                openOtherHome = { home ->
                                    val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                                        .setComponent(home.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    safeStart(i, "無法開啟 ${home.label}")
                                },
                                openChecker = { screen = Screen.Checker },
                                openHomeSettings = {
                                    try { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
                                    catch (e: Exception) { safeStart(Intent(Settings.ACTION_SETTINGS), "無法開啟系統設定") }
                                },
                            ),
                        )
                    }
                }
                }
                }

                editing?.let { index ->
                    val slot = slots.getOrNull(index)
                    ShortcutEditDialog(
                        label = slot?.entry?.label ?: "App 已移除",
                        canMoveBack = index > 0,
                        canMoveForward = index < ShortcutSlots.visibleCount(state.shortcutPages) - 1,
                        onReplace = { editing = null; screen = Screen.PickForSlot(index) },
                        onRemove = {
                            prefs.update { st -> st.copy(shortcuts = ShortcutSlots.replace(st.shortcuts, index, null)) }
                            editing = null
                        },
                        onMoveBack = {
                            prefs.update { st -> st.copy(shortcuts = ShortcutSlots.move(st.shortcuts, index, index - 1)) }
                            editing = index - 1
                        },
                        onMoveForward = {
                            prefs.update { st -> st.copy(shortcuts = ShortcutSlots.move(st.shortcuts, index, index + 1)) }
                            editing = index + 1
                        },
                        onDismiss = { editing = null },
                    )
                }
            }
        }
    }
}
