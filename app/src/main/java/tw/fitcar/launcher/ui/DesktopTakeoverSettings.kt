package tw.fitcar.launcher.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tw.fitcar.launcher.system.AdbCommands
import tw.fitcar.launcher.system.DesktopGuard
import tw.fitcar.launcher.system.LauncherPermissions

/** 0.4.27 桌面接管：預設桌面、adb 指令、原廠桌面切回、喚醒、背景、使用情況、浮動鍵。皆需使用者自行開啟。 */
/**
 * 桌面接管、權限總覽、導航、捷徑等分類共用同一份狀態。
 * 0.4.34：不再每 2 秒在主執行緒輪詢；只在畫面回到前景（例如從系統設定頁返回）與按下開關後重讀。
 */
internal class TakeoverStatusHolder(private val context: Context) {
    var status by mutableStateOf(TakeoverStatus.read(context))
        private set
    fun refresh() { status = TakeoverStatus.read(context) }
}

@Composable
internal fun rememberTakeoverStatus(): TakeoverStatusHolder {
    val context = LocalContext.current
    val holder = remember(context) { TakeoverStatusHolder(context) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { holder.refresh() }
    return holder
}

internal fun openSystemPage(context: Context, intent: Intent, fallback: String) {
    try { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    catch (_: Exception) {
        try { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (_: Exception) { Toast.makeText(context, fallback, Toast.LENGTH_SHORT).show() }
    }
}

/** 「桌面接管」分類：預設桌面、系統服務、切回、喚醒、adb。 */
@Composable
internal fun DesktopTakeoverSettings(holder: TakeoverStatusHolder = rememberTakeoverStatus()) {
    val context = LocalContext.current
    val status = holder.status
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { holder.refresh() }

    Section("預設桌面") {
        StatusLine(status.isDefault, "目前預設桌面：車機桌面", "目前預設桌面：${status.defaultLabel}")
        Hint("按下後由系統跳出確認視窗。", size = 16)
        Hint(tw.fitcar.launcher.system.DesktopTakeoverAdvice.FACTORY_PROPERTY_NOTE, size = 16)
        PrimaryButton("設為預設桌面", {
            val role = LauncherPermissions.homeRoleIntent(context)
            if (role != null && status.roleAvailable && !status.roleHeld) {
                try { roleLauncher.launch(role) } catch (_: Exception) { openSystemPage(context, Intent(Settings.ACTION_HOME_SETTINGS), "無法開啟預設桌面設定") }
            } else openSystemPage(context, Intent(Settings.ACTION_HOME_SETTINGS), "無法開啟預設桌面設定")
        })
    }

    AccessibilityCard(status)

    Section("原廠桌面出現時切回") {
        TakeoverToggle("原廠桌面出現時自動回到車機桌面", status.autoReturn) { DesktopGuard.setAutoReturn(context, it); holder.refresh() }
        Hint("只在原廠桌面的主畫面出現時切回（例如按 Home 鍵），原廠桌面內的收音機、設定等畫面不受影響。30 秒內切回 3 次會自動暫停 10 分鐘，避免與原廠互搶。", size = 16)
        Hint("從下方「開啟原廠桌面」進入時，會自動暫停 10 分鐘。", size = 16)
        Hint("上次紀錄：${status.lastEvent}", size = 16)
        if (status.paused) SecondaryButton("立即恢復切回", { DesktopGuard.resume(context); holder.refresh() })
        else SecondaryButton("暫停 10 分鐘（要使用原廠功能時）", { DesktopGuard.pause(context); holder.refresh() })
    }

    Section("螢幕喚醒時開啟") {
        TakeoverToggle("熄火喚醒後開啟車機桌面", status.wakeLaunch) { DesktopGuard.setWakeLaunch(context, it); holder.refresh() }
        Hint("螢幕亮起約 1.5 秒後開啟一次，不重試；需開啟系統服務。開機時若已開「開機時嘗試開啟車機桌面」，系統服務也會補開一次。", size = 16)
    }

    Section("用電腦 adb 指定（進階）") {
        Hint("不需要 root。車機以 USB 或無線偵錯連到電腦後，在電腦執行下列指令；本 App 不會執行任何指令。", size = 16)
        val own = DesktopGuard.ownHomeComponent(context).flattenToShortString()
        val commands = buildList {
            add("設為車機桌面" to AdbCommands.setHome(own))
            add("加入預設桌面角色" to AdbCommands.addHomeRole(context.packageName))
            add("查看目前預設桌面" to AdbCommands.showHome())
            status.factoryComponents.firstOrNull()?.let { add("改回原廠桌面" to AdbCommands.setHome(it)) }
            add("匯出音訊狀態（音量分析用）" to AdbCommands.dumpAudio())
        }
        commands.forEach { (label, cmd) ->
            Text(label, color = LocalPalette.current.text, fontSize = TypeScale.SECONDARY.sp)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(cmd, color = LocalPalette.current.textSoft, fontSize = TypeScale.NOTE.sp, modifier = Modifier.weight(1f))
                SecondaryButton("複製", { copy(context, cmd) })
            }
        }
    }
}

/** 無障礙服務狀態：桌面接管與導航浮動鍵都依賴它，兩處共用同一張卡片。 */
@Composable
internal fun AccessibilityCard(status: TakeoverStatus) {
    val context = LocalContext.current
    Section("系統服務（無障礙）") {
        StatusLine(status.accessibility, "已開啟：可切回桌面、喚醒開啟、判斷導航前景",
            "未開啟：「切回、喚醒、浮動鍵」不會作用")
        Hint("只接收目前前景 App 的名稱，不讀取畫面內容、不攔截按鍵。在系統無障礙清單中找「車機桌面系統快捷」開啟。", size = 16)
        SecondaryButton("開啟無障礙設定", { openSystemPage(context, LauncherPermissions.accessibilityIntent(), "無法開啟無障礙設定") })
    }
}

/** 「導航」分類：導航 App 在前景時的浮動回桌面鍵。 */
@Composable
internal fun NavOverlaySettings(holder: TakeoverStatusHolder) {
    val context = LocalContext.current
    val status = holder.status
    Section("導航時浮動回桌面鍵") {
        TakeoverToggle("導航 App 在前景時顯示「⌂ 桌面」", status.overlay) { DesktopGuard.setOverlay(context, it); holder.refresh() }
        StatusLine(status.canOverlay, "已允許浮動視窗", "未允許浮動視窗：浮動鍵不會顯示")
        if (status.overlay && !status.accessibility) StatusLine(false, "", "系統服務未開啟：浮動鍵不會出現（到「桌面接管」開啟）")
        Hint("只在 Google 地圖、導航王或你綁定的導航 App 在前景時，於左側中間顯示；點一下回桌面，長按隱藏到下次開啟導航。", size = 16)
        if (!status.canOverlay) SecondaryButton("允許浮動視窗", { openSystemPage(context, LauncherPermissions.overlayIntent(context), "車機沒有提供浮動視窗設定") })
    }
}

/** 「捷徑與 App」分類：最近使用來源與系統小工具。 */
@Composable
internal fun UsageAndWidgetSettings(holder: TakeoverStatusHolder) {
    val context = LocalContext.current
    val status = holder.status
    Section("最近使用的 App") {
        StatusLine(status.usage, "已允許：「所有 App」的最近使用會加入系統紀錄", "未允許：最近使用只記錄從車機桌面開啟的 App")
        Hint("只讀取近 3 天用過哪些 App 與時間，不讀取 App 內容，只存在車機。", size = 16)
        SecondaryButton(if (status.usage) "管理" else "允許", { openSystemPage(context, LauncherPermissions.usageIntent(), "車機沒有提供使用情況存取設定") })
    }
    Section("桌面小工具") {
        Hint("在「所有 App」頁最上方可加入一個系統小工具（例如時鐘、天氣、音樂）。只在停車時顯示，加入時由系統視窗確認。", size = 16)
    }
}

/** 「權限總覽」分類：背景執行。 */
@Composable
internal fun BatterySettings(holder: TakeoverStatusHolder) {
    val context = LocalContext.current
    val status = holder.status
    Section("背景不被關閉") {
        StatusLine(status.battery, "已允許：通知、定位與服務較不會被系統關閉", "未允許：長時間休眠後可能需重新開啟")
        if (!status.battery) SecondaryButton("允許", { openSystemPage(context, LauncherPermissions.batteryIntent(context), "車機沒有提供電池最佳化設定") })
    }
}

@Composable
private fun TakeoverToggle(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = LocalPalette.current.text, fontSize = 18.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.semantics { contentDescription = title })
    }
}

