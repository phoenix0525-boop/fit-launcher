package tw.fitcar.launcher.nav
import org.junit.Assert.*
import org.junit.Test
class GoogleGuidanceTest {
    @Test fun readsTurnWithoutUsingTotalTripDistance() {
        val g = GoogleGuidance.parse("300 公尺後右轉", "進入中山路", "5 公里・預計 12:40 抵達")!!
        assertEquals("→", g.arrow)
        assertEquals("300 公尺", g.distance)
        assertEquals("12:40", g.eta)
    }
    @Test fun doesNotInventRightTurnForRampOrArrowForUnknown() {
        assertEquals("", GoogleGuidance.parse("駛入匝道", "沿台 64 線", null)!!.arrow)
        assertNull(GoogleGuidance.parse("離線地圖下載", "300 公尺", null))
        assertNull(GoogleGuidance.parse(null, null, null))
    }
    @Test fun expiredOrDisconnectedHintsCannotBeDisplayed() {
        assertTrue(GuidanceFreshness.isFresh(1000, 8999, true))
        assertFalse(GuidanceFreshness.isFresh(1000, 9000, true))
        assertFalse(GuidanceFreshness.isFresh(1000, 500, true))
        assertFalse(GuidanceFreshness.isFresh(1000, 2000, false))
    }
    @Test fun genericNavigationDoesNotPretendToKnowATurn() {
        val g = GoogleGuidance.parse("Google 地圖導航中", "請看地圖", null)!!
        assertEquals("", g.arrow)
        assertNull(g.distance)
    }
    @Test fun distanceAndRoadRequireTrustedNavigationCategory() {
        assertNull(GoogleGuidance.parse("260 公尺", "測試道路", null))
        val g = GoogleGuidance.parse("260 公尺", "測試道路", null, navigationCategory = true)!!
        assertEquals("260 公尺", g.distance)
        assertEquals("測試道路", g.secondary)
        assertEquals("", g.arrow)
        assertNull(GoogleGuidance.parse(null, "", null, navigationCategory = true))
    }
    @Test fun distanceMayBeInBodyButNeverUsesSubtextTripDistance() {
        val g = GoogleGuidance.parse("地圖", "260 公尺 · 測試道路", "剩餘 5 公里", navigationCategory = true)!!
        assertEquals("260 公尺", g.distance)
        assertEquals("", g.arrow)
    }
}
