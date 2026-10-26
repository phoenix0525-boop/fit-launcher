package tw.fitcar.launcher.nav
import org.junit.Assert.*
import org.junit.Test
class SplitGeometryTest {
    @Test fun acceptsLandscapeSidePaneWithSystemBars() { assertTrue(SplitGeometry.isSideBySide(2000, 1200, 650, 1120)) }
    @Test fun rejectsTopBottomAndFullscreen() {
        assertFalse(SplitGeometry.isSideBySide(2000, 1200, 2000, 560))
        assertFalse(SplitGeometry.isSideBySide(2000, 1200, 2000, 1120))
        assertFalse(SplitGeometry.isSideBySide(1200, 2000, 1200, 980))
    }
}