@Composable
internal fun StatusLine(ok: Boolean, okText: String, badText: String) {
    val p = LocalPalette.current
    Text(if (ok) "● $okText" else "● $badText", color = if (ok) p.success else p.danger, fontSize = 16.sp)
}

private fun copy(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("adb", text))
    Toast.makeText(context, "已複製指令", Toast.LENGTH_SHORT).show()
}

internal data class TakeoverStatus(
    val isDefault: Boolean, val defaultLabel: String, val roleAvailable: Boolean, val roleHeld: Boolean,
    val factoryComponents: List<String>, val accessibility: Boolean, val autoReturn: Boolean, val paused: Boolean,
    val lastEvent: String, val wakeLaunch: Boolean, val battery: Boolean, val usage: Boolean,
    val overlay: Boolean, val canOverlay: Boolean,
) {
    companion object {
        fun read(context: Context): TakeoverStatus {
            val pm = context.packageManager
            val def = DesktopGuard.defaultHomePackage(context)
            val label = def?.let { pkg -> try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() + "（$pkg）" } catch (_: Exception) { pkg } }
                ?: "未指定（按 Home 會出現選擇視窗）"
            val factory = DesktopGuard.factoryHomes(context).sorted()
            return TakeoverStatus(def == context.packageName, label, LauncherPermissions.homeRoleAvailable(context), LauncherPermissions.homeRoleHeld(context),
                factory, LauncherPermissions.accessibilityEnabled(context), DesktopGuard.autoReturn(context),
                DesktopGuard.pausedUntil(context) > android.os.SystemClock.elapsedRealtime(), DesktopGuard.lastEvent(context),
                DesktopGuard.wakeLaunch(context), LauncherPermissions.ignoringBattery(context), LauncherPermissions.usageGranted(context),
                DesktopGuard.overlay(context), LauncherPermissions.canOverlay(context))
        }
    }
}
