package tw.fitcar.launcher.media

import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import tw.fitcar.launcher.nav.NavGuidanceRepository

/**
 * 「通知存取」用途只有兩個：
 * 1. 讓 MediaSessionManager 列出其他 App（例如 KKBOX）的 MediaSession。
 * 2. 讀取「使用者綁定的導航 App」的進行中導航通知文字，顯示在桌面導航區。
 * 其他 App 的通知內容一律不讀取；內容不保存、不上傳。
 */
class MediaListenerService : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        MediaRepository.refresh(applicationContext)
        NavGuidanceRepository.rescan(this)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        MediaRepository.refresh(applicationContext)
        requestRebind(ComponentName(this, MediaListenerService::class.java))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        NavGuidanceRepository.onPosted(applicationContext, sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        NavGuidanceRepository.onRemoved(sbn)
    }
}
