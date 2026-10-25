package tw.fitcar.launcher.nav

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tw.fitcar.launcher.data.Prefs

data class NavGuidanceState(
    val guidance: NavGuidance? = null,
    val updatedAt: Long = 0L,
    /** 診斷用：最近一次收到綁定導航 App 的進行中通知時間，以及當時有沒有文字。 */
    val lastSeenAt: Long = 0L,
    val lastHadText: Boolean? = null,
)

/**
 * 只看「使用者綁定的導航 App」的進行中通知，把轉彎文字顯示在桌面導航區。
 * 其他 App 的通知一律忽略；內容只放在記憶體，不保存、不上傳。
 */
object NavGuidanceRepository {
    private val _state = MutableStateFlow(NavGuidanceState())
    val state: StateFlow<NavGuidanceState> = _state.asStateFlow()
    private var currentKey: String? = null

    private fun boundPackage(context: Context): String? =
        Prefs.get(context).state.value.bindings.navigation
            ?.let { ComponentName.unflattenFromString(it)?.packageName }

    fun onPosted(context: Context, sbn: StatusBarNotification) {
        val pkg = boundPackage(context) ?: return
        if (sbn.packageName != pkg) return
        val n = sbn.notification ?: return
        if (!sbn.isOngoing && n.category != Notification.CATEGORY_NAVIGATION) return
        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)
        val text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT)
        val sub = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)
        val g = NavGuidance.parse(title, text, sub)
        val now = System.currentTimeMillis()
        if (g == null) {
            _state.value = _state.value.copy(lastSeenAt = now, lastHadText = false)
            return
        }
        currentKey = sbn.key
        _state.value = NavGuidanceState(g, now, now, true)
    }

    fun onRemoved(sbn: StatusBarNotification) {
        if (sbn.key == currentKey) {
            currentKey = null
            _state.value = _state.value.copy(guidance = null, updatedAt = System.currentTimeMillis())
        }
    }

    fun rescan(service: NotificationListenerService) {
        val active = try { service.activeNotifications } catch (e: SecurityException) { null } ?: return
        active.forEach { onPosted(service.applicationContext, it) }
    }

    /** 綁定改變時清除舊內容。 */
    fun clear() {
        currentKey = null
        _state.value = NavGuidanceState()
    }
}
