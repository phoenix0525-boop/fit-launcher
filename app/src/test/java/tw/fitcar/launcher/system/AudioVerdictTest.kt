package tw.fitcar.launcher.system

import org.junit.Assert.*
import org.junit.Test
import tw.fitcar.launcher.check.Status

class AudioVerdictTest {
    private fun reading(v: Int) = AudioReading(mapOf("MUSIC" to v), 0, 40, false, 0, true, listOf(2))
    private fun report(method: AudioProbeMethod, values: List<Int>, attempted: Boolean = !method.observesOnly) =
        AudioProbeReport(method, values.mapIndexed { i, v -> AudioProbeSample(i * 100L, reading(v)) }, requestAttempted = attempted)

    @Test fun controlSentButNoAudibleChangeIsAProblem() {
        val v = AudioVerdicts.of(report(AudioProbeMethod.ABSOLUTE, listOf(36, 35, 35, 35)), AudibleAnswer.UNCHANGED)
        assertEquals(Status.FAIL, v.status)
    }
    @Test fun androidUnchangedButUserHeardQuieterPasses() {
        val v = AudioVerdicts.of(report(AudioProbeMethod.ABSOLUTE, listOf(36, 36, 36, 36)), AudibleAnswer.QUIETER)
        assertEquals(Status.PASS, v.status)
        assertFalse(v.finding.contains("未生效"))
    }
    @Test fun unreportedOrUnsureStaysPendingNotInfo() {
        val r = report(AudioProbeMethod.RELATIVE, listOf(36, 35, 35, 35))
        assertEquals(Status.TODO, AudioVerdicts.of(r, null).status)
        assertEquals(Status.TODO, AudioVerdicts.of(r, AudibleAnswer.UNSURE).status)
    }
    @Test fun reportedAnswerNoLongerAsksToReport() {
        val r = report(AudioProbeMethod.KEYS, List(21) { 36 })
        val pending = AudioVerdicts.of(r, null)
        assertTrue(pending.finding.contains("請另行回報"))
        val answered = AudioVerdicts.of(r, AudibleAnswer.QUIETER)
        assertFalse(answered.finding.contains("請另行回報"))
        assertTrue(answered.finding.contains("有變小"))
        assertEquals(Status.INFO, answered.status)
    }
    @Test fun observationOfFactoryKeysIsNeverOurFailure() {
        val v = AudioVerdicts.of(report(AudioProbeMethod.ASSISTANT, List(21) { 36 }), AudibleAnswer.UNCHANGED)
        assertEquals(Status.INFO, v.status)
    }
    @Test fun noRequestSentCannotFail() {
        val v = AudioVerdicts.of(report(AudioProbeMethod.ABSOLUTE, listOf(0, 0, 0, 0), attempted = false), AudibleAnswer.UNCHANGED)
        assertEquals(Status.TODO, v.status)
    }
    @Test fun probeErrorIsVisibleProblem() {
        val v = AudioVerdicts.of(AudioProbeReport(AudioProbeMethod.CURRENT, emptyList(), error = "SecurityException"), null)
        assertEquals(Status.FAIL, v.status)
    }
    @Test fun numericReadbackNeverClaimsVehicleSound() {
        VolumeReadback.entries.forEach { assertFalse(it.message, it.message.contains("未生效")); assertFalse(it.verifiesVehicleSound) }
    }
}
