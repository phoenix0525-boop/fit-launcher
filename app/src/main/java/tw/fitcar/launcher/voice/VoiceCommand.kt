package tw.fitcar.launcher.voice

enum class VoiceAction { OPEN_NAV, OPEN_GOOGLE, OPEN_MUSIC, OPEN_SHIELD, PLAY, PAUSE, NEXT, PREVIOUS, BRIGHTER, DIMMER, NAVIGATE_TO }
data class VoiceCommand(val action: VoiceAction, val destination: String? = null) {
    companion object {
        fun parse(raw: String): VoiceCommand? {
            if (raw.length > 180) return null
            val text = raw.trim().trimEnd('。', '！', '!', '？', '?').replace(Regex("\\s+"), "")
            val action = when (text) {
                "開導航", "開啟導航", "開啟導航王" -> VoiceAction.OPEN_NAV
                "開啟Google地圖", "開啟谷歌地圖" -> VoiceAction.OPEN_GOOGLE
                "開音樂", "開啟音樂", "開啟播放器" -> VoiceAction.OPEN_MUSIC
                "開神盾", "開啟神盾" -> VoiceAction.OPEN_SHIELD
                "播放", "播放音樂", "繼續播放", "繼續播放音樂" -> VoiceAction.PLAY
                "暫停", "暫停音樂", "暫停播放" -> VoiceAction.PAUSE
                "下一首", "切下一首" -> VoiceAction.NEXT
                "上一首", "切上一首" -> VoiceAction.PREVIOUS
                "提高亮度", "調亮", "亮一點" -> VoiceAction.BRIGHTER
                "降低亮度", "調暗", "暗一點" -> VoiceAction.DIMMER
                else -> null
            }
            if (action != null) return VoiceCommand(action)
            val destination = Regex("^(?:導航到|帶我去)(.+)$").matchEntire(text)?.groupValues?.get(1)
            return destination?.takeIf { it.isNotBlank() && it.length <= 120 && it.none(Char::isISOControl) }
                ?.let { VoiceCommand(VoiceAction.NAVIGATE_TO, it) }
        }
    }
}
