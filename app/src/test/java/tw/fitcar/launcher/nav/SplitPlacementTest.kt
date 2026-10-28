package tw.fitcar.launcher.nav

import org.junit.Assert.*
import org.junit.Test

class SplitPlacementTest {
    @Test fun observesOwnPaneEdgesInsteadOfGuessingFromWidth() {
        assertEquals(PaneSide.LEFT, SplitGeometry.paneSide(true,2000,1200,0,650,1120))
        assertEquals(PaneSide.RIGHT, SplitGeometry.paneSide(true,2000,1200,1350,650,1120))
        assertEquals(PaneSide.LEFT, SplitGeometry.paneSide(true,2000,1200,0,1400,1120))
        assertEquals(PaneSide.RIGHT, SplitGeometry.paneSide(true,2000,1200,620,1380,1120))
    }
    @Test fun refusesFullscreenTopBottomFloatingAndInvalidBounds() {
        listOf(
            SplitGeometry.paneSide(false,2000,1200,0,650,1120),
            SplitGeometry.paneSide(true,2000,1200,0,2000,1120),
            SplitGeometry.paneSide(true,2000,1200,0,2000,560),
            SplitGeometry.paneSide(true,2000,1200,400,650,1120),
            SplitGeometry.paneSide(true,2000,1200,-300,650,1120),
            SplitGeometry.paneSide(true,2000,1200,1600,650,1120)
        ).forEach { assertEquals(PaneSide.UNKNOWN,it) }
    }
    @Test fun targetDefaultsLeftAndObservationNeverClaimsNavigationVerified() {
        assertEquals(NavigationSide.LEFT,NavigationSide.fromId(null))
        assertEquals(NavigationSide.LEFT,NavigationSide.fromId("future"))
        val text=SplitPlacement.describe(NavigationSide.LEFT,PaneSide.RIGHT)
        assertTrue(text.contains("側欄在右"))
        assertTrue(text.contains("目視確認"))
        assertFalse(text.contains("導航已在左"))
    }
}
