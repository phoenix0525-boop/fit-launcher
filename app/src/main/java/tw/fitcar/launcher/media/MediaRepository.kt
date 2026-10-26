package tw.fitcar.launcher.media

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tw.fitcar.launcher.data.Prefs

data class SessionInfo(val packageName: String, val state: Int, val actions: Long, val hasMetadata: Boolean)

/** 純選擇規則：已綁定音樂 App 時，絕不拿其他 App 的歌曲冒充。 */
object MediaSelectionPolicy {
    fun choosePackage(preferredPackage: String?, sessions: List<SessionInfo>): String? {
        if (preferredPackage != null) return sessions.firstOrNull { it.packageName == preferredPackage }?.packageName
        return sessions.firstOrNull { it.state == PlaybackState.STATE_PLAYING }?.packageName
            ?: sessions.firstOrNull()?.packageName
    }
}

data class MediaUiState(
    /** 使用者是否已開啟本 App 的通知存取。 */
    val permissionGranted: Boolean = false,
    val packageName: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val art: Bitmap? = null,
    val playbackState: Int = PlaybackState.STATE_NONE,
    val actions: Long = 0L,
    val positionMs: Long = 0L,
    val positionUpdateElapsed: Long = 0L,
    val speed: Float = 1f,
    val durationMs: Long = 0L,
    /** 診斷用：目前所有作用中的 session。 */
    val sessions: List<SessionInfo> = emptyList(),
    val error: String? = null,
) {
    val hasSession: Boolean get() = packageName != null
    val isPlaying: Boolean get() = playbackState == PlaybackState.STATE_PLAYING
    fun supports(action: Long): Boolean = actions and action != 0L
    val canPlayPause: Boolean
        get() = hasSession && if (isPlaying) {
            supports(PlaybackState.ACTION_PAUSE) || supports(PlaybackState.ACTION_PLAY_PAUSE)
        } else {
            supports(PlaybackState.ACTION_PLAY) || supports(PlaybackState.ACTION_PLAY_PAUSE)
        }
    val canSkipPrevious: Boolean get() = hasSession && supports(PlaybackState.ACTION_SKIP_TO_PREVIOUS)
    val canSkipNext: Boolean get() = hasSession && supports(PlaybackState.ACTION_SKIP_TO_NEXT)
    val canSeek: Boolean get() = supports(PlaybackState.ACTION_SEEK_TO) && durationMs > 0

    fun livePosition(nowElapsed: Long = SystemClock.elapsedRealtime()): Long {
        if (!isPlaying || positionUpdateElapsed == 0L) return positionMs.coerceAtLeast(0)
        val p = positionMs + ((nowElapsed - positionUpdateElapsed) * speed).toLong()
        return if (durationMs > 0) p.coerceIn(0, durationMs) else p.coerceAtLeast(0)
    }
}

/**
 * 透過 Android 正式的 MediaSession / MediaController 讀取正在播放的音樂。
 * 只顯示 App 實際送出的欄位，只啟用 PlaybackState 實際宣告的控制。
 */
object MediaRepository {
    private val _state = MutableStateFlow(MediaUiState())
    val state: StateFlow<MediaUiState> = _state.asStateFlow()

    private val main = Handler(Looper.getMainLooper())
    private var manager: MediaSessionManager? = null
    private var listenerRegistered = false
    private var current: MediaController? = null
    private var appContext: Context? = null

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        appContext?.let { select(it, controllers.orEmpty()) }
    }

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onSessionDestroyed() {
            appContext?.let { refresh(it) }
        }
    }

    fun isPermissionGranted(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun listenerComponent(context: Context) = ComponentName(context, MediaListenerService::class.java)

    fun refresh(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        if (!isPermissionGranted(ctx)) {
            detach()
            unregister()
            _state.value = MediaUiState(permissionGranted = false)
            return
        }
        try {
            val m = manager ?: (ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager).also { manager = it }
            if (!listenerRegistered) {
                m.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent(ctx), main)
                listenerRegistered = true
            }
            select(ctx, m.getActiveSessions(listenerComponent(ctx)))
        } catch (e: SecurityException) {
            detach()
            _state.value = MediaUiState(permissionGranted = false, error = "系統拒絕讀取媒體工作階段：${e.message}")
        }
    }

    private fun unregister() {
        if (listenerRegistered) {
            try { manager?.removeOnActiveSessionsChangedListener(sessionsListener) } catch (_: Exception) {}
            listenerRegistered = false
        }
    }

    private fun select(ctx: Context, controllers: List<MediaController>) {
        val preferredPackage = Prefs.get(ctx).state.value.bindings.music
            ?.let { ComponentName.unflattenFromString(it)?.packageName }
        val infos = controllers.map {
            SessionInfo(
                packageName = it.packageName,
                state = it.playbackState?.state ?: PlaybackState.STATE_NONE,
                actions = it.playbackState?.actions ?: 0L,
                hasMetadata = it.metadata != null,
            )
        }
        val chosenPackage = MediaSelectionPolicy.choosePackage(preferredPackage, infos)
        val chosen = chosenPackage?.let { pkg -> controllers.firstOrNull { it.packageName == pkg } }

        if (chosen?.sessionToken != current?.sessionToken) {
            detach()
            current = chosen
            chosen?.registerCallback(callback, main)
        }
        publish(controllers)
    }

    private fun detach() {
        current?.unregisterCallback(callback)
        current = null
    }

    private var lastSessions: List<SessionInfo> = emptyList()

    private fun publish(all: List<MediaController>? = null) {
        if (all != null) {
            lastSessions = all.map {
                SessionInfo(
                    packageName = it.packageName,
                    state = it.playbackState?.state ?: PlaybackState.STATE_NONE,
                    actions = it.playbackState?.actions ?: 0L,
                    hasMetadata = it.metadata != null,
                )
            }
        }
        val c = current
        if (c == null) {
            _state.value = MediaUiState(permissionGranted = true, sessions = lastSessions)
            return
        }
        val md = c.metadata
        val ps = c.playbackState
        _state.value = MediaUiState(
            permissionGranted = true,
            packageName = c.packageName,
            title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE),
            artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE),
            album = md?.getString(MediaMetadata.METADATA_KEY_ALBUM),
            art = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON),
            playbackState = ps?.state ?: PlaybackState.STATE_NONE,
            actions = ps?.actions ?: 0L,
            positionMs = ps?.position ?: 0L,
            positionUpdateElapsed = ps?.lastPositionUpdateTime ?: 0L,
            speed = ps?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
            durationMs = md?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.coerceAtLeast(0) ?: 0L,
            sessions = lastSessions,
        )
    }

    fun playPause() {
        val c = current ?: return
        val s = _state.value
        if (!s.canPlayPause) return
        if (s.isPlaying) {
            c.transportControls.pause()
        } else {
            c.transportControls.play()
        }
    }

    fun next() { if (_state.value.canSkipNext) current?.transportControls?.skipToNext() }
    fun previous() { if (_state.value.canSkipPrevious) current?.transportControls?.skipToPrevious() }
    fun seekTo(ms: Long) { if (_state.value.canSeek) current?.transportControls?.seekTo(ms) }
}
