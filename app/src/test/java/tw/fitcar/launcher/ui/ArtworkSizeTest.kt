package tw.fitcar.launcher.ui
import org.junit.Assert.*
import org.junit.Test
class ArtworkSizeTest {
    @Test fun allEightOriginalsNeverExceed150PercentAtAnyDensity() {
        ArtworkSize.originals.values.forEach { (w,h) ->
            listOf(1f,1.5f,2f,3f).forEach { density ->
                val result=ArtworkSize.fit(w,h,2000f/density,1200f/density,density)
                assertTrue(result.first*density <= w*1.5f+.01f)
                assertTrue(result.second*density <= h*1.5f+.01f)
                assertEquals(w.toFloat()/h, result.first/result.second,.001f)
            }
        }
        assertEquals(8,ArtworkSize.originals.size)
    }
}
