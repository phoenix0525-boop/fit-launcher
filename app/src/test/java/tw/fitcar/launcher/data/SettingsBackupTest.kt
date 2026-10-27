package tw.fitcar.launcher.data
import org.junit.Assert.*
import org.junit.Test
class SettingsBackupTest {
    private val sample = mapOf("fit_launcher" to mapOf<String, Any>("shortcut_pages" to 3, "bind_music" to "com.example.music/.Main", "home_address" to "測試路1號", "manual_brightness" to 30), "theme_appearance" to mapOf("clarity" to "airy"))
    @Test fun roundTripAndUnknownFields() {
        val json = SettingsBackup.encode(sample, "0.4.15", "2026-10-27T00:00:00Z")
        assertEquals(sample, SettingsBackup.decode(json).groups)
        assertEquals(sample, SettingsBackup.decode(json.replace("\"schemaVersion\":1", "\"future\":123,\"schemaVersion\":1")).groups)
    }
    @Test fun secretsAndConsentNeverExportOrRestore() {
        val json = SettingsBackup.encode(sample + mapOf("weather" to mapOf("key" to "SECRET"), "desktop_lyrics" to mapOf("lrclib" to true)), "0.4.15", "now")
        listOf("SECRET", "lrclib", "weather", "password", "IMEI").forEach { assertFalse(json.contains(it)) }
        assertFalse(SettingsBackup.decode(json).groups.containsKey("desktop_lyrics"))
    }
    @Test fun malformedAndOutOfRangeRejected() {
        listOf("bad", "{}", "{\"schemaVersion\":99,\"groups\":{}}", "{\"schemaVersion\":1,\"groups\":{\"fit_launcher\":{\"shortcut_pages\":7}}}", "{\"schemaVersion\":1,\"groups\":{\"fit_launcher\":{\"manual_brightness\":1.5}}}").forEach {
            assertTrue(it, runCatching { SettingsBackup.decode(it) }.isFailure)
        }
    }
    @Test fun legacySchemaZeroCompatible() {
        assertEquals(sample, SettingsBackup.decode(SettingsBackup.encode(sample, "old", "now").replace("\"schemaVersion\":1", "\"schemaVersion\":0")).groups)
    }
}
