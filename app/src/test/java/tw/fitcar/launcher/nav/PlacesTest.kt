package tw.fitcar.launcher.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlacesTest {
    @Test fun cleansAndEncodesAddress() {
        assertNull(Places.clean("   "))
        assertEquals("桃園市 八德區", Places.clean("  桃園市   八德區 "))
        assertEquals("geo:0,0?q=%E6%A1%83%E5%9C%92%20A", Places.geoUri("桃園 A"))
        assertNull(Places.geoUri(null))
        assertEquals(Places.MAX_LENGTH, Places.clean("路".repeat(200))!!.length)
    }
}
