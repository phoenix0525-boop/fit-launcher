package tw.fitcar.launcher.ui

import org.junit.Assert.*
import org.junit.Test

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
}
