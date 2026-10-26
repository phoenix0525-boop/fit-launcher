package tw.fitcar.launcher.system

import org.junit.Assert.*
import org.junit.Test

class MapFollowPolicyTest {
    private val fix = GpsState(permission = true, providerEnabled = true, latitude = 25.0, longitude = 121.5, fixElapsed = 1_000)
    @Test fun briefOutageKeepsMarkerButNeverClaimsFreshGps() {
        assertFalse(fix.hasRecentPosition(16_000))
        assertTrue(MapFollowPolicy.canHold(fix, 16_000))
        assertFalse(MapFollowPolicy.canHold(fix, 61_000))
        assertFalse(MapFollowPolicy.canHold(fix, 500))
        assertFalse(MapFollowPolicy.canHold(fix.copy(permission = false), 2_000))
        assertFalse(MapFollowPolicy.canHold(fix.copy(providerEnabled = false), 2_000))
    }
    @Test fun highwaySpeedShowsMoreRoadAndZoomIsBounded() {
        assertTrue(MapFollowPolicy.zoom(110f) < MapFollowPolicy.zoom(30f))
        assertEquals(17.0, MapFollowPolicy.zoom(-5f), 0.001)
        assertEquals(14.0, MapFollowPolicy.zoom(200f), 0.001)
        assertEquals(16.0, MapFollowPolicy.zoom(Float.NaN), 0.001)
        assertTrue(kotlin.math.abs(MapFollowPolicy.smoothZoom(17.0, 14.0) - 17.0) <= 0.151)
    }
    @Test fun addressPrefersChineseAndDoesNotInventARoad() {
        assertEquals("桃園市 八德區", RoadGeocoder.chineseLabel(listOf("桃園市", "八德區", "桃園市"), null))
        assertNull(RoadGeocoder.chineseLabel(listOf("Taiwan", "Taoyuan"), null))
        assertEquals("國道一號", RoadGeocoder.chineseLabel(emptyList(), "國道一號"))
    }
}
