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
        assertEquals(MusicResumePolicy.Action.WAIT, attempt().decide(1000, true, target, false, true, false))
    }
    @Test fun fixedPlayerMismatchDoesNotResumeLastFollowedPlayer() {
        assertNull(MusicResumePolicy.target("com.kkbox", target, true))
        assertEquals(target, MusicResumePolicy.target(target, target, true))
        assertEquals(target, MusicResumePolicy.target("com.kkbox", target, false))
    }
}
