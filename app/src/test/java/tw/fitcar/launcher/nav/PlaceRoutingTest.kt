package tw.fitcar.launcher.nav

import org.junit.Assert.*
import org.junit.Test

class PlaceRoutingTest {
    @Test fun googleUsesDocumentedDrivingNavigation() {
        assertEquals(PlaceRoute.GOOGLE, PlaceRouting.route("com.google.android.apps.maps", true))
    }
    @Test fun kingwaytekNeverClaimsGeoStartsNavigation() {
        for (accepts in listOf(true, false)) {
            assertEquals(PlaceRoute.CONFIRM, PlaceRouting.route("com.kingwaytek.a5i_3d.plus", accepts))
        }
    }
    @Test fun receiverMustExistBeforeSendingAnAddress() {
        assertEquals(PlaceRoute.CONFIRM, PlaceRouting.route("test.maps", false))
        assertEquals(PlaceRoute.GEO, PlaceRouting.route("test.maps", true))
        assertEquals(PlaceRoute.CONFIRM, PlaceRouting.route(null, false))
    }
}
