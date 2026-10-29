package tw.fitcar.launcher.nav

import android.app.Notification
import android.content.Context
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class GoogleGuidanceState(
    val connected: Boolean = false,
    val activeNavigation: Boolean = false,
    val guidance: GoogleGuidance? = null,
    val receivedElapsed: Long = 0,
    val notificationKey: String? = null,
    val postedAt: Long = 0,
) {
    /** [speedKmh] 只傳有效 GPS 車速；未知時採最保守的 30 秒。 */
    fun fresh(now: Long = SystemClock.elapsedRealtime(), speedKmh: Float? = null) = guidance != null &&
        GuidanceFreshness.isFresh(receivedElapsed, now, connected, GuidanceFreshness.distanceMeters(guidance.distance), speedKmh)
}

/** Only the explicitly selected Google Maps package is read; text stays in process memory. */
object GoogleGuidanceRepository {
    private val mutable = MutableStateFlow(GoogleGuidanceState())
    val state = mutable.asStateFlow()
    fun connection(connected: Boolean) {
        mutable.value = if (connected) mutable.value.copy(connected = true) else GoogleGuidanceState()
    }
    fun clear() { mutable.value = GoogleGuidanceState(connected = mutable.value.connected) }

    private fun candidate(context: Context, sbn: StatusBarNotification): Boolean = GoogleNavigation.isSelected(context) &&
        sbn.packageName == GoogleNavigation.PACKAGE && (sbn.isOngoing || sbn.notification.category == Notification.CATEGORY_NAVIGATION)

    private fun read(context: Context, sbn: StatusBarNotification): GoogleGuidanceState? {
        if (!candidate(context, sbn)) return null
        val n = sbn.notification
        val e = n.extras
        // Big text may be present but blank; some Maps versions put the road in text lines.
        val bodies = (listOf(e.getCharSequence(Notification.EXTRA_BIG_TEXT), e.getCharSequence(Notification.EXTRA_TEXT)) +
            e.getCharSequenceArray(Notification.EXTRA_TEXT_LINES).orEmpty().take(8))
            .mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotBlank) }.distinct()
        val g = GoogleGuidance.parse(e.getCharSequence(Notification.EXTRA_TITLE),
            bodies.joinToString(" · "), e.getCharSequence(Notification.EXTRA_SUB_TEXT),
            navigationCategory = n.category == Notification.CATEGORY_NAVIGATION)
        if (g == null && n.category != Notification.CATEGORY_NAVIGATION) return null
        val age = System.currentTimeMillis() - sbn.postTime
        // 重新掃描（例如回到桌面）沿用通知真正的發布時間，不把舊提示當新的，也不因重掃就直接判為過期；
        // 是否仍可信交給 GuidanceFreshness 依車速與距離判斷。時鐘異常（未來時間）才視為無效。
        val received = if (age < -5000) 0L
            else (SystemClock.elapsedRealtime() - age.coerceAtLeast(0)).coerceAtLeast(1)
        return GoogleGuidanceState(mutable.value.connected, true, g, received, sbn.key, sbn.postTime)
    }

    fun posted(context: Context, sbn: StatusBarNotification) {
        if (!GoogleNavigation.isSelected(context) || sbn.packageName != GoogleNavigation.PACKAGE) return
        val next = read(context, sbn)
        if (next == null) {
            if (mutable.value.notificationKey == sbn.key) clear()
            return
        }
        mutable.value = GuidanceUpdates.posted(mutable.value, next)
    }

    internal fun replaceSnapshot(context: Context, notifications: List<StatusBarNotification>) {
        if (!GoogleNavigation.isSelected(context)) { clear(); return }
        val next = notifications.mapNotNull { read(context, it) }
        mutable.value = GuidanceUpdates.snapshot(mutable.value, next)
    }

    fun rescan(service: NotificationListenerService, excludedKey: String? = null) {
        if (!GoogleNavigation.isSelected(service)) { clear(); return }
        val notifications = try { service.activeNotifications } catch (_: SecurityException) { connection(false); return }
        replaceSnapshot(service, notifications.orEmpty().filter { it.key != excludedKey })
    }
    fun removed(service: NotificationListenerService, sbn: StatusBarNotification) {
        if (sbn.key == mutable.value.notificationKey) rescan(service, sbn.key)
    }
}
