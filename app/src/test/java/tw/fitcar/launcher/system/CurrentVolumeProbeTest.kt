package tw.fitcar.launcher.system

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CurrentVolumeProbeTest {
    private class Port : AudioProbePort {
        var reading = AudioReading(mapOf("MUSIC" to 36), 0, 36, false, 0, true, listOf(2))
        val calls = mutableListOf<String>()
        override fun read() = reading
        override fun setMusic(target: Int) { calls += "absolute" }
        override fun lowerMusic() { calls += "relative" }
        override fun lowerCurrent() { calls += "current" }
    }
    private fun run(p: Port, wait: () -> Unit = {}) = runBlocking {
        var clock = 0L
        AudioProbe.run(AudioProbeMethod.CURRENT, p, pause = { clock += it; wait() }, clock = { clock })
    }
    @Test fun currentVolumeUsesItsOwnSingleCommandWithoutFallback() {
        val p = Port(); val r = run(p)
        assertEquals(listOf("current"), p.calls)
        assertTrue(r.requestAttempted); assertFalse(r.verifiesVehicleSound)
        assertTrue(r.summary().contains("未觀察到 Android 音量數值變化"))
        assertTrue(r.summary().contains("不能據此判定實際聲音"))
    }
    @Test fun callOrNoMusicOrFixedOrMinimumDoesNotSendCommand() {
        val blocked = listOf(
            Port().reading.copy(mode = 3), Port().reading.copy(musicActive = false),
            Port().reading.copy(fixed = true), Port().reading.copy(streams = mapOf("MUSIC" to 0)))
        blocked.forEach { reading ->
            val p = Port().apply { this.reading = reading }; val r = run(p)
            assertTrue(p.calls.isEmpty()); assertFalse(r.requestAttempted)
            assertTrue(r.summary().contains("未送出"))
        }
    }
    @Test fun numericChangeStillCannotVerifyVehicleSound() {
        val p = Port(); val r = run(p) { p.reading = p.reading.copy(streams = mapOf("MUSIC" to 35)) }
        assertTrue(r.summary().contains("Android 音量數值有變化")); assertFalse(r.verifiesVehicleSound)
    }
    @Test fun playbackInterruptionCannotBecomeVolumeEvidence() {
        val p = Port(); val r = run(p) { p.reading = p.reading.copy(musicActive = false, streams = mapOf("MUSIC" to 0)) }
        assertTrue(r.summary().contains("播放活動或音訊模式變動")); assertFalse(r.verifiesVehicleSound)
    }
    @Test fun muteFlagIsRecordedWithoutGuessingWhyItChanged() {
        val p = Port(); val r = run(p) { p.reading = p.reading.copy(mutedStreams = setOf("MUSIC")) }
        assertTrue(r.text().contains("靜音流=[MUSIC]")); assertFalse(r.verifiesVehicleSound)
    }
    @Test fun assistantZeroAnd36WithInactiveMusicIsMarkedConfounded() = runBlocking {
        val p = Port()
        val r = AudioProbe.run(AudioProbeMethod.ASSISTANT, p, pause = {
            p.reading = p.reading.copy(streams = mapOf("MUSIC" to 0), musicActive = false)
        })
        assertTrue(p.calls.isEmpty()); assertFalse(r.requestAttempted)
        assertTrue(r.summary().contains("播放活動或音訊模式變動"))
    }
}
