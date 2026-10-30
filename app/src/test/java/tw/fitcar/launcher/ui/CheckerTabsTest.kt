package tw.fitcar.launcher.ui

import org.junit.Assert.*
import org.junit.Test

/** CheckerScreen 的分頁內容依索引顯示；名稱與索引必須一致，設定頁才不會開錯分頁。 */
class CheckerTabsTest {
    @Test fun namedTabsMatchScreenOrder() {
        assertEquals(8, CheckerTabs.index(CheckerTabs.VOLUME))
        assertEquals(4, CheckerTabs.index(CheckerTabs.BRIGHTNESS))
        assertEquals(7, CheckerTabs.index(CheckerTabs.MANUAL))
        assertEquals(0, CheckerTabs.index(CheckerTabs.IMPROVEMENTS))
        assertEquals(10, CheckerTabs.ALL.size)
    }
}
