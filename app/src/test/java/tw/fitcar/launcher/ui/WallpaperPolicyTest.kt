package tw.fitcar.launcher.ui
import org.junit.Assert.*
import org.junit.Test
import tw.fitcar.launcher.wallpaper.*
class WallpaperPolicyTest {
    @Test fun largeImagesAreBoundedWithoutStretchingOrUpscaling() {
        assertEquals(2048 to 1024, WallpaperPolicy.size(8000, 4000))
        assertEquals(100 to 200, WallpaperPolicy.size(100, 200))
        assertEquals(1024 to 2048, WallpaperPolicy.size(4000, 8000))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectPixelBomb() { WallpaperPolicy.size(20000, 20000) }
    @Test fun settingsCannotEscapeSafeRanges() {
        val s = WallpaperOptions(x = Float.NaN, y = 99f, nightDim = 4f).sanitized()
        assertEquals(0f, s.x, 0f); assertEquals(1f, s.y, 0f); assertEquals(.75f, s.nightDim, 0f)
        assertFalse(WallpaperOptions().enabled)
    }
}
