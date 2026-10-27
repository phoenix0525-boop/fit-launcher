package tw.fitcar.launcher.ui
import androidx.compose.runtime.*

/** Scoped to one HomeScreen, shared by its map and bottom toolbar. */
class MapPanelControls {
    var following by mutableStateOf(true)
    var autoZoom by mutableStateOf(true)
    var recenter by mutableIntStateOf(0)
    fun toggleZoom() { autoZoom = !autoZoom; if (autoZoom) following = true }
    fun returnToPosition() { following = true; recenter++ }
}
internal val LocalMapControls = staticCompositionLocalOf { MapPanelControls() }
