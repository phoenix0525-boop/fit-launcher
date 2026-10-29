package tw.fitcar.launcher.ui

object NavigationPanelPolicy {
    /** Leaving an app does not imply stopping navigation; use the selected provider's notification state. */
    fun showGoogleHints(googleSelected: Boolean, activeNavigation: Boolean) = googleSelected && activeNavigation
}
