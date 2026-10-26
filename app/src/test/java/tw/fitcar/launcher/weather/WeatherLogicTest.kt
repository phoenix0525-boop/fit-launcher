package tw.fitcar.launcher.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherLogicTest {
    private fun obs(name: String, lat: Double, lon: Double, county: String = "", t: Double? = 25.0, w: String? = "多雲", hi: Double? = 28.0, lo: Double? = 20.0) =
        StationObs(name, county, "", lat, lon, "2026-10-26T14:00:00+08:00", w, t, hi, lo)

    private val taoyuan = obs("新屋", 24.97, 121.07, "桃園市")
    private val taipei = obs("臺北", 25.04, 121.51, "臺北市")
    private val kaohsiung = obs("高雄", 22.57, 120.31, "高雄市")

    @Test fun picksNearestStationToBade() {
        // 八德區附近
        assertEquals("新屋", WeatherLogic.nearest(listOf(taipei, kaohsiung, taoyuan), 24.93, 121.29)?.station?.name)
    }

    @Test fun withoutGpsPrefersTaoyuanThenFirst() {
        assertEquals("新屋", WeatherLogic.nearest(listOf(taipei, taoyuan), null, null)?.station?.name)
        assertEquals("臺北", WeatherLogic.nearest(listOf(taipei, kaohsiung), null, null)?.station?.name)
        assertNull(WeatherLogic.nearest(emptyList(), 25.0, 121.0))
    }

    @Test fun ignoresStationsWithoutTemperature() {
        val broken = obs("壞站", 24.93, 121.29, t = -99.0)
        assertEquals("新屋", WeatherLogic.nearest(listOf(broken, taoyuan), 24.93, 121.29)?.station?.name)
    }

    @Test fun distanceIsReasonable() {
        val km = WeatherLogic.distanceKm(25.04, 121.51, 22.57, 120.31)
        assertTrue(km in 290.0..320.0)
    }

    @Test fun cleansMissingValues() {
        assertNull(WeatherLogic.clean(-99.0))
        assertNull(WeatherLogic.clean(null))
        assertEquals(12.5, WeatherLogic.clean(12.5)!!, 0.0)
        assertNull(WeatherLogic.cleanText("-99"))
        assertNull(WeatherLogic.cleanText(" "))
        assertEquals("晴", WeatherLogic.cleanText("晴"))
    }

    @Test fun historyMergesSameDayAndKeepsFiveDays() {
        var h = emptyList<DayRecord>()
        h = WeatherLogic.addToHistory(h, taoyuan.copy(tempC = 22.0, highC = null, lowC = null, weather = "晴"), "2026-10-20")
        h = WeatherLogic.addToHistory(h, taoyuan.copy(tempC = 27.0, highC = null, lowC = null, weather = "多雲"), "2026-10-20")
        assertEquals(1, h.size)
        assertEquals(27.0, h[0].highC!!, 0.0)
        assertEquals(22.0, h[0].lowC!!, 0.0)
        assertEquals("多雲", h[0].weather)
        for (d in 21..26) h = WeatherLogic.addToHistory(h, taoyuan, "2026-10-$d")
        assertEquals(5, h.size)
        assertEquals("2026-10-26", h.first().date)
        assertEquals("2026-10-22", h.last().date)
    }

    @Test fun historyUsesStationDailyExtremesWhenPresent() {
        val h = WeatherLogic.addToHistory(emptyList(), taoyuan.copy(tempC = 25.0, highC = 29.5, lowC = 19.0), "2026-10-26")
        assertEquals(29.5, h[0].highC!!, 0.0)
        assertEquals(19.0, h[0].lowC!!, 0.0)
    }
}
