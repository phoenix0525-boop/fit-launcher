package tw.fitcar.launcher.system

import kotlinx.coroutines.flow.MutableStateFlow

/** Last observations are evidence, not a claim that a hidden map is still rendering. */
data class MapObservation(val state: String = "尚未測試", val time: Long = 0L)
object MapDiagnostics {
    val base = MutableStateFlow(MapObservation())
    val address = MutableStateFlow(MapObservation())
    fun base(state: String) { base.value = MapObservation(state, System.currentTimeMillis()) }
    fun address(state: String) { address.value = MapObservation(state, System.currentTimeMillis()) }
}
