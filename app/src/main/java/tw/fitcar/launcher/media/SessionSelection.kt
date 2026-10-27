package tw.fitcar.launcher.media

/** Pure selection policy; package names are exact identities, never aliases. */
data class SessionCandidate(val packageName: String, val playing: Boolean)
object SessionSelection {
    fun choose(sessions: List<SessionCandidate>, preferred: String?, follow: Boolean, current: Int?, remembered: String? = null): Int? {
        val preferredSource = if (follow) remembered ?: preferred else preferred
        val preferredPlaying = sessions.indexOfFirst { it.packageName == preferredSource && it.playing }.takeIf { it >= 0 }
        if (preferredPlaying != null) return preferredPlaying
        if (follow) {
            // Keep the current playing token stable when multiple players are active.
            current?.takeIf { it in sessions.indices && sessions[it].playing }?.let { return it }
            sessions.indexOfFirst { it.playing }.takeIf { it >= 0 }?.let { return it }
            // Pausing the source we just controlled must leave a way to resume it.
            // This never picks an unrelated paused token on a cold start.
            current?.takeIf { it in sessions.indices }?.let { return it }
        }
        // A selected paused source can be resumed; unrelated stale sessions are never selected.
        return sessions.indexOfFirst { it.packageName == preferredSource }.takeIf { it >= 0 }
    }
}
