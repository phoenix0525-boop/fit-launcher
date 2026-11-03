package tw.fitcar.launcher.ui

import android.content.ComponentName
import android.graphics.Bitmap
import android.media.session.PlaybackState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import tw.fitcar.launcher.data.AppEntry
import tw.fitcar.launcher.data.ThemeChoice
import tw.fitcar.launcher.media.MediaUiState
import tw.fitcar.launcher.system.GpsState
import java.io.File

class MechaThemeLayoutTest {
    @get:Rule val compose = createComposeRule()
    private val actions = HomeActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, voice = {})

    private fun verify(width: Int, height: Int, driving: Boolean = false) {
        var mecha by mutableStateOf(false)
        var night by mutableStateOf(false)
        val player = AppEntry("Spotify", ComponentName("com.spotify.music", "Player"), null)
        val fix = if (driving) GpsState(permission = true, providerEnabled = true, speedKmh = 60f,
            latitude = 24.95, longitude = 121.29, accuracyM = 5f, fixElapsed = android.os.SystemClock.elapsedRealtime()) else GpsState()
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(width.dp, height.dp))) {
                val density = LocalDensity.current.density
                CompositionLocalProvider(LocalDensity provides Density(density, 1.25f)) {
                    FitTheme(paletteFor(if (night) ThemeChoice.NIGHT else ThemeChoice.DAY, AccentTheme.DYNAMIC,
                        if (mecha) VisualStyle.MECHA else VisualStyle.ORIGINAL),
                        appearanceOverride = ThemeAppearanceOptions(fullTheme = true, smallArt = true)) {
                        HomeScreen(nav = player, navModeLabel = "全螢幕", splitResult = false, music = player, shield = null,
                            media = MediaUiState(permissionGranted = true, packageName = player.component.packageName,
                                playerName = "Spotify", title = "晴天", artist = "周杰倫", playbackState = PlaybackState.STATE_PAUSED,
                                durationMs = 240000, actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_SEEK_TO or
                                    PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS),
                            gps = fix, slots = List(8) { SlotView(player.key, player) }, actions = actions,
                            mapContent = { _, m -> Box(m.testTag("mecha-map").background(Color(0xFF303743))) })
                    }
                }
            }
        }
        val baseline = compose.onNodeWithTag("mecha-map").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { mecha = true }
        repeat(2) { phase ->
            val map = compose.onNodeWithTag("mecha-map").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertEquals("Map left", baseline.left, map.left, 1f)
            assertEquals("Map top", baseline.top, map.top, 1f)
            assertEquals("Map width", baseline.width, map.width, 1f)
            assertEquals("Map height", baseline.height, map.height, 1f)
            listOf("紫綠機甲插畫", "橘白機甲插畫", "紅黑機甲插畫").forEach { desc ->
                compose.onAllNodesWithContentDescription(desc).fetchSemanticsNodes().forEach { art ->
                    assertFalse("Artwork overlaps map: $desc", art.boundsInRoot.overlaps(map))
                }
            }
            compose.onNodeWithContentDescription("播放／暫停").assertIsDisplayed().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            compose.onNode(hasText("導航王") and hasClickAction()).assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            compose.onNode(hasText("Google 地圖") and hasClickAction()).assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            compose.onAllNodesWithContentDescription("Spotify")[0].assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            screenshot("home-mecha-${width}x$height-${if (phase == 0) "day" else "night"}${if (driving) "-driving" else ""}-125")
            compose.runOnIdle { night = true }
        }
    }
    private fun screenshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.getExternalFilesDir(null), "$name.png")
        file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        val shell = InstrumentationRegistry.getInstrumentation().uiAutomation
        shell.executeShellCommand("mkdir -p /sdcard/Download/fit-ui").use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
        shell.executeShellCommand("cp ${file.absolutePath} /sdcard/Download/fit-ui/$name.png").use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
    }
    @Test fun zx09KeepsOriginalMapBounds() = verify(1333, 800)
    @Test fun common1024KeepsOriginalMapBounds() = verify(1024, 600)
    @Test fun narrow853KeepsOriginalMapBounds() = verify(853, 480)
    @Test fun portraitKeepsOriginalMapBounds() = verify(720, 1280)
    @Test fun drivingKeepsOriginalMapBounds() = verify(1333, 800, true)
}
