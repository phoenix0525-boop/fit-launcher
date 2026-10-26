package tw.fitcar.launcher.media

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import tw.fitcar.launcher.nav.GoogleGuidanceRepository

/** MediaSession access plus selected Google Maps navigation notifications; never persisted. */
class MediaListenerService : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        live = this
        GoogleGuidanceRepository.connection(true)
        GoogleGuidanceRepository.rescan(this)
        MediaRepository.refresh(applicationContext)
    }
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        GoogleGuidanceRepository.posted(applicationContext, sbn)
    }
    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        GoogleGuidanceRepository.removed(this, sbn)
    }
    override fun onListenerDisconnected() {
        live = null
        GoogleGuidanceRepository.connection(false)
        super.onListenerDisconnected()
        MediaRepository.refresh(applicationContext)
    }
    override fun onDestroy() {
        if (live === this) { live = null; GoogleGuidanceRepository.connection(false) }
        super.onDestroy()
    }
    companion object {
        private var live: MediaListenerService? = null
        fun rescanNavigation() {
            GoogleGuidanceRepository.clear()
            live?.let { GoogleGuidanceRepository.rescan(it) }
        }
    }
}
