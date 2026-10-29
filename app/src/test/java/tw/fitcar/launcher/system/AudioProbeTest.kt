package tw.fitcar.launcher.system

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AudioProbeTest {
    private class Port(var value: Int = 36, val fixed: Boolean = false, val fail: Boolean = false) : AudioProbePort {
        var writes = 0
        override fun read() = AudioReading(mapOf("MUSIC" to value, "SYSTEM" to 7), 0, 40, fixed, 0, true, listOf(2))
        override fun setMusic(target: Int) { writes++; if (fail) throw SecurityException(); value = target }
        override fun lowerMusic() = setMusic(value - 1)
    }
    private fun run(method: AudioProbeMethod, port: Port, wait: (Long) -> Unit = {}): AudioProbeReport = runBlocking {
        var clock = 0L
        AudioProbe.run(method, port, pause = { clock += it; wait(it) }, clock = { clock })
    }
    @Test fun observationNeverWritesAndDoesNotClaimHardwareCause() {
        val p = Port()
        val r = run(AudioProbeMethod.ASSISTANT, p) { p.value-- }
        assertEquals(0, p.writes); assertEquals(21, r.samples.size)
        assertTrue(r.summary().contains("Android 音量數值有變化"))
        assertFalse(r.verifiesVehicleSound)
        assertTrue(r.summary().contains("無法僅憑此判定"))
    }
    @Test fun absoluteOnlyLowersOneStepAndRequiresHumanSoundReport() {
        val p = Port(); val r = run(AudioProbeMethod.ABSOLUTE, p)
        assertEquals(1, p.writes); assertEquals(35, p.value)
        assertEquals(listOf(0L, 150L, 600L, 1500L), r.samples.map { it.elapsedMs })
        assertTrue(r.summary().contains("仍需確認實際聲音")); assertFalse(r.verifiesVehicleSound)
    }
    @Test fun relativeAlsoLowersOneStep() {
        val p = Port(); run(AudioProbeMethod.RELATIVE, p)
        assertEquals(35, p.value); assertEquals(1, p.writes)
    }
    @Test fun fixedAndMinimumDoNotSendCommands() {
        val p = Port(fixed = true); val r = run(AudioProbeMethod.ABSOLUTE, p)
        assertEquals(0, p.writes); assertTrue(r.summary().contains("固定"))
        val low = Port(0); run(AudioProbeMethod.RELATIVE, low); assertEquals(0, low.writes)
    }
    @Test fun blockedWriteIsReportedWithoutDiscardingBaseline() {
        val r = run(AudioProbeMethod.ABSOLUTE, Port(fail = true))
        assertEquals("SecurityException", r.error); assertEquals(36, r.samples.first().reading.music)
        assertTrue(r.summary().contains("未完成")); assertFalse(r.verifiesVehicleSound)
    }
    @Test fun unchangedAndRevertedValuesAreNotSuccess() {
        val p = Port()
        val r = run(AudioProbeMethod.ABSOLUTE, p) { if (it == 900L) p.value = 36 }
        assertTrue(r.summary().contains("改回")); assertFalse(r.verifiesVehicleSound)
        val idle = run(AudioProbeMethod.KEYS, Port())
        assertTrue(idle.summary().contains("未觀察到 Android 音量數值變化"))
    }
    @Test fun cancellationMustNotBecomeACompleteReport() = runBlocking {
        try {
            AudioProbe.run(AudioProbeMethod.KEYS, Port(), pause = { throw kotlinx.coroutines.CancellationException() })
            fail("cancelled run returned a complete report")
        } catch (_: kotlinx.coroutines.CancellationException) { }
    }
}
