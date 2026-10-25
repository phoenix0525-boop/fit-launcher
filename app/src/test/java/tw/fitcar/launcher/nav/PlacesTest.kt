package tw.fitcar.launcher.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlacesTest {
    @Test fun encodesChineseAddressForGeoIntent() {
        assertEquals("geo:0,0?q=%E6%A1%83%E5%9C%92%E5%B8%82%E5%85%AB%E5%BE%B7%E5%8D%80", Places.geoUri("桃園市八德區"))
    }

    @Test fun spacesBecomePercent20NotPlus() {
        assertEquals("geo:0,0?q=Taipei%20101", Places.geoUri("  Taipei 101 "))
    }

    @Test fun blankAddressGivesNull() {
        assertNull(Places.geoUri("   "))
        assertNull(Places.geoUri(null))
    }

    @Test fun addressIsTrimmedAndLimited() {
        val long = "路".repeat(300)
        assertEquals(Places.MAX_LENGTH, Places.clean(long)!!.length)
        assertEquals("中正路 1 號", Places.clean("  中正路 1 號\n"))
    }
}
