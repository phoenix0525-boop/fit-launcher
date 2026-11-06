package tw.fitcar.launcher.system

object MusicResumeDefaults {
    const val AUTO_CONNECT = true
    const val AUTO_RESUME = true
    const val SOURCE = "last"
    const val WAIT_MS = 3_000L
    /** 熄火前多久內的「暫停」視為車機自動暫停（FYT 熄火時會先暫停播放器，再關螢幕）。 */
    const val ACC_PAUSE_GRACE_MS = 15_000L
    /** 使用者在桌面按暫停後，播放器回報暫停的容許延遲。 */
    const val MANUAL_PAUSE_WINDOW_MS = 3_000L
}

/** One wake attempt. It never launches an app, toggles playback, or retries a play command. */
class MusicResumePolicy(val packageName: String, private val wasPlaying: Boolean, private val startedAt: Long) {
    enum class Action { WAIT, PLAY, MANUAL, SKIP }
    private var finished = false
    fun cancel() { finished = true }

    fun decide(now: Long, enabled: Boolean, sessionPackage: String?, anyPlaying: Boolean,
               canPlay: Boolean, blocked: Boolean): Action {
        if (finished) return Action.SKIP
        val action = when {
            !enabled || !wasPlaying || anyPlaying -> Action.SKIP
            now - startedAt >= 60_000 -> Action.MANUAL
            now - startedAt < MusicResumeDefaults.WAIT_MS || blocked || sessionPackage != packageName || !canPlay -> Action.WAIT
            else -> Action.PLAY
        }
        if (action != Action.WAIT) finished = true
        return action
    }

    companion object {
        /**
         * 0.4.39：熄火前的暫停是否像車機自動暫停（而非使用者手動暫停）。
         * 條件：播放器最後是由播放轉為暫停、暫停發生在關螢幕（或關機）前 [MusicResumeDefaults.ACC_PAUSE_GRACE_MS] 內，
         * 而且暫停前沒有在桌面按過暫停／切歌。
         */
        fun pauseLooksAutomatic(pausedAt: Long?, offAt: Long, manualAt: Long?): Boolean {
            if (pausedAt == null || offAt - pausedAt !in 0..MusicResumeDefaults.ACC_PAUSE_GRACE_MS) return false
            return manualAt == null || pausedAt - manualAt !in 0..MusicResumeDefaults.MANUAL_PAUSE_WINDOW_MS
        }

        fun target(selected: String?, remembered: String?, fixed: Boolean): String? =
            remembered?.takeIf { !fixed || it == selected }
    }
}
