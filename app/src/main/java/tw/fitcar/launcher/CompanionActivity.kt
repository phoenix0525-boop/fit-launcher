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
        handleLaunch(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleLaunch(intent)
    }

    override fun onResume() {
        super.onResume()
        MediaRepository.refresh(this)
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

    private fun checkSplit() {
        val metrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        val view = window.decorView
        val inSplit = isInMultiWindowMode && tw.fitcar.launcher.nav.SplitGeometry.isSideBySide(
            metrics.widthPixels, metrics.heightPixels, view.width, view.height)
        // Multi-window alone could mean top/bottom or a floating window; do not call it a left/right success.
        Prefs.get(this).update { it.copy(lastSplitResult = inSplit) }
        if (!inSplit) finishAndRemoveTask()
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Clock(Modifier.weight(1f), big = false)
                    SecondaryButton("桌面", { goHome() }, icon = { GlyphIcon(Glyph.HOME, size = 22.dp) })
                }
                MusicCard(music, media, actions, emptyList(), Modifier.fillMaxWidth().weight(1.2f))
                ShortcutsCard(slots, actions, Modifier.fillMaxWidth().weight(0.8f))
            }
        }
    }

    companion object {
        const val EXTRA_LAUNCH_NAV = "launch_nav"
    }
}
