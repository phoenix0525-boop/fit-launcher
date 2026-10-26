package tw.fitcar.launcher.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusIndicatorsTest {
    private fun labels(s: SystemStatus) = StatusIndicators.build(s).map { it.label }

    @Test fun wifiWithInternet() {
        val items = StatusIndicators.build(SystemStatus(network = NetworkKind.WIFI, internet = true))
        val wifi = items.first { it.kind == IndicatorKind.NETWORK }
        assertEquals("Wi-Fi", wifi.label)
        assertTrue(wifi.active)
        assertFalse(wifi.warning)
    }

    @Test fun wifiWithoutInternetIsWarning() {
        val wifi = StatusIndicators.build(SystemStatus(network = NetworkKind.WIFI, internet = false)).first { it.kind == IndicatorKind.NETWORK }
        assertTrue(wifi.warning)
        assertEquals("Wi-Fi 無網路", wifi.label)
    }

    @Test fun cellularAndOffline() {
        assertTrue("行動網路" in labels(SystemStatus(network = NetworkKind.CELLULAR, internet = true)))
        val off = StatusIndicators.build(SystemStatus(network = NetworkKind.NONE)).first { it.kind == IndicatorKind.NETWORK }
        assertEquals("離線", off.label)
        assertFalse(off.active)
    }

    @Test fun bluetoothStates() {
        assertTrue("藍牙已連線" in labels(SystemStatus(bluetoothOn = true, bluetoothConnected = true)))
        assertTrue("藍牙" in labels(SystemStatus(bluetoothOn = true, bluetoothConnected = null)))
        val off = StatusIndicators.build(SystemStatus(bluetoothOn = false)).first { it.kind == IndicatorKind.BLUETOOTH }
        assertFalse(off.active)
    }

    @Test fun usbOnlyWhenMounted() {
        assertFalse(StatusIndicators.build(SystemStatus(usbStorage = 0)).any { it.kind == IndicatorKind.USB })
        assertTrue("USB×2" in labels(SystemStatus(usbStorage = 2)))
        assertTrue("USB" in labels(SystemStatus(usbStorage = 1)))
    }

    @Test fun batteryHiddenWhenNotPresent() {
        assertFalse(StatusIndicators.build(SystemStatus(batteryPercent = null)).any { it.kind == IndicatorKind.BATTERY })
        val b = StatusIndicators.build(SystemStatus(batteryPercent = 15, charging = false)).first { it.kind == IndicatorKind.BATTERY }
        assertEquals("15%", b.label)
        assertTrue(b.warning)
        assertTrue("⚡80%" in labels(SystemStatus(batteryPercent = 80, charging = true)))
    }

    @Test fun orderIsStable() {
        val kinds = StatusIndicators.build(SystemStatus(network = NetworkKind.WIFI, internet = true, bluetoothOn = true, usbStorage = 1, batteryPercent = 90)).map { it.kind }
        assertEquals(listOf(IndicatorKind.NETWORK, IndicatorKind.BLUETOOTH, IndicatorKind.USB, IndicatorKind.BATTERY), kinds)
    }
}
