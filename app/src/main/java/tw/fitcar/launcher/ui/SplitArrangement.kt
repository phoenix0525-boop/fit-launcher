package tw.fitcar.launcher.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tw.fitcar.launcher.data.Prefs
import tw.fitcar.launcher.nav.NavigationSide

@Composable
internal fun SplitArrangementEntry() {
    val context=LocalContext.current
    val prefs=remember(context) { Prefs.get(context) }
    val state by prefs.state.collectAsState()
    var open by remember { mutableStateOf(false) }
    SecondaryButton("排列",{ open=true })
    if (open) SplitArrangementDialog(state.splitNavigationSide,
        { side -> prefs.update { it.copy(splitNavigationSide=side) } },{ open=false })
}

@Composable
internal fun SplitArrangementDialog(side: NavigationSide, select: (NavigationSide) -> Unit, close: () -> Unit, openSidebar: (() -> Unit)? = null) {
    val context=LocalContext.current
    val p=LocalPalette.current
    AlertDialog(onDismissRequest=close,containerColor=p.surface,
        title={ Text("分割排列指引",color=p.text,fontSize=22.sp) },
        text={ Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            NavigationSide.entries.forEach { value ->
                Row(Modifier.fillMaxWidth().heightIn(min=56.dp).clickable { select(value) },verticalAlignment=Alignment.CenterVertically) {
                    RadioButton(side==value,{ select(value) })
                    Text(value.label,color=p.text,fontSize=20.sp,modifier=Modifier.weight(1f))
                }
            }
            Text("只記住排列目標，不會自動搬移視窗。",color=p.text,fontSize=18.sp)
            Text("先開啟車機側欄，再用原廠分割介面選擇導航與「車機側欄」。左右相反時，請用系統提供的交換或重新排列功能。",color=p.textSoft,fontSize=16.sp)
            PrimaryButton("開啟車機側欄",{
                if (openSidebar!=null) openSidebar()
                else try {
                    context.startActivity(tw.fitcar.launcher.nav.CompanionEntry.intent(context))
                    close()
                } catch (_: Exception) {
                    android.widget.Toast.makeText(context,"無法開啟車機側欄，請返回桌面後再試。",android.widget.Toast.LENGTH_LONG).show()
                }
            })
            SystemSplitShortcut()
        } },
        confirmButton={ TextButton(onClick=close) { Text("完成",color=p.accent,fontSize=20.sp) } })
}

@Composable
internal fun SplitArrangementSettings() {
    val context=LocalContext.current
    val state by Prefs.get(context).state.collectAsState()
    Section("分割排列（手動）") {
        Text("排列目標：${state.splitNavigationSide.label}",color=LocalPalette.current.text,fontSize=20.sp)
        Hint("預設希望導航在左、控制在右。選擇只作排列指引；位置與比例仍由原廠系統控制，請目視確認。",essential=true)
        SplitArrangementEntry()
    }
}

@Composable
internal fun CompanionHeader(returnToDesktop: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val controls: @Composable () -> Unit = {
            SplitArrangementEntry()
            SecondaryButton("桌面",returnToDesktop,icon={ GlyphIcon(Glyph.HOME,size=22.dp) })
        }
        if (maxWidth<360.dp) Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Clock(big=false)
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) { controls() }
        } else Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {
            Clock(Modifier.weight(1f),big=false)
            controls()
        }
    }
}
