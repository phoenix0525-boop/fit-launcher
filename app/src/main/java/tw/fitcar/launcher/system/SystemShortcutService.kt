package tw.fitcar.launcher.system

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import java.lang.ref.WeakReference
import tw.fitcar.launcher.data.Prefs
import tw.fitcar.launcher.nav.GoogleNavigation
import tw.fitcar.launcher.nav.NavigationKingShortcut

/**
 * 使用者自行開啟的系統服務。只接收「視窗切換」事件的套件與畫面名稱，不讀取畫面內容、不攔截按鍵。
 * 功能（皆需在設定中另外開啟）：系統分割快捷、原廠桌面出現時切回、螢幕喚醒時開啟桌面、導航時浮動回桌面鍵。
 */
class SystemShortcutService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var returns = emptyList<Long>()
    private var homes: Set<String> = emptySet()
    private var homesLoaded = 0L

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_ON && DesktopGuard.wakeLaunch(context)) {
                // 等系統與原廠桌面先完成喚醒，再開啟一次；不重試。
                handler.postDelayed({
                    val ok = DesktopGuard.launchDesktop(context)
                    DesktopGuard.recordEvent(context, if (ok) "螢幕喚醒：已開啟車機桌面" else "螢幕喚醒：開啟失敗")
                }, 1500)
            }
        }
    }

    override fun onServiceConnected() {
        instance = WeakReference(this)
        try { registerReceiver(screenReceiver, IntentFilter(Intent.ACTION_SCREEN_ON)) } catch (_: Exception) { }
        // 開機後系統會重新連上本服務；若使用者開啟「開機啟動」，在開機 3 分鐘內補開一次。
        if (SystemClock.elapsedRealtime() < 180_000 && Prefs.get(this).state.value.startOnBoot) {
            handler.postDelayed({
                val ok = DesktopGuard.launchDesktop(this)
                DesktopGuard.recordEvent(this, if (ok) "開機：已由系統服務開啟車機桌面" else "開機：系統服務開啟失敗")
            }, 3000)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString()
        val cls = event.className?.toString()
        foreground = pkg ?: foreground
        val now = SystemClock.elapsedRealtime()
        if (now - homesLoaded > 60_000) { homes = DesktopGuard.factoryHomes(this); homesLoaded = now }

        val d = DesktopGuardPolicy.decide(DesktopGuard.autoReturn(this), now, pkg, cls, homes, DesktopGuard.pausedUntil(this), returns)
        returns = d.returns
        when (d.action) {
            DesktopGuardPolicy.Action.RETURN -> {
                val ok = DesktopGuard.launchDesktop(this)
                DesktopGuard.recordEvent(this, "偵測到 $pkg/$cls，${if (ok) d.reason else "切回失敗"}")
            }
            DesktopGuardPolicy.Action.PAUSE_LOOP -> {
                DesktopGuard.setPausedUntil(this, d.pausedUntil)
                DesktopGuard.recordEvent(this, d.reason)
            }
            DesktopGuardPolicy.Action.IGNORE -> if (pkg != null && homes.any { it.startsWith("$pkg/") })
                DesktopGuard.recordEvent(this, "偵測到 $pkg/$cls：${d.reason}")
        }

        val navPackages = setOfNotNull(GoogleNavigation.PACKAGE, NavigationKingShortcut.VERIFIED_PACKAGE,
            Prefs.get(this).state.value.bindings.navigation?.let { android.content.ComponentName.unflattenFromString(it)?.packageName })
        NavOverlay.update(this, DesktopGuardPolicy.overlay(DesktopGuard.overlay(this), pkg, navPackages, packageName), pkg)
    }

    override fun onInterrupt() = Unit
    override fun onDestroy() {
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) { }
        NavOverlay.hide(this)
        handler.removeCallbacksAndMessages(null)
        if (instance.get() === this) instance.clear()
        super.onDestroy()
    }

    private fun openSplitOrRecents(): ShortcutResult {
        val available = if (android.os.Build.VERSION.SDK_INT >= 30) systemActions.map { it.id }.toSet() else emptySet()
        if (GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN in available && performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN))
            return ShortcutResult.SPLIT_REQUESTED
        return if (performGlobalAction(GLOBAL_ACTION_RECENTS)) ShortcutResult.RECENTS_OPENED else ShortcutResult.UNAVAILABLE
    }

    companion object {
        private var instance = WeakReference<SystemShortcutService>(null)
        /** 最近一次視窗切換的前景套件（只在服務開啟時更新，只存在記憶體）。 */
        @Volatile var foreground: String? = null
            private set
        fun request(): ShortcutResult = instance.get()?.openSplitOrRecents() ?: ShortcutResult.NOT_ENABLED
        fun connected() = instance.get() != null
    }
}

enum class ShortcutResult { NOT_ENABLED, SPLIT_REQUESTED, RECENTS_OPENED, UNAVAILABLE }
