package tw.fitcar.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 常見車機螢幕（dp）：版面分類必須穩定，避免不同尺寸擠壓或裁字。 */
class AdaptiveLayoutTest {
    private fun spec(w: Float, h: Float) = AdaptiveLayout.of(w, h)

    @Test fun zx09_2000x1200_at_240dpi_isTwoColumnFullChrome() {
        val s = spec(1333f, 800f)
        assertEquals(HomeArrangement.TWO_COLUMN, s.arrangement)
        assertFalse(s.compactChrome)
        assertTrue(s.showLocationCard)
    }

    @Test fun common1024x600_isTwoColumnCompactChrome() {
        val s = spec(1024f, 600f)
        assertEquals(HomeArrangement.TWO_COLUMN, s.arrangement)
        assertTrue(s.compactChrome)
        assertTrue(s.shortHeight)
    }

    @Test fun hd720_at_240dpi_isCompactWithoutLocationCard() {
        val s = spec(853f, 480f)
        assertEquals(HomeArrangement.TWO_COLUMN, s.arrangement)
        assertTrue(s.compactChrome)
        assertFalse(s.showLocationCard)
    }

    @Test fun ultraWide1920x720_isThreeColumns() {
        assertEquals(HomeArrangement.THREE_COLUMN, spec(1280f, 480f).arrangement)
        assertEquals(HomeArrangement.THREE_COLUMN, spec(1920f, 720f).arrangement)
    }

    @Test fun portraitAndSquareScreensStack() {
        assertEquals(HomeArrangement.STACKED, spec(768f, 1024f).arrangement)
        assertEquals(HomeArrangement.STACKED, spec(720f, 1280f).arrangement)
        assertEquals(HomeArrangement.STACKED, spec(800f, 800f).arrangement)
    }

    @Test fun fullHdLandscapeIsTwoColumnFullChrome() {
        val s = spec(1280f, 720f)
        assertEquals(HomeArrangement.TWO_COLUMN, s.arrangement)
        assertFalse(s.compactChrome)
    }

    @Test fun spacingShrinksOnSmallScreens() {
        assertTrue(spec(853f, 480f).gapDp < spec(1333f, 800f).gapDp)
        assertTrue(spec(1333f, 800f).gapDp <= 14f)
    }

    @Test fun mapGetsLargestShareInEveryArrangement() {
        listOf(spec(1333f, 800f), spec(1280f, 480f), spec(853f, 480f)).forEach {
            assertTrue(it.mapWeight > it.sideWeight)
        }
    }
}
