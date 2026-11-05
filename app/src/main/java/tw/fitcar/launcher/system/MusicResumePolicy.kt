package tw.fitcar.launcher.system

object MusicResumeDefaults {
    const val AUTO_CONNECT = true
    const val AUTO_RESUME = true
    const val SOURCE = "last"
    const val WAIT_MS = 3_000L
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
        fun target(selected: String?, remembered: String?, fixed: Boolean): String? =
            remembered?.takeIf { !fixed || it == selected }
    }
}
