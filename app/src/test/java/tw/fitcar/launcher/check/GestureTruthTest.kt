package tw.fitcar.launcher.check

import org.junit.Assert.*
import org.junit.Test

class GestureTruthTest {
    @Test fun numericChangeDoesNotProveVehicleSoundOrDriver() {
        val r = GestureTest.evaluate(GestureKind.TWO_DOWN, VolumeSnapshot(36, false), VolumeSnapshot(35, false), true)
        assertEquals(Status.INFO, r.status)
        assertFalse(r.finding.contains("系統層級有處理"))
        assertTrue(r.finding.contains("不代表實際聲音"))
    }
    @Test fun unchangedDoesNotProveGestureOnlyExistsInFactoryHome() {
        val r = GestureTest.evaluate(GestureKind.TWO_DOWN, VolumeSnapshot(36, false), VolumeSnapshot(36, false), true)
        assertEquals(Status.INFO, r.status)
        assertFalse(r.suggestion.contains("表示手勢只存在原廠桌面"))
        assertTrue(r.finding.contains("無法判定"))
    }
}
