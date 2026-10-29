package tw.fitcar.launcher.system

/** Read-only Android Settings snapshot; tables that the system refuses are listed instead of guessed. */
data class SettingsSnapshot(val values: Map<String, String?> = emptyMap(), val unreadable: List<String> = emptyList())

data class SettingsChange(val key: String, val before: String?, val after: String?)

/** Only audio-looking keys keep their values; other changed keys are reported by name so the report stays private. */
data class SettingsDiffResult(val audioChanges: List<SettingsChange>, val otherChangedKeys: List<String>, val unreadable: List<String>) {
    fun text(): String {
        val head = if (audioChanges.isEmpty()) "系統設定：未觀察到音訊相關設定值變化。"
            else "系統設定：音訊相關變化 ${audioChanges.size} 項：" + audioChanges.joinToString("；") { "${it.key} ${it.before ?: "無"}→${it.after ?: "無"}" }
        val other = if (otherChangedKeys.isEmpty()) "" else "\n其他變化 ${otherChangedKeys.size} 項（只列名稱）：${otherChangedKeys.joinToString()}"
        val blocked = if (unreadable.isEmpty()) "" else "\n無法讀取：${unreadable.joinToString()}（系統限制，不代表沒有變化）"
        return head + other + blocked
    }
}

object SettingsDiff {
    private val AUDIO = Regex("vol|audio|sound|mute|amp|dsp|eq|gain|fader|balance|loud|bass|treble|mcu|syu|fyt", RegexOption.IGNORE_CASE)
    const val MAX_ITEMS = 40
    fun isAudioKey(key: String) = AUDIO.containsMatchIn(key.substringAfter(':'))
    private fun clip(v: String?) = v?.let { if (it.length > 40) it.take(40) + "…" else it }
    fun diff(before: SettingsSnapshot, after: SettingsSnapshot): SettingsDiffResult {
        val changed = (before.values.keys + after.values.keys).sorted()
            .filter { before.values[it] != after.values[it] }
        val (audio, other) = changed.partition(::isAudioKey)
        return SettingsDiffResult(
            audio.take(MAX_ITEMS).map { SettingsChange(it, clip(before.values[it]), clip(after.values[it])) },
            other.take(MAX_ITEMS), (before.unreadable + after.unreadable).distinct().sorted())
    }
}
