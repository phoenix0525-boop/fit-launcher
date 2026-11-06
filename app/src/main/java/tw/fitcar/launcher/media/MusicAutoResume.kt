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
import tw.fitcar.launcher.system.MusicResumeDefaults
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
    fun autoConnectEnabled(c: Context) = prefs(c).getBoolean("auto_connect", MusicResumeDefaults.AUTO_CONNECT)
    fun enabled(c: Context) = prefs(c).getBoolean("enabled", MusicResumeDefaults.AUTO_RESUME)
    fun setAutoConnectEnabled(c: Context, value: Boolean) {
        prefs(c).edit().putBoolean("auto_connect", value).apply()
        cancel()
        attach(c)
        MediaRepository.refresh(c)
    }
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
            addAction(Intent.ACTION_SHUTDOWN)
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
                Intent.ACTION_SCREEN_OFF, Intent.ACTION_SHUTDOWN -> {
                    cancel()
                    keepIntentAcrossAccPause(c)
                    prefs(c).edit().putBoolean("sleeping", true).commit()
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
        val sp = prefs(c)
        if (state == PlaybackState.STATE_PLAYING) {
            _hint.value = ""
            sp.edit().putString("package", pkg).putBoolean("eligible", true).putBoolean("was_playing", true).remove("paused_at").apply()
        }
        else if (state == PlaybackState.STATE_PAUSED || state == PlaybackState.STATE_STOPPED)
            if (sp.getString("package", null) == pkg && sp.getBoolean("eligible", false)) {
                // 0.4.39：記下「由播放轉為暫停」的時間；熄火時再判斷是不是車機自動暫停
                sp.edit().putBoolean("eligible", false).putLong("paused_at", SystemClock.elapsedRealtime()).apply()
            }
    }

    /**
     * 0.4.39：FYT 熄火時會先暫停播放器、再關螢幕。原本這個暫停會讓續播資格被清掉，
     * 所以熄火前在播放的歌，發動後也不會續播。關螢幕／關機時，若暫停發生在 15 秒內且不是在桌面手動暫停，恢復續播資格。
     */
    private fun keepIntentAcrossAccPause(c: Context) {
        val sp = prefs(c)
        if (sp.getBoolean("eligible", false) || !sp.getBoolean("was_playing", false)) return
        val pausedAt = if (sp.contains("paused_at")) sp.getLong("paused_at", 0) else null
        val manualAt = if (sp.contains("manual_at")) sp.getLong("manual_at", 0) else null
        if (MusicResumePolicy.pauseLooksAutomatic(pausedAt, SystemClock.elapsedRealtime(), manualAt))
            sp.edit().putBoolean("eligible", true).commit()
    }

    fun manualControl() {
        context?.let { prefs(it).edit().putBoolean("eligible", false).putBoolean("was_playing", false)
            .putLong("manual_at", SystemClock.elapsedRealtime()).apply() }
        cancel()
    }
    fun playbackChanged(c: Context, pkg: String, state: Int?) {
        // 0.4.39：播放器在喚醒後重建工作階段時，通常先回報「暫停」；那不是使用者操作，不可取消續播。
        // 使用者在桌面按的暫停／切歌會經過 manualControl()；方向盤暫停則代表播放器原本就在播，decide() 會判定 SKIP。
    }
    fun cancel() {
        pending?.cancel()
        pending = null
        handler.removeCallbacks(tick)
        _hint.value = ""
    }
    private fun begin(c: Context) {
        if (pending != null) return
        val sp = prefs(c)
        val s = Prefs.get(c).state.value
        val selected = s.bindings.music?.let { ComponentName.unflattenFromString(it)?.packageName }
        val target = MusicResumePolicy.target(selected, sp.getString("package", null), s.mediaMode == MediaMode.FIXED)
        val resumeEligible = enabled(c) && sp.getBoolean("eligible", false)
        if (target == null || (!autoConnectEnabled(c) && !resumeEligible)) return
        pending = MusicResumePolicy(target, resumeEligible, SystemClock.elapsedRealtime())
        // Consume eligibility now. A crash/reconnect cannot send a second play.
        if (resumeEligible) sp.edit().putBoolean("eligible", false).commit()
        _hint.value = if (resumeEligible) "等待播放器恢復，準備續播" else ""
        handler.postDelayed(tick, MusicResumeDefaults.WAIT_MS)
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
            // 0.4.39：續播只送出播放指令、不切換畫面，不需要系統服務（無障礙）。
            // 前景未知（服務未開或剛啟動）時照常續播；只有確定是倒車／相機畫面時才等待。
            val blocked = !interactive(c) || LaunchGate.isCamera(foreground) || audio.mode != AudioManager.MODE_NORMAL ||
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
