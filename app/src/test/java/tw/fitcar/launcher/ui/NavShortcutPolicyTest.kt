package tw.fitcar.launcher.ui

import org.junit.Assert.*
import org.junit.Test
import tw.fitcar.launcher.ui.NavShortcutPolicy.Action
import tw.fitcar.launcher.ui.NavShortcutPolicy.Selected

class NavShortcutPolicyTest {
    private fun labels(s: Selected) = NavShortcutPolicy.buttons(s).map { it.label }
    @Test fun kingAndGoogleAreAlwaysAdjacentInFixedOrder() {
        Selected.entries.forEach { s ->
            val l = labels(s)
            val k = l.indexOf("導航王"); val g = l.indexOf("Google 地圖")
            assertTrue(k >= 0 && g == k + 1)
        }
    }
    @Test fun selectedProviderIsPrimaryAndOpensCurrent() {
        val king = NavShortcutPolicy.buttons(Selected.KING)
        assertEquals(listOf(true, false), king.map { it.primary })
        assertEquals(listOf(Action.OPEN_CURRENT, Action.GOOGLE), king.map { it.action })
        val google = NavShortcutPolicy.buttons(Selected.GOOGLE)
        assertEquals(listOf(false, true), google.map { it.primary })
        assertEquals(listOf(Action.KING, Action.OPEN_CURRENT), google.map { it.action })
        assertEquals(1, NavShortcutPolicy.buttons(Selected.OTHER).count { it.primary })
        assertEquals(Action.SELECT_APP, NavShortcutPolicy.buttons(Selected.NONE).first().action)
    }
    @Test fun detectsProvidersFromVerifiedPackageOrLabel() {
        assertEquals(Selected.GOOGLE, NavShortcutPolicy.selected("Google 地圖", "com.google.android.apps.maps"))
        assertEquals(Selected.KING, NavShortcutPolicy.selected("x", "com.kingwaytek.a5i_3d.plus"))
        assertEquals(Selected.KING, NavShortcutPolicy.selected("樂客導航王A5i 3D⁺", "test.nav"))
        assertEquals(Selected.OTHER, NavShortcutPolicy.selected("其他導航", "other.nav"))
        assertEquals(Selected.NONE, NavShortcutPolicy.selected(null, null))
    }
    @Test fun threeNavButtonsMovePlacesIntoMore() {
        assertTrue(NavShortcutPolicy.placesInline(false, 2))
        assertFalse(NavShortcutPolicy.placesInline(false, 3))
        assertFalse(NavShortcutPolicy.placesInline(true, 2))
    }
}
