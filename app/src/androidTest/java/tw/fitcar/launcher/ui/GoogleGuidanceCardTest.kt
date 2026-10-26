package tw.fitcar.launcher.ui
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import tw.fitcar.launcher.nav.GoogleGuidance
import tw.fitcar.launcher.nav.GoogleGuidanceState
class GoogleGuidanceCardTest {
    @get:Rule val compose = createComposeRule()
    @Test fun expiredTurnAndDistanceDisappear() {
        val state = GoogleGuidanceState(connected = true, activeNavigation = true,
            guidance = GoogleGuidance("300 公尺後右轉", "中山路", "300 公尺", null, "→"), receivedElapsed = 1000)
        compose.setContent { FitTheme(DayPalette) { GoogleGuidanceCard(state, true, 9000, {}) } }
        compose.onNodeWithText("300 公尺後右轉", substring = true).assertDoesNotExist()
        compose.onNodeWithText("已過期", substring = true).assertIsDisplayed()
    }
    @Test fun freshTurnIsDisplayed() {
        val state = GoogleGuidanceState(connected = true, activeNavigation = true,
            guidance = GoogleGuidance("300 公尺後右轉", "中山路", "300 公尺", null, "→"), receivedElapsed = 1000)
        compose.setContent { FitTheme(DayPalette) { GoogleGuidanceCard(state, true, 2000, {}) } }
        compose.onNodeWithText("→ 300 公尺後右轉").assertIsDisplayed()
    }
}
