package tw.fitcar.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUsagePolicyTest {
    @Test fun recentKeepsNewestDistinctUpToEight() {
        var list = emptyList<String>()
        (1..10).forEach { list = AppUsagePolicy.push(list, "a$it/Main") }
        list = AppUsagePolicy.push(list, "a5/Main")
        assertEquals(8, list.size)
        assertEquals("a5/Main", list.first())
        assertEquals(1, list.count { it == "a5/Main" })
        assertEquals(list, AppUsagePolicy.decode(AppUsagePolicy.encode(list)))
        assertEquals(emptyList<String>(), AppUsagePolicy.decode(null))
    }

    @Test fun searchMatchesLabelOrPackageIgnoringCase() {
        assertTrue(AppUsagePolicy.matches("KKBOX", "com.skysoft.kkbox.android", "kkbox"))
        assertTrue(AppUsagePolicy.matches("導航王", "com.kingwaytek.naviking", " 導航 "))
        assertTrue(AppUsagePolicy.matches("任何", "x.y", "   "))
        assertFalse(AppUsagePolicy.matches("工具程式3", "test.app3", "程式12"))
    }
    @Test fun systemUsageOnlyFillsAfterOwnHistory() {
        val keys = mapOf("a" to "a/A", "b" to "b/B", "c" to "c/C")
        assertEquals(listOf("b/B", "a/A", "c/C"), AppUsagePolicy.merge(listOf("b/B"), listOf("a", "b", "x", "c"), keys::get))
        assertEquals(8, AppUsagePolicy.merge(List(8) { "o$it" }, listOf("a"), keys::get).size)
    }
}
