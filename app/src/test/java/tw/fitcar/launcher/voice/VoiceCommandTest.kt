package tw.fitcar.launcher.voice
import org.junit.Assert.*
import org.junit.Test
class VoiceCommandTest {
    @Test fun explicitPauseAndPlayAreDifferent() {
        assertEquals(VoiceAction.PAUSE, VoiceCommand.parse("暫停音樂")?.action)
        assertEquals(VoiceAction.PLAY, VoiceCommand.parse("播放音樂")?.action)
        assertEquals(VoiceAction.NEXT, VoiceCommand.parse("下一首。")?.action)
    }
    @Test fun parsesDestinationWithoutExecutingArbitraryText() {
        assertEquals("桃園火車站", VoiceCommand.parse("導航到桃園火車站")?.destination)
        assertNull(VoiceCommand.parse("不要暫停音樂"))
        assertNull(VoiceCommand.parse("導航到"))
        assertNull(VoiceCommand.parse("下一首然後刪除檔案"))
    }
    @Test fun supportsBoundAppsAndBrightness() {
        assertEquals(VoiceAction.OPEN_NAV, VoiceCommand.parse("開啟導航王")?.action)
        assertEquals(VoiceAction.OPEN_SHIELD, VoiceCommand.parse("開啟神盾")?.action)
        assertEquals(VoiceAction.BRIGHTER, VoiceCommand.parse("提高亮度")?.action)
    }
}
