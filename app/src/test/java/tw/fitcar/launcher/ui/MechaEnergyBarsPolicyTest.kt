package tw.fitcar.launcher.ui

import org.junit.Assert.*
import org.junit.Test
import tw.fitcar.launcher.lyrics.LyricLine
import tw.fitcar.launcher.lyrics.LyricsResult

class MechaEnergyBarsPolicyTest {
    @Test fun onlyMissingLyricsShowEnergyBars() {
        assertTrue(shouldShowMechaEnergyBars(LyricsResult("找不到相符歌詞")))
        assertTrue(shouldShowMechaEnergyBars(LyricsResult("純音樂，無歌詞")))
        assertFalse(shouldShowMechaEnergyBars(LyricsResult("正在查詢歌詞…", loading = true)))
        assertFalse(shouldShowMechaEnergyBars(LyricsResult("文字歌詞", plain = "hello")))
        assertFalse(shouldShowMechaEnergyBars(LyricsResult("同步歌詞", listOf(LyricLine(0, "hello")))))
    }

    @Test fun frameGateNeverExceedsThirtyFramesPerSecond() {
        assertEquals(33_333_334L, MechaEnergyFrameGate.MIN_FRAME_NANOS)
        assertFalse(MechaEnergyFrameGate.shouldAdvance(100L, 33_333_433L))
        assertTrue(MechaEnergyFrameGate.shouldAdvance(100L, 33_333_434L))
    }

    @Test fun pausedPlaybackDoesNotRequestFrames() {
        assertFalse(MechaEnergyFrameGate.shouldAnimate(isPlaying = false, visible = true))
        assertFalse(MechaEnergyFrameGate.shouldAnimate(isPlaying = true, visible = false))
        assertTrue(MechaEnergyFrameGate.shouldAnimate(isPlaying = true, visible = true))
    }

    @Test fun silenceProducesZeroEnergyInsteadOfFakeMotion() {
        assertEquals(List(12) { 0f }, FftBands.levels(ByteArray(256), 12))
    }

    @Test fun lowAndHighFrequenciesLandInDifferentBars() {
        val low = ByteArray(256).apply { this[2] = 120 }
        val high = ByteArray(256).apply { this[240] = 120 }

        val lowLevels = FftBands.levels(low, 12)
        val highLevels = FftBands.levels(high, 12)

        assertTrue(lowLevels.indexOf(lowLevels.maxOrNull()) < 3)
        assertTrue(highLevels.indexOf(highLevels.maxOrNull()) >= 9)
    }

    @Test fun pausedOrUnavailableCaptureAlwaysRendersZero() {
        val captured = List(12) { 0.8f }

        assertEquals(List(12) { 0f }, SpectrumDisplayPolicy.levels(false, true, captured))
        assertEquals(List(12) { 0f }, SpectrumDisplayPolicy.levels(true, false, captured))
        assertEquals(captured, SpectrumDisplayPolicy.levels(true, true, captured))
    }
}
