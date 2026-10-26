package tw.fitcar.launcher.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test

class SectionNavLayoutTest {
    @get:Rule val compose = createComposeRule()
    private fun check(width: Int) {
        compose.setContent {
            FitTheme(DayPalette) {
                var selected by remember { mutableIntStateOf(0) }
                Box(Modifier.width(width.dp)) {
                    SectionNavLayout(listOf("顯示", "播放器"), selected, { selected = it }) {
                        Text("section-$selected")
                    }
                }
            }
        }
        compose.onNodeWithText("播放器").assertIsDisplayed().performClick()
        compose.onNodeWithText("section-1").assertIsDisplayed()
    }
    @Test fun wideSidebarKeepsSectionContentAccessible() = check(1100)
    @Test fun narrowTabsKeepSectionContentAccessible() = check(600)
}
