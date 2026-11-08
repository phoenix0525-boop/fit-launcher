package tw.fitcar.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AppGridPolicyTest {
    @Test fun allAppsUsesSixColumnsWithoutSearchRecentOrWidgets() {
        val policy = AppGridPolicy.ALL_APPS

        assertEquals(6, policy.fixedColumns)
        assertFalse(policy.searchable)
        assertFalse(policy.showRecent)
        assertFalse(policy.showHeader)
    }
}
