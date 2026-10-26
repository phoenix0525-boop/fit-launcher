package tw.fitcar.launcher.ui

import android.content.ComponentName
import android.graphics.Bitmap
import android.media.session.PlaybackState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import tw.fitcar.launcher.data.AppEntry
import tw.fitcar.launcher.media.MediaUiState
import tw.fitcar.launcher.system.GpsState
import java.io.File

/**
 * 常見車機螢幕尺寸（dp）＋字體 125%：主畫面按鈕與歌名不得被裁切。
 * 1333×800＝ZX09 2000×1200@240dpi；1024×600、853×480＝7～9 吋車機；1280×480＝1920×720 超寬；
 * 800×800 正方形；720×1280 直向。
 */
class MultiScreenLayoutTest {
    @get:Rule val compose = createComposeRule()
    private val actions = HomeActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, voice = {})

    private fun show(size: DpSize) {
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(size)) {
                val density = LocalDensity.current.density
                CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 1.25f)) {
                    FitTheme(DayPalette) {
                        HomeScreen(
                            nav = AppEntry("樂客導航王A5i 3D⁺", ComponentName("test.nav", "Nav"), null),
                            navModeLabel = "全螢幕", splitResult = false,
                            music = AppEntry("KKBOX", ComponentName("test.music", "Player"), null),
                            shield = null,
                            media = MediaUiState(
                                permissionGranted = true, packageName = "test.music", playerName = "KKBOX", title = "晴天", artist = "周杰倫",
                                playbackState = PlaybackState.STATE_PLAYING, durationMs = 240000,
                                actions = PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS,
                            ),
                            gps = GpsState(),
                            slots = List(8) { SlotView(null, null) }, actions = actions,
                            mapContent = { _, modifier -> Box(modifier.background(Color(0xffe4e8df))) { Text("地圖區域（版面測試）") } },
                        )
                    }
                }
            }
        }
    }

    private fun assertNotClipped(text: String) {
        val node = compose.onNodeWithText(text, useUnmergedTree = true)
        node.assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val visible = node.fetchSemanticsNode().boundsInRoot
        val clipped = layouts.isEmpty() || layouts.any { layout ->
            visible.height + 1f < layout.size.height || visible.width + 1f < layout.size.width ||
                layout.didOverflowHeight || (0 until layout.lineCount).any { layout.isLineEllipsized(it) }
        }
        assertFalse("文字裁切：$text", clipped)
    }

    private fun screenshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.getExternalFilesDir(null), "$name.png")
        file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        val shell = InstrumentationRegistry.getInstrumentation().uiAutomation
        shell.executeShellCommand("mkdir -p /sdcard/Download/fit-ui").use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
        shell.executeShellCommand("cp ${file.absolutePath} /sdcard/Download/fit-ui/$name.png").use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
    }

    private fun check(w: Int, h: Int) {
        show(DpSize(w.dp, h.dp))
        screenshot("home-${w}x${h}-125")
        listOf("所有 App", "設定", "語音", "開啟導航", "Google 導航", "KKBOX", "晴天").forEach(::assertNotClipped)
    }

    @Test fun zx09Landscape() {
        check(1333, 800)
        compose.onNodeWithText("目前位置").assertIsDisplayed()
        assertTrue("不應再顯示即時地圖標題", compose.onAllNodesWithText("即時地圖").fetchSemanticsNodes().isEmpty())
    }
    @Test fun common1024x600() = check(1024, 600)
    @Test fun hd720At240dpi() = check(853, 480)
    @Test fun ultraWide1920x720() = check(1280, 480)
    @Test fun square800() = check(800, 800)
    @Test fun portrait720x1280() = check(720, 1280)
}
