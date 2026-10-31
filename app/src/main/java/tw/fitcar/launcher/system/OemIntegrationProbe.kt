package tw.fitcar.launcher.system

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Build

/** 原廠 App（例如 DSP／EQ）目前能不能開啟；只用公開 PackageManager 查詢。 */
enum class FactoryAppState(val text: String) {
    MISSING("車機上找不到此原廠 App（或系統不讓本程式看見）"),
    DISABLED("此原廠 App 已被停用"),
    NO_LAUNCHER("此原廠 App 沒有可直接開啟的畫面"),
    AVAILABLE("可開啟原廠 App"),
}

/** 公開可讀的元件資訊：是否存在、是否匯出、需要什麼權限、該權限的保護等級、本程式是否持有。 */
data class ComponentFacts(val found: Boolean, val exported: Boolean = false, val permission: String? = null,
    val protection: String? = null, val granted: Boolean = false)

data class OemFinding(val title: String, val verdict: String, val evidence: String)

/**
 * 原廠 Binder 音量、TaskView 導航嵌入、DSP 自動套用的「可行性」判讀。
 * 只根據公開 metadata 判斷「能不能連」，從不連接服務、不送出任何命令；
 * 即使可以連接，協議與讀回也必須另外驗證才可宣稱可用。
 */
object OemAssessment {
    fun factoryApp(installed: Boolean, enabled: Boolean, hasLaunchIntent: Boolean): FactoryAppState = when {
        !installed -> FactoryAppState.MISSING
        !enabled -> FactoryAppState.DISABLED
        !hasLaunchIntent -> FactoryAppState.NO_LAUNCHER
        else -> FactoryAppState.AVAILABLE
    }

    fun binderVolume(f: ComponentFacts): OemFinding {
        val evidence = if (!f.found) "未解析到 com.syu.ms.toolkit 服務" else
            "exported=${f.exported}；permission=${f.permission ?: "無"}；保護等級=${f.protection ?: "—"}；本程式持有=${f.granted}"
        val verdict = when {
            !f.found -> "不可行：本程式看不到該服務。"
            !f.exported -> "不可行：服務未匯出，只有同 UID／系統可連。"
            f.permission != null && !f.granted -> "不可行：需要 ${f.permission}（${f.protection ?: "未知保護等級"}），本程式沒有。"
            else -> "可嘗試連接但未驗證：命令格式來自反組譯，尚無讀回方法；不發送任何音量命令，需先找到唯讀查詢並在實車比對聽感。"
        }
        return OemFinding("原廠 Binder 音量", verdict, evidence)
    }

    fun taskView(manageTasksProtection: String?, granted: Boolean): OemFinding {
        val verdict = if (granted) "權限已持有（不預期）：仍需系統簽章的 WMShell 介面，未實作。"
        else "直接 TaskView 路徑受限：未持有 MANAGE_ACTIVITY_TASKS（${manageTasksProtection ?: "系統保護"}）。原廠嵌入宿主／橋接入口尚未驗證，不能據此判定所有嵌入方式不可行；目前使用分割畫面／全螢幕。"
        return OemFinding("導航 TaskView 嵌入", verdict, "MANAGE_ACTIVITY_TASKS 保護等級=${manageTasksProtection ?: "查不到"}；持有=$granted")
    }

    fun dspAutoApply(app: FactoryAppState, openComponents: Int, protectedComponents: Int): OemFinding {
        val verdict = when {
            app == FactoryAppState.MISSING -> "不可行：找不到原廠 DSP／EQ。"
            openComponents == 0 -> "不可行：原廠 DSP／EQ 沒有不需權限的匯出服務或資料提供者，只能開啟原廠畫面手動調整。"
            else -> "有 $openComponents 個匯出元件不需權限，但協議、參數格式與讀回均未知；未驗證前不寫入，只開啟原廠畫面。"
        }
        return OemFinding("DSP 參數自動套用", verdict, "未受保護匯出服務／提供者 $openComponents 個；受權限保護 $protectedComponents 個")
    }

    fun protectionName(base: Int, flags: Int): String {
        val b = when (base) { 0 -> "normal"; 1 -> "dangerous"; 2 -> "signature"; 3 -> "signatureOrSystem"; else -> "base$base" }
        return if (flags and 0x10 != 0) "$b|privileged" else b
    }
}

/** 讀取上述公開 metadata；任何例外都視為「查不到」，不會崩潰。 */
object OemIntegrationProbe {
    const val EQ_PACKAGE = "com.syu.eq"
    private const val MS_PACKAGE = "com.syu.ms"
    private const val MS_ACTION = "com.syu.ms.toolkit"

    fun factoryApp(context: Context, pkg: String = EQ_PACKAGE): FactoryAppState {
        val pm = context.packageManager
        val info = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull()
        val launch = runCatching { pm.getLaunchIntentForPackage(pkg) }.getOrNull()
        return OemAssessment.factoryApp(info != null, info?.enabled == true, launch != null)
    }

    /** 回傳 null 代表已開啟；否則為不能開啟的原因。 */
    fun openFactoryApp(context: Context, pkg: String = EQ_PACKAGE): String? {
        val state = factoryApp(context, pkg)
        if (state != FactoryAppState.AVAILABLE) return state.text
        return runCatching {
            val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return state.text
            if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            null
        }.getOrElse { "無法開啟原廠 App（${it.javaClass.simpleName}）" }
    }

    private fun protection(pm: PackageManager, name: String?): String? = name?.let {
        runCatching {
            val p = pm.getPermissionInfo(it, 0)
            @Suppress("DEPRECATION") val level = p.protectionLevel
            if (Build.VERSION.SDK_INT >= 28) OemAssessment.protectionName(p.protection, p.protectionFlags)
            else OemAssessment.protectionName(level and PermissionInfo.PROTECTION_MASK_BASE, level and PermissionInfo.PROTECTION_MASK_FLAGS)
        }.getOrNull()
    }

    private fun granted(context: Context, name: String?) =
        name != null && context.checkSelfPermission(name) == PackageManager.PERMISSION_GRANTED

    fun run(context: Context): List<OemFinding> {
        val pm = context.packageManager
        val ms = runCatching { pm.resolveService(Intent(MS_ACTION).setPackage(MS_PACKAGE), 0)?.serviceInfo }.getOrNull()
        val msFacts = if (ms == null) ComponentFacts(false) else ComponentFacts(true, ms.exported, ms.permission,
            protection(pm, ms.permission), granted(context, ms.permission))
        val manage = "android.permission.MANAGE_ACTIVITY_TASKS"
        var open = 0; var guarded = 0
        runCatching {
            @Suppress("DEPRECATION")
            val info = pm.getPackageInfo(EQ_PACKAGE, PackageManager.GET_SERVICES or PackageManager.GET_PROVIDERS)
            info.services.orEmpty().filter { it.exported }.forEach { if (it.permission == null) open++ else guarded++ }
            info.providers.orEmpty().filter { it.exported }.forEach {
                if (it.readPermission == null && it.writePermission == null) open++ else guarded++
            }
        }
        return listOf(
            OemAssessment.binderVolume(msFacts),
            OemAssessment.taskView(protection(pm, manage), granted(context, manage)),
            OemAssessment.dspAutoApply(factoryApp(context), open, guarded),
        )
    }
}
