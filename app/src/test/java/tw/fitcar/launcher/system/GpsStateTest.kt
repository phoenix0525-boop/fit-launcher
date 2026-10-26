package tw.fitcar.launcher.system
import org.junit.Assert.*
import org.junit.Test
class GpsStateTest {
    private val fix = GpsState(permission = true, providerEnabled = true, latitude = 25.0, longitude = 121.5, fixElapsed = 1000)
    @Test fun stationaryFixDoesNotNeedSpeedToPositionMap() { assertTrue(fix.hasRecentPosition(2000)) }
    @Test fun staleOrFutureFixCannotBeCurrentVehiclePosition() {
        assertFalse(fix.hasRecentPosition(11000))
        assertFalse(fix.hasRecentPosition(500))
    }
    @Test fun permissionAndGpsMustBeEnabled() {
        assertFalse(fix.copy(permission = false).hasRecentPosition(2000))
        assertFalse(fix.copy(providerEnabled = false).hasRecentPosition(2000))
    }
}
