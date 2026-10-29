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
    fun fresh(now: Long = SystemClock.elapsedRealtime()) = guidance != null &&
        GuidanceFreshness.isFresh(receivedElapsed, now, connected)
}

/** Only the explicitly selected Google Maps package is read; text stays in process memory. */
object GoogleGuidanceRepository {
    private val mutable = MutableStateFlow(GoogleGuidanceState())
    val state = mutable.asStateFlow()
    fun connection(connected: Boolean) { mutable.value = GoogleGuidanceState(connected = connected) }
    fun clear() { mutable.value = GoogleGuidanceState(connected = mutable.value.connected) }

    private fun candidate(context: Context, sbn: StatusBarNotification): Boolean = GoogleNavigation.isSelected(context) &&
        sbn.packageName == GoogleNavigation.PACKAGE && (sbn.isOngoing || sbn.notification.category == Notification.CATEGORY_NAVIGATION)

    fun posted(context: Context, sbn: StatusBarNotification) {
        if (!candidate(context, sbn)) return
        val n = sbn.notification
        val e = n.extras
        // Big text may be present but blank; some Maps versions put the road in text lines.
        val bodies = (listOf(e.getCharSequence(Notification.EXTRA_BIG_TEXT), e.getCharSequence(Notification.EXTRA_TEXT)) +
            e.getCharSequenceArray(Notification.EXTRA_TEXT_LINES).orEmpty().take(8))
            .mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotBlank) }.distinct()
        val g = GoogleGuidance.parse(e.getCharSequence(Notification.EXTRA_TITLE),
            bodies.joinToString(" · "), e.getCharSequence(Notification.EXTRA_SUB_TEXT),
            navigationCategory = n.category == Notification.CATEGORY_NAVIGATION)
        if (g == null && n.category != Notification.CATEGORY_NAVIGATION) {
            if (mutable.value.notificationKey == sbn.key) clear()
            return
        }
        if (sbn.postTime < mutable.value.postedAt) return
        val age = System.currentTimeMillis() - sbn.postTime
        // Rescanning an old notification must not make its instruction look new.
        val received = if (age < -5000 || age >= GuidanceFreshness.MAX_AGE_MS) 0L
            else (SystemClock.elapsedRealtime() - age.coerceAtLeast(0)).coerceAtLeast(1)
        mutable.value = GoogleGuidanceState(mutable.value.connected, true, g, received, sbn.key, sbn.postTime)
    }

    fun rescan(service: NotificationListenerService, excludedKey: String? = null) {
        if (!GoogleNavigation.isSelected(service)) { clear(); return }
        val notifications = try { service.activeNotifications } catch (_: SecurityException) { connection(false); return }
        clear()
        notifications.orEmpty().filter { it.key != excludedKey && candidate(service, it) }
            .sortedBy { it.postTime }.forEach { posted(service, it) }
    }
    fun removed(service: NotificationListenerService, sbn: StatusBarNotification) {
        if (sbn.key == mutable.value.notificationKey) rescan(service, sbn.key)
    }
}
