package tw.fitcar.launcher.ui
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.*
import org.junit.Test
import tw.fitcar.launcher.data.ThemeChoice
import tw.fitcar.launcher.system.DrivingMode
class DrivingClarityTest {
    @Test fun hysteresisAndSavedPreferenceArePreserved() {
        val options=ThemeAppearanceOptions(clarity=ThemeClarity.AIRY)
        var driving=false
        listOf(10f,9f,4f).forEach { driving=DrivingMode.next(driving,it,true,0); assertEquals(.98f,cardOpacity(options.clarity.alpha,driving,options.keepDrivingTransparency),0f) }
        driving=DrivingMode.next(driving,3f,true,0)
        assertFalse(driving)
        assertEquals(.90f,cardOpacity(options.clarity.alpha,driving,false),0f)
        assertEquals(ThemeClarity.AIRY,options.clarity)
        assertEquals(.9f,cardOpacity(.9f,true,true),0f)
    }
    @Test fun movingPrimaryTextContrastExceeds45AgainstBothImageExtremes() {
        VisualStyle.entries.forEach { style -> ThemeChoice.entries.forEach { theme ->
            val p=paletteFor(theme,AccentTheme.HERO_RED,style)
            listOf(Color.Black,Color.White).forEach { bg ->
                val actual=p.surface.copy(alpha=cardOpacity(.90f,true,false)).compositeOver(bg)
                assertTrue(Contrast.ratio(p.text.toArgb().toLong(),actual.toArgb().toLong())>=4.5)
            }
        } }
    }
}
