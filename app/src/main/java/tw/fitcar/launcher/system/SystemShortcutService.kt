package tw.fitcar.launcher.system

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import java.lang.ref.WeakReference

/** Optional user-enabled system actions only. Does not retrieve windows or filter hardware keys. */
class SystemShortcutService : AccessibilityService() {
    override fun onServiceConnected() { instance = WeakReference(this) }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit
    override fun onDestroy() {
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
        fun request(): ShortcutResult = instance.get()?.openSplitOrRecents() ?: ShortcutResult.NOT_ENABLED
    }
}

enum class ShortcutResult { NOT_ENABLED, SPLIT_REQUESTED, RECENTS_OPENED, UNAVAILABLE }
