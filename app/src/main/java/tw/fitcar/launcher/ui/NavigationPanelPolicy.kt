package tw.fitcar.launcher.ui

import tw.fitcar.launcher.nav.GoogleGuidance
import tw.fitcar.launcher.nav.GoogleGuidanceState

object NavigationPanelPolicy {
    /** Reserve the panel for the selected provider, even between notification updates. The map stays mounted. */
    fun showGooglePanel(googleSelected: Boolean) = googleSelected
    /** Leaving an app does not imply stopping navigation; use the selected provider's notification state. */
    fun showGoogleHints(googleSelected: Boolean, activeNavigation: Boolean) = googleSelected && activeNavigation
    /** The bottom strip is intentionally blank unless a fresh, trusted notification can supply real text. */
    fun visibleGuidance(
        googleSelected: Boolean,
        notificationAccess: Boolean,
        state: GoogleGuidanceState,
        now: Long,
        speedKmh: Float?,
    ): GoogleGuidance? = state.guidance.takeIf {
        googleSelected && notificationAccess && state.activeNavigation && state.fresh(now, speedKmh)
    }
}
