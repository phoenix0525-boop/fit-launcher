package tw.fitcar.launcher.system

import org.junit.Assert.*
import org.junit.Test

class OemAssessmentTest {
    @Test fun factoryAppStates() {
        assertEquals(FactoryAppState.MISSING, OemAssessment.factoryApp(false, false, false))
        assertEquals(FactoryAppState.DISABLED, OemAssessment.factoryApp(true, false, true))
        assertEquals(FactoryAppState.NO_LAUNCHER, OemAssessment.factoryApp(true, true, false))
        assertEquals(FactoryAppState.AVAILABLE, OemAssessment.factoryApp(true, true, true))
    }
    @Test fun binderNeverClaimedUsableFromMetadataAlone() {
        assertTrue(OemAssessment.binderVolume(ComponentFacts(false)).verdict.startsWith("不可行"))
        assertTrue(OemAssessment.binderVolume(ComponentFacts(true, exported = false)).verdict.startsWith("不可行"))
        assertTrue(OemAssessment.binderVolume(ComponentFacts(true, true, "com.syu.permission.X", "signature", false)).verdict.contains("需要"))
        val open = OemAssessment.binderVolume(ComponentFacts(true, true, null)).verdict
        assertTrue(open.contains("未驗證")); assertTrue(open.contains("不發送"))
    }
    @Test fun missingTaskPermissionDoesNotRuleOutAnOemHost() {
        val finding = OemAssessment.taskView("signature|privileged", false)
        assertTrue(finding.verdict.startsWith("直接 TaskView 路徑受限"))
        assertTrue(finding.verdict.contains("尚未驗證"))
        assertFalse(finding.evidence.contains("持有=true"))
    }
    @Test fun dspAutoApplyNeverClaimedAvailable() {
        assertTrue(OemAssessment.dspAutoApply(FactoryAppState.MISSING, 0, 0).verdict.startsWith("不可行"))
        assertTrue(OemAssessment.dspAutoApply(FactoryAppState.AVAILABLE, 0, 2).verdict.startsWith("不可行"))
        assertTrue(OemAssessment.dspAutoApply(FactoryAppState.AVAILABLE, 1, 0).verdict.contains("未驗證前不寫入"))
    }
    @Test fun protectionNames() {
        assertEquals("signature|privileged", OemAssessment.protectionName(2, 0x10))
        assertEquals("normal", OemAssessment.protectionName(0, 0))
    }
}
