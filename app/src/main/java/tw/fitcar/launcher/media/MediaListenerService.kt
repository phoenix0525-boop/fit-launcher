package tw.fitcar.launcher.media

import android.service.notification.NotificationListenerService

/**
 * 只用來取得「通知存取」授權，讓 MediaSessionManager 能列出其他 App（例如 KKBOX）的 MediaSession。
 * 不讀取、不保存任何通知內容。
 */
class MediaListenerService : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        MediaRepository.refresh(applicationContext)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        MediaRepository.refresh(applicationContext)
    }
}
