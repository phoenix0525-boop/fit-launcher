package tw.fitcar.launcher.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import tw.fitcar.launcher.check.CheckResult
import tw.fitcar.launcher.check.CheckStore
import tw.fitcar.launcher.check.Status
import tw.fitcar.launcher.system.*

/** Never runs automatically on entry; manual observations are explicitly separate from numeric readback. */
@Composable
internal fun AudioDiagnosticsTab(portOverride: AudioProbePort? = null) {
    val context = LocalContext.current
    val port = remember(context, portOverride) { portOverride ?: AndroidAudioProbe(context) }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<AudioProbeMethod?>(null) }
    var progress by remember { mutableIntStateOf(0) }
    var report by remember { mutableStateOf<AudioProbeReport?>(null) }
    var saved by remember { mutableStateOf(false) }
    val results by CheckStore.results.collectAsState()
    fun save(r: AudioProbeReport, audible: String) {
        CheckStore.put(context, CheckResult("audio_probe_${r.method.name}", "操作", r.method.label, Status.INFO,
            r.text(), suggestion = "Android 數值不能證明原廠實際音量或私有控制通道；對照使用者回報後再分析。",
            notes = "實際聲音：$audible（使用者回報，非程式自動驗證）；不錄音、不讀取語音內容。"))
    }
    Section("音量對照檢測") {
        Hint("請停車、保持同一首音樂播放，先用原廠按鍵調到舒適音量。每項分開測，不同播放來源不能直接比較。", size = 16)
        Hint("本次先測「系統目前音量：降低一次」即可。播放音樂、結束通話與語音播報後再按；由系統選擇音量通道，不保證等同方向盤。", size = 16)
        Hint("每次只降低一次，不自動調回；需要時用方向盤調回。小芳與原廠按鍵觀察不送出任何音量控制。", size = 16)
        Hint("開始觀察後，用小芳說「小聲一點」，或按原廠降低音量鍵。20 秒內不要混用其他控制。離開此檢測分頁會取消本次檢測。", size = 16)
        AudioProbeMethod.entries.forEach { method ->
            SecondaryButton(method.label, {
                if (busy == null) {
                    busy = method; progress = 0; report = null; saved = false
                    scope.launch {
                        try {
                            val r = AudioProbe.run(method, port, progress = { progress = it })
                            report = r; save(r, "尚未回報")
                        } finally { busy = null }
                    }
                }
            }, Modifier.fillMaxWidth(), enabled = busy == null)
        }
        busy?.let { Hint("檢測中：${it.label}；已取得 $progress 個後續樣本，尚未完成。", size = 16) }
        report?.let { r ->
            Hint(r.summary(), size = 16, essential = true)
            Hint("請回報這一項測試的實際聲音：", size = 16)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("有變小", "沒有變化", "不確定").forEach { answer ->
                    SecondaryButton(answer, { save(r, answer); saved = true })
                }
            }
            if (saved) Hint("已儲存聲音回報；不代表程式已修復。", size = 16)
        }
        Hint("完成後到「匯出報告」儲存文字檔，再提供報告分析。不錄音、不抓小芳語音內容，不含藍牙位址或裝置序號。", size = 16)
    }
    AudioProbeMethod.entries.mapNotNull { results["audio_probe_${it.name}"] }.forEach { ResultCard(it, null) }
}
