package tw.fitcar.launcher.ui

import org.junit.Assert.*
import org.junit.Test

class SettingsSectionsTest {
    @Test fun elevenSectionsInFourGroupsInFrequencyOrder() {
        assertEquals(listOf("日常", "初次設定", "維護", "診斷"), SettingsSections.GROUPS.map { it.title })
        assertEquals(11, SettingsSections.ALL.size)
        assertEquals(SettingsSections.ALL.toSet().size, SettingsSections.ALL.size)
        assertEquals(SettingsSections.APPEARANCE, SettingsSections.ALL.first())
        assertEquals(SettingsSections.ABOUT, SettingsSections.ALL.last())
    }
    @Test fun groupLabelsSitOnFirstSectionOfEachGroup() {
        assertEquals(mapOf(0 to "日常", 6 to "初次設定", 8 to "維護", 9 to "診斷"), SettingsSections.GROUP_LABELS)
    }
    @Test fun technicalNamesAreGoneAndLegacyNamesStillResolve() {
        listOf("App 綁定", "權限", "啟動設定", "返回原廠桌面", "實機資訊").forEach { assertFalse(it in SettingsSections.ALL) }
        assertEquals(SettingsSections.NAV, SettingsSections.resolve("App 綁定"))
        assertEquals(SettingsSections.TAKEOVER, SettingsSections.resolve("返回原廠桌面"))
        assertEquals(SettingsSections.MAINTENANCE, SettingsSections.resolve("更新"))
        assertEquals(SettingsSections.CHECKER, SettingsSections.resolve("實機資訊"))
        assertNull(SettingsSections.resolve("不存在"))
        SettingsSections.LEGACY.values.forEach { assertTrue(it in SettingsSections.ALL) }
    }
    @Test fun characterUnlockEntryExists() {
        assertTrue(SettingsSections.CHARACTER_UNLOCK in SettingsSections.ALL)
    }
}
