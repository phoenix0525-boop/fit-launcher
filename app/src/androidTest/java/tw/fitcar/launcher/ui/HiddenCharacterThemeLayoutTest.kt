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

    private fun snapshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.getExternalFilesDir(null), "$name.png")
        file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        val shell = InstrumentationRegistry.getInstrumentation().uiAutomation
        shell.executeShellCommand("mkdir -p /sdcard/Download/fit-ui").use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
        shell.executeShellCommand("cp ${file.absolutePath} /sdcard/Download/fit-ui/$name.png").use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
    }

    private fun fullLabel(text: String) {
        val node = compose.onNodeWithText(text, useUnmergedTree = true)
        node.assertIsDisplayed()
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val bounds = node.fetchSemanticsNode().boundsInRoot
        assertFalse("文字被裁切：$text", layouts.isEmpty() || layouts.any {
            bounds.height + 1f < it.size.height || bounds.width + 1f < it.size.width ||
                it.didOverflowHeight || (0 until it.lineCount).any { line -> it.isLineEllipsized(line) }
        })
    }

    @Test fun unlockAndRehideAt125Percent() {
        state = LauncherState()
        screen()
        compose.onNodeWithText("布丁狗").assertDoesNotExist()
        compose.onNodeWithText("蜘蛛人").assertDoesNotExist()
        compose.onNodeWithText("實機資訊").performScrollTo().performClick()
        repeat(4) { compose.onNodeWithText("實機資訊").performClick() }
        compose.onNodeWithText("顯示與亮度").performScrollTo().performClick()
        compose.onNodeWithText("布丁狗").performScrollTo()
        fullLabel("布丁狗"); fullLabel("蜘蛛人")
        compose.onNodeWithText("隱藏角色主題").performScrollTo()
        fullLabel("隱藏角色主題")
        snapshot("theme-unlocked-125")
        compose.onNodeWithText("蜘蛛人").performScrollTo().performClick()
        compose.onNodeWithText("隱藏角色主題").performClick()
        fullLabel("隱藏角色主題？")
        fullLabel("切回並隱藏")
        snapshot("theme-rehide-confirm-125")
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("蜘蛛人").assertExists()
        compose.onNodeWithText("隱藏角色主題").performClick()
        compose.onNodeWithText("切回並隱藏").performClick()
        compose.onNodeWithText("蜘蛛人").assertDoesNotExist()
        compose.onNodeWithText("原有").performScrollTo()
        fullLabel("原有")
    }
}
