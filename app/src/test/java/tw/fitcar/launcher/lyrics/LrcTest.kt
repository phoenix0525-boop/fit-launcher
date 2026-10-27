package tw.fitcar.launcher.lyrics
import org.junit.Assert.*
import org.junit.Test
class LrcTest {
    @Test fun parsesMultiStampFractionsAndOffset() {
        val lines = Lrc.parse("[offset:100]\n[00:01.2][00:03.250]測試甲\n[00:03.250]測試乙\n[ar:測試歌手]\nwrong")
        assertEquals(listOf(1100L, 3150L), lines.map { it.timeMs })
        assertEquals("測試甲\n測試乙", lines.last().text)
    }
    @Test fun pauseSeekAndIntroUsePositionRatherThanFakeProgress() {
        val lines = Lrc.parse("[00:05.00]第一行\n[00:10.00]第二行")
        assertEquals(-1, Lrc.current(lines, 4000))
        assertEquals(0, Lrc.current(lines, 5000))
        assertEquals(1, Lrc.current(lines, 20000))
        assertEquals(0, Lrc.current(lines, 7000))
    }
    @Test fun invalidTimestampsAreIgnoredAndBlankLinesRemainTimed() {
        assertEquals(emptyList<LyricLine>(), Lrc.parse("[99:99]invalid"))
        val lines = Lrc.parse("[00:01.00]\n[00:02.00]測試")
        assertEquals("", lines.first().text)
    }
}
