package tw.fitcar.launcher

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import tw.fitcar.launcher.data.AppRepository
import tw.fitcar.launcher.data.Prefs
import tw.fitcar.launcher.data.Schedule
import tw.fitcar.launcher.media.MediaRepository
import tw.fitcar.launcher.nav.LaunchResult
import tw.fitcar.launcher.nav.NavLauncher
import tw.fitcar.launcher.system.BrightnessController
import tw.fitcar.launcher.ui.Clock
import tw.fitcar.launcher.ui.FitTheme
import tw.fitcar.launcher.ui.Glyph
import tw.fitcar.launcher.ui.GlyphIcon
import tw.fitcar.launcher.ui.HomeActions
import tw.fitcar.launcher.ui.MusicCard
import tw.fitcar.launcher.ui.SecondaryButton
import tw.fitcar.launcher.ui.ShortcutsCard
import tw.fitcar.launcher.ui.SlotView
import tw.fitcar.launcher.ui.paletteFor

/**
 * 分割畫面時的 30% 側欄（音樂＋捷徑）。屬一般 Activity，才能進入分割畫面。
 * 啟動時帶 EXTRA_LAUNCH_NAV：把導航王開在相鄰視窗，約 2 秒後檢查是否真的進入分割畫面。
 * 沒進入分割畫面 → 導航王已是全螢幕（自然退回），側欄自行關閉並記錄結果。
 */
class CompanionActivity : ComponentActivity() {

    private val main = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CompanionRoot() }
        window.decorView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> recordPlacement() }
        handleLaunch(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleLaunch(intent)
    }

    override fun onResume() {
        super.onResume()
        MediaRepository.refresh(this)
        main.post { recordPlacement() }
    }

    private fun handleLaunch(intent: Intent?) {
        if (intent == null || !intent.getBooleanExtra(EXTRA_LAUNCH_NAV, false)) return
        intent.removeExtra(EXTRA_LAUNCH_NAV)
        main.postDelayed({
            when (val r = NavLauncher.launchAdjacent(this)) {
                is LaunchResult.Failed -> Toast.makeText(this, r.reason, Toast.LENGTH_LONG).show()
                else -> Unit
            }
            main.postDelayed({ checkSplit() }, 2000)
        }, 350)
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode,newConfig)
        lastRecorded = null // 模式改變時一定重新判斷
        main.post { recordPlacement() }
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    /** 螢幕與本視窗的實際邊界（Android 11+ WindowMetrics，取代已棄用的 getRealMetrics／decorView 位置）。 */
    private fun bounds(): Pair<android.graphics.Rect, android.graphics.Rect> =
        windowManager.maximumWindowMetrics.bounds to windowManager.currentWindowMetrics.bounds

    private var lastRecorded: Pair<android.graphics.Rect, tw.fitcar.launcher.nav.NavigationSide>? = null

    private fun recordPlacement() {
        val (display, pane) = bounds()
        val target = Prefs.get(this).state.value.splitNavigationSide
        // 版面重排很頻繁；視窗邊界與排列目標都沒變時不重算
        val key = android.graphics.Rect(pane) to target
        if (key == lastRecorded) return
        lastRecorded = key
        val side = tw.fitcar.launcher.nav.SplitGeometry.paneSide(isInMultiWindowMode,
            display.width(), display.height(), pane.left - display.left, pane.width(), pane.height())
        tw.fitcar.launcher.nav.SplitPlacement.record(target, side)
    }

    private fun checkSplit() {
        recordPlacement()
        val (display, pane) = bounds()
        val inSplit = isInMultiWindowMode && tw.fitcar.launcher.nav.SplitGeometry.isSideBySide(
            display.width(), display.height(), pane.width(), pane.height())
        // Multi-window alone could mean top/bottom or a floating window; do not call it a left/right success.
        Prefs.get(this).update { it.copy(lastSplitResult = inSplit) }
        if (!inSplit) finishAndRemoveTask()
    }

    private fun goHome() {
        startActivity(tw.fitcar.launcher.nav.DesktopReturn.intent(this))
        finishAndRemoveTask()
    }

    @androidx.compose.runtime.Composable
    private fun CompanionRoot() {
        val prefs = Prefs.get(this)
        val state by prefs.state.collectAsState()
        val media by MediaRepository.state.collectAsState()
        var minute by remember { mutableIntStateOf(BrightnessController.nowMinutes()) }
        LaunchedEffect(Unit) { while (true) { minute = BrightnessController.nowMinutes(); delay(30_000) } }
        val palette = paletteFor(Schedule.themeFor(state.display, minute), tw.fitcar.launcher.ui.AccentTheme.fromId(state.accentTheme), tw.fitcar.launcher.ui.VisualStyle.fromId(state.visualStyle))
        val music = remember(state.bindings.music) { AppRepository.resolve(this, state.bindings.music) }
        val slots = remember(state.shortcuts, state.shortcutPages) {
            tw.fitcar.launcher.data.ShortcutSlots.visible(state.shortcuts, state.shortcutPages).map { SlotView(it, AppRepository.resolve(this, it)) }
        }

        fun launch(key: String?) {
            val e = AppRepository.resolve(this, key) ?: return
            try { startActivity(AppRepository.launchIntent(e.component)) } catch (_: Exception) {}
        }

        val actions = HomeActions(
            openAllApps = { goHome() }, openSettings = { goHome() },
            openNav = { NavLauncher.launchAdjacent(this) }, openNavFullscreen = { NavLauncher.open(this, true) },
            bindNav = { goHome() },
            openMusicApp = {
                val intent = media.packageName?.let { packageManager.getLaunchIntentForPackage(it) }
                if (intent != null) runCatching { startActivity(intent) } else launch(state.bindings.music)
            }, bindMusic = { goHome() },
            grantNotificationAccess = { goHome() },
            playPause = { MediaRepository.playPause() }, next = { MediaRepository.next() },
            previous = { MediaRepository.previous() }, seekTo = { MediaRepository.seekTo(it) },
            openShield = { launch(state.bindings.shield) },
            launchSlot = { i -> launch(state.shortcuts.getOrNull(i)) },
            pickSlot = { goHome() }, editSlot = { goHome() },
        )

        FitTheme(palette) {
            Column(
                Modifier.fillMaxSize().background(palette.bg).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                tw.fitcar.launcher.ui.CompanionHeader { goHome() }
                MusicCard(music, media, actions, emptyList(), Modifier.fillMaxWidth().weight(1.2f))
                ShortcutsCard(slots, actions, Modifier.fillMaxWidth().weight(0.8f))
            }
        }
    }

    companion object {
        const val EXTRA_LAUNCH_NAV = "launch_nav"
    }
}
