package tw.fitcar.launcher.system

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

enum class AudioProbeMethod(val label: String) {
    ABSOLUTE("桌面絕對設定：降低一格"), RELATIVE("桌面相對調整：降低一格"),
    CURRENT("系統目前音量：降低一次"),
    ASSISTANT("小芳／小方：20 秒觀察"), KEYS("原廠按鍵：20 秒觀察");
    val observesOnly get() = this == ASSISTANT || this == KEYS
}

/** Output device types are available outputs, not proof of the currently routed amplifier. */
data class AudioReading(val streams: Map<String, Int>, val minimumMusic: Int, val maximumMusic: Int,
    val fixed: Boolean, val mode: Int, val musicActive: Boolean, val outputTypes: List<Int>,
    val mutedStreams: Set<String> = emptySet()) {
    val music get() = streams.getValue("MUSIC")
    fun text() = "${streams.entries.joinToString { "${it.key}=${it.value}" }}; MUSIC範圍=$minimumMusic..$maximumMusic; fixed=$fixed; mode=$mode; musicActive=$musicActive; 靜音流=${mutedStreams.sorted()}; 可用輸出類型=$outputTypes（非實際路由證明）"
}
data class AudioProbeSample(val elapsedMs: Long, val reading: AudioReading)
interface AudioProbePort {
    fun read(): AudioReading
    fun setMusic(target: Int)
    fun lowerMusic()
    fun lowerCurrent()
}
data class AudioProbeReport(val method: AudioProbeMethod, val samples: List<AudioProbeSample>, val error: String? = null,
    val skipReason: String? = null, val requestAttempted: Boolean = false) {
    val verifiesVehicleSound get() = false
    fun summary(): String {
        if (error != null) return "檢測未完成：$error；不能判定控制有效或故障原因。"
        if (skipReason != null) return "未送出音量請求：$skipReason"
        val before = samples.firstOrNull()?.reading ?: return "尚無樣本，不能判定。"
        val after = samples.last().reading
        if (samples.any { it.reading.mode != before.mode || it.reading.musicActive != before.musicActive }) {
            return "觀察期間播放活動或音訊模式變動；不能把取樣變化直接歸因為調整音量。請另行回報實際聲音。"
        }
        if (method.observesOnly || method == AudioProbeMethod.CURRENT) {
            val changed = samples.any { it.reading.streams != before.streams }
            val mutedChanged = samples.any { it.reading.mutedStreams != before.mutedStreams }
            return (if (method == AudioProbeMethod.CURRENT) "已呼叫系統目前音量降低一次。" else "") +
                (if (changed) "觀察期間 Android 音量數值有變化。" else "未觀察到 Android 音量數值變化。") +
                (if (mutedChanged) "靜音旗標有變化。" else "") +
                "不能據此判定實際聲音是否改變，請另行回報；無法僅憑此判定原廠介面、權限或硬體原因。"
        }
        val target = (before.music - 1).coerceAtLeast(before.minimumMusic)
        val reachedEarly = samples.drop(1).dropLast(1).any { it.reading.music == target }
        return VolumeVerification.check(before.music, target, if (reachedEarly) target else before.music,
            after.music, before.fixed).message
    }
    fun text() = "${method.label}\n請求曾嘗試=$requestAttempted（非成功確認）\n${summary()}\n" + samples.joinToString("\n") { "+${it.elapsedMs}ms ${it.reading.text()}" }
}

object AudioProbe {
    /** Only explicit button actions lower once; CURRENT lets Android select a stream. No fallback, raise or restore. */
    suspend fun run(method: AudioProbeMethod, port: AudioProbePort,
        pause: suspend (Long) -> Unit = { delay(it) }, clock: () -> Long = { System.nanoTime() / 1_000_000 },
        progress: (Int) -> Unit = {}): AudioProbeReport {
        val samples = mutableListOf<AudioProbeSample>()
        var attempted = false
        val start = clock()
        fun sample() { samples += AudioProbeSample((clock() - start).coerceAtLeast(0), port.read()) }
        try {
            sample()
            val before = samples.first().reading
            if (method == AudioProbeMethod.CURRENT && (before.fixed || before.mode != 0 || !before.musicActive || before.music <= before.minimumMusic)) {
                return AudioProbeReport(method, samples, skipReason = "需非固定音量、一般音訊模式且有音樂播放，Android媒體數值須高於最低值；請結束通話或語音播報後再測。")
            }
            if (!method.observesOnly && !before.fixed && before.music > before.minimumMusic) {
                attempted = true
                when (method) {
                    AudioProbeMethod.ABSOLUTE -> port.setMusic(before.music - 1)
                    AudioProbeMethod.RELATIVE -> port.lowerMusic()
                    AudioProbeMethod.CURRENT -> port.lowerCurrent()
                    else -> error("observation cannot write")
                }
            }
            val intervals = if (method.observesOnly) List(20) { 1000L } else listOf(150L, 450L, 900L)
            intervals.forEachIndexed { i, ms -> pause(ms); sample(); progress(i + 1) }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { return AudioProbeReport(method, samples, e.javaClass.simpleName, requestAttempted = attempted) }
        return AudioProbeReport(method, samples, requestAttempted = attempted)
    }
}
