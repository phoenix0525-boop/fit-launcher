package tw.fitcar.launcher.ui

import android.graphics.drawable.Drawable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap

val RadiusLg = RoundedCornerShape(24.dp)
val RadiusMd = RoundedCornerShape(16.dp)
val RadiusSm = RoundedCornerShape(10.dp)

/** 原型的卡片：細分隔線、適度圓角、無陰影特效。 */
@Composable
fun FitCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = LocalPalette.current
    Column(
        modifier
            .clip(RadiusLg)
            .background(p.surface)
            .border(1.dp, p.line, RadiusLg)
            .padding(padding),
        content = content,
    )
}

@Composable
fun CardTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    val p = LocalPalette.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text, color = p.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun Hint(text: String, modifier: Modifier = Modifier, color: Color? = null, size: Int = 14) {
    Text(text, modifier = modifier, color = color ?: LocalPalette.current.textSoft, fontSize = size.sp, lineHeight = (size + 6).sp)
}

/** 大觸控範圍主要按鈕（高度 ≥ 56dp）。 */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: (@Composable () -> Unit)? = null) {
    val p = LocalPalette.current
    val compact = LocalLayoutSpec.current.compactChrome
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 56.dp),
        shape = RadiusMd,
        contentPadding = PaddingValues(horizontal = if (compact) 12.dp else 18.dp, vertical = 10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = p.accent,
            contentColor = if (p.isNight) p.bgDeep else Color.White,
            disabledContainerColor = p.surfaceMuted,
            disabledContentColor = p.textSoft,
        ),
    ) {
        if (icon != null && !compact) { icon(); Box(Modifier.size(10.dp)) }
        Text(text, fontSize = if (compact) 16.sp else 18.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: (@Composable () -> Unit)? = null) {
    val p = LocalPalette.current
    val compact = LocalLayoutSpec.current.compactChrome
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 56.dp),
        shape = RadiusMd,
        border = BorderStroke(1.dp, p.line),
        contentPadding = PaddingValues(horizontal = if (compact) 12.dp else 16.dp, vertical = 10.dp),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = p.surfaceStrong, contentColor = p.text, disabledContentColor = p.textSoft),
    ) {
        if (icon != null && !compact) { icon(); Box(Modifier.size(10.dp)) }
        Text(text, fontSize = if (compact) 16.sp else 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
    }
}

@Composable
fun AppIcon(drawable: Drawable?, size: Dp, modifier: Modifier = Modifier) {
    val bitmap: ImageBitmap? = remember(drawable) {
        drawable?.let {
            try { it.toBitmap(192, 192).asImageBitmap() } catch (e: Exception) { null }
        }
    }
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, modifier = modifier.size(size))
    } else {
        val p = LocalPalette.current
        Box(modifier.size(size).clip(RadiusMd).background(p.surfaceMuted))
    }
}

/* ---------- 線條圖示（與原型 SVG 同風格：1.8 粗細、圓端點） ---------- */

enum class Glyph { PLAY, PAUSE, PREV, NEXT, GRID, GEAR, HOME, PLUS, EXPAND, NAV, MUSIC, SHIELD, BACK }

