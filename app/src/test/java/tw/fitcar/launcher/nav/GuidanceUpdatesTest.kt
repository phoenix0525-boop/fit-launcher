package tw.fitcar.launcher.nav

import org.junit.Assert.*
import org.junit.Test

class GuidanceUpdatesTest {
    private fun state(key: String, posted: Long, received: Long, text: String? = "260 公尺") =
        GoogleGuidanceState(true, true, text?.let { GoogleGuidance.parse(it, "測試道路", null, true) }, received, key, posted)
    @Test fun snapshotReplacesWithoutEmptyIntermediateState() {
        val previous = state("a", 100, 1000)
        val next = GuidanceUpdates.snapshot(previous, listOf(state("a", 200, 2000)))
        assertTrue(next.activeNavigation); assertEquals(2000L, next.receivedElapsed)
    }
    @Test fun blankUpdateRetainsTextButNeverRenewsItsAge() {
        val previous = state("a", 100, 1000)
        val next = GuidanceUpdates.posted(previous, state("a", 200, 2000, null))
        assertEquals(previous.guidance, next.guidance); assertEquals(1000L, next.receivedElapsed)
        assertFalse(next.fresh(9000))
    }
    @Test fun differentNotificationCannotInheritOldTurn() {
        val next = GuidanceUpdates.posted(state("a", 100, 1000), state("b", 200, 2000, null))
        assertNull(next.guidance)
    }
    @Test fun emptyAuthoritativeSnapshotEndsNavigation() {
        val next = GuidanceUpdates.snapshot(state("a", 100, 1000), emptyList())
        assertTrue(next.connected); assertFalse(next.activeNavigation); assertNull(next.guidance)
    }
    @Test fun repeatedSnapshotDoesNotRefreshOldText() {
        val old = state("a", 100, 1000)
        val next = GuidanceUpdates.snapshot(old, listOf(old))
        assertEquals(1000L, next.receivedElapsed); assertFalse(next.fresh(9000))
    }
    @Test fun olderPostedEventDoesNotReplaceNewerState() {
        val current = state("a", 200, 2000)
        assertEquals(current, GuidanceUpdates.posted(current, state("a", 100, 1000)))
    }
}
