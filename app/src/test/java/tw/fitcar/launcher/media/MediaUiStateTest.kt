package tw.fitcar.launcher.media

import android.media.session.PlaybackState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaUiStateTest {
    @Test
    fun controlsAreDisabledWithoutAnActiveSession() {
        val state = MediaUiState(
            actions = PlaybackState.ACTION_PLAY or
                PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                PlaybackState.ACTION_SKIP_TO_NEXT,
        )

        assertFalse(state.canPlayPause)
        assertFalse(state.canSkipPrevious)
        assertFalse(state.canSkipNext)
    }

    @Test
    fun pausedSessionEnablesPlayWhenAdvertised() {
        val state = MediaUiState(
            packageName = "com.example.music",
            playbackState = PlaybackState.STATE_PAUSED,
            actions = PlaybackState.ACTION_PLAY,
        )

        assertTrue(state.canPlayPause)
    }

    @Test
    fun playingSessionRequiresPauseSupport() {
        val unsupported = MediaUiState(
            packageName = "com.example.music",
            playbackState = PlaybackState.STATE_PLAYING,
            actions = PlaybackState.ACTION_PLAY,
        )
        val supported = unsupported.copy(actions = PlaybackState.ACTION_PAUSE)

        assertFalse(unsupported.canPlayPause)
        assertTrue(supported.canPlayPause)
    }

    @Test
    fun skipControlsFollowAdvertisedActions() {
        val state = MediaUiState(
            packageName = "com.example.music",
            actions = PlaybackState.ACTION_SKIP_TO_PREVIOUS,
        )

        assertTrue(state.canSkipPrevious)
        assertFalse(state.canSkipNext)
    }
}
