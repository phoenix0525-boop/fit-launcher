package tw.fitcar.launcher.data
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import tw.fitcar.launcher.lyrics.LyricsRepository
class BackupStoreTest {
    @Test fun previewIncludesRemovalsAndMissingAppsAndRestoreRevokesLyrics() {
        val c=InstrumentationRegistry.getInstrumentation().targetContext
        val before=BackupStore.snapshot(c)
        val oldLyrics=LyricsRepository.enabled.value
        try {
            Prefs.get(c).update { it.copy(homeAddress="測試舊地址") }
            LyricsRepository.setEnabled(c,true)
            val plan=BackupPlan(mapOf("fit_launcher" to mapOf("bind_music" to "nonexistent.test.app/.Main")))
            val changes=BackupStore.changes(c,plan).joinToString("\n")
            assertTrue(changes.contains("回家地址"))
            assertTrue(changes.contains("找不到，請重新選擇"))
            BackupStore.apply(c,plan)
            assertNull(Prefs.get(c).state.value.homeAddress)
            assertFalse(LyricsRepository.enabled.value)
            assertEquals("nonexistent.test.app/.Main",Prefs.get(c).state.value.bindings.music)
            val snapshot=BackupStore.snapshot(c)
            assertTrue(snapshot.getValue("fit_launcher").containsKey("manual_brightness"))
            // 手動亮度覆寫是暫時狀態：不匯出，匯入後一律回到排程
            assertFalse(snapshot.getValue("fit_launcher").containsKey("manual_override"))
            Prefs.get(c).update { it.copy(manualOverride = true) }
            BackupStore.apply(c, BackupPlan(mapOf("fit_launcher" to mapOf("shortcut_pages" to 2))))
            assertFalse(Prefs.get(c).state.value.manualOverride)
            assertEquals(snapshot,SettingsBackup.decode(SettingsBackup.encode(snapshot,"test","now")).groups)
        } finally {
            BackupStore.apply(c,BackupPlan(before))
            LyricsRepository.setEnabled(c,oldLyrics)
        }
    }
    @Test fun splitTargetPersistsAndRestoresWithoutReplacingOtherSettings() {
        val c=InstrumentationRegistry.getInstrumentation().targetContext
        val before=BackupStore.snapshot(c)
        try {
            Prefs.get(c).update { it.copy(splitNavigationSide=tw.fitcar.launcher.nav.NavigationSide.RIGHT) }
            Prefs.get(c).reload()
            assertEquals(tw.fitcar.launcher.nav.NavigationSide.RIGHT,Prefs.get(c).state.value.splitNavigationSide)
            val saved=BackupStore.snapshot(c)
            val raw=SettingsBackup.encode(saved,"test","now")
            Prefs.get(c).update { it.copy(splitNavigationSide=tw.fitcar.launcher.nav.NavigationSide.LEFT) }
            BackupStore.apply(c,SettingsBackup.decode(raw))
            assertEquals(tw.fitcar.launcher.nav.NavigationSide.RIGHT,Prefs.get(c).state.value.splitNavigationSide)
            assertEquals(saved,BackupStore.snapshot(c))
        } finally { BackupStore.apply(c,BackupPlan(before)) }
    }
}
