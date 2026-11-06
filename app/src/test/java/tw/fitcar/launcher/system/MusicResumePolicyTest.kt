package tw.fitcar.launcher.system

import org.junit.Assert.*
import org.junit.Test

class MusicResumePolicyTest {
    private val target = "com.spotify.music"
    private fun attempt(playing: Boolean = true) = MusicResumePolicy(target, playing, 0)

    @Test fun pausedBeforeLeavingNeverResumes() {
        assertEquals(MusicResumePolicy.Action.SKIP, attempt(false).decide(5000, true, target, false, true, false))
    }
    @Test fun sendsOnlyOnePlayEvenIfPlayerRemainsPaused() {
        val p = attempt()
        assertEquals(MusicResumePolicy.Action.PLAY, p.decide(5000, true, target, false, true, false))
        assertEquals(MusicResumePolicy.Action.SKIP, p.decide(6000, true, target, false, true, false))
    }
    @Test fun waitsForAudioAndCameraOrCallToClear() {
        val p = attempt()
        assertEquals(MusicResumePolicy.Action.WAIT, p.decide(5000, true, target, false, true, true))
        assertEquals(MusicResumePolicy.Action.PLAY, p.decide(6000, true, target, false, true, false))
    }
    @Test fun missingSessionFallsBackWithoutLaunchingAnything() {
        val p = attempt()
        assertEquals(MusicResumePolicy.Action.WAIT, p.decide(5000, true, null, false, false, false))
        assertEquals(MusicResumePolicy.Action.MANUAL, p.decide(60000, true, null, false, false, false))
    }
    @Test fun anotherPlayerNeverReceivesPlay() {
        assertEquals(MusicResumePolicy.Action.WAIT, attempt().decide(5000, true, "com.kkbox", false, true, false))
    }
    @Test fun alreadyPlayingDoesNotReceiveAnotherPlay() {
        assertEquals(MusicResumePolicy.Action.SKIP, attempt().decide(5000, true, target, true, true, false))
    }
    @Test fun disablingOrManualPauseCancelsPendingAttempt() {
        val p = attempt()
        p.cancel()
        assertEquals(MusicResumePolicy.Action.SKIP, p.decide(5000, true, target, false, true, false))
        assertEquals(MusicResumePolicy.Action.SKIP, attempt().decide(5000, false, target, false, true, false))
    }
    @Test fun waitsAtLeastThreeSecondsForWakeToSettle() {
        val p = attempt()
        assertEquals(3_000L, MusicResumeDefaults.WAIT_MS)
        assertEquals(MusicResumePolicy.Action.WAIT, p.decide(2_999, true, target, false, true, false))
        assertEquals(MusicResumePolicy.Action.PLAY, p.decide(3_000, true, target, false, true, false))
    }
    @Test fun requestedStartupDefaultsUseLastPlayer() {
        assertTrue(MusicResumeDefaults.AUTO_CONNECT)
        assertTrue(MusicResumeDefaults.AUTO_RESUME)
        assertEquals("last", MusicResumeDefaults.SOURCE)
    }
    @Test fun fixedPlayerMismatchDoesNotResumeLastFollowedPlayer() {
        assertNull(MusicResumePolicy.target("com.kkbox", target, true))
        assertEquals(target, MusicResumePolicy.target(target, target, true))
        assertEquals(target, MusicResumePolicy.target("com.kkbox", target, false))
    }
    // 0.4.39：熄火時車機自動暫停不應取消續播
    @Test fun accOffPauseRightBeforeScreenOffKeepsResumeIntent() {
        assertTrue(MusicResumePolicy.pauseLooksAutomatic(pausedAt = 100_000, offAt = 101_500, manualAt = null))
        assertTrue(MusicResumePolicy.pauseLooksAutomatic(100_000, 115_000, 50_000))
        assertFalse(MusicResumePolicy.pauseLooksAutomatic(100_000, 115_001, null))
        assertFalse(MusicResumePolicy.pauseLooksAutomatic(null, 101_000, null))
        assertFalse(MusicResumePolicy.pauseLooksAutomatic(100_000, 99_000, null))
    }
    @Test fun desktopPauseRightBeforeIsStillManual() {
        assertFalse(MusicResumePolicy.pauseLooksAutomatic(pausedAt = 100_500, offAt = 101_000, manualAt = 100_000))
        assertFalse(MusicResumePolicy.pauseLooksAutomatic(100_000, 101_000, 100_000))
    }
}
