package tw.fitcar.launcher.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import tw.fitcar.launcher.system.DesktopGuard
import tw.fitcar.launcher.system.SecureSettingsHeal
import tw.fitcar.launcher.system.ShellPlan
import tw.fitcar.launcher.system.ShizukuShell

/** Shizuku 狀態：連線、斷線、授權結果與回到畫面時重新讀取。 */
@Composable
private fun rememberShizukuState(): Pair<ShizukuShell.State, () -> Unit> {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val refresh: () -> Unit = { tick++ }
    DisposableEffect(Unit) {
        val received = Shizuku.OnBinderReceivedListener { tick++ }
        val dead = Shizuku.OnBinderDeadListener { tick++ }
        val result = Shizuku.OnRequestPermissionResultListener { _, _ -> tick++ }
        try {
            Shizuku.addBinderReceivedListenerSticky(received)
            Shizuku.addBinderDeadListener(dead)
            Shizuku.addRequestPermissionResultListener(result)
        } catch (_: Throwable) { }
        onDispose {
            try {
                Shizuku.removeBinderReceivedListener(received)
                Shizuku.removeBinderDeadListener(dead)
                Shizuku.removeRequestPermissionResultListener(result)
            } catch (_: Throwable) { }
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val state = remember(tick) { ShizukuShell.state(context) }
    return state to refresh
}

private fun openUrl(context: Context, url: String) =
    openSystemPage(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)), "無法開啟瀏覽器，請手動前往 $url")

@Composable
private fun ResultList(results: List<ShellPlan.Result>) {
    val p = LocalPalette.current
    results.forEach { r ->
        val detail = if (r.ok) "" else "：" + r.output.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().take(80)
        Text((if (r.ok) "✓ " else "✕ ") + r.label + detail, color = if (r.ok) p.success else p.danger, fontSize = TypeScale.NOTE.sp)
    }
}

/** 「桌面接管」：一鍵授權。沒有 Shizuku 時只顯示安裝與啟動說明。 */
@Composable
internal fun ShizukuSetupSection(holder: TakeoverStatusHolder) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val (state, refresh) = rememberShizukuState()
    var busy by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<ShellPlan.Result>>(emptyList()) }
    Section("一鍵授權（Shizuku）") {
        StatusLine(state == ShizukuShell.State.READY, state.text, state.text)
        Hint("Shizuku 是開源工具，啟動一次後，車機桌面就能代你完成原本要用電腦 adb 手打的授權，不需 root。授權後重開機仍有效。", size = 16)
        when (state) {
            ShizukuShell.State.NOT_INSTALLED, ShizukuShell.State.UNSUPPORTED -> {
                Hint("1. 安裝 Shizuku　2. 車機連上 Wi-Fi（手機熱點也可）　3. 在 Shizuku 用「無線偵錯」啟動　4. 回到這裡按「允許」。", size = 16)
                SecondaryButton("下載 Shizuku", { openUrl(context, ShizukuShell.DOWNLOAD_URL) })
            }
            ShizukuShell.State.NOT_RUNNING -> {
                Hint("開啟 Shizuku，用「無線偵錯」配對並啟動（需先在開發者選項開啟無線偵錯）。車機重開機後需再啟動一次，但已完成的授權不會消失。", size = 16)
                SecondaryButton("開啟 Shizuku", {
                    context.packageManager.getLaunchIntentForPackage(ShizukuShell.MANAGER_PACKAGE)
                        ?.let { openSystemPage(context, it, "無法開啟 Shizuku") } ?: refresh()
                })
            }
            ShizukuShell.State.NO_PERMISSION -> PrimaryButton("允許車機桌面使用 Shizuku", { ShizukuShell.requestPermission(); refresh() })
            ShizukuShell.State.READY -> {
                Hint("會開啟：通知存取、浮動視窗、使用情況存取、修改系統設定、背景不被關閉、定位、附近裝置、系統服務（無障礙），並設為預設桌面。「原廠切回」「喚醒開啟」等開關仍由你決定。", size = 16)
                PrimaryButton(if (busy) "執行中…" else "一鍵開啟所有權限", {
                    if (!busy) { busy = true; scope.launch {
                        val pkg = context.packageName
                        val first = ShizukuShell.run(context, ShellPlan.setup(pkg, android.os.Build.VERSION.SDK_INT))
                        val read = ShizukuShell.run(context, listOf(ShellPlan.readAccessibility())).firstOrNull()
                        val access = if (read != null && read.exitCode == 0)
                            ShizukuShell.run(context, ShellPlan.enableAccessibility(read.output.trim(), pkg)) else listOfNotNull(read)
                        results = first + access
                        DesktopGuard.recordEvent(context, "一鍵授權：${results.count { it.ok }}／${results.size} 項成功")
                        busy = false; holder.refresh(); refresh()
                    } }
                }, enabled = !busy)
            }
        }
        if (results.isNotEmpty()) ResultList(results)
        if (SecureSettingsHeal.granted(context)) {
            TakeoverToggle("系統服務被關閉時自動恢復", SecureSettingsHeal.enabled(context)) { SecureSettingsHeal.setEnabled(context, it); refresh() }
            Hint("只在你開啟「原廠切回」「喚醒開啟」或導航浮動鍵時才會恢復；App 更新或當掉後系統常會關掉無障礙服務。", size = 16)
        }
    }
}

