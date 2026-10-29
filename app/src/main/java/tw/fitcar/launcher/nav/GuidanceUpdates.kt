package tw.fitcar.launcher.nav

/** Updates retain no fabricated freshness. A snapshot is published only after its replacement is ready. */
object GuidanceUpdates {
    fun posted(previous: GoogleGuidanceState, next: GoogleGuidanceState): GoogleGuidanceState {
        if (next.postedAt < previous.postedAt) return previous
        return if (next.notificationKey == previous.notificationKey && next.guidance == null && previous.guidance != null)
            next.copy(guidance = previous.guidance, receivedElapsed = previous.receivedElapsed)
        else next
    }
    fun snapshot(previous: GoogleGuidanceState, candidates: List<GoogleGuidanceState>): GoogleGuidanceState {
        val next = candidates.maxByOrNull { it.postedAt } ?: return GoogleGuidanceState(connected = previous.connected)
        val baseline = if (previous.notificationKey == next.notificationKey) previous else GoogleGuidanceState(connected = previous.connected)
        return posted(baseline, next)
    }
}
