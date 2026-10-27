package tw.fitcar.launcher.wallpaper

import kotlin.math.roundToInt

data class WallpaperOptions(
    val enabled: Boolean = false, val crop: Boolean = true,
    val x: Float = 0f, val y: Float = 0f, val nightDim: Float = .35f,
    val showArt: Boolean = true,
) {
    fun sanitized() = copy(x = x.takeIf(Float::isFinite)?.coerceIn(-1f, 1f) ?: 0f,
        y = y.takeIf(Float::isFinite)?.coerceIn(-1f, 1f) ?: 0f,
        nightDim = nightDim.takeIf(Float::isFinite)?.coerceIn(0f, .75f) ?: .35f)
}
object WallpaperPolicy {
    const val MAX_BYTES = 20 * 1024 * 1024
    const val MAX_EDGE = 2048
    fun size(w: Int, h: Int): Pair<Int, Int> {
        require(w > 0 && h > 0 && w.toLong() * h <= 120_000_000) { "圖片尺寸過大或無效" }
        val ratio = minOf(1.0, MAX_EDGE.toDouble() / maxOf(w, h))
        return (w * ratio).roundToInt().coerceAtLeast(1) to (h * ratio).roundToInt().coerceAtLeast(1)
    }
}
