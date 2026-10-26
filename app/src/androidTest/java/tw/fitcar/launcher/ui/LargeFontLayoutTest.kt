package tw.fitcar.launcher.ui

import android.content.ComponentName
import android.graphics.Bitmap
import android.media.session.PlaybackState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import tw.fitcar.launcher.data.AppEntry
import tw.fitcar.launcher.media.MediaUiState
import tw.fitcar.launcher.system.GpsState
import java.io.File

class LargeFontLayoutTest {
    @get:Rule val compose = createComposeRule()
    private val actions = HomeActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
    private val musicApp = AppEntry("YouTube Music", ComponentName("test.music", "Player"), null)
    private fun show(media: MediaUiState) {
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 1.25f)) {
                FitTheme(DayPalette) {
                    HomeScreen(
                        nav = AppEntry("樂客導航王A5i 3D⁺", ComponentName("test.nav", "Nav"), null),
                        navModeLabel = "全螢幕", splitResult = false, music = musicApp,
                        shield = null, media = media, gps = GpsState(),
                        slots = List(8) { SlotView(null, null) }, actions = actions,
                        mapContent = { _, modifier ->
                            Box(modifier.background(Color(0xffe4e8df))) { Text("地圖區域（版面測試）") }
                        },
                    )
                }
            }
        }
    }
    private fun fullLabel(text: String) {
        val node = compose.onNodeWithText(text, useUnmergedTree = true)
        node.assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertFalse("文字裁切：$text", layouts.isEmpty() || layouts.any { it.hasVisualOverflow })
    }
    private fun screenshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.getExternalFilesDir(null), "$name.png")
        file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun permissionCardButtonAt125Percent() {
        show(MediaUiState())
        compose.onNodeWithText("前往授權").performScrollTo()
        listOf("前往授權", "Google 導航", "開啟導航", "所有 App", "設定", "目前位置").forEach(::fullLabel)
        screenshot("home-permission-125")
    }
    @Test fun playingCardAt125Percent() {
        show(MediaUiState(permissionGranted = true, packageName = "test.music", title = "晴天", artist = "周杰倫",
            playbackState = PlaybackState.STATE_PAUSED, durationMs = 240000,
            actions = PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS))
        listOf("晴天", "周杰倫", "Google 導航", "開啟導航", "所有 App", "設定").forEach(::fullLabel)
        screenshot("home-playing-125")
    }
}
