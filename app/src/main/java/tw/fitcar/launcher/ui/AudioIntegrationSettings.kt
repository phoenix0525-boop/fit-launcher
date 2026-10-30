package tw.fitcar.launcher.ui

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/** Keep the factory DSP app responsible for hardware tuning until its protocol is verified. */
@Composable
internal fun AudioIntegrationSection(actions: SettingsActions) {
    val context = LocalContext.current
    Section("音量控制") {
        Hint("原廠音量鍵可改變聲音，但 Android 媒體數值可能維持不變。請以實際聽感記錄結果。", size = 16)
        Hint("目前桌面音量控制仍待實機驗證；若沒有作用，請使用原廠音量鍵。", size = 14)
        actions.openTestTab?.let { open ->
            SecondaryButton("開啟音量對照檢測", { open(8) })
        }
    }
    Section("原廠 DSP／EQ") {
        Hint("保留原廠音效 App 的調整與儲存方式，可從這裡直接開啟。", size = 16)
        SecondaryButton("開啟原廠 DSP／EQ", {
            val launched = runCatching {
                val intent = context.packageManager.getLaunchIntentForPackage("com.syu.eq")
                    ?: return@runCatching false
                context.startActivity(intent)
                true
            }.getOrDefault(false)
            if (!launched) Toast.makeText(context, "找不到可開啟的原廠 DSP／EQ，請從所有 App 選擇車機音效設定。", Toast.LENGTH_LONG).show()
        })
        Hint("照片參考：前聲道高通 62 Hz、12 dB/oct，低通直通；Loud 關閉。這是照片中的設定，仍需依喇叭與聽感確認。", size = 14)
        Hint("尚未提供自動套用：先確認參數讀回、備份與還原，再驗證原廠控制方式。", size = 14)
    }
}
