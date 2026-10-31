package tw.fitcar.launcher.ui

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tw.fitcar.launcher.check.CheckResult
import tw.fitcar.launcher.check.CheckStore
import tw.fitcar.launcher.check.Status
import tw.fitcar.launcher.system.FactoryAppState
import tw.fitcar.launcher.system.OemFinding
import tw.fitcar.launcher.system.OemIntegrationProbe

/** Keep the factory DSP app responsible for hardware tuning until its protocol is verified. */
@Composable
internal fun AudioIntegrationSection(actions: SettingsActions) {
    val context = LocalContext.current
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    Section("音量控制") {
        Hint("本車機的實際音量由原廠主機控制：請用方向盤音量鍵或原廠語音助手。", size = 16)
        Hint("實車已確認桌面以 Android 公開方法調整音量不會改變聲音，所以車機桌面不提供音量滑桿；主畫面音量鈕只顯示說明與原廠入口。", size = 14)
        actions.openTestTab?.let { open ->
            SecondaryButton("開啟音量對照檢測（只讀）", { open(CheckerTabs.index(CheckerTabs.VOLUME)) })
        }
    }
    Section("原廠 DSP／EQ") {
        Hint("保留原廠音效 App 的調整與儲存方式，可從這裡直接開啟。", size = 16)
        // 進入分頁時查一次；開啟前再查一次（可能已被停用或移除）
        var state by remember { mutableStateOf(OemIntegrationProbe.factoryApp(context)) }
        FactoryAppStatusLine(state)
        SecondaryButton("開啟原廠 DSP／EQ", {
            val problem = OemIntegrationProbe.openFactoryApp(context)
            state = OemIntegrationProbe.factoryApp(context)
            if (problem != null) Toast.makeText(context, "$problem。可從「所有 App」選擇車機音效設定。", Toast.LENGTH_LONG).show()
        }, enabled = state == FactoryAppState.AVAILABLE)
        Hint("照片參考：前聲道高通 62 Hz、12 dB/oct，低通直通；Loud 關閉。這是照片中的設定，仍需依喇叭與聽感確認。", size = 14)
        Hint("尚未提供自動套用：先確認參數讀回、備份與還原，再驗證原廠控制方式。", size = 14)
    }
    Section("原廠整合可行性（只讀檢查）") {
        Hint("只讀取系統公開的元件資訊（是否匯出、需要的權限）；不連接原廠服務、不送出任何音量或 DSP 命令。", size = 14)
        var findings by remember { mutableStateOf<List<OemFinding>?>(null) }
        var busy by remember { mutableStateOf(false) }
        SecondaryButton(if (busy) "檢查中…" else "檢查並記入報告", {
            busy = true
            scope.launch {
                val list = runCatching { withContext(Dispatchers.IO) { OemIntegrationProbe.run(context) } }.getOrDefault(emptyList())
                findings = list; busy = false
                if (list.isNotEmpty()) CheckStore.put(context, CheckResult("oem_feasibility", "車機硬體", "原廠整合可行性（只讀）",
                    Status.INFO, list.joinToString("\n") { "${it.title}：${it.verdict}（${it.evidence}）" },
                    "可行性只表示能否連接，協議與讀回仍需實車驗證後才可宣稱可用。"))
            }
        }, enabled = !busy)
        findings?.let { list ->
            if (list.isEmpty()) Hint("查詢失敗，請稍後再試。", size = 16, color = p.danger)
            list.forEach { f ->
                Hint(f.title, size = 16, color = p.text)
                Hint(f.verdict, size = 15)
                Hint("依據：${f.evidence}", size = 14)
            }
        }
    }
}

@Composable
private fun FactoryAppStatusLine(state: FactoryAppState) {
    val p = LocalPalette.current
    androidx.compose.material3.Text("● ${state.text}",
        color = if (state == FactoryAppState.AVAILABLE) p.success else p.danger, fontSize = 15.sp)
}