/** 「桌面接管」：停用／還原原廠桌面（需 Shizuku；可隨時還原）。 */
@Composable
internal fun FactoryDisableSection(holder: TakeoverStatusHolder) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val (state, refresh) = rememberShizukuState()
    val status = holder.status
    val prefs = remember { context.getSharedPreferences("factory_disable", Context.MODE_PRIVATE) }
    var disabled by remember { mutableStateOf(prefs.getStringSet("disabled", emptySet()).orEmpty().sorted()) }
    var confirm by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<ShellPlan.Result>>(emptyList()) }
    val recents = remember { recentsPackage() }
    val candidates = remember(status) {
        ShellPlan.disableCandidates(DesktopGuard.factoryHomes(context).map { it.substringBefore('/') }, context.packageName, recents)
    }
    fun save(next: List<String>) { disabled = next.sorted(); prefs.edit().putStringSet("disabled", next.toSet()).apply() }

    Section("停用原廠桌面（進階）") {
        Hint("系統若由原廠設定強制回到原廠桌面，停用它之後按 Home 與開機就只會進車機桌面。可隨時在這裡還原，不會刪除任何資料。", size = 16)
        if (state != ShizukuShell.State.READY) Hint("需先完成上方「一鍵授權（Shizuku）」的啟動與允許。", size = 16, essential = true)
        if (!status.isDefault) Hint("車機桌面必須先是預設桌面，才可停用原廠桌面。", size = 16, essential = true)
        candidates.forEach { pkg ->
            SecondaryButton("停用 $pkg", { confirm = pkg }, Modifier.fillMaxWidth(),
                enabled = state == ShizukuShell.State.READY && ShellPlan.canDisable(status.isDefault, pkg, candidates))
        }
        if (candidates.isEmpty() && disabled.isEmpty()) Hint("沒有可停用的其他桌面。", size = 16)
        disabled.forEach { pkg ->
            Hint("已停用：$pkg　電腦還原指令：${ShellPlan.restoreByAdb(pkg)}", size = 16)
            PrimaryButton("還原 $pkg", { scope.launch {
                results = ShizukuShell.run(context, listOf(ShellPlan.enable(pkg)))
                if (results.all { it.ok }) save(disabled - pkg)
                DesktopGuard.recordEvent(context, "還原原廠桌面 $pkg：${if (results.all { it.ok }) "成功" else "失敗"}")
                holder.refresh(); refresh()
            } }, Modifier.fillMaxWidth(), enabled = state == ShizukuShell.State.READY)
        }
        if (results.isNotEmpty()) ResultList(results)
    }

    confirm?.let { pkg ->
        val p = LocalPalette.current
        AlertDialog(onDismissRequest = { confirm = null }, containerColor = p.surface,
            title = { Text("停用 $pkg？", color = p.text, fontSize = 22.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "請在停車時操作。停用後立刻測試：排 R 檔的倒車影像、方向盤按鍵、熄火再發動。",
                        "原廠桌面裡的收音機、內嵌導航等入口會一起消失；收音機等 App 仍可從「所有 App」開啟。",
                        "有任何異常，回到這裡按「還原」，或用電腦執行：${ShellPlan.restoreByAdb(pkg)}",
                    ).forEach { Hint("• $it", size = 18, essential = true) }
                }
            },
            confirmButton = { TextButton(onClick = {
                confirm = null
                scope.launch {
                    results = ShizukuShell.run(context, listOf(ShellPlan.disable(pkg)))
                    if (results.all { it.ok }) save(disabled + pkg)
                    DesktopGuard.recordEvent(context, "停用原廠桌面 $pkg：${if (results.all { it.ok }) "成功" else "失敗"}")
                    holder.refresh(); refresh()
                }
            }) { Text("確定停用", color = p.danger, fontSize = 18.sp) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("取消", color = p.accent, fontSize = 18.sp) } },
        )
    }
}

/** 最近使用畫面的提供者（停用會讓多工與分割畫面失效，絕不列入）。 */
private fun recentsPackage(): String? = try {
    val res = android.content.res.Resources.getSystem()
    val id = res.getIdentifier("config_recentsComponentName", "string", "android")
    ShellPlan.recentsPackage(if (id != 0) res.getString(id) else null)
} catch (_: Exception) { null }
