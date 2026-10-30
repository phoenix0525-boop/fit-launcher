package tw.fitcar.launcher.check

import org.junit.Assert.*
import org.junit.Test

class ReportSummaryTest {
    @Test fun zeroProblemsWithPendingTestsIsNotAllClear() {
        val h = ReportSummary.headline(0, 3)
        assertTrue(h.contains("3 項待測試")); assertTrue(h.contains("不代表全部正常"))
        assertFalse(h == "目前沒有")
    }
    @Test fun problemsAndPendingBothShown() {
        assertEquals("2 項；另有 1 項待測試", ReportSummary.headline(2, 1))
        assertEquals("2 項", ReportSummary.headline(2, 0))
        assertEquals("目前沒有", ReportSummary.headline(0, 0))
    }
}
