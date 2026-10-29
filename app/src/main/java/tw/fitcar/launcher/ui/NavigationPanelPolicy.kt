package tw.fitcar.launcher.ui

object NavigationPanelPolicy {
    /** Reserve the panel for the selected provider, even between notification updates. The map stays mounted. */
    fun showGooglePanel(googleSelected: Boolean) = googleSelected
    /** Leaving an app does not imply stopping navigation; use the selected provider's notification state. */
    fun showGoogleHints(googleSelected: Boolean, activeNavigation: Boolean) = googleSelected && activeNavigation
}
