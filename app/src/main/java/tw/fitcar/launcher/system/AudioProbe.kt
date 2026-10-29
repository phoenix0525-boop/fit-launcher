package tw.fitcar.launcher.system

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

enum class AudioProbeMethod(val label: String) {
    ABSOLUTE("桌面絕對設定：降低一格"), RELATIVE("桌面相對調整：降低一格"),
    ASSISTANT("小芳／小方：20 秒觀察"), KEYS("原廠按鍵：20 秒觀察");
    val observesOnly get() = this == ASSISTANT || this == KEYS
}

/** Output device types are available outputs, not proof of the currently routed amplifier. */
data class AudioReading(val streams: Map<String, Int>, val minimumMusic: Int, val maximumMusic: Int,
    val fixed: Boolean, val mode: Int, val musicActive: Boolean, val outputTypes: List<Int>) {
    val music get() = streams.getValue("MUSIC")
    fun text() = "${streams.entries.joinToString { "${it.key}=${it.value}" }}; MUSIC範圍=$minimumMusic..$maximumMusic; fixed=$fixed; mode=$mode; musicActive=$musicActive; 可用輸出類型=$outputTypes（非實際路由證明）"
}
data class AudioProbeSample(val elapsedMs: Long, val reading: AudioReading)
interface AudioProbePort {
    fun read(): AudioReading
    fun setMusic(target: Int)
    fun lowerMusic()
}
data class AudioProbeReport(val method: AudioProbeMethod, val samples: List<AudioProbeSample>, val error: String? = null) {
    val verifiesVehicleSound get() = false
    fun summary(): String {
        if (error != null) return "檢測未完成：$error；不能判定控制有效或故障原因。"
        val before = samples.firstOrNull()?.reading ?: return "尚無樣本，不能判定。"
        val after = samples.last().reading
        if (method.observesOnly) {
            val changed = samples.any { it.reading.streams != before.streams }
            return (if (changed) "觀察期間 Android 音量數值有變化。" else "未觀察到 Android 音量數值變化。") +
                "無法僅憑此判定原廠介面、權限或硬體原因；實際聲音請另行回報。"
        }
        val target = (before.music - 1).coerceAtLeast(before.minimumMusic)
        val reachedEarly = samples.drop(1).dropLast(1).any { it.reading.music == target }
        return VolumeVerification.check(before.music, target, if (reachedEarly) target else before.music,
            after.music, before.fixed).message
    }
    fun text() = "${method.label}\n${summary()}\n" + samples.joinToString("\n") { "+${it.elapsedMs}ms ${it.reading.text()}" }
}

object AudioProbe {
    /** Only explicit button actions write, only MUSIC -1, never raise or auto-restore another actor's changes. */
    suspend fun run(method: AudioProbeMethod, port: AudioProbePort,
        pause: suspend (Long) -> Unit = { delay(it) }, clock: () -> Long = { System.nanoTime() / 1_000_000 },
        progress: (Int) -> Unit = {}): AudioProbeReport {
        val samples = mutableListOf<AudioProbeSample>()
        val start = clock()
        fun sample() { samples += AudioProbeSample((clock() - start).coerceAtLeast(0), port.read()) }
        try {
            sample()
            val before = samples.first().reading
            if (!method.observesOnly && !before.fixed && before.music > before.minimumMusic) {
                if (method == AudioProbeMethod.ABSOLUTE) port.setMusic(before.music - 1) else port.lowerMusic()
            }
            val intervals = if (method.observesOnly) List(20) { 1000L } else listOf(150L, 450L, 900L)
            intervals.forEachIndexed { i, ms -> pause(ms); sample(); progress(i + 1) }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { return AudioProbeReport(method, samples, e.javaClass.simpleName) }
        return AudioProbeReport(method, samples)
    }
}
