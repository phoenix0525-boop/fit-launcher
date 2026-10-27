package tw.fitcar.launcher.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tw.fitcar.launcher.update.AppUpdates

/** Foreground checks only, at most once per day. No modal interrupts normal driving. */
@Composable
fun AutoUpdateNotice() {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(context, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            delay(5_000)
            while (true) { AppUpdates.check(context); delay(6 * 60 * 60_000L) }
        }
    }
}

@Composable
internal fun UpdateIndicator() {
    val state by AppUpdates.state.collectAsState()
    var open by remember { mutableStateOf(false) }
    if (state.offer != null) SecondaryButton("有更新", { open = true })
    if (open) AlertDialog(onDismissRequest = { open = false },
        title = { Text("車機桌面更新") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { UpdateSection() } },
        confirmButton = { TextButton(onClick = { open = false }) { Text("關閉") } })
}

@Composable
fun UpdateSection() {
    val context = LocalContext.current
    val p = LocalPalette.current
    val state by AppUpdates.state.collectAsState()
    val scope = rememberCoroutineScope()
    var automatic by remember { mutableStateOf(AppUpdates.auto(context)) }
    val compatible = remember { AppUpdates.compatible(context) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("程式更新", color = p.text, fontSize = 22.sp)
        Row(Modifier.fillMaxWidth()) {
            Hint("自動檢查新版（每天一次）", Modifier.weight(1f))
            Switch(checked = automatic, onCheckedChange = { automatic = it; AppUpdates.preferences(context).edit().putBoolean("automatic", it).apply() })
        }
        Hint(state.message)
        state.progress?.let { LinearProgressIndicator(progress = { it / 100f }, modifier = Modifier.fillMaxWidth()); Hint("下載 $it%") }
        SecondaryButton("立即檢查", { scope.launch { AppUpdates.check(context, force = true) } }, enabled = !state.busy)
        if (!compatible) Hint("目前安裝的是不同簽章的測試版。正式更新需沿用既有正式簽章；請勿為更新自行清除資料。", size = 14)
        state.offer?.let { offer ->
            Text("新版 ${offer.version}", color = p.text, fontSize = 18.sp)
            if (offer.notes.isNotBlank()) Hint(offer.notes, size = 14)
            if (state.downloaded != null) PrimaryButton("確認安裝", { AppUpdates.install(context) }, enabled = !state.busy && compatible)
            else PrimaryButton("下載新版", { scope.launch { AppUpdates.download(context) } }, enabled = !state.busy && compatible)
        }
        Hint("只接收專案正式發佈版本；下載後核對檔案雜湊、套件、版本及簽章。安裝需你確認，首次會要求允許此來源。", size = 13)
    }
}
