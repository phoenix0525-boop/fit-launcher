package tw.fitcar.launcher.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tw.fitcar.launcher.data.*

@Composable
internal fun BackupSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<BackupPlan?>(null) }
    var changes by remember { mutableStateOf(emptyList<String>()) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            busy=true
            message=runCatching { withContext(Dispatchers.IO) {
                val raw=SettingsBackup.encode(BackupStore.snapshot(context), tw.fitcar.launcher.BuildConfig.VERSION_NAME, java.time.Instant.now().toString())
                context.contentResolver.openOutputStream(uri,"wt")?.use { it.write(raw.toByteArray(Charsets.UTF_8)) } ?: error("無法寫入所選檔案")
            }; "設定已匯出" }.getOrElse { "匯出失敗：${it.message}" }
            busy=false
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy=true
            runCatching { withContext(Dispatchers.IO) {
                val bytes=context.contentResolver.openInputStream(uri)?.use { input ->
                    val out=java.io.ByteArrayOutputStream(); val chunk=ByteArray(8192)
                    while(true) { val n=input.read(chunk); if(n<0) break; require(out.size()+n<=SettingsBackup.MAX_BYTES) { "備份超過256KB" }; out.write(chunk,0,n) }; out.toByteArray()
                } ?: error("無法讀取檔案")
                val raw=Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString()
                SettingsBackup.decode(raw)
            } }.onSuccess { plan -> changes=BackupStore.changes(context,plan); pending=plan; message=null }
                .onFailure { message="拒絕匯入：${it.message}" }
            busy=false
        }
    }
    BackupPage(busy, message, { export.launch("車機桌面設定-${java.time.LocalDate.now()}.json") }, { import.launch(arrayOf("application/json","text/plain","application/octet-stream")) })
    pending?.let { plan -> AlertDialog(
        onDismissRequest={ if(!busy) pending=null },
        title={ Text("將變更的項目") },
        text={ Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState())) { changes.forEach { Text(it, fontSize=17.sp); Spacer(Modifier.height(8.dp)) }; Text("僅還原列出的設定，不包含桌布圖片。") } },
        confirmButton={ TextButton(enabled=!busy,onClick={ scope.launch {
            busy=true
            message=runCatching { withContext(Dispatchers.IO) { BackupStore.apply(context,plan) }; tw.fitcar.launcher.system.BrightnessController.scheduleNext(context); "設定已匯入；歌詞已關閉。找不到的App請重新選擇。" }.getOrElse { "匯入失敗：${it.message}" }
            pending=null; busy=false
        } }) { Text("確認匯入") } },
        dismissButton={ TextButton(enabled=!busy,onClick={ pending=null }) { Text("取消") } }
    ) }
}
@Composable
internal fun BackupPage(busy: Boolean, message: String?, export: () -> Unit, import: () -> Unit) {
    Section("備份與還原") {
        Text("匯出設定供換版或重裝後還原。", color=LocalPalette.current.text, fontSize=20.sp)
        Hint("包括App綁定、捷徑、導航與播放器模式、地址、顯示排程、主題及地圖縮放偏好。由系統檔案選擇器選擇位置，不需要儲存權限。")
        Hint("不含天氣授權碼、金鑰密碼、裝置識別、歌詞同意或桌布圖片。匯入後歌詞關閉，須重新同意；圖片請重新選取。")
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            PrimaryButton("匯出設定",export,Modifier.weight(1f),enabled=!busy)
            SecondaryButton("匯入設定",import,Modifier.weight(1f),enabled=!busy)
        }
        message?.let { Text(it,color=LocalPalette.current.text,fontSize=18.sp) }
    }
}
