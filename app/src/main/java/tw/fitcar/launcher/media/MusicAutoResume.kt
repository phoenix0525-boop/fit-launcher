package tw.fitcar.launcher.media

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import tw.fitcar.launcher.data.MediaMode
import tw.fitcar.launcher.data.Prefs
import tw.fitcar.launcher.system.DesktopGuard
import tw.fitcar.launcher.system.LaunchGate
import tw.fitcar.launcher.system.MusicResumePolicy
import tw.fitcar.launcher.system.SystemShortcutService

/** Opt-in, process-wide wake receiver. Returning to the launcher never starts a new attempt. */
object MusicAutoResume {
    private val handler = Handler(Looper.getMainLooper())
    private var context: Context? = null
    private var pending: MusicResumePolicy? = null
    private val _hint = MutableStateFlow("")
    val hint = _hint.asStateFlow()
    private fun prefs(c: Context) = c.getSharedPreferences("music_auto_resume", Context.MODE_PRIVATE)
    fun enabled(c: Context) = prefs(c).getBoolean("enabled", false)
    fun setEnabled(c: Context, value: Boolean) {
        prefs(c).edit().putBoolean("enabled", value).putBoolean("eligible", false).apply()
        cancel()
        attach(c)
        MediaRepository.refresh(c)
    }

    fun attach(c: Context) {
        if (context != null) return
        context = c.applicationContext
        ContextCompat.registerReceiver(context!!, receiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        val sp = prefs(c)
        val boot = DesktopGuard.bootCount(c)
        val oldBoot = sp.getInt("boot", -1)
        // Save before scheduling, so restarting the process cannot replay in the same boot.
        sp.edit().putInt("boot", boot).commit()
        if (oldBoot >= 0 && boot >= 0 && oldBoot != boot && SystemClock.elapsedRealtime() < 180_000) {
            sp.edit().putBoolean("sleeping", false).apply()
            begin(c)
        }
        else if (sp.getBoolean("sleeping", false) && interactive(c)) wake(c)
    }

    private fun interactive(c: Context) = c.getSystemService(PowerManager::class.java).isInteractive
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    cancel()
                    prefs(c).edit().putBoolean("sleeping", true).apply()
                }
                Intent.ACTION_SCREEN_ON -> wake(c)
            }
        }
    }
    private fun wake(c: Context) {
        val sp = prefs(c)
        if (!sp.getBoolean("sleeping", false)) return
        sp.edit().putBoolean("sleeping", false).apply()
        begin(c)
    }

    /** Record only real session observations; missing sessions never erase the last known intent. */
    fun observed(c: Context, pkg: String, state: Int) {
        if (!interactive(c) || prefs(c).getBoolean("sleeping", false) || pending != null) return
        if (state == PlaybackState.STATE_PLAYING) {
            _hint.value = ""
            prefs(c).edit().putString("package", pkg).putBoolean("eligible", true).apply()
        }
        else if (state == PlaybackState.STATE_PAUSED || state == PlaybackState.STATE_STOPPED)
            if (prefs(c).getString("package", null) == pkg) prefs(c).edit().putBoolean("eligible", false).apply()
    }

    fun manualControl() {
        context?.let { prefs(it).edit().putBoolean("eligible", false).apply() }
        cancel()
    }
    fun playbackChanged(c: Context, pkg: String, state: Int?) {
        // A pause after wake may be a user action or audio-focus loss; both cancel conservatively.
        if (interactive(c) && pending?.packageName == pkg &&
            (state == PlaybackState.STATE_PAUSED || state == PlaybackState.STATE_STOPPED)) manualControl()
    }
    fun cancel() {
        pending?.cancel()
        pending = null
        handler.removeCallbacks(tick)
        _hint.value = ""
    }
    private fun begin(c: Context) {
        if (!enabled(c) || pending != null) return
        val sp = prefs(c)
        val s = Prefs.get(c).state.value
        val selected = s.bindings.music?.let { ComponentName.unflattenFromString(it)?.packageName }
        val target = MusicResumePolicy.target(selected, sp.getString("package", null), s.mediaMode == MediaMode.FIXED)
        if (target == null || !sp.getBoolean("eligible", false)) return
        pending = MusicResumePolicy(target, true, SystemClock.elapsedRealtime())
        // Consume eligibility now. A crash/reconnect cannot send a second play.
        sp.edit().putBoolean("eligible", false).commit()
        _hint.value = "等待播放器恢復，準備續播"
        handler.postDelayed(tick, 3000)
    }

    private val tick = object : Runnable {
        override fun run() {
            val c = context ?: return
            val attempt = pending ?: return
            MediaRepository.refresh(c)
            val media = MediaRepository.state.value
            val audio = c.getSystemService(AudioManager::class.java)
            val foreground = SystemShortcutService.foreground
            val config = Prefs.get(c).state.value
            val selected = config.bindings.music?.let { ComponentName.unflattenFromString(it)?.packageName }
            val targetAllowed = MusicResumePolicy.target(selected, attempt.packageName, config.mediaMode == MediaMode.FIXED) != null
            val blocked = !interactive(c) || !SystemShortcutService.connected() || foreground == null ||
                LaunchGate.isCamera(foreground) || audio.mode != AudioManager.MODE_NORMAL ||
                audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).isEmpty()
            val action = attempt.decide(SystemClock.elapsedRealtime(), enabled(c) && targetAllowed, media.packageName,
                media.sessions.any { it.state == PlaybackState.STATE_PLAYING },
                media.supports(PlaybackState.ACTION_PLAY), blocked)
            when (action) {
                MusicResumePolicy.Action.WAIT -> handler.postDelayed(this, 1000)
                MusicResumePolicy.Action.PLAY -> {
                    pending = null
                    val sent = MediaRepository.playForResume(attempt.packageName)
                    _hint.value = if (sent) "已請求續播；若未播放，請點播放鍵" else "未能自動續播，請點播放鍵"
                    DesktopGuard.recordEvent(c, "音樂續播：${if (sent) "已送出" else "未送出"}")
                }
                MusicResumePolicy.Action.MANUAL -> { pending = null; _hint.value = "未能自動續播，請點播放鍵或連接上次播放器" }
                MusicResumePolicy.Action.SKIP -> {
                    pending = null
                    _hint.value = ""
                    if (media.isPlaying) media.packageName?.let { observed(c, it, media.playbackState) }
                }
            }
        }
    }
}
