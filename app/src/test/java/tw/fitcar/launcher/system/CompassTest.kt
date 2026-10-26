package tw.fitcar.launcher.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompassTest {
    @Test fun eightWayCardinals() {
        assertEquals("北", Compass.cardinal(0f))
        assertEquals("北", Compass.cardinal(359f))
        assertEquals("北", Compass.cardinal(22.4f))
        assertEquals("東北", Compass.cardinal(22.5f))
        assertEquals("東", Compass.cardinal(90f))
        assertEquals("東南", Compass.cardinal(135f))
        assertEquals("南", Compass.cardinal(180f))
        assertEquals("西南", Compass.cardinal(225f))
        assertEquals("西", Compass.cardinal(270f))
        assertEquals("西北", Compass.cardinal(315f))
    }

    @Test fun normalizesOutOfRange() {
        assertEquals("東", Compass.cardinal(450f))
        assertEquals("西", Compass.cardinal(-90f))
    }

    @Test fun bearingOnlyWhenMoving() {
        assertNull(Compass.heading(bearing = 90f, speedKmh = 2f))
        assertNull(Compass.heading(bearing = null, speedKmh = 40f))
        assertEquals(90f, Compass.heading(bearing = 90f, speedKmh = 40f))
        assertEquals(90f, Compass.heading(bearing = 450f, speedKmh = 40f))
    }
}
