package tw.fitcar.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import tw.fitcar.launcher.lyrics.LyricsResult
import tw.fitcar.launcher.media.MediaUiState
import kotlin.coroutines.coroutineContext
import kotlin.math.absoluteValue

internal fun shouldShowMechaEnergyBars(result: LyricsResult): Boolean =
    !result.loading && result.lines.none { it.text.isNotBlank() } && result.plain.isNullOrBlank()

internal object MechaEnergyFrameGate {
    const val MIN_FRAME_NANOS = 33_333_334L
    fun shouldAdvance(previous: Long, now: Long): Boolean = now - previous >= MIN_FRAME_NANOS
    fun shouldAnimate(isPlaying: Boolean, visible: Boolean): Boolean = isPlaying && visible
}

@Composable
internal fun MechaEnergyBars(media: MediaUiState, status: String) {
    val palette = LocalPalette.current
    var frame by remember(media.packageName, media.title) { mutableLongStateOf(0L) }
    LaunchedEffect(media.isPlaying, media.packageName, media.title) {
        if (!MechaEnergyFrameGate.shouldAnimate(media.isPlaying, visible = true)) {
            frame = 0L
            return@LaunchedEffect
        }
        var previous = 0L
        while (coroutineContext.isActive) {
            withFrameNanos { now ->
                if (previous == 0L || MechaEnergyFrameGate.shouldAdvance(previous, now)) {
                    previous = now
                    frame++
                }
            }
        }
    }
    val seed = remember(media.packageName, media.title) { (media.packageName.orEmpty() + media.title.orEmpty()).hashCode() }
    Column(
        Modifier.fillMaxWidth().semantics {
            contentDescription = if (media.isPlaying) "機甲能量柱，播放中" else "機甲能量柱，已暫停"
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Hint(status, size = 14, essential = true)
        Canvas(Modifier.width(220.dp).height(92.dp).padding(vertical = 4.dp)) {
            val count = 12
            val gap = 5.dp.toPx()
            val barWidth = (size.width - gap * (count - 1)) / count
            repeat(count) { index ->
                val phase = if (media.isPlaying) frame else 0L
                val mixed = (seed.toLong() * 31L + phase * 17L + index * 43L).hashCode().absoluteValue
                val level = if (media.isPlaying) 0.22f + (mixed % 79) / 100f else 0.16f + (index % 3) * 0.05f
                val height = size.height * level.coerceIn(0.12f, 1f)
                val color = when {
                    index % 4 == 0 -> palette.accentStrong
                    index % 2 == 0 -> palette.accent
                    else -> Color(0xFFE9667C)
                }
                drawRect(color, Offset(index * (barWidth + gap), size.height - height), Size(barWidth, height))
            }
        }
    }
}
