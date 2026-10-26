package tw.fitcar.launcher.nav

object SplitGeometry {
    /** Evidence of a tall side pane on a landscape display, rather than a top/bottom pane. */
    fun isSideBySide(displayWidth: Int, displayHeight: Int, paneWidth: Int, paneHeight: Int): Boolean =
        displayWidth > displayHeight && paneWidth > 0 && paneHeight > 0 &&
            paneWidth < displayWidth * 0.9 && paneHeight >= displayHeight * 0.75
}
