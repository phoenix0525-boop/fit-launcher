package tw.fitcar.launcher.ui

import org.junit.Assert.*
import org.junit.Test
import tw.fitcar.launcher.nav.GoogleGuidance
import tw.fitcar.launcher.nav.GoogleGuidanceState

class NavigationPanelPolicyTest {
    @Test fun onlyAnActiveSelectedGoogleNotificationContainsHints() {
        assertTrue(NavigationPanelPolicy.showGoogleHints(true, true))
        assertFalse(NavigationPanelPolicy.showGoogleHints(true, false))
        assertFalse(NavigationPanelPolicy.showGoogleHints(false, true))
        assertFalse(NavigationPanelPolicy.showGoogleHints(false, false))
    }
    @Test fun selectedGoogleKeepsItsPanelBetweenNotificationUpdates() {
        assertTrue(NavigationPanelPolicy.showGooglePanel(true))
        assertFalse(NavigationPanelPolicy.showGooglePanel(false))
    }

    @Test fun bottomStripIsBlankWithoutFreshTrustedGuidance() {
        val guidance = GoogleGuidance("350 公尺後右轉", "中正路三段", "350 公尺", null, "→")
        val fresh = GoogleGuidanceState(
            connected = true,
            activeNavigation = true,
            guidance = guidance,
            receivedElapsed = 1_000,
        )

        assertEquals(guidance, NavigationPanelPolicy.visibleGuidance(true, true, fresh, 2_000, 40f))
        assertNull(NavigationPanelPolicy.visibleGuidance(true, false, fresh, 2_000, 40f))
        assertNull(NavigationPanelPolicy.visibleGuidance(false, true, fresh, 2_000, 40f))
        assertNull(NavigationPanelPolicy.visibleGuidance(true, true, fresh.copy(activeNavigation = false), 2_000, 40f))
        assertNull(NavigationPanelPolicy.visibleGuidance(true, true, fresh, 120_000, 40f))
    }
}
