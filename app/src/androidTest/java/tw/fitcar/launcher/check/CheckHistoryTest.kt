package tw.fitcar.launcher.check

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class CheckHistoryTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @After fun clean() { CheckStore.clearSaved(context) }
    private fun seed(version: String?, scenario: String?, id: String = "m_reverse") {
        CheckStore.clearSaved(context)
        val row = JSONObject().apply {
            put("id", id); put("group", "手動檢查"); put("title", "倒車影像")
            put("status", "PASS"); put("finding", "正常"); put("time", 1000L)
            version?.let { put("appVersion", it) }
            scenario?.let { put("scenario", it); put("contextKey", it) }
        }
        context.getSharedPreferences("fit_checks", 0).edit().putString("saved", JSONArray().put(row).toString()).commit()
        CheckStore.reload(context)
    }
    @Test fun legacyPassIsHistoricalAfterUpgrade() {
        seed(null, null)
        val results = CheckStore.results.value.values
        assertEquals(0, results.count { it.status == Status.PASS })
        assertEquals(1, results.count { it.status == Status.HISTORY })
        assertEquals(Status.TODO, CheckStore.results.value["m_reverse"]?.status)
        assertTrue(Report.text(context, CheckStore.results.value).contains("歷史結果"))
    }
    @Test fun currentVersionWithDifferentDefaultHomeContextNeedsRetest() {
        seed(CheckStore.version(context), "另一個預設桌面")
        assertEquals(Status.TODO, CheckStore.results.value["m_reverse"]?.status)
        assertEquals(0, CheckStore.results.value.values.count { it.status == Status.PASS })
    }
    @Test fun matchingVersionAndContextPassSurvivesReload() {
        seed(CheckStore.version(context), CheckStore.scenario(context), "m_touch")
        assertEquals(Status.PASS, CheckStore.results.value["m_touch"]?.status)
        CheckStore.reload(context)
        assertEquals(1, CheckStore.results.value.values.count { it.status == Status.PASS })
    }
}
