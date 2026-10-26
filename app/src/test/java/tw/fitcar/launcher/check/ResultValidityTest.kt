package tw.fitcar.launcher.check
import org.junit.Assert.*
import org.junit.Test

class ResultValidityTest {
    @Test fun oldVersionsAndLegacyResultsNeverCountAsCurrent() {
        assertFalse(ResultValidity.matches("0.2.1 (3)", "FIT=true", "0.3.0 (4)", "FIT=true"))
        assertFalse(ResultValidity.matches("", "", "0.3.0 (4)", "FIT=true"))
    }
    @Test fun reverseResultNeedsSameDefaultHomeContext() {
        assertFalse(ResultValidity.matches("v4", "FIT=false", "v4", "FIT=true"))
        assertTrue(ResultValidity.matches("v4", "FIT=true", "v4", "FIT=true"))
    }
    @Test fun sameBootIsOnlyObservation() {
        assertTrue(BootComparison.describe(10, 10, 1000, 9000).contains("無法單憑此判定"))
        assertTrue(BootComparison.describe(10, 11, 1000, 9000).contains("重新開機"))
        assertTrue(BootComparison.describe(-1, -1, 1000, 9000).contains("不足以確認"))
    }
}
