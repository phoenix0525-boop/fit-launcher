package tw.fitcar.launcher.nav

import org.junit.Assert.*
import org.junit.Test

class NavigationKingShortcutTest {
    @Test fun recognisesUserReportedPackageAndInstalledLabel() {
        assertTrue(NavigationKingShortcut.matches("", "com.kingwaytek.a5i_3d.plus"))
        assertTrue(NavigationKingShortcut.matches("樂客導航王", "installed.other.edition"))
        assertFalse(NavigationKingShortcut.matches("Google 地圖", GoogleNavigation.PACKAGE))
    }
}
