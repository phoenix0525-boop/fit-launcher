package tw.fitcar.launcher.nav

object SplitGeometry {
    /** Evidence of a tall side pane on a landscape display, rather than a top/bottom pane. */
    fun isSideBySide(displayWidth: Int, displayHeight: Int, paneWidth: Int, paneHeight: Int): Boolean =
        displayWidth > displayHeight && paneWidth > 0 && paneHeight > 0 &&
            paneWidth < displayWidth * 0.9 && paneHeight >= displayHeight * 0.75
    /** Anchor our pane to a display edge; a tall floating window or invalid geometry is not a side. */
    fun paneSide(inMultiWindow: Boolean, displayWidth: Int, displayHeight: Int, left: Int, paneWidth: Int, paneHeight: Int): PaneSide {
        if (!inMultiWindow || !isSideBySide(displayWidth,displayHeight,paneWidth,paneHeight)) return PaneSide.UNKNOWN
        val tolerance=maxOf(24.0,displayWidth*0.03).toInt()
        val right=left.toLong()+paneWidth
        if (left < -tolerance || right > displayWidth+tolerance) return PaneSide.UNKNOWN
        return when {
            left <= tolerance && right < displayWidth-tolerance -> PaneSide.LEFT
            right >= displayWidth-tolerance && left > tolerance -> PaneSide.RIGHT
            else -> PaneSide.UNKNOWN
        }
    }
}
