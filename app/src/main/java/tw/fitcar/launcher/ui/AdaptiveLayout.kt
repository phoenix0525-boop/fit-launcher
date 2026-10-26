package tw.fitcar.launcher.ui

/**
 * 依螢幕可用尺寸（dp）決定主畫面排列。純邏輯，可做 JVM 單元測試。
 *
 * 參考 Android Auto（Coolwalk）與一般車機桌面的做法：
 * - 一般橫向：左地圖、右側音樂＋捷徑（ZX09 2000×1200）。
 * - 超寬螢幕（1920×720 等）：地圖、音樂、捷徑三欄，避免右欄被壓扁。
 * - 直向或近正方形螢幕：地圖在上、音樂與捷徑在下。
 * - 小螢幕（1024×600、1280×720@240dpi）：狀態列改精簡版，間距縮小。
 */
enum class HomeArrangement { TWO_COLUMN, THREE_COLUMN, STACKED }

data class LayoutSpec(
    val arrangement: HomeArrangement,
    /** 狀態列精簡：按鈕不放圖示、時鐘縮小。 */
    val compactChrome: Boolean,
    /** 高度不足：音樂卡隱藏封面與次要資訊。 */
    val shortHeight: Boolean,
    /** 狀態列是否有空間放「目前位置」。 */
    val showLocationCard: Boolean,
    val outerDp: Float,
    val gapDp: Float,
    val mapWeight: Float,
    val sideWeight: Float,
)

object AdaptiveLayout {
    fun of(widthDp: Float, heightDp: Float): LayoutSpec {
        val w = widthDp.coerceAtLeast(1f)
        val h = heightDp.coerceAtLeast(1f)
        val arrangement = when {
            h >= w * 0.95f -> HomeArrangement.STACKED
            w / h >= 2.2f && w >= 1100f -> HomeArrangement.THREE_COLUMN
            else -> HomeArrangement.TWO_COLUMN
        }
        val compact = w < 1200f || h < 640f
        val small = w < 960f || h < 520f
        return LayoutSpec(
            arrangement = arrangement,
            compactChrome = compact,
            shortHeight = h < 640f,
            showLocationCard = if (arrangement == HomeArrangement.STACKED) w >= 700f else w >= 1000f,
            outerDp = if (small) 8f else if (compact) 12f else 16f,
            gapDp = if (small) 8f else if (compact) 10f else 14f,
            mapWeight = when (arrangement) {
                HomeArrangement.THREE_COLUMN -> 5f
                HomeArrangement.STACKED -> 1.25f
                HomeArrangement.TWO_COLUMN -> if (small) 6f else 6.5f
            },
            sideWeight = when (arrangement) {
                HomeArrangement.THREE_COLUMN -> 3f
                HomeArrangement.STACKED -> 1f
                HomeArrangement.TWO_COLUMN -> if (small) 4f else 3.5f
            },
        )
    }
}
