package tw.fitcar.launcher.nav

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import tw.fitcar.launcher.MainActivity

class DesktopReturnTest {
    @Test fun returnTargetsOurActivityEvenWhenAnotherHomeIsDefault() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = DesktopReturn.intent(context)
        assertEquals(context.packageName, intent.component?.packageName)
        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertFalse(intent.categories.orEmpty().contains(Intent.CATEGORY_HOME))
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
        assertEquals(Intent.FLAG_ACTIVITY_CLEAR_TOP, intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
    @Test fun manualEntryCreatesSidebarWithoutAutoLaunchingNavigation() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val intent=CompanionEntry.intent(context)
        assertEquals(tw.fitcar.launcher.CompanionActivity::class.java.name,intent.component?.className)
        assertEquals(context.packageName,intent.component?.packageName)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK,intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
        assertFalse(intent.getBooleanExtra(tw.fitcar.launcher.CompanionActivity.EXTRA_LAUNCH_NAV,false))
    }
}
