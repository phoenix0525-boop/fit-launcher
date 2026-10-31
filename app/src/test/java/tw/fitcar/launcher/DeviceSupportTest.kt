package tw.fitcar.launcher

import org.junit.Assert.*
import org.junit.Test

class DeviceSupportTest {
    @Test fun reportedDeviceIsRecognisedButStillPointsToChecks() {
        val t = DeviceSupport.current("AKZ", "ZX09", "uis7870sc_2h10_nosec", 33, "13")
        assertTrue(t.contains("已回報")); assertTrue(t.contains("實機檢測"))
    }
    @Test fun sameChipOtherDeviceIsNotClaimedSupported() {
        val t = DeviceSupport.current("Other", "X", "uis7870sc_other", 33, "13")
        assertTrue(t.contains("不在已回報實測清單"))
        assertTrue(DeviceSupport.current("AKZ", "ZX09", "uis7870sc_2h10_nosec", 34, "14").contains("不在已回報"))
    }
    @Test fun basicsDoNotImplyIntegrations() {
        assertTrue(DeviceSupport.basics().contains("Android 8.0（API 26）"))
        assertTrue(DeviceSupport.basics().contains("不代表車機專屬整合可用"))
        // 0.4.34：實車與權限分析確認做不到的整合列為「不提供」，不再列為待驗證
        assertTrue(DeviceSupport.NOT_PROVIDED.any { it.contains("原廠實際音量") })
        assertTrue(DeviceSupport.NOT_PROVIDED.any { it.contains("DSP") })
        assertTrue(DeviceSupport.NOT_PROVIDED.any { it.contains("嵌入") })
        assertTrue(DeviceSupport.PENDING.none { it.contains("方向盤") })
    }
}
