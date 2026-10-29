package tw.fitcar.launcher.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import android.graphics.Bitmap
import java.io.File
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import tw.fitcar.launcher.check.CheckStore
import tw.fitcar.launcher.check.Status

class ManualTestButtonsTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @After fun clean() { CheckStore.clearSaved(context) }

    private fun show(onAction: (String) -> String?, onTab: (Int) -> Unit = {}) {
        CheckStore.clearSaved(context)
        compose.setContent {
            val results by CheckStore.results.collectAsState()
            var visible by remember { mutableStateOf(true) }
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.25f)) {
                FitTheme(DayPalette) {
                    Column {
                        SecondaryButton("重新進入檢測頁", { visible = !visible })
                        if (visible) Column(Modifier.verticalScroll(rememberScrollState())) {
                            ManualTab(results, CheckerActions({}, {}, {}, {}, {}, {}, onAction), onTab)
                        }
                    }
                }
            }
        }
    }

    @Test fun openingAppSavesPendingBeforeCallbackAndSurvivesLeavingPage() {
        var called = ""
        show({ action ->
            called = action
            assertEquals(Status.TODO, CheckStore.results.value["m_google_hint"]?.status)
            assertTrue(CheckStore.results.value["m_google_hint"]!!.time > 0)
            null
        })
        compose.onNodeWithText("開啟 Google 地圖測試").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals("google", called)
            assertEquals(Status.TODO, CheckStore.results.value["m_google_hint"]?.status)
            assertTrue(CheckStore.results.value["m_google_hint"]!!.notes.contains("請目視確認"))
        }
        compose.onNodeWithText("重新進入檢測頁").performClick()
        compose.onNodeWithText("重新進入檢測頁").performClick()
        // Pending form is restored from persisted CheckStore after removing its composition.
        compose.onNodeWithText("開啟 Google 地圖測試").performScrollTo()
        compose.runOnIdle {
            assertEquals(CheckStore.version(context), CheckStore.results.value["m_google_hint"]?.appVersion)
            assertEquals(Status.TODO, CheckStore.results.value["m_google_hint"]?.status)
        }
        compose.onNode(hasText("正常") and hasAnyAncestor(hasTestTag("manual-m_google_hint")))
            .performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(Status.PASS, CheckStore.results.value["m_google_hint"]?.status) }
    }

    @Test fun missingAppRetainsFailureInformationWithoutAutomaticPass() {
        show({ "找不到對應 App；請先安裝或在 App 綁定選擇" })
        compose.onNodeWithText("開啟已綁定測速 App 測試").performScrollTo().performClick()
        compose.onNodeWithText("找不到對應 App；請先安裝或在 App 綁定選擇").performScrollTo().assertIsDisplayed()
        screenshot("manual-test-missing-app-125")
        compose.runOnIdle {
            val result = CheckStore.results.value["m_shield"]!!
            assertEquals(Status.TODO, result.status)
            assertTrue(result.notes.contains("找不到對應 App"))
        }
    }

    private fun screenshot(name: String) {
        val file = File(context.getExternalFilesDir(null), "$name.png")
        file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        val shell = InstrumentationRegistry.getInstrumentation().uiAutomation
        shell.executeShellCommand("mkdir -p /sdcard/Download/fit-ui").use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
        shell.executeShellCommand("cp ${file.absolutePath} /sdcard/Download/fit-ui/$name.png").use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
    }

    @Test fun brightnessShortcutOnlyNavigatesWithoutStartingHardwareTest() {
        var target = -1
        var actionCalls = 0
        show({ actionCalls++; null }, { target = it })
        compose.onNodeWithText("測試亮度（4 秒後恢復）").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(4, target); assertEquals(0, actionCalls) }
    }
}
