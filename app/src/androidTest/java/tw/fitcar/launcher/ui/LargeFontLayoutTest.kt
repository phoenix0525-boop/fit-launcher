package tw.fitcar.launcher.ui

import android.content.ComponentName
import android.graphics.Bitmap
import android.media.session.PlaybackState
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import tw.fitcar.launcher.data.AppEntry
import tw.fitcar.launcher.media.MediaUiState
import tw.fitcar.launcher.system.GpsState
import java.io.File

class LargeFontLayoutTest {
    @get:Rule val compose = createComposeRule()
    private var originalMapZoom: Boolean? = null
    @org.junit.Before fun isolatePersistedMapPreference() {
        val c=InstrumentationRegistry.getInstrumentation().targetContext
        val sp=c.getSharedPreferences("map_preferences",android.content.Context.MODE_PRIVATE)
        originalMapZoom=if(sp.contains("auto_zoom")) sp.getBoolean("auto_zoom",true) else null
        check(sp.edit().putBoolean("auto_zoom",true).commit())
    }
    @org.junit.After fun restorePersistedMapPreference() {
        val c=InstrumentationRegistry.getInstrumentation().targetContext
        val editor=c.getSharedPreferences("map_preferences",android.content.Context.MODE_PRIVATE).edit()
        originalMapZoom?.let { editor.putBoolean("auto_zoom",it) } ?: editor.remove("auto_zoom")
        check(editor.commit())
    }
    private val actions = HomeActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, voice = {})
    private val musicApp = AppEntry("YouTube Music", ComponentName("test.music", "Player"), null)
    private fun show(media: MediaUiState, google: Boolean = false, gps: GpsState = GpsState(), palette: Palette = DayPalette, lyricsContent: (@androidx.compose.runtime.Composable (MediaUiState) -> Unit)? = null, wallpaper: tw.fitcar.launcher.wallpaper.WallpaperState? = null) {
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 1.25f)) {
                FitTheme(palette) {
                    HomeScreen(
                        nav = if (google) AppEntry("Google 地圖", ComponentName("com.google.android.apps.maps", "MapsActivity"), null) else AppEntry("樂客導航王A5i 3D⁺", ComponentName("test.nav", "Nav"), null),
                        navModeLabel = "全螢幕", splitResult = false, music = musicApp,
                        shield = null, media = media, gps = gps,
                        lyricsContent = lyricsContent, wallpaperOverride = wallpaper,
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
        // 「更多」對話框內再開說明對話框時會有兩層，截最上層
        val capture = if (dialog) compose.onAllNodes(isDialog()).onLast() else compose.onRoot()
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
    @Test fun centeredLyricsInNarrowPaneAt125Percent() {
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 1.25f)) {
                FitTheme(DayPalette) {
                    Box(Modifier.width(360.dp).padding(12.dp)) {
                        LyricsLines(tw.fitcar.launcher.lyrics.LyricsResult("同步歌詞 · LRCLIB",
                            listOf(tw.fitcar.launcher.lyrics.LyricLine(0, "目前歌詞測試"))),
                            MediaUiState(positionUpdateElapsed = 1))
                    }
                }
            }
        }
        fullLabel("目前歌詞測試")
    }
    @Test fun splitShortcutExplainsOptionalPermissionAt125Percent() {
        show(MediaUiState())
        compose.onNodeWithText("更多").performClick()
        compose.onNodeWithText("分割／多工").performClick()
        listOf("啟用系統快捷入口", "前往設定", "稍後").forEach(::fullLabel)
        screenshot("home-split-permission-125", dialog = true)
    }
    @Test fun volumeDialogAndIconOnlyShortcutAt125Percent() {
        show(MediaUiState(permissionGranted = true, packageName = "test.music", playerName = "KKBOX", title = "晴天", artist = "周杰倫", playbackState = PlaybackState.STATE_PLAYING))
        compose.onAllNodesWithContentDescription("這是一個很長的應用程式捷徑名稱")[0].assertIsDisplayed()
        compose.onAllNodesWithText("這是一個很長的應用程式捷徑名稱").assertCountEquals(0)
        val audio = InstrumentationRegistry.getInstrumentation().targetContext.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
        fullLabel(audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC).toString())
        compose.onNodeWithContentDescription("媒體音量", substring = true).performClick()
        compose.onNodeWithContentDescription("調整媒體音量").assertIsDisplayed()
        listOf("降低", "提高", "系統音量", "關閉").forEach(::fullLabel)
        screenshot("home-volume-125", dialog = true)
    }
    @Test fun permissionCardButtonAt125Percent() {
        show(MediaUiState())
        compose.onNodeWithText("前往授權").performScrollTo()
        screenshot("home-permission-125")
        listOf("前往授權", "更多", "開啟導航", "所有 App", "設定", "語音", "目前位置").forEach(::fullLabel)
        screenshot("home-permission-125")
    }
    @Test fun googleNotificationCardKeepsButtonsVisibleAt125Percent() {
        show(MediaUiState(), google = true)
        listOf("更多", "開啟導航", "語音", "所有 App", "設定", "通知權限").forEach(::fullLabel)
        screenshot("home-google-voice-125")
    }
    @Test fun drivingModeAt125Percent() {
        // 60 km/h、剛定位：進入行車大字模式（按鈕 20 sp、隱藏次要說明、車速放大）
        val now = android.os.SystemClock.elapsedRealtime()
        show(MediaUiState(permissionGranted = true, packageName = "test.music", playerName = "KKBOX", title = "晴天", artist = "周杰倫",
            playbackState = PlaybackState.STATE_PLAYING, durationMs = 240000,
            actions = PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS),
            gps = GpsState(permission = true, providerEnabled = true, speedKmh = 60f, accuracyM = 5f, latitude = 24.95, longitude = 121.29, fixElapsed = now, bearing = 90f))
        compose.waitForIdle()
        listOf("km/h", "晴天", "周杰倫", "開啟導航", "所有 App", "設定", "語音", "−15", "+15").forEach(::fullLabel)
        // Primary driving labels must stay legible at the agreed 19.2 sp minimum.
        listOf("晴天", "周杰倫", "開啟導航", "所有 App", "設定", "語音", "−15", "+15").forEach { text ->
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(text, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue("行車主要文字不得小於 19.2 sp：$text", layouts.isNotEmpty() &&
                layouts.all { it.layoutInput.style.fontSize.value >= 19.2f })
        }
        assertTrue("行車中應隱藏「GPS km/h」完整標籤", compose.onAllNodesWithText("GPS km/h").fetchSemanticsNodes().isEmpty())
        screenshot("home-driving-125")
    }
    @Test fun drivingVolumeValueAt125PercentKeepsMinimumFont() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val audio = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
        val value = audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC).toString()
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 1.25f), LocalDriving provides true) {
                FitTheme(DayPalette) { VolumeControl() }
            }
        }
        compose.onAllNodesWithText("音量", useUnmergedTree = true).assertCountEquals(0)
        fullLabel(value)
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(value, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue("行車音量數值不得小於 19.2 sp", layouts.isNotEmpty() &&
            layouts.all { it.layoutInput.style.fontSize.value >= 19.2f })
    }
    @Test fun playingCardAt125Percent() {
        show(MediaUiState(permissionGranted = true, packageName = "app.morphe.android.apps.youtube.music", playerName = "Morphe Music", title = "晴天", artist = "周杰倫",
            playbackState = PlaybackState.STATE_PAUSED, durationMs = 240000,
            actions = PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS))
        screenshot("home-playing-125")
        listOf("Morphe Music", "晴天", "周杰倫", "更多", "開啟導航", "所有 App", "設定", "語音").forEach(::fullLabel)
        screenshot("home-playing-125")
    }
    private fun pompompurin(night: Boolean, driving: Boolean = false, spider: Boolean = false) {
        val fix = if (driving) GpsState(permission = true, providerEnabled = true, speedKmh = 60f,
            latitude = 24.95, longitude = 121.29, accuracyM = 5f,
            fixElapsed = android.os.SystemClock.elapsedRealtime()) else GpsState()
        show(MediaUiState(permissionGranted = true, packageName = "test.music", playerName = "KKBOX",
            title = "晴天", artist = "周杰倫", playbackState = PlaybackState.STATE_PAUSED, durationMs = 240000,
            actions = PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS),
            gps = fix, palette = paletteFor(if (night) tw.fitcar.launcher.data.ThemeChoice.NIGHT else tw.fitcar.launcher.data.ThemeChoice.DAY,
                if (spider) AccentTheme.HERO_RED else AccentTheme.CARAMEL, if (spider) VisualStyle.SPIDERMAN else VisualStyle.POMPOMPURIN))
        listOf("晴天", "周杰倫", "開啟導航", "所有 App", "設定", "語音", "−15", "+15").forEach(::fullLabel)
        // 0.4.16：主畫面不再放會被卡片遮住的大背景，只保留時鐘旁小圖
        compose.onNodeWithContentDescription(if (spider) "蜘蛛人全版背景" else "布丁狗全版背景").assertDoesNotExist()
        compose.onNodeWithContentDescription(if (spider) "蜘蛛人蹲姿插畫" else "布丁狗主題插畫").assertDoesNotExist()
        compose.onAllNodesWithContentDescription("這是一個很長的應用程式捷徑名稱")[0].assertHeightIsAtLeast(48.dp)
        compose.onNodeWithContentDescription(if (spider) "蜘蛛人飛躍插畫" else "布丁狗趴睡插畫").assertDoesNotExist()
        compose.onNodeWithContentDescription(if (spider) "蜘蛛人伸手插畫" else "布丁狗感謝插畫").assertIsDisplayed()
        fullLabel(java.text.SimpleDateFormat("M月d日 EEEE", java.util.Locale.TAIWAN).format(java.util.Date()))
        screenshot("home-${if (spider) "spiderman" else "pompompurin"}-${if (night) "night" else "day"}${if (driving) "-driving" else ""}-125")
    }
    @Test fun pompompurinDayAt125Percent() = pompompurin(false)
    @Test fun pompompurinNightAt125Percent() = pompompurin(true)
    @Test fun pompompurinDrivingAt125Percent() = pompompurin(false, true)
    @Test fun pompompurinAppListArtworkAt125Percent() {
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 1.25f)) {
                FitTheme(paletteFor(tw.fitcar.launcher.data.ThemeChoice.DAY, AccentTheme.CARAMEL, VisualStyle.POMPOMPURIN)) {
                    FullScreenAppList("所有 App", "選擇要開啟的程式", emptyList(), "返回桌面", {}, {})
                }
            }
        }
        compose.onNodeWithContentDescription("布丁狗活力插畫").assertIsDisplayed()
        listOf("所有 App", "返回桌面").forEach(::fullLabel)
        screenshot("apps-pompompurin-125")
    }
    private fun lyricsCard(driving: Boolean, night: Boolean, spider: Boolean = false) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val old = tw.fitcar.launcher.lyrics.LyricsRepository.enabled.value
        tw.fitcar.launcher.lyrics.LyricsRepository.setEnabled(context, true)
        try {
            val fix = if (driving) GpsState(permission = true, providerEnabled = true, speedKmh = 60f,
                latitude = 24.95, longitude = 121.29, fixElapsed = android.os.SystemClock.elapsedRealtime()) else GpsState()
            show(MediaUiState(permissionGranted = true, packageName = "test.music", playerName = "KKBOX",
                title = "自製歌曲測試", artist = "測試歌手", playbackState = PlaybackState.STATE_PAUSED,
                positionMs = 10000, positionUpdateElapsed = android.os.SystemClock.elapsedRealtime(), durationMs = 240000,
                actions = PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS),
                gps = fix, palette = paletteFor(if (night) tw.fitcar.launcher.data.ThemeChoice.NIGHT else tw.fitcar.launcher.data.ThemeChoice.DAY,
                    if (spider) AccentTheme.HERO_RED else AccentTheme.CARAMEL, if (spider) VisualStyle.SPIDERMAN else VisualStyle.POMPOMPURIN),
                lyricsContent = { state -> LyricsLines(tw.fitcar.launcher.lyrics.LyricsResult("同步歌詞 · LRCLIB", listOf(
                    tw.fitcar.launcher.lyrics.LyricLine(0, "上一句測試文字"),
                    tw.fitcar.launcher.lyrics.LyricLine(5000, "目前這一行比較長也要完整顯示不能裁切"),
                    tw.fitcar.launcher.lyrics.LyricLine(20000, "下一行也要完整顯示"))), state) })
            // Source attribution remains available without taking a row from the current/next lyrics.
            compose.onNodeWithContentDescription("同步歌詞 · LRCLIB", useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithText("同步歌詞 · LRCLIB").assertDoesNotExist()
            listOf("自製歌曲測試", "測試歌手", "目前這一行比較長也要完整顯示不能裁切", "下一行也要完整顯示", "−15", "+15").forEach(::fullLabel)
            val current = compose.onNodeWithText("目前這一行比較長也要完整顯示不能裁切", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val next = compose.onNodeWithText("下一行也要完整顯示", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue("下一行必須位於目前行下方", next.top >= current.bottom)
            compose.onNodeWithContentDescription("播放／暫停").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            compose.onNodeWithContentDescription(if (spider) "蜘蛛人伸手插畫" else "布丁狗感謝插畫").assertWidthIsAtLeast(64.dp)
            compose.onAllNodesWithContentDescription("這是一個很長的應用程式捷徑名稱")[0].assertHeightIsAtLeast(48.dp)
            screenshot("home-${if (spider) "spiderman-" else ""}lyrics-${if (driving) "driving" else if (night) "night" else "day"}-125")
            if (!driving) {
                compose.onNodeWithContentDescription("播放進度，點擊調整").performClick()
                compose.onNodeWithContentDescription("調整播放進度滑桿").assertIsDisplayed()
                fullLabel("調整播放進度")
                compose.onNodeWithText("關閉").performClick()
            }
        } finally { tw.fitcar.launcher.lyrics.LyricsRepository.setEnabled(context, old) }
    }
    @Test fun lyricsCurrentAndNextOnHomeAt125Percent() = lyricsCard(false, false)
    @Test fun lyricsCurrentAndNextDrivingAt125Percent() = lyricsCard(true, false)
    @Test fun lyricsCurrentAndNextNightAt125Percent() = lyricsCard(false, true)
    @Test fun homeLyricsEntryRequiresConsentAndPersistsChoice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val old = tw.fitcar.launcher.lyrics.LyricsRepository.enabled.value
        tw.fitcar.launcher.lyrics.LyricsRepository.setEnabled(context, false)
        try {
            show(MediaUiState(permissionGranted = true, packageName = "test.music", playerName = "KKBOX", title = "測試曲", artist = "測試歌手"), lyricsContent = {})
            compose.onNodeWithContentDescription("啟用桌面歌詞").performClick()
            fullLabel("啟用桌面歌詞")
            assertFalse(tw.fitcar.launcher.lyrics.LyricsRepository.enabled.value)
            compose.onNodeWithText("同意並啟用").performClick()
            tw.fitcar.launcher.lyrics.LyricsRepository.initialize(context)
            assertTrue(tw.fitcar.launcher.lyrics.LyricsRepository.enabled.value)
        } finally { tw.fitcar.launcher.lyrics.LyricsRepository.setEnabled(context, old) }
    }

    @Test fun relativeVolumeReportsOnlyAndroidReadback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val audio = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
        val before = audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
        val max = audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
        val initial = (max / 2).coerceAtLeast(2)
        try {
            audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, initial, 0)
            show(MediaUiState(permissionGranted = true, packageName = "test.music", playerName = "KKBOX", title = "測試曲"))
            compose.onNodeWithContentDescription("媒體音量", substring = true).performClick()
            compose.onNodeWithText("降低").performClick()
            compose.waitUntil(5000) { audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) == initial - 1 }
            val message = tw.fitcar.launcher.system.VolumeReadback.NUMERIC_ONLY.message
            compose.waitUntil(5000) { compose.onAllNodesWithText(message).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(message).performScrollTo()
            fullLabel(message)
            assertTrue(context.getSharedPreferences("volume_diagnostics", android.content.Context.MODE_PRIVATE)
                .getString("last_readback", "")!!.startsWith("相對增減"))
            screenshot("volume-readback-125", dialog = true)
        } finally { audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, before, 0) }
    }

    @Test fun spidermanDayAt125Percent() = pompompurin(false, spider = true)
    @Test fun spidermanNightAt125Percent() = pompompurin(true, spider = true)
    @Test fun spidermanDrivingAt125Percent() = pompompurin(false, true, true)
    @Test fun spidermanLyricsDayAt125Percent() = lyricsCard(false, false, true)
    @Test fun spidermanLyricsNightAt125Percent() = lyricsCard(false, true, true)
    @Test fun spidermanLyricsDrivingAt125Percent() = lyricsCard(true, false, true)
    @Test fun spidermanAppListAt125Percent() {
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 1.25f)) {
                FitTheme(paletteFor(tw.fitcar.launcher.data.ThemeChoice.DAY, AccentTheme.HERO_RED, VisualStyle.SPIDERMAN)) {
                    FullScreenAppList("所有 App", "選擇要開啟的程式", emptyList(), "返回桌面", {}, {})
                }
            }
        }
        compose.onNodeWithContentDescription("蜘蛛人蓄勢插畫").assertIsDisplayed()
        listOf("所有 App", "返回桌面").forEach(::fullLabel)
        screenshot("apps-spiderman-125")
    }

    @Test fun mapToolsShareBottomToolbarAndKeepCameraState() {
        show(MediaUiState())
        val group = compose.onNodeWithContentDescription("地圖工具列", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        // 0.4.16：下方工具列只留常用動作，且只有一排；縮放與回到定位浮在地圖右下角
        listOf("開啟導航", "更多").forEach { text ->
            fullLabel(text)
            val child = compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue("常用動作必須在工具列內：$text", child.top >= group.top && child.bottom <= group.bottom)
        }
        assertTrue("工具列必須維持一排", group.height < 100.dp.value * InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density)
        listOf("自動縮放", "回到目前位置").forEach { text ->
            fullLabel(text)
            val child = compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue("地圖浮動控制應在工具列上方：$text", child.bottom <= group.top)
        }
        listOf("Google 導航", "分割／多工", "桌面調暗").forEach { compose.onNodeWithText(it).assertDoesNotExist() }
        compose.onNodeWithText("更多").performClick()
        listOf("更多工具", "Google 導航", "分割／多工", "桌面調暗", "關閉").forEach(::fullLabel)
        screenshot("home-more-tools-125", dialog = true)
        compose.onNodeWithText("關閉").performClick()
        compose.onNodeWithText("自動縮放").performClick()
        fullLabel("手動縮放")
        compose.onNodeWithText("回到目前位置").performClick()
        fullLabel("手動縮放") // Recentring must not silently re-enable automatic zoom.
        screenshot("home-map-toolbar-125")
    }
    private fun wallpaperHome(night: Boolean) {
        val bitmap = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.RED) }
        show(MediaUiState(permissionGranted = true, packageName = "test.music", playerName = "KKBOX", title = "測試歌曲", artist = "測試歌手"),
            palette = paletteFor(if (night) tw.fitcar.launcher.data.ThemeChoice.NIGHT else tw.fitcar.launcher.data.ThemeChoice.DAY,
                AccentTheme.HERO_RED, VisualStyle.SPIDERMAN),
            wallpaper = tw.fitcar.launcher.wallpaper.WallpaperState(tw.fitcar.launcher.wallpaper.WallpaperOptions(enabled = true, showArt = false), bitmap, true))
        compose.onNodeWithContentDescription("自訂桌布").assertExists()
        compose.onAllNodesWithContentDescription("蜘蛛人伸手插畫").assertCountEquals(0)
        listOf("測試歌曲", "測試歌手", "自動縮放", "回到目前位置", "更多").forEach(::fullLabel)
        screenshot("home-wallpaper-${if (night) "night" else "day"}-125")
    }
    @Test fun customWallpaperDayAt125Percent() = wallpaperHome(false)
    @Test fun customWallpaperNightAt125Percent() = wallpaperHome(true)

    @Test fun wallpaperSettingsAndResetAt125Percent() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = tw.fitcar.launcher.wallpaper.WallpaperStore(context, "wallpaper_ui_${java.util.UUID.randomUUID()}")
        val source = File(context.cacheDir, "wallpaper_ui_${java.util.UUID.randomUUID()}.png")
        val image = Bitmap.createBitmap(100, 80, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLUE) }
        source.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }; image.recycle()
        try {
            kotlinx.coroutines.runBlocking { assertTrue(store.importImage(android.net.Uri.fromFile(source)).isSuccess) }
            compose.setContent {
                val density = LocalDensity.current.density
                CompositionLocalProvider(LocalDensity provides Density(density, 1.25f)) {
                    FitTheme(DayPalette) {
                        Column(Modifier.fillMaxSize().padding(20.dp).then(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()))) {
                            WallpaperSettings(store)
                        }
                    }
                }
            }
            listOf("選擇桌布圖片", "使用自訂桌布", "左右位置", "上下位置", "桌布啟用時顯示主題插畫", "恢復預設桌布").forEach {
                compose.onNodeWithText(it).performScrollTo(); fullLabel(it)
            }
            compose.onNodeWithText("恢復預設桌布").performClick()
            fullLabel("恢復預設桌布？")
            compose.onNodeWithText("取消").performClick()
            assertTrue(store.state.value.visible)
            compose.onNodeWithText("恢復預設桌布").performClick()
            compose.onNodeWithText("恢復預設").performClick()
            compose.waitUntil(5000) { store.state.value.bitmap == null }
            assertTrue("恢復預設不能刪原圖", source.exists())
            screenshot("wallpaper-settings-reset-125")
        } finally { source.delete(); kotlinx.coroutines.runBlocking { store.reset() } }
    }

    private fun albumFallback(night: Boolean, driving: Boolean = false) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repo = tw.fitcar.launcher.lyrics.LyricsRepository
        repo.initialize(context)
        val oldEnabled = repo.enabled.value
        val oldCover = repo.coverFallback.value
        repo.setEnabled(context, true); repo.setCoverFallback(context, true)
        val art = Bitmap.createBitmap(180, 180, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.RED)
            android.graphics.Canvas(this).drawCircle(90f, 90f, 60f, android.graphics.Paint().apply { color = android.graphics.Color.YELLOW })
        }
        val result = androidx.compose.runtime.mutableStateOf(tw.fitcar.launcher.lyrics.LyricsResult("找不到相符歌詞"))
        val state = androidx.compose.runtime.mutableStateOf(MediaUiState(permissionGranted = true, packageName = "test.music",
            title = "封面顯示測試", artist = "測試歌手", art = art))
        try {
            compose.setContent {
                val density = LocalDensity.current.density
                CompositionLocalProvider(LocalDensity provides Density(density, 1.25f), LocalDriving provides driving) {
                    FitTheme(paletteFor(if (night) tw.fitcar.launcher.data.ThemeChoice.NIGHT else tw.fitcar.launcher.data.ThemeChoice.DAY,
                        AccentTheme.HERO_RED, VisualStyle.SPIDERMAN)) {
                        Box(Modifier.fillMaxSize().padding(24.dp)) {
                            MusicCard(musicApp, state.value, actions, emptyList(), Modifier.width(420.dp).fillMaxHeight(),
                                lyricsResultOverride = result.value)
                        }
                    }
                }
            }
            val cover = compose.onNodeWithContentDescription("無歌詞專輯封面底圖", useUnmergedTree = true)
            cover.assertIsDisplayed()
            listOf("封面顯示測試", "測試歌手", "找不到相符歌詞").forEach(::fullLabel)
            val bg = cover.fetchSemanticsNode().boundsInRoot
            val play = compose.onNodeWithContentDescription("播放／暫停").fetchSemanticsNode().boundsInRoot
            assertTrue("封面必須覆蓋整張播放器，包含控制列", bg.top < play.top && bg.bottom >= play.bottom)
            screenshot("player-album-fallback-${if (driving) "driving" else if (night) "night" else "day"}-125")
            compose.runOnIdle { result.value = tw.fitcar.launcher.lyrics.LyricsResult("文字歌詞", plain = "可顯示的實際歌詞") }
            cover.assertDoesNotExist(); fullLabel("可顯示的實際歌詞")
            compose.runOnIdle { result.value = tw.fitcar.launcher.lyrics.LyricsResult("歌詞連線失敗，保留歌曲資訊") }
            cover.assertIsDisplayed()
            compose.runOnIdle { state.value = state.value.copy(packageName = "other.music", title = "下一首無封面", art = null) }
            cover.assertDoesNotExist(); fullLabel("下一首無封面")
            compose.runOnIdle { state.value = state.value.copy(art = art); repo.setCoverFallback(context, false) }
            cover.assertDoesNotExist()
        } finally { repo.setEnabled(context, oldEnabled); repo.setCoverFallback(context, oldCover) }
    }
    @Test fun albumFallbackDayAt125Percent() = albumFallback(false)
    @Test fun albumFallbackNightAt125Percent() = albumFallback(true)
    @Test fun albumFallbackDrivingAt125Percent() = albumFallback(false, true)
    @Test fun albumFallbackSettingPersistsWithoutEnablingLyrics() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repo = tw.fitcar.launcher.lyrics.LyricsRepository
        repo.initialize(context)
        val oldEnabled = repo.enabled.value; val oldCover = repo.coverFallback.value
        repo.setEnabled(context, false); repo.setCoverFallback(context, true)
        try {
            compose.setContent { FitTheme(DayPalette) { Column(Modifier.padding(24.dp)) { LyricsSetting() } } }
            compose.onNodeWithContentDescription("無歌詞時顯示專輯封面").performClick()
            repo.initialize(context)
            assertFalse(repo.coverFallback.value); assertFalse(repo.enabled.value)
            compose.onNodeWithContentDescription("無歌詞時顯示專輯封面").performClick()
            repo.initialize(context)
            assertTrue(repo.coverFallback.value); assertFalse(repo.enabled.value)
        } finally { repo.setEnabled(context, oldEnabled); repo.setCoverFallback(context, oldCover) }
    }

    private fun googleNotificationPanel(night: Boolean, driving: Boolean = false) {
        val now = android.os.SystemClock.elapsedRealtime()
        val guidance = androidx.compose.runtime.mutableStateOf(tw.fitcar.launcher.nav.GoogleGuidanceState(connected = true,
            activeNavigation = true, guidance = tw.fitcar.launcher.nav.GoogleGuidance.parse("300 公尺後右轉", "進入中山路", "預計 12:40 抵達"), receivedElapsed = now))
        val permission = androidx.compose.runtime.mutableStateOf(true)
        val google = androidx.compose.runtime.mutableStateOf(true)
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 1.25f)) {
                FitTheme(paletteFor(if (night) tw.fitcar.launcher.data.ThemeChoice.NIGHT else tw.fitcar.launcher.data.ThemeChoice.DAY,
                    AccentTheme.HERO_RED, VisualStyle.SPIDERMAN)) {
                    HomeScreen(nav = AppEntry("導航", ComponentName(if (google.value) "com.google.android.apps.maps" else "test.nav", "Main"), null),
                        navModeLabel = "全螢幕", splitResult = false, music = musicApp, shield = null,
                        media = MediaUiState(permissionGranted = permission.value),
                        gps = if (driving) GpsState(permission = true, providerEnabled = true, speedKmh = 60f,
                            latitude = 24.95, longitude = 121.29, fixElapsed = now) else GpsState(),
                        slots = emptyList(), actions = actions, googleGuidanceOverride = guidance.value, googleGuidanceNow = now,
                        mapContent = { _, m -> Box(m) { Text("免費底圖測試") } })
                }
            }
        }
        compose.onNodeWithText("免費底圖測試").assertDoesNotExist()
        compose.onNodeWithText("自動縮放").assertDoesNotExist()
        compose.onNodeWithText("回到目前位置").assertDoesNotExist()
        listOf("Google 地圖導航通知", "→ 300 公尺後右轉", "進入中山路", "通知到達時間 12:40", "開啟導航", "更多").forEach(::fullLabel)
        screenshot("google-notification-panel-${if (driving) "driving" else if (night) "night" else "day"}-125")
        compose.runOnIdle { guidance.value = guidance.value.copy(receivedElapsed = now - tw.fitcar.launcher.nav.GuidanceFreshness.MAX_AGE_MS) }
        compose.onNodeWithText("→ 300 公尺後右轉").assertDoesNotExist()
        fullLabel("導航提示已過期，請開啟 Google 地圖確認")
        compose.runOnIdle { guidance.value = tw.fitcar.launcher.nav.GoogleGuidanceState(connected = true, activeNavigation = true) }
        fullLabel("導航提示暫不可用，請開啟 Google 地圖確認")
        compose.runOnIdle { guidance.value = tw.fitcar.launcher.nav.GoogleGuidanceState(connected = true) }
        fullLabel("請在 Google 地圖開始導航")
        compose.runOnIdle { guidance.value = tw.fitcar.launcher.nav.GoogleGuidanceState() }
        fullLabel("導航通知服務未連線")
        compose.runOnIdle { permission.value = false }
        fullLabel("請開啟通知存取以顯示導航提示")
        fullLabel("通知權限")
        compose.runOnIdle { google.value = false }
        compose.onNodeWithText("Google 地圖導航通知").assertDoesNotExist()
        fullLabel("免費底圖測試")
        // Switching providers must preserve the same driving-safe controls as a fresh map.
        if (driving) compose.onNodeWithText("自動縮放").assertDoesNotExist()
        else fullLabel("自動縮放")
        fullLabel("回到目前位置")
    }
    @Test fun googleNotificationPanelDayAt125Percent() = googleNotificationPanel(false)
    @Test fun googleNotificationPanelNightAt125Percent() = googleNotificationPanel(true)
    @Test fun googleNotificationPanelDrivingAt125Percent() = googleNotificationPanel(false, true)

    @Test fun fullThemeOptionsKeepLyricsAndPersistChoiceAt125Percent() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ThemeAppearanceStore.initialize(context)
        val old = ThemeAppearanceStore.state.value
        try {
            ThemeAppearanceStore.save(context, ThemeAppearanceOptions())
            compose.setContent {
                val density = LocalDensity.current.density
                CompositionLocalProvider(LocalDensity provides Density(density, 1.25f)) {
                    FitTheme(paletteFor(tw.fitcar.launcher.data.ThemeChoice.NIGHT, AccentTheme.HERO_RED, VisualStyle.SPIDERMAN)) {
                        ThemePageFrame(ThemePage.SETTINGS) { Column(Modifier.fillMaxSize().padding(24.dp)) { ThemeAppearanceSettings() } }
                    }
                }
            }
            listOf("全版角色主題", "顯示小插畫", "清楚", "✓ 均衡", "通透").forEach(::fullLabel)
            compose.onNodeWithContentDescription("全版角色主題").performClick()
            compose.onNodeWithContentDescription("蜘蛛人全版背景").assertDoesNotExist()
            compose.onNodeWithContentDescription("顯示小插畫").performClick()
            compose.onNodeWithText("通透").performClick()
            ThemeAppearanceStore.initialize(context)
            assertFalse(ThemeAppearanceStore.state.value.fullTheme)
            assertFalse(ThemeAppearanceStore.state.value.smallArt)
            assertTrue(ThemeAppearanceStore.state.value.clarity == ThemeClarity.AIRY)
            screenshot("theme-options-light-night-125")
        } finally { ThemeAppearanceStore.save(context, old) }
    }
    private fun settingsFixture(): SettingsData = SettingsData(tw.fitcar.launcher.data.LauncherState(), null, null, null, true, 50, true, true,
        otherHomes = emptyList(), diagnostics = emptyList(), versionName = tw.fitcar.launcher.BuildInfo.versionName(InstrumentationRegistry.getInstrumentation().targetContext))
    private fun settingsActionsFixture() = SettingsActions(back = {}, updateDisplay = {}, setManualBrightness = {}, restoreAuto = {}, applyNow = {},
        pickTime = { _, _ -> }, requestWriteSettings = {}, requestNotificationAccess = {}, requestLocation = {}, bind = {}, clearBinding = {},
        setNavMode = {}, setMediaMode = {}, testSplit = {}, openOtherHome = {}, openHomeSettings = {}, openChecker = {})
    @Test fun lightSpiderSettingsAndAboutAt125Percent() {
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 1.25f)) {
                FitTheme(paletteFor(tw.fitcar.launcher.data.ThemeChoice.NIGHT, AccentTheme.HERO_RED, VisualStyle.SPIDERMAN), appearanceOverride = ThemeAppearanceOptions()) {
                    SettingsScreen(settingsFixture(), settingsActionsFixture())
                }
            }
        }
        fullLabel("設定"); fullLabel("返回桌面")
        compose.onNodeWithContentDescription("蜘蛛人飛躍插畫").assertIsDisplayed()
        compose.onNodeWithText("關於").performScrollTo().performClick()
        fullLabel("個人化啟動器"); fullLabel("版本 ${tw.fitcar.launcher.BuildInfo.versionName(InstrumentationRegistry.getInstrumentation().targetContext)}")
        compose.onNodeWithContentDescription("蜘蛛人蹲姿插畫").assertIsDisplayed()
        screenshot("settings-about-spiderman-light-night-125")
    }

    @Test fun lightSpiderAppGridKeepsCornerArtSeparateAt125Percent() {
        val apps = (1..12).map { AppEntry("工具程式$it", ComponentName("test.app$it", "Main"), null) }
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 1.25f)) {
                FitTheme(paletteFor(tw.fitcar.launcher.data.ThemeChoice.NIGHT, AccentTheme.HERO_RED, VisualStyle.SPIDERMAN), appearanceOverride = ThemeAppearanceOptions()) {
                    FullScreenAppList("所有 App", "選擇要開啟的程式", apps, "返回桌面", {}, {})
                }
            }
        }
        listOf("所有 App", "返回桌面", "工具程式1", "工具程式12").forEach(::fullLabel)
        val art = compose.onNodeWithContentDescription("蜘蛛人蓄勢插畫").fetchSemanticsNode().boundsInRoot
        val app = compose.onNodeWithText("工具程式12").fetchSemanticsNode().boundsInRoot
        assertTrue("角落插畫不得蓋住App項目", art.top >= app.bottom)
        screenshot("apps-spiderman-light-night-grid-125")
    }

    @Test fun backupRestoreAt125Percent() {
        var exports=0; var imports=0
        compose.setContent {
            val density=LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density,1.25f)) {
                FitTheme(paletteFor(tw.fitcar.launcher.data.ThemeChoice.DAY,AccentTheme.HERO_RED,VisualStyle.SPIDERMAN)) {
                    Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                        BackupPage(false,null,{ exports++ },{ imports++ })
                    }
                }
            }
        }
        listOf("備份與還原","匯出設定","匯入設定").forEach(::fullLabel)
        compose.onNodeWithText("匯出設定").performClick(); compose.onNodeWithText("匯入設定").performClick()
        compose.runOnIdle { assertTrue(exports==1 && imports==1) }
        screenshot("backup-restore-125")
    }
    @Test fun desktopDimmingAt125Percent() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        DesktopDimStore.initialize(context)
        val before=DesktopDimStore.state.value
        compose.setContent {
            val density=LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density,1.25f)) {
                FitTheme(DayPalette) {
                    Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                        DesktopDimShortcut(); DesktopDimSettings()
                    }
                }
            }
        }
        listOf("桌面自己調暗","夜間自動調暗","桌面調暗").forEach(::fullLabel)
        compose.onNodeWithText("桌面調暗").performClick()
        compose.runOnIdle { assertTrue(DesktopDimStore.manual.value==true) }
        compose.onNodeWithContentDescription("夜間自動調暗").performClick()
        compose.runOnIdle { assertTrue(DesktopDimStore.state.value.auto!=before.auto) }
        screenshot("desktop-dimming-125")
        compose.runOnIdle { DesktopDimStore.save(context,before); DesktopDimStore.manual.value=null }
    }
    @Test fun movingCardClarityAt125Percent() {
        val appearance=ThemeAppearanceOptions(clarity=ThemeClarity.AIRY)
        compose.setContent {
            val density=LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density,1.25f)) {
                FitTheme(paletteFor(tw.fitcar.launcher.data.ThemeChoice.DAY,AccentTheme.HERO_RED,VisualStyle.SPIDERMAN),appearanceOverride=appearance) {
                    CompositionLocalProvider(LocalDriving provides true, LocalFullThemeActive provides true) {
                        Box(Modifier.fillMaxSize().background(Color.Black).padding(30.dp)) {
                            FitCard { Text("行車卡片清楚顯示",color=LocalPalette.current.text,fontSize=androidx.compose.ui.unit.TextUnit(20f, androidx.compose.ui.unit.TextUnitType.Sp)) }
                        }
                    }
                }
            }
        }
        fullLabel("行車卡片清楚顯示")
        val nodes=compose.onAllNodes(SemanticsMatcher.expectValue(CardOpacityKey,.98f),useUnmergedTree=true).fetchSemanticsNodes()
        assertTrue(nodes.isNotEmpty())
        assertTrue(appearance.clarity==ThemeClarity.AIRY)
        screenshot("moving-card-clarity-125")
    }

    @Test fun originalArtworkActualBoundsNeverExceed150Percent() {
        compose.setContent {
            val density=LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density,1.25f)) {
                Column(Modifier.fillMaxSize()) {
                    listOf(VisualStyle.POMPOMPURIN,VisualStyle.SPIDERMAN).forEach { style ->
                        FitTheme(paletteFor(tw.fitcar.launcher.data.ThemeChoice.DAY,AccentTheme.HERO_RED,style)) {
                            Row { ThemeArtPose.entries.forEach { pose -> ThemeArtwork(250.dp,pose) } }
                        }
                    }
                }
            }
        }
        ThemeArtPose.entries.forEach { pose ->
            listOf(pose.drawable to pose.description,pose.spiderDrawable to pose.spiderDescription).forEach { (id,desc) ->
                val context=InstrumentationRegistry.getInstrumentation().targetContext
                val (w,h)=checkNotNull(ArtworkSize.originals[context.resources.getResourceEntryName(id)])
                val rect=compose.onNodeWithContentDescription(desc,useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
                assertTrue("$desc width $rect",rect.width<=w*1.5f+1f)
                assertTrue("$desc height $rect",rect.height<=h*1.5f+1f)
                assertTrue(rect.width>0 && rect.height>0)
            }
        }
        screenshot("original-artwork-bounds-125")
    }

    @Test fun unauthorizedLocationIsOneTappableLineAt125Percent() {
        var asked = false
        val withLocation = HomeActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, voice = {}, requestLocation = { asked = true })
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 1.25f)) {
                FitTheme(DayPalette) {
                    HomeScreen(nav = null, navModeLabel = "全螢幕", splitResult = false, music = null, shield = null,
                        media = MediaUiState(), gps = GpsState(), slots = List(8) { SlotView(null, null) }, actions = withLocation,
                        mapContent = { _, modifier -> Box(modifier.background(Color(0xffe4e8df))) { Text("地圖區域（版面測試）") } })
                }
            }
        }
        fullLabel("目前位置：尚未授權定位 · 點此允許")
        compose.onNodeWithText("目前位置：尚未授權定位 · 點此允許").performClick()
        compose.runOnIdle { assertTrue(asked) }
        compose.onAllNodesWithContentDescription("新增捷徑")[0].assertIsDisplayed()
        screenshot("home-location-prompt-125")
    }
    @Test fun allAppsSearchAndRecentAt125Percent() {
        val apps = (1..12).map { AppEntry("工具程式$it", ComponentName("test.app$it", "Main"), null) }
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 1.25f)) {
                FitTheme(paletteFor(tw.fitcar.launcher.data.ThemeChoice.DAY, AccentTheme.HERO_RED, VisualStyle.SPIDERMAN)) {
                    FullScreenAppList("所有 App", "選擇要開啟的程式", apps, "返回桌面", {}, {}, recentKeys = listOf(apps[4].key))
                }
            }
        }
        listOf("最近使用", "全部", "搜尋 App 名稱").forEach(::fullLabel)
        // 不再顯示套件名稱
        compose.onNodeWithText("test.app1").assertDoesNotExist()
        compose.onNodeWithText("搜尋 App 名稱").performTextInput("程式12")
        fullLabel("工具程式12")
        compose.onNodeWithText("工具程式3").assertDoesNotExist()
        screenshot("apps-search-125")
    }
}
