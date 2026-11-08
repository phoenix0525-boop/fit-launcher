package tw.fitcar.launcher.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import tw.fitcar.launcher.lyrics.LyricsResult
import tw.fitcar.launcher.media.MediaUiState
import kotlin.coroutines.coroutineContext
import kotlin.math.ln
import kotlin.math.sqrt

internal fun shouldShowMechaEnergyBars(result: LyricsResult): Boolean =
    !result.loading && result.lines.none { it.text.isNotBlank() } && result.plain.isNullOrBlank()

internal object MechaEnergyFrameGate {
    const val MIN_FRAME_NANOS = 33_333_334L
    fun shouldAdvance(previous: Long, now: Long): Boolean = now - previous >= MIN_FRAME_NANOS
    fun shouldAnimate(isPlaying: Boolean, visible: Boolean): Boolean = isPlaying && visible
}

internal object FftBands {
    fun levels(fft: ByteArray, bandCount: Int): List<Float> {
        require(bandCount > 0)
        val levels = FloatArray(bandCount)
        val maxBin = (fft.size / 2 - 1).coerceAtLeast(1)
        val logMax = ln(maxBin.toDouble().coerceAtLeast(2.0))
        for (bin in 1..maxBin) {
            val realIndex = bin * 2
            if (realIndex + 1 >= fft.size) break
            val real = fft[realIndex].toInt()
            val imaginary = fft[realIndex + 1].toInt()
            val magnitude = (sqrt((real * real + imaginary * imaginary).toDouble()) / 181.0).toFloat()
            val position = (ln(bin.toDouble()) / logMax).coerceIn(0.0, 0.999999)
            val band = (position * bandCount).toInt().coerceIn(0, bandCount - 1)
            levels[band] = maxOf(levels[band], magnitude.coerceIn(0f, 1f))
        }
        return levels.toList()
    }

    fun smooth(previous: List<Float>, next: List<Float>): List<Float> = next.mapIndexed { index, value ->
        val old = previous.getOrElse(index) { 0f }
        val factor = if (value >= old) 0.72f else 0.24f
        (old + (value - old) * factor).coerceIn(0f, 1f)
    }
}

internal object SpectrumDisplayPolicy {
    fun levels(isPlaying: Boolean, captureAvailable: Boolean, captured: List<Float>): List<Float> =
        if (isPlaying && captureAvailable) captured else List(captured.size) { 0f }
}

private enum class SpectrumStatus { ACTIVE, PERMISSION_REQUIRED, UNAVAILABLE, PAUSED }

private data class SpectrumFrame(
    val levels: List<Float> = List(12) { 0f },
    val status: SpectrumStatus = SpectrumStatus.PAUSED,
)

@Composable
private fun rememberSpectrumFrame(isPlaying: Boolean, permissionGranted: Boolean, homeVisible: Boolean): SpectrumFrame {
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumed by remember { mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME || event == Lifecycle.Event.ON_PAUSE) {
                resumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var frame by remember { mutableStateOf(SpectrumFrame()) }
    LaunchedEffect(isPlaying, permissionGranted, resumed, homeVisible) {
        if (!MechaEnergyFrameGate.shouldAnimate(isPlaying, resumed && homeVisible)) {
            frame = SpectrumFrame(status = SpectrumStatus.PAUSED)
            return@LaunchedEffect
        }
        if (!permissionGranted) {
            frame = SpectrumFrame(status = SpectrumStatus.PERMISSION_REQUIRED)
            return@LaunchedEffect
        }

        withContext(Dispatchers.Default) {
            val visualizer = runCatching {
                Visualizer(0).apply {
                    val range = Visualizer.getCaptureSizeRange()
                    captureSize = 256.coerceIn(range[0], range[1])
                    scalingMode = Visualizer.SCALING_MODE_NORMALIZED
                    enabled = true
                }
            }.getOrNull()
            if (visualizer == null) {
                frame = SpectrumFrame(status = SpectrumStatus.UNAVAILABLE)
                return@withContext
            }
            val bytes = ByteArray(visualizer.captureSize)
            var smoothed = List(12) { 0f }
            try {
                while (coroutineContext.isActive) {
                    if (visualizer.getFft(bytes) == Visualizer.SUCCESS) {
                        smoothed = FftBands.smooth(smoothed, FftBands.levels(bytes, 12))
                        frame = SpectrumFrame(smoothed, SpectrumStatus.ACTIVE)
                    } else {
                        frame = SpectrumFrame(status = SpectrumStatus.UNAVAILABLE)
                    }
                    delay((MechaEnergyFrameGate.MIN_FRAME_NANOS + 999_999L) / 1_000_000L)
                }
            } catch (_: IllegalStateException) {
                frame = SpectrumFrame(status = SpectrumStatus.UNAVAILABLE)
            } finally {
                runCatching { visualizer.enabled = false }
                visualizer.release()
            }
        }
    }
    return frame
}

@Composable
internal fun MechaEnergyBars(media: MediaUiState, status: String) {
    val palette = LocalPalette.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var permissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionGranted = it
    }
    val spectrum = rememberSpectrumFrame(media.isPlaying, permissionGranted, LocalHomeVisible.current)
    val visibleLevels = SpectrumDisplayPolicy.levels(
        media.isPlaying,
        spectrum.status == SpectrumStatus.ACTIVE,
        spectrum.levels,
    )
    val spectrumLabel = when (spectrum.status) {
        SpectrumStatus.ACTIVE -> "真實 FFT，播放中"
        SpectrumStatus.PERMISSION_REQUIRED -> "需要音訊權限才能同步節奏"
        SpectrumStatus.UNAVAILABLE -> "此播放器或車機未提供可讀取的頻譜，靜態待命"
        SpectrumStatus.PAUSED -> "已暫停，能量柱歸零"
    }
    Column(
        Modifier.fillMaxWidth().semantics {
            contentDescription = "機甲能量柱，$spectrumLabel"
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Hint(status, size = 14, essential = true)
        Hint(spectrumLabel, size = 14, essential = true)
        if (spectrum.status == SpectrumStatus.PERMISSION_REQUIRED) {
            SecondaryButton("允許音訊頻譜", { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) })
        }
        Canvas(Modifier.width(220.dp).height(92.dp).padding(vertical = 4.dp)) {
            val count = visibleLevels.size
            val gap = 5.dp.toPx()
            val barWidth = (size.width - gap * (count - 1)) / count
            repeat(count) { index ->
                val height = size.height * visibleLevels[index].coerceIn(0f, 1f)
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
