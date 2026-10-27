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
        assertEquals("桃園市八德區介壽路一段123號", RoadGeocoder.chineseLabel(listOf("桃園市", "八德區", "介壽路一段"), "桃園市八德區介壽路一段123號"))
        assertEquals("桃園市八德區介壽路10之2號", RoadGeocoder.chineseLabel(listOf("桃園市"), "桃園市八德區介壽路10之2號"))
        assertEquals("國道一號", RoadGeocoder.chineseLabel(emptyList(), "國道一號"))
    }
    @Test fun headingUpUsesOnlyFreshMovingGps() {
        val moving = fix.copy(speedKmh = 60f, bearing = 90f)
        assertEquals(90.0, MapFollowPolicy.bearing(35.0, moving, 2000), .001)
        listOf(moving.copy(speedKmh = 4.9f), moving.copy(speedKmh = Float.NaN),
            moving.copy(speedKmh = Float.POSITIVE_INFINITY), moving.copy(bearing = null),
            moving.copy(bearing = Float.NaN), moving.copy(bearing = Float.POSITIVE_INFINITY),
            moving.copy(permission = false), moving.copy(providerEnabled = false)).forEach {
            assertEquals(35.0, MapFollowPolicy.bearing(35.0, it, 2000), .001)
        }
        assertEquals(35.0, MapFollowPolicy.bearing(35.0, moving, 6000), .001)
        assertEquals(35.0, MapFollowPolicy.bearing(35.0, moving, 500), .001)
        assertEquals(0.0, MapFollowPolicy.bearing(Double.NaN, moving.copy(bearing = null), 2000), .001)
    }
    @Test fun bearingCrossesNorthByShortTurnAndRejectsJitter() {
        assertEquals(2.0, MapFollowPolicy.bearingDelta(359.0, 1.0), .001)
        assertEquals(-2.0, MapFollowPolicy.bearingDelta(1.0, 359.0), .001)
        assertEquals(350.0, MapFollowPolicy.bearing(0.0, fix.copy(speedKmh = 30f, bearing = -10f), 2000), .001)
        assertEquals(90.0, MapFollowPolicy.bearing(0.0, fix.copy(speedKmh = 30f, bearing = 450f), 2000), .001)
        assertEquals(90.0, MapFollowPolicy.bearing(90.0, fix.copy(speedKmh = 30f, bearing = 91f), 2000), .001)
        assertEquals(93.0, MapFollowPolicy.bearing(90.0, fix.copy(speedKmh = 30f, bearing = 93f), 2000), .001)
    }

}
