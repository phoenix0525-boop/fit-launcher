package tw.fitcar.launcher.ui
import org.junit.Assert.*
import org.junit.Test
class MapPanelControlsTest {
    @Test fun recenterKeepsManualZoomButResumesFollowing() {
        val state = MapPanelControls()
        state.toggleZoom(); state.following = false
        state.returnToPosition()
        assertFalse(state.autoZoom); assertTrue(state.following); assertEquals(1, state.recenter)
        state.toggleZoom(); assertTrue(state.autoZoom); assertTrue(state.following)
    }
}
