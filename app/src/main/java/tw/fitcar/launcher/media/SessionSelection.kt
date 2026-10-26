package tw.fitcar.launcher.media

/** Pure selection policy; package names are exact identities, never aliases. */
data class SessionCandidate(val packageName: String, val playing: Boolean)
object SessionSelection {
    fun choose(sessions: List<SessionCandidate>, preferred: String?, follow: Boolean, current: Int?): Int? {
        val preferredPlaying = sessions.indexOfFirst { it.packageName == preferred && it.playing }.takeIf { it >= 0 }
        if (preferredPlaying != null) return preferredPlaying
        if (follow) {
            // Keep the current playing token stable when multiple players are active.
            current?.takeIf { it in sessions.indices && sessions[it].playing }?.let { return it }
            sessions.indexOfFirst { it.playing }.takeIf { it >= 0 }?.let { return it }
        }
        // A selected paused source can be resumed; unrelated stale sessions are never selected.
        return sessions.indexOfFirst { it.packageName == preferred }.takeIf { it >= 0 }
    }
}
