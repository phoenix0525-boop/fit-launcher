package tw.fitcar.launcher.ui

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import tw.fitcar.launcher.system.ShortcutResult
import tw.fitcar.launcher.system.SystemShortcutService

@Composable
internal fun SystemSplitShortcut() {
    val context = LocalContext.current
    var explain by remember { mutableStateOf(false) }
    val p = LocalPalette.current
    SecondaryButton("分割／多工", {
        val message = when (SystemShortcutService.request()) {
            ShortcutResult.NOT_ENABLED -> { explain = true; null }
            ShortcutResult.SPLIT_REQUESTED -> "已送出系統分割請求；是否進入分割請目視確認。"
            ShortcutResult.RECENTS_OPENED -> "請在最近使用畫面選擇分割及另一個 App。"
            ShortcutResult.UNAVAILABLE -> "車機未接受系統請求，請使用原廠分割按鈕或系統手勢。"
        }
        message?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    })
    if (explain) AlertDialog(
        onDismissRequest = { explain = false }, containerColor = p.surface,
        title = { Text("啟用系統快捷入口", color = p.text) },
        text = { Text("需在 Android 無障礙設定手動開啟「車機桌面系統快捷」。用途僅為你按下按鈕時呼叫系統分割或最近使用畫面，不讀取畫面文字、不操作其他 App 內容。\n\n直接分割取決於車機是否開放；未開放時改開最近使用畫面。此功能不會自動選取左右兩個 App，也不會綁定方向盤按鍵。", color = p.text) },
        confirmButton = { TextButton(onClick = {
            explain = false
            try { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            catch (_: Exception) { Toast.makeText(context, "無法開啟設定，請手動前往系統的無障礙設定。", Toast.LENGTH_LONG).show() }
        }) { Text("前往設定") } },
        dismissButton = { TextButton(onClick = { explain = false }) { Text("稍後") } },
    )
}
