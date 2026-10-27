package tw.fitcar.launcher.nav

import android.content.Intent
import org.junit.Assert.*
import org.junit.Test

class PlaceIntentTest {
    @Test fun googleIntentIsDrivingAndEncodesDestinationAsData() {
        val intent = GoogleNavigation.destinationIntent("桃園 A&B #1")
        assertEquals("com.google.android.apps.maps", intent.`package`)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertTrue(intent.dataString!!.contains("%26B%20%231&mode=d"))
        assertNull(intent.component)
    }
    @Test fun geoRemainsImplicitWithinTheSelectedPackage() {
        val intent = NavLauncher.placeIntent("桃園 A&B #1", "test.maps")
        assertEquals("test.maps", intent.`package`)
        assertNull(intent.component)
        assertEquals("geo", intent.data!!.scheme)
        assertTrue(intent.dataString!!.contains("%26B%20%231"))
    }
}
