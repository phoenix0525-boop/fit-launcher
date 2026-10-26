package tw.fitcar.launcher.system

/**
 * 指南針：使用 GPS 行進方向（bearing），不用磁力感測器（車內金屬與喇叭會干擾）。
 * 停車或低速時 GPS 方向不可靠，不顯示。
 */
object Compass {
    const val MIN_SPEED_KMH = 5f
    private val NAMES = listOf("北", "東北", "東", "東南", "南", "西南", "西", "西北")

    fun normalize(deg: Float): Float = ((deg % 360f) + 360f) % 360f

    fun cardinal(deg: Float): String = NAMES[(((normalize(deg) + 22.5f) / 45f).toInt()) % 8]

    fun heading(bearing: Float?, speedKmh: Float?): Float? =
        if (bearing == null || speedKmh == null || speedKmh < MIN_SPEED_KMH) null else normalize(bearing)
}
