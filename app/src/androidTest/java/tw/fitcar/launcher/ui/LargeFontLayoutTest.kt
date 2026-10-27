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
    private val actions = HomeActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, voice = {})
    private val musicApp = AppEntry("YouTube Music", ComponentName("test.music", "Player"), null)
    private fun show(media: MediaUiState, google: Boolean = false) {
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 1.25f)) {
                FitTheme(DayPalette) {
                    HomeScreen(
                        nav = if (google) AppEntry("Google 地圖", ComponentName("com.google.android.apps.maps", "MapsActivity"), null) else AppEntry("樂客導航王A5i 3D⁺", ComponentName("test.nav", "Nav"), null),
                        navModeLabel = "全螢幕", splitResult = false, music = musicApp,
                        shield = null, media = media, gps = GpsState(),
                        slots = List(8) { SlotView(musicApp.key, musicApp.copy(label = "這是一個很長的應用程式捷徑名稱")) }, actions = actions,
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
        // Check painted lines, ellipsis and vertical overflow. Paragraph width can include
        // fractional rounding/unused alignment space beyond the integer Text node width.
        val visible = node.fetchSemanticsNode().boundsInRoot
        val clipped = layouts.isEmpty() || layouts.any { layout ->
            visible.height + 1f < layout.size.height || visible.width + 1f < layout.size.width ||
            layout.didOverflowHeight || (0 until layout.lineCount).any { line ->
                layout.isLineEllipsized(line) || layout.getLineLeft(line) < -1f ||
                    layout.getLineRight(line) > layout.size.width + 1f
            }
        }
        assertFalse("文字裁切：$text layouts=${layouts.map { layout ->
            "${layout.size} paragraph=${layout.multiParagraph.width} lines=${(0 until layout.lineCount).map { layout.getLineLeft(it) to layout.getLineRight(it) }}"
        }}", clipped)
    }
    private fun screenshot(name: String, dialog: Boolean = false) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.getExternalFilesDir(null), "$name.png")
        val capture = if (dialog) compose.onNode(isDialog()) else compose.onRoot()
        file.outputStream().use { capture.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        // Gradle removes test APKs and their app-specific files before the shell pull.
        val shell = InstrumentationRegistry.getInstrumentation().uiAutomation
        shell.executeShellCommand("mkdir -p /sdcard/Download/fit-ui").use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
        shell.executeShellCommand("cp ${file.absolutePath} /sdcard/Download/fit-ui/$name.png").use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
    }
    @Test fun updateAndLyricsTextAt125Percent() {
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 1.25f)) {
                FitTheme(DayPalette) {
                    Column(Modifier.fillMaxSize().padding(20.dp)) {
                        UpdateSection()
                        LyricsLines(tw.fitcar.launcher.lyrics.LyricsResult("同步歌詞 · LRCLIB", listOf(tw.fitcar.launcher.lyrics.LyricLine(0, "自製測試文字"))), MediaUiState(positionUpdateElapsed = 1))
                    }
                }
            }
        }
        listOf("程式更新", "立即檢查", "自製測試文字").forEach(::fullLabel)
        screenshot("updates-lyrics-125")
    }
    @Test fun splitShortcutExplainsOptionalPermissionAt125Percent() {
        show(MediaUiState())
        compose.onNodeWithText("分割／多工").performClick()
        listOf("啟用系統快捷入口", "前往設定", "稍後").forEach(::fullLabel)
        screenshot("home-split-permission-125", dialog = true)
    }
    @Test fun volumeDialogAndIconOnlyShortcutAt125Percent() {
        show(MediaUiState(permissionGranted = true, packageName = "test.music", playerName = "KKBOX", title = "晴天", artist = "周杰倫", playbackState = PlaybackState.STATE_PLAYING))
        compose.onAllNodesWithContentDescription("這是一個很長的應用程式捷徑名稱")[0].assertIsDisplayed()
        compose.onAllNodesWithText("這是一個很長的應用程式捷徑名稱").assertCountEquals(0)
        compose.onNodeWithContentDescription("媒體音量", substring = true).performClick()
        compose.onNodeWithContentDescription("調整媒體音量").assertIsDisplayed()
        listOf("降低", "提高", "關閉").forEach(::fullLabel)
        screenshot("home-volume-125", dialog = true)
    }
    @Test fun permissionCardButtonAt125Percent() {
        show(MediaUiState())
        compose.onNodeWithText("前往授權").performScrollTo()
        screenshot("home-permission-125")
        listOf("前往授權", "Google 導航", "開啟導航", "所有 App", "設定", "語音", "目前位置").forEach(::fullLabel)
        screenshot("home-permission-125")
    }
    @Test fun googleNotificationCardKeepsButtonsVisibleAt125Percent() {
        show(MediaUiState(), google = true)
        listOf("Google 導航", "開啟導航", "語音", "所有 App", "設定", "通知權限").forEach(::fullLabel)
        screenshot("home-google-voice-125")
    }
    @Test fun playingCardAt125Percent() {
        show(MediaUiState(permissionGranted = true, packageName = "app.morphe.android.apps.youtube.music", playerName = "Morphe Music", title = "晴天", artist = "周杰倫",
            playbackState = PlaybackState.STATE_PAUSED, durationMs = 240000,
            actions = PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS))
        screenshot("home-playing-125")
        listOf("Morphe Music", "晴天", "周杰倫", "Google 導航", "開啟導航", "所有 App", "設定", "語音").forEach(::fullLabel)
        screenshot("home-playing-125")
    }
}
