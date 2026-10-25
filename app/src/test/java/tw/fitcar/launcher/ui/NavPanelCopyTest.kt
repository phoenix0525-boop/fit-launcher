package tw.fitcar.launcher.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavPanelCopyTest {
    @Test
    fun `navigation hint describes available actions instead of showing an Android error`() {
        val hint = NavPanelCopy.supportMessage(null)

        assertTrue(hint.startsWith("導航會以分割畫面或全螢幕開啟"))
        assertFalse(hint.contains("Android 不允許"))
        assertFalse(hint.contains("無法嵌入"))
    }

    @Test
    fun `navigation hint reports the latest split screen result`() {
        assertTrue(NavPanelCopy.supportMessage(true).contains("最近一次：分割畫面成功"))
        assertTrue(NavPanelCopy.supportMessage(false).contains("最近一次：已改用全螢幕"))
    }
}
