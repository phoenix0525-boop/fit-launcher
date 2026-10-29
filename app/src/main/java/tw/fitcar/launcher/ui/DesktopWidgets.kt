package tw.fitcar.launcher.ui

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * 「所有 App」頁上方的一個系統小工具（Android 公開 AppWidgetHost）。
 * 綁定需使用者在系統視窗確認；只在停車時顯示，不讀取小工具內容。
 */
class DesktopWidgets(private val activity: ComponentActivity) {
    private val manager by lazy { AppWidgetManager.getInstance(activity) }
    private val host by lazy { AppWidgetHost(activity.applicationContext, HOST_ID) }
    private val prefs by lazy { activity.getSharedPreferences("desktop_widget", Context.MODE_PRIVATE) }

    var widgetId by mutableIntStateOf(AppWidgetManager.INVALID_APPWIDGET_ID)
        private set
    private var picking by mutableStateOf(false)
    private var pendingId = AppWidgetManager.INVALID_APPWIDGET_ID

    private val bindLauncher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == android.app.Activity.RESULT_OK) configureOrSave(pendingId) else discard(pendingId)
    }
    private val configureLauncher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == android.app.Activity.RESULT_OK) save(pendingId) else discard(pendingId)
    }

    fun load() { widgetId = prefs.getInt("id", AppWidgetManager.INVALID_APPWIDGET_ID) }
    fun start() { try { host.startListening() } catch (_: Exception) { } }
    fun stop() { try { host.stopListening() } catch (_: Exception) { } }

    private fun providers(): List<AppWidgetProviderInfo> = try {
        manager.installedProviders.sortedBy { it.loadLabel(activity.packageManager) }
    } catch (_: Exception) { emptyList() }

    private fun choose(info: AppWidgetProviderInfo) {
        picking = false
        val id = host.allocateAppWidgetId()
        pendingId = id
        if (manager.bindAppWidgetIdIfAllowed(id, info.provider)) configureOrSave(id)
        else try {
            bindLauncher.launch(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider))
        } catch (_: Exception) { discard(id) }
    }

    private fun configureOrSave(id: Int) {
        val info = manager.getAppWidgetInfo(id) ?: return discard(id)
        val configure = info.configure
        if (configure == null) return save(id)
        try {
            configureLauncher.launch(Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).setComponent(configure)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        } catch (_: Exception) { save(id) } // 設定畫面無法開啟時仍保留，多數小工具可用預設值
    }

    private fun save(id: Int) {
        val old = widgetId
        if (old != AppWidgetManager.INVALID_APPWIDGET_ID && old != id) host.deleteAppWidgetId(old)
        prefs.edit().putInt("id", id).apply()
        widgetId = id
        pendingId = AppWidgetManager.INVALID_APPWIDGET_ID
    }

    private fun discard(id: Int) {
        if (id != AppWidgetManager.INVALID_APPWIDGET_ID) try { host.deleteAppWidgetId(id) } catch (_: Exception) { }
        pendingId = AppWidgetManager.INVALID_APPWIDGET_ID
    }

    fun remove() {
        val id = widgetId
        if (id != AppWidgetManager.INVALID_APPWIDGET_ID) try { host.deleteAppWidgetId(id) } catch (_: Exception) { }
        prefs.edit().remove("id").apply()
        widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    }

    @Composable
    fun Panel() {
        val p = LocalPalette.current
        val info = widgetId.takeIf { it != AppWidgetManager.INVALID_APPWIDGET_ID }?.let { manager.getAppWidgetInfo(it) }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (info != null) {
                val id = widgetId
                AndroidView(factory = { ctx -> host.createView(ctx, id, info) },
                    modifier = Modifier.fillMaxWidth().height(220.dp).clip(RadiusMd))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SecondaryButton("更換小工具", { picking = true })
                    SecondaryButton("移除小工具", { remove() })
                }
            } else SecondaryButton("加入小工具", { picking = true })
        }
        if (picking) {
            val list = providers()
            AlertDialog(onDismissRequest = { picking = false }, containerColor = p.surface,
                title = { Text("選擇小工具", color = p.text, fontSize = 22.sp) },
                text = {
                    Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (list.isEmpty()) Hint("車機上沒有可用的小工具。", size = 18)
                        list.forEach { w ->
                            Text(w.loadLabel(activity.packageManager), color = p.text, fontSize = TypeScale.BODY.sp,
                                modifier = Modifier.fillMaxWidth().clip(RadiusMd).clickable { choose(w) }.padding(12.dp))
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { picking = false }) { Text("取消", color = p.accent, fontSize = 18.sp) } })
        }
    }

    companion object { const val HOST_ID = 0x0FC4 }
}
