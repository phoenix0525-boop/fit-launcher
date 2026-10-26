package tw.fitcar.launcher.media

import org.junit.Assert.*
import org.junit.Test

class SessionSelectionTest {
    private val kk = "com.skysoft.kkbox.android"
    private val yt = "com.google.android.apps.youtube.music"
    private val morphe = "app.morphe.android.apps.youtube.music"

    @Test fun pausedPreferredNeverStealsPlayingSourceInFollowMode() {
        listOf(kk, yt, morphe).forEach { preferred ->
            val other = listOf(kk, yt, morphe).first { it != preferred }
            assertEquals(1, SessionSelection.choose(listOf(SessionCandidate(preferred, false), SessionCandidate(other, true)), preferred, true, 0))
        }
    }
    @Test fun exactPackageIdentityIsUsedForAllThreePlayers() {
        val sessions = listOf(kk, yt, morphe).map { SessionCandidate(it, true) }
        listOf(kk, yt, morphe).forEachIndexed { index, pkg ->
            assertEquals(index, SessionSelection.choose(sessions, pkg, true, null))
            assertEquals(index, SessionSelection.choose(sessions, pkg, false, null))
        }
    }
    @Test fun fixedModeDoesNotControlOtherPlayingApps() {
        assertEquals(0, SessionSelection.choose(listOf(SessionCandidate(kk, false), SessionCandidate(yt, true)), kk, false, 1))
        assertNull(SessionSelection.choose(listOf(SessionCandidate(yt, true)), kk, false, null))
    }
    @Test fun unrelatedPausedSessionsAreNeverSelectedOnColdStart() {
        assertNull(SessionSelection.choose(listOf(SessionCandidate(yt, false)), kk, true, null))
    }
    @Test fun pausedFollowingSourceCanResumeUntilAnotherPlayerStarts() {
        assertEquals(1, SessionSelection.choose(listOf(SessionCandidate(kk, false), SessionCandidate(yt, false)), kk, true, 1))
        assertEquals(0, SessionSelection.choose(listOf(SessionCandidate(kk, true), SessionCandidate(yt, false)), kk, true, 1))
    }
    @Test fun playingTokenWinsOverPausedTokenOfSamePackage() {
        assertEquals(1, SessionSelection.choose(listOf(SessionCandidate(kk, false), SessionCandidate(kk, true)), kk, false, null))
    }
    @Test fun existingPlayingTokenIsStableUntilPreferredStarts() {
        val sessions = listOf(SessionCandidate(yt, true), SessionCandidate(morphe, true))
        assertEquals(1, SessionSelection.choose(sessions, kk, true, 1))
        assertEquals(0, SessionSelection.choose(sessions, yt, true, 1))
    }
}
