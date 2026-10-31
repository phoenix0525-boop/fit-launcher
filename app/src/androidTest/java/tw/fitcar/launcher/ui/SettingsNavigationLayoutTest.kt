package tw.fitcar.launcher.ui

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import tw.fitcar.launcher.data.LauncherState
import java.io.File

/**
 * 0.4.31：設定從子畫面（檢測、選 App）返回時保留分類、捲動與授權碼草稿；切換分類捲動歸零；
 * 新分頁在 ZX09（1333×800 dp）與較窄螢幕、字體 125% 不裁切，選取的分類在可見範圍內。
 */
class SettingsNavigationLayoutTest {
    @get:Rule val compose = createComposeRule()
    private var visible by mutableStateOf(true)

    private fun show(nav: SettingsNavState, size: DpSize = DpSize(1333.dp, 800.dp)) {
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(size)) {
                val density = LocalDensity.current.density
                CompositionLocalProvider(LocalDensity provides Density(density, 1.25f)) {
                    FitTheme(DayPalette) {
                        if (visible) SettingsScreen(
                            SettingsData(LauncherState(), null, null, null, true, 50, true, true,
                                otherHomes = emptyList(), diagnostics = emptyList(), versionName = "0.4.34"),
                            SettingsActions(back = {}, updateDisplay = {}, setManualBrightness = {}, restoreAuto = {}, applyNow = {},
                                pickTime = { _, _ -> }, requestWriteSettings = {}, requestNotificationAccess = {},
                                requestLocation = {}, bind = {}, clearBinding = {}, setNavMode = {}, setMediaMode = {},
                                testSplit = {}, openOtherHome = {}, openHomeSettings = {}, openChecker = {}, openTestTab = {}),
                            nav,
                        )
                    }
                }
            }
        }
    }

    private fun section(name: String) = compose.onNode(hasText(name) and hasClickAction())

    private fun assertNotClipped(text: String) {
        val node = compose.onNode(hasText(text), useUnmergedTree = true)
        node.assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val box = node.fetchSemanticsNode().boundsInRoot
        assertFalse("文字裁切：$text", layouts.isEmpty() || layouts.any { l ->
            box.height + 1f < l.size.height || box.width + 1f < l.size.width ||
                l.didOverflowHeight || (0 until l.lineCount).any { l.isLineEllipsized(it) }
        })
    }

    private fun snapshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.getExternalFilesDir(null), "$name.png")
        file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        val shell = InstrumentationRegistry.getInstrumentation().uiAutomation
        shell.executeShellCommand("mkdir -p /sdcard/Download/fit-ui").use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
        shell.executeShellCommand("cp ${file.absolutePath} /sdcard/Download/fit-ui/$name.png").use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
    }

    @Test fun returningFromSubScreenKeepsSectionScrollAndWeatherDraft() {
        val nav = SettingsNavState()
        show(nav)
        section("天氣").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextInput("draft-key")
        section("關於").performScrollTo().performClick()
        compose.onNodeWithText("最近改版").performScrollTo()
        val scrolled = compose.runOnIdle { nav.scroll.value }
        assertTrue("關於頁應已捲動", scrolled > 0)
        // 模擬進入實機檢測再返回：設定畫面離開組合後重新進入
        visible = false; compose.waitForIdle(); visible = true; compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("關於", nav.section)
            assertEquals(scrolled, nav.scroll.value)
            assertEquals("draft-key", nav.weatherKeyDraft)
        }
        section("關於").assertIsDisplayed()
        section("天氣").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(0, nav.scroll.value) }
        compose.runOnIdle { nav.reset(); assertEquals("外觀與配色", nav.section); assertEquals("", nav.weatherKeyDraft) }
    }

    private fun checkSize(w: Int, h: Int) {
        val nav = SettingsNavState()
        show(nav, DpSize(w.dp, h.dp))
        mapOf(
            "導航" to listOf("導航 App 與神盾", "常用地點（回家／去公司）", "導航時浮動回桌面鍵"),
            "音樂與音效" to listOf("播放器", "音量控制", "原廠 DSP／EQ", "原廠整合可行性（只讀檢查）"),
            "捷徑與 App" to listOf("最近使用的 App", "桌面小工具"),
            "桌面接管" to listOf("開機啟動", "預設桌面", "原廠桌面出現時切回", "開啟原廠桌面"),
            "權限總覽" to listOf("通知存取（音樂資訊）", "背景不被關閉", "系統服務（無障礙）"),
            "備份與更新" to listOf("備份與還原"),
            "實機檢測" to listOf("實機資訊"),
            "天氣" to listOf("天氣（中央氣象署開放資料）", "儲存授權碼", "立即更新"),
            "關於" to listOf("測試機型與相容性", "適用條件", "已回報實測機型", "待驗證功能（本版未通過實車驗收）", "不提供，改用原廠功能"),
        ).forEach { (name, texts) ->
            section(name).performScrollTo().performClick()
            section(name).assertIsDisplayed()
            texts.forEach { t -> compose.onNode(hasText(t), useUnmergedTree = true).performScrollTo(); assertNotClipped(t) }
            snapshot("settings-${name}-${w}x$h-125")
        }
    }

    @Test fun zx09At125Percent() = checkSize(1333, 800)
    @Test fun common1024x600At125Percent() = checkSize(1024, 600)
    @Test fun hd853x480At125Percent() = checkSize(853, 480)
    @Test fun portrait720x1280At125Percent() = checkSize(720, 1280)

    /** 0.4.34：寬螢幕側欄顯示四個群組小標，順序為日常 → 初次設定 → 維護 → 診斷。 */
    @Test fun sidebarShowsGroupHeadingsInOrder() {
        show(SettingsNavState())
        val tops = SettingsSections.GROUPS.map { g ->
            compose.onNode(hasText(g.title) and isHeading(), useUnmergedTree = true).performScrollTo().fetchSemanticsNode().boundsInRoot.top
        }
        compose.runOnIdle { assertEquals(tops.sorted(), tops) }
        compose.onAllNodes(hasText("App 綁定") and hasClickAction()).assertCountEquals(0)
    }
}
