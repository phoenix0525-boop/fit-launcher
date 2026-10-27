package tw.fitcar.launcher

import org.junit.Assert.*
import org.junit.Test
import tw.fitcar.launcher.lyrics.*

class LyricsCoverPolicyTest {
    @Test fun fallbackRequiresNoLyricsAndCurrentArtwork() {
        val missing = LyricsResult("找不到相符歌詞")
        assertTrue(shouldShowLyricsCover(true, true, true, true, missing))
        assertFalse(shouldShowLyricsCover(false, true, true, true, missing))
        assertFalse(shouldShowLyricsCover(true, false, true, true, missing))
        assertFalse(shouldShowLyricsCover(true, true, false, true, missing))
        assertFalse(shouldShowLyricsCover(true, true, true, false, missing))
        assertFalse(shouldShowLyricsCover(true, true, true, true, null))
    }
    @Test fun loadingAndAnyUsableLyricsHideCover() {
        assertFalse(shouldShowLyricsCover(true, true, true, true, LyricsResult("查詢中", loading = true)))
        assertFalse(shouldShowLyricsCover(true, true, true, true, LyricsResult("文字", plain = "實際歌詞")))
        assertFalse(shouldShowLyricsCover(true, true, true, true, LyricsResult("同步", listOf(LyricLine(0, "實際歌詞")))))
        assertTrue(shouldShowLyricsCover(true, true, true, true, LyricsResult("空白", plain = " \n ")))
        assertTrue(shouldShowLyricsCover(true, true, true, true, LyricsResult("連線失敗")))
    }
}
