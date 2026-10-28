package tw.fitcar.launcher.nav

import kotlinx.coroutines.flow.MutableStateFlow

enum class NavigationSide(val id: String, val label: String) {
    LEFT("left","導航在左／控制在右"), RIGHT("right","導航在右／控制在左");
    companion object { fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: LEFT }
}
enum class PaneSide { LEFT, RIGHT, UNKNOWN }

/** Only our own window is observed. This is not inspection of another app's window or proof of navigation placement. */
object SplitPlacement {
    data class Observation(val target: NavigationSide, val pane: PaneSide, val at: Long)
    val observation = MutableStateFlow<Observation?>(null)
    fun begin() { observation.value=null }
    fun record(target: NavigationSide, pane: PaneSide) {
        val old=observation.value
        if (old==null || old.target!=target || old.pane!=pane) observation.value=Observation(target,pane,System.currentTimeMillis())
    }
    fun describe(target: NavigationSide, pane: PaneSide): String = target.label+"；"+when(pane) {
        PaneSide.LEFT -> "觀察到本程式側欄在左；導航位置仍需目視確認。"
        PaneSide.RIGHT -> "觀察到本程式側欄在右；導航位置仍需目視確認。"
        PaneSide.UNKNOWN -> "尚未確認側欄左右位置；請用系統分割介面排列並目視確認。"
    }
}