@Composable
fun GlyphIcon(glyph: Glyph, modifier: Modifier = Modifier, size: Dp = 26.dp, tint: Color = LocalPalette.current.text) {
    Canvas(modifier.size(size)) {
        val u = this.size.minDimension / 24f
        fun o(x: Float, y: Float) = Offset(x * u, y * u)
        val stroke = Stroke(width = 1.9f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun path(block: Path.() -> Unit) = Path().apply(block)
        when (glyph) {
            Glyph.PLAY -> drawPath(path { moveTo(8 * u, 5 * u); lineTo(19 * u, 12 * u); lineTo(8 * u, 19 * u); close() }, tint)
            Glyph.PAUSE -> {
                drawLine(tint, o(8f, 5f), o(8f, 19f), 3f * u, StrokeCap.Round)
                drawLine(tint, o(16f, 5f), o(16f, 19f), 3f * u, StrokeCap.Round)
            }
            Glyph.PREV -> {
                drawPath(path { moveTo(15 * u, 6 * u); lineTo(9 * u, 12 * u); lineTo(15 * u, 18 * u) }, tint, style = stroke)
                drawLine(tint, o(7f, 6f), o(7f, 18f), 1.9f * u, StrokeCap.Round)
            }
            Glyph.NEXT -> {
                drawPath(path { moveTo(9 * u, 6 * u); lineTo(15 * u, 12 * u); lineTo(9 * u, 18 * u) }, tint, style = stroke)
                drawLine(tint, o(17f, 6f), o(17f, 18f), 1.9f * u, StrokeCap.Round)
            }
            Glyph.GRID -> listOf(4f to 4f, 14f to 4f, 4f to 14f, 14f to 14f).forEach { (x, y) ->
                drawRoundRect(tint, o(x, y), androidx.compose.ui.geometry.Size(6 * u, 6 * u), androidx.compose.ui.geometry.CornerRadius(u, u), style = stroke)
            }
            Glyph.GEAR -> {
                drawCircle(tint, 3.2f * u, o(12f, 12f), style = stroke)
                drawCircle(tint, 8f * u, o(12f, 12f), style = stroke)
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0)
                    val c = Math.cos(a).toFloat(); val s = Math.sin(a).toFloat()
                    drawLine(tint, o(12 + 8 * c, 12 + 8 * s), o(12 + 10.5f * c, 12 + 10.5f * s), 2.4f * u, StrokeCap.Round)
                }
            }
            Glyph.HOME -> drawPath(path {
                moveTo(4 * u, 11 * u); lineTo(12 * u, 4 * u); lineTo(20 * u, 11 * u)
                moveTo(6 * u, 9.5f * u); lineTo(6 * u, 20 * u); lineTo(18 * u, 20 * u); lineTo(18 * u, 9.5f * u)
            }, tint, style = stroke)
            Glyph.PLUS -> {
                drawLine(tint, o(12f, 5f), o(12f, 19f), 2f * u, StrokeCap.Round)
                drawLine(tint, o(5f, 12f), o(19f, 12f), 2f * u, StrokeCap.Round)
            }
            Glyph.EXPAND -> drawPath(path {
                moveTo(8 * u, 3 * u); lineTo(3 * u, 3 * u); lineTo(3 * u, 8 * u)
                moveTo(16 * u, 3 * u); lineTo(21 * u, 3 * u); lineTo(21 * u, 8 * u)
                moveTo(8 * u, 21 * u); lineTo(3 * u, 21 * u); lineTo(3 * u, 16 * u)
                moveTo(16 * u, 21 * u); lineTo(21 * u, 21 * u); lineTo(21 * u, 16 * u)
            }, tint, style = stroke)
            Glyph.NAV -> drawPath(path { moveTo(12 * u, 3 * u); lineTo(19 * u, 20 * u); lineTo(12 * u, 16 * u); lineTo(5 * u, 20 * u); close() }, tint, style = stroke)
            Glyph.MUSIC -> {
                drawPath(path { moveTo(9 * u, 18 * u); lineTo(9 * u, 5 * u); lineTo(20 * u, 3 * u); lineTo(20 * u, 16 * u) }, tint, style = stroke)
                drawCircle(tint, 2.6f * u, o(6.5f, 18f), style = stroke)
                drawCircle(tint, 2.6f * u, o(17.5f, 16f), style = stroke)
            }
            Glyph.SHIELD -> drawPath(path {
                moveTo(12 * u, 3 * u); lineTo(19 * u, 6 * u); lineTo(19 * u, 11 * u)
                cubicTo(19 * u, 16 * u, 16 * u, 19.5f * u, 12 * u, 21 * u)
                cubicTo(8 * u, 19.5f * u, 5 * u, 16 * u, 5 * u, 11 * u)
                lineTo(5 * u, 6 * u); close()
            }, tint, style = stroke)
            Glyph.BACK -> drawPath(path { moveTo(15 * u, 5 * u); lineTo(8 * u, 12 * u); lineTo(15 * u, 19 * u) }, tint, style = stroke)
        }
    }
}

@Composable
fun Pill(text: String, modifier: Modifier = Modifier, color: Color? = null) {
    val p = LocalPalette.current
    Text(
        text,
        modifier = modifier.clip(RoundedCornerShape(50)).background(p.surfaceMuted).padding(horizontal = 12.dp, vertical = 6.dp),
        color = color ?: p.textSoft,
        fontSize = 14.sp,
        maxLines = 1,
    )
}

@Composable
fun Section(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    Column(
        modifier.clip(RadiusMd).background(p.surfaceStrong).border(1.dp, p.line, RadiusMd).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, color = p.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        content()
    }
}
