package tw.fitcar.launcher.ui

/** Count consecutive taps on the device information entry; elapsedRealtime is monotonic. */
class HiddenCharacterThemeTaps(private val maxGapMs: Long = 1_500L) {
    private var count = 0
    private var last = -1L

    fun onTap(nowMs: Long): Boolean {
        count = if (last >= 0 && nowMs >= last && nowMs - last <= maxGapMs) count + 1 else 1
        last = nowMs
        if (count < 5) return false
        reset()
        return true
    }

    fun reset() { count = 0; last = -1L }
}

object CharacterThemeAccess {
    fun isCharacter(styleId: String) = styleId == VisualStyle.POMPOMPURIN.id || styleId == VisualStyle.SPIDERMAN.id

    /** Keep an already selected character theme visible, including one restored from backup. */
    fun isUnlocked(saved: Boolean, currentStyleId: String) = saved || isCharacter(currentStyleId)

    fun visibleStyles(saved: Boolean, currentStyleId: String): List<VisualStyle> =
        if (isUnlocked(saved, currentStyleId)) VisualStyle.entries else VisualStyle.entries.filterNot { isCharacter(it.id) }
}
