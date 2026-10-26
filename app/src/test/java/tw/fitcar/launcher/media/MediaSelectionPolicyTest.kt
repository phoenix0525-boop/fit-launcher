package tw.fitcar.launcher.media

import android.media.session.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaSelectionPolicyTest {
    private fun session(pkg: String, state: Int = PlaybackState.STATE_PAUSED) =
        SessionInfo(pkg, state, 0L, false)

    @Test fun boundMusicAppNeverFallsBackToAnotherApp() {
        val sessions = listOf(session("com.video", PlaybackState.STATE_PLAYING))
        assertNull(MediaSelectionPolicy.choosePackage("com.kkbox", sessions))
    }

    @Test fun boundMusicAppIsSelectedWhenPresent() {
        val sessions = listOf(session("com.video", PlaybackState.STATE_PLAYING), session("com.kkbox"))
        assertEquals("com.kkbox", MediaSelectionPolicy.choosePackage("com.kkbox", sessions))
    }

    @Test fun unboundLauncherPrefersPlayingSession() {
        val sessions = listOf(session("com.paused"), session("com.playing", PlaybackState.STATE_PLAYING))
        assertEquals("com.playing", MediaSelectionPolicy.choosePackage(null, sessions))
    }
}
