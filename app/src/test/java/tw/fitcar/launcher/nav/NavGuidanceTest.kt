package tw.fitcar.launcher.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavGuidanceTest {

    @Test fun parsesTurnDistanceAndRoad() {
        val g = NavGuidance.parse(title = "300 公尺後右轉", text = "進入中正路", subText = "預計 18:42 抵達")!!
        assertEquals(Turn.RIGHT, g.turn)
        assertEquals("300 公尺", g.distance)
        assertEquals("300 公尺後右轉", g.primary)
        assertEquals("進入中正路", g.secondary)
        assertEquals("18:42", g.eta)
    }

    @Test fun detectsTurnFromTextWhenTitleIsRoad() {
        val g = NavGuidance.parse(title = "國道1號", text = "1.2 公里後靠左行駛", subText = null)!!
        assertEquals(Turn.KEEP_LEFT, g.turn)
        assertEquals("1.2 公里", g.distance)
    }

    @Test fun englishUnitsAndUturn() {
        val g = NavGuidance.parse(title = "迴轉", text = "500 m", subText = null)!!
        assertEquals(Turn.U_TURN, g.turn)
        assertEquals("500 m", g.distance)
    }

    @Test fun unknownDirectionStillShowsText() {
        val g = NavGuidance.parse(title = "導航中", text = "往台北方向", subText = null)!!
        assertEquals(Turn.UNKNOWN, g.turn)
        assertNull(g.distance)
        assertEquals("導航中", g.primary)
    }

    @Test fun emptyNotificationGivesNull() {
        assertNull(NavGuidance.parse(title = "  ", text = null, subText = null))
    }

    @Test fun primaryFallsBackToTextWhenTitleBlank() {
        val g = NavGuidance.parse(title = null, text = "前方 200 公尺左轉", subText = null)!!
        assertEquals("前方 200 公尺左轉", g.primary)
        assertNull(g.secondary)
        assertEquals(Turn.LEFT, g.turn)
        assertTrue(g.arrow.isNotEmpty())
    }
}
