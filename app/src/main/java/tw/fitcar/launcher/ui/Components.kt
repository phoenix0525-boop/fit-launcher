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
import androidx.compose.runtime.getValue
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

/** 使用者提供的靜態主題插畫；不蓋住地圖、文字或觸控區域。 */
internal enum class ThemeArtPose(val drawable: Int, val description: String, val spiderDrawable: Int, val spiderDescription: String) {
    SHORTCUTS(tw.fitcar.launcher.R.drawable.pompompurin, "布丁狗主題插畫", tw.fitcar.launcher.R.drawable.spiderman_crouch, "蜘蛛人蹲姿插畫"),
    MUSIC(tw.fitcar.launcher.R.drawable.pompompurin_sleep, "布丁狗趴睡插畫", tw.fitcar.launcher.R.drawable.spiderman_swing, "蜘蛛人飛躍插畫"),
    APPS(tw.fitcar.launcher.R.drawable.pompompurin_cheer, "布丁狗活力插畫", tw.fitcar.launcher.R.drawable.spiderman_perch, "蜘蛛人蓄勢插畫"),
    CLOCK(tw.fitcar.launcher.R.drawable.pompompurin_thanks, "布丁狗感謝插畫", tw.fitcar.launcher.R.drawable.spiderman_reach, "蜘蛛人伸手插畫"),
}

@Composable
internal fun ThemeArtwork(size: Dp = 64.dp, pose: ThemeArtPose = ThemeArtPose.SHORTCUTS) {
    val spider = LocalPalette.current.artworkStyle == VisualStyle.SPIDERMAN
    Image(painter = androidx.compose.ui.res.painterResource(if (spider) pose.spiderDrawable else pose.drawable),
        contentDescription = if (spider) pose.spiderDescription else pose.description, modifier = Modifier.size(size).clip(RadiusSm))
}

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
            .background(p.surface.copy(alpha = if (LocalWallpaperActive.current) .96f else 1f))
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

/** 次要說明文字。行車中（[LocalDriving]）預設隱藏；必要的提示傳 essential = true。 */
@Composable
fun Hint(text: String, modifier: Modifier = Modifier, color: Color? = null, size: Int = 14, essential: Boolean = false) {
    if (LocalDriving.current && !essential) return
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
        Text(text, fontSize = if (LocalDriving.current) DriveText.BUTTON.sp else if (compact) 16.sp else 18.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
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
        Text(text, fontSize = if (LocalDriving.current) DriveText.BUTTON.sp else if (compact) 16.sp else 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
    }
}

/** 圖示點陣圖快取：同一個 Drawable 只轉換一次，切頁與返回主畫面不再重算。 */
private object IconCache {
    private val cache = android.util.LruCache<Drawable, ImageBitmap>(160)
    fun get(d: Drawable): ImageBitmap? = cache.get(d)
    fun put(d: Drawable, b: ImageBitmap) { cache.put(d, b) }
}

@Composable
fun AppIcon(drawable: Drawable?, size: Dp, modifier: Modifier = Modifier) {
    // 轉點陣圖改在背景執行緒，第一次開「所有 App」或翻捷徑頁時不卡主執行緒
    val bitmap: ImageBitmap? by androidx.compose.runtime.produceState(drawable?.let { IconCache.get(it) }, drawable) {
        val d = drawable ?: run { value = null; return@produceState }
        if (value == null) value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            try { d.toBitmap(192, 192).asImageBitmap().also { IconCache.put(d, it) } } catch (e: Exception) { null }
        }
    }
    val renderedBitmap = bitmap
    if (renderedBitmap != null) {
        Image(bitmap = renderedBitmap, contentDescription = null, modifier = modifier.size(size))
    } else {
        val p = LocalPalette.current
        Box(modifier.size(size).clip(RadiusMd).background(p.surfaceMuted))
    }
}

/* ---------- 線條圖示（與原型 SVG 同風格：1.8 粗細、圓端點） ---------- */

enum class Glyph { PLAY, PAUSE, PREV, NEXT, GRID, GEAR, HOME, PLUS, EXPAND, NAV, MUSIC, SHIELD, BACK, VOLUME }

@Composable
fun GlyphIcon(glyph: Glyph, modifier: Modifier = Modifier, size: Dp = 26.dp, tint: Color = LocalPalette.current.text) {
    Canvas(modifier.size(size)) {
        val u = this.size.minDimension / 24f
        fun o(x: Float, y: Float) = Offset(x * u, y * u)
        val stroke = Stroke(width = 1.9f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun path(block: Path.() -> Unit) = Path().apply(block)
        when (glyph) {
            Glyph.VOLUME -> {
                drawPath(path { moveTo(4 * u, 9 * u); lineTo(8 * u, 9 * u); lineTo(13 * u, 5 * u)
                    lineTo(13 * u, 19 * u); lineTo(8 * u, 15 * u); lineTo(4 * u, 15 * u); close() }, tint, style = stroke)
                drawPath(path { moveTo(17 * u, 8 * u); quadraticBezierTo(21 * u, 12 * u, 17 * u, 16 * u) }, tint, style = stroke)
            }
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
