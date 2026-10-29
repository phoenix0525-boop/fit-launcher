package tw.fitcar.launcher.nav

object NavigationKingShortcut {
    // Confirmed by the user's 0.4.22 device report, not an inferred private interface.
    const val VERIFIED_PACKAGE = "com.kingwaytek.a5i_3d.plus"
    fun matches(label: String, packageName: String) = packageName == VERIFIED_PACKAGE || label.contains("導航王")
}
