package tw.fitcar.launcher.ui
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow

data class DesktopDimOptions(val auto: Boolean=true, val percent: Int=35)
object DesktopDimStore {
    val state=MutableStateFlow(DesktopDimOptions())
    val manual=MutableStateFlow<Boolean?>(null)
    val active=MutableStateFlow(false)
    fun initialize(context: Context) {
        val p=context.getSharedPreferences("desktop_dimming",Context.MODE_PRIVATE)
        state.value=DesktopDimOptions(p.getBoolean("auto",true),p.getInt("percent",35).coerceIn(10,100))
    }
    fun save(context: Context, next: DesktopDimOptions) {
        context.getSharedPreferences("desktop_dimming",Context.MODE_PRIVATE).edit().putBoolean("auto",next.auto).putInt("percent",next.percent.coerceIn(10,100)).apply()
        state.value=next
    }
}
@Composable internal fun DesktopDimShortcut() {
    val active by DesktopDimStore.active.collectAsState()
    SecondaryButton(if(active) "恢復亮度" else "桌面調暗",{ DesktopDimStore.manual.value=!active })
}
@Composable internal fun DesktopDimSettings() {
    val context=LocalContext.current
    val options by DesktopDimStore.state.collectAsState()
    Section("桌面自己調暗") {
        Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
            Text("夜間自動調暗",Modifier.weight(1f),color=LocalPalette.current.text,fontSize=20.sp)
            Switch(options.auto,{ DesktopDimStore.save(context,options.copy(auto=it)); DesktopDimStore.manual.value=null },Modifier.semantics { contentDescription="夜間自動調暗" })
        }
        Text("桌面亮度 ${options.percent}%",color=LocalPalette.current.text,fontSize=20.sp)
        Slider(options.percent.toFloat(),{ DesktopDimStore.save(context,options.copy(percent=it.toInt().coerceIn(10,100))) },valueRange=10f..100f)
        Hint("只在車機桌面（含所有App與設定頁）有效；導航王等其他App全螢幕不受影響。這不是修好了車燈調光，也不更改系統背光。")
        Hint("使用既有夜間時段。手動切換維持到下個日夜邊界；桌面視窗調暗優先於系統亮度排程，離開後恢復跟隨系統。")
        SecondaryButton("恢復依時段",{ DesktopDimStore.manual.value=null })
    }
}
