package tw.fitcar.launcher.system

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SettingsDiffTest {
    @Test fun audioKeysKeepValuesOtherKeysOnlyNames() {
        val before = SettingsSnapshot(mapOf("system:volume_music_speaker" to "36", "secure:bluetooth_name" to "MyCar", "system:same" to "1"))
        val after = SettingsSnapshot(mapOf("system:volume_music_speaker" to "34", "secure:bluetooth_name" to "Other", "system:same" to "1",
            "global:amp_gain" to "5"), unreadable = listOf("global(SecurityException)"))
        val d = SettingsDiff.diff(before, after)
        assertEquals(listOf("global:amp_gain", "system:volume_music_speaker"), d.audioChanges.map { it.key })
        assertEquals(SettingsChange("system:volume_music_speaker", "36", "34"), d.audioChanges[1])
        assertEquals(listOf("secure:bluetooth_name"), d.otherChangedKeys)
        val t = d.text()
        assertFalse("非音訊設定值不可出現在報告", t.contains("MyCar") || t.contains("Other"))
        assertTrue(t.contains("無法讀取"))
    }
    @Test fun noChangeIsReportedPlainly() {
        val s = SettingsSnapshot(mapOf("system:volume_x" to "1"))
        assertTrue(SettingsDiff.diff(s, s).text().contains("未觀察到"))
    }
    @Test fun observationCollectsSettingsAndEventsWithoutWriting() {
        var reads = 0; var writes = 0; var began = 0; var ended = 0
        val port = object : AudioProbePort {
            override fun read() = AudioReading(mapOf("MUSIC" to 36), 0, 40, false, 0, true, listOf(2))
            override fun setMusic(target: Int) { writes++ }
            override fun lowerMusic() { writes++ }
            override fun lowerCurrent() { writes++ }
            override fun settings() = SettingsSnapshot(mapOf("system:volume_music" to (if (reads++ == 0) "36" else "30")))
            override fun beginEvents() { began++ }
            override fun endEvents(): List<String> { ended++; return listOf("+500ms VOLUME_CHANGED 流=3 36→34") }
        }
        val r = runBlocking { AudioProbe.run(AudioProbeMethod.KEYS, port, pause = {}, clock = { 0L }) }
        assertEquals(0, writes); assertEquals(1, began); assertEquals(1, ended)
        assertEquals(1, r.settingsDiff?.audioChanges?.size)
        assertTrue(r.text().contains("系統音量廣播 1 次")); assertTrue(r.summary().contains("音訊相關變化"))
    }
    @Test fun writeMethodsDoNotSnapshotSettings() {
        var snaps = 0
        val port = object : AudioProbePort {
            var v = 36
            override fun read() = AudioReading(mapOf("MUSIC" to v), 0, 40, false, 0, true, listOf(2))
            override fun setMusic(target: Int) { v = target }
            override fun lowerMusic() { v-- }
            override fun lowerCurrent() { v-- }
            override fun settings(): SettingsSnapshot { snaps++; return SettingsSnapshot() }
        }
        runBlocking { AudioProbe.run(AudioProbeMethod.ABSOLUTE, port, pause = {}, clock = { 0L }) }
        assertEquals(0, snaps)
    }
}
