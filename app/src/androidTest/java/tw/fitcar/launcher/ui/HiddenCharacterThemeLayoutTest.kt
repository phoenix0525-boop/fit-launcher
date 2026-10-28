package tw.fitcar.launcher.ui

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import tw.fitcar.launcher.data.LauncherState
import java.io.File

class HiddenCharacterThemeLayoutTest {
    @get:Rule val compose = createComposeRule()
    private var state by mutableStateOf(LauncherState())

    private fun screen() {
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 1.25f)) {
                FitTheme(DayPalette) {
                    SettingsScreen(
                        SettingsData(state, null, null, null, true, 50, true, true,
                            otherHomes = emptyList(), diagnostics = emptyList(), versionName = "0.4.18"),
                        SettingsActions(back = {}, setStyle = { state = state.copy(visualStyle = it.id) },
                            setCharacterThemesUnlocked = { unlocked -> state = state.copy(
                                characterThemesUnlocked = unlocked,
                                visualStyle = if (!unlocked && CharacterThemeAccess.isCharacter(state.visualStyle)) "original" else state.visualStyle,
                            ) }, updateDisplay = {}, setManualBrightness = {}, restoreAuto = {}, applyNow = {},
                            pickTime = { _, _ -> }, requestWriteSettings = {}, requestNotificationAccess = {},
                            requestLocation = {}, bind = {}, clearBinding = {}, setNavMode = {}, setMediaMode = {},
                            testSplit = {}, openOtherHome = {}, openHomeSettings = {}, openChecker = {}),
                    )
                }
            }
        }
    }

    private fun snapshot(name: String, dialog: Boolean = false) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.getExternalFilesDir(null), "$name.png")
        val capture = if (dialog) compose.onNode(isDialog()) else compose.onRoot()
        file.outputStream().use { capture.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        val shell = InstrumentationRegistry.getInstrumentation().uiAutomation
        shell.executeShellCommand("mkdir -p /sdcard/Download/fit-ui").use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
        shell.executeShellCommand("cp ${file.absolutePath} /sdcard/Download/fit-ui/$name.png").use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
    }

    private fun styleLabel(text: String) = hasText(text) or hasText("✓ $text")

    private fun fullLabel(text: String) {
        val node = compose.onNode(styleLabel(text), useUnmergedTree = true)
        node.assertIsDisplayed()
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val bounds = node.fetchSemanticsNode().boundsInRoot
        assertFalse("文字被裁切：$text", layouts.isEmpty() || layouts.any {
            bounds.height + 1f < it.size.height || bounds.width + 1f < it.size.width ||
                it.didOverflowHeight || (0 until it.lineCount).any { line -> it.isLineEllipsized(line) }
        })
    }

    @Test fun selectingAnotherSectionResetsUnlockSequence() {
        state = LauncherState()
        screen()
        val info = compose.onNode(hasText("實機資訊") and hasClickAction())
        val display = compose.onNode(hasText("顯示與亮度") and hasClickAction())
        info.performScrollTo().performClick()
        repeat(2) { info.performClick() }
        display.performScrollTo().performClick()
        info.performScrollTo().performClick()
        repeat(3) { info.performClick() }
        compose.runOnIdle { assertFalse(state.characterThemesUnlocked) }
        info.performClick()
        compose.runOnIdle { org.junit.Assert.assertTrue(state.characterThemesUnlocked) }
    }

    @Test fun legacyCharacterThemeRemainsSelected() {
        state = LauncherState(visualStyle = "spiderman")
        screen()
        compose.onNode(styleLabel("蜘蛛人")).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("隱藏角色主題").performScrollTo().performClick()
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { org.junit.Assert.assertEquals("spiderman", state.visualStyle) }
    }

    @Test fun unlockAndRehideAt125Percent() {
        state = LauncherState()
        screen()
        compose.onNode(styleLabel("布丁狗")).assertDoesNotExist()
        compose.onNode(styleLabel("蜘蛛人")).assertDoesNotExist()
        compose.onNode(hasText("實機資訊") and hasClickAction()).performScrollTo().performClick()
        repeat(4) { compose.onNode(hasText("實機資訊") and hasClickAction()).performClick() }
        compose.onNode(hasText("顯示與亮度") and hasClickAction()).performScrollTo().performClick()
        compose.onNode(styleLabel("布丁狗")).performScrollTo()
        fullLabel("布丁狗"); fullLabel("蜘蛛人")
        compose.onNodeWithText("隱藏角色主題").performScrollTo()
        fullLabel("隱藏角色主題")
        snapshot("theme-unlocked-125")
        compose.onNode(styleLabel("蜘蛛人")).performScrollTo().performClick()
        compose.onNodeWithText("隱藏角色主題").performClick()
        fullLabel("隱藏角色主題？")
        fullLabel("切回並隱藏")
        snapshot("theme-rehide-confirm-125", dialog = true)
        compose.onNodeWithText("取消").performClick()
        compose.onNode(styleLabel("蜘蛛人")).assertExists()
        compose.onNodeWithText("隱藏角色主題").performClick()
        compose.onNodeWithText("切回並隱藏").performClick()
        compose.onNode(styleLabel("蜘蛛人")).assertDoesNotExist()
        compose.onNode(styleLabel("原有")).performScrollTo()
        fullLabel("原有")
    }
}
