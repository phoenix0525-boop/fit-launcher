package tw.fitcar.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import tw.fitcar.launcher.R

enum class MechaPanel { MUSIC, SHORTCUTS }

fun mechaPanelPalette(base: Palette, panel: MechaPanel): Palette {
    if (base.artworkStyle != VisualStyle.MECHA) return base
    return if (panel == MechaPanel.MUSIC) base.copy(
        accent = Color(0xFFFFAB40), accentStrong = Color(0xFFFFC477), accentSoft = Color(0xFF513312), line = Color(0xFFDCA04D),
    ) else base.copy(
        accent = Color(0xFFE9667C), accentStrong = Color(0xFFFFA8B6), accentSoft = Color(0xFF4B202A), line = Color(0xFFC9546A),
    )
}

@Composable
internal fun MechaPanelColors(panel: MechaPanel, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPalette provides mechaPanelPalette(LocalPalette.current, panel), content = content)
}

/** Newly generated three-cell alpha sheet. Only renders inside an existing header's art bounds. */
@Composable
internal fun MechaArtwork(size: Dp, pose: ThemeArtPose) {
    val index = when (pose) { ThemeArtPose.MUSIC -> 1; ThemeArtPose.SHORTCUTS -> 2; else -> 0 }
    val description = listOf("紫綠機甲插畫", "橘白機甲插畫", "紅黑機甲插畫")[index]
    val bitmap = ImageBitmap.imageResource(R.drawable.mecha_units)
    Canvas(Modifier.size(size).semantics { contentDescription = description }) {
        val cell = bitmap.width / 3
        drawImage(bitmap, srcOffset = IntOffset(cell * index, 0), srcSize = IntSize(cell, bitmap.height),
            dstSize = IntSize(this.size.width.toInt(), this.size.height.toInt()))
    }
}

/** Drawn inside the existing border: no padding, overlay panel, or change to map geometry. */
internal fun Modifier.mechaTrim(color: Color) = drawWithContent {
    drawContent()
    val edge = 2.dp.toPx()
    drawLine(color, Offset(14.dp.toPx(), edge), Offset(70.dp.toPx().coerceAtMost(size.width * .25f), edge), edge)
    repeat(3) { i ->
        val x = size.width - (18 + i * 7).dp.toPx()
        drawLine(color, Offset(x, size.height - 3.dp.toPx()), Offset(x + 3.dp.toPx(), size.height - 6.dp.toPx()), edge)
    }
}
