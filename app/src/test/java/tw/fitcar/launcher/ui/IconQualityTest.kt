package tw.fitcar.launcher.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IconQualityTest {
    @Test
    fun `tiny visible dot is rejected as an unusable app icon`() {
        assertFalse(IconQuality.isUsable(visiblePixels = 100, totalPixels = 10_000))
    }

    @Test
    fun `normally occupied app icon remains usable`() {
        assertTrue(IconQuality.isUsable(visiblePixels = 2_000, totalPixels = 10_000))
    }

    @Test
    fun `empty bitmap is rejected`() {
        assertFalse(IconQuality.isUsable(visiblePixels = 0, totalPixels = 0))
    }
}
