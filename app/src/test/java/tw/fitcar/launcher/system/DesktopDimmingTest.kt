package tw.fitcar.launcher.system
import org.junit.Assert.*
import org.junit.Test
import tw.fitcar.launcher.data.*
class DesktopDimmingTest {
    @Test fun scheduleEdgesAndManualSwitch() {
        val d=DisplaySettings()
        assertFalse(DesktopDimming.night(d,1109))
        assertTrue(DesktopDimming.night(d,1110))
        assertTrue(DesktopDimming.night(d,419))
        assertFalse(DesktopDimming.night(d,420))
        assertEquals(.3f,DesktopDimming.window(true,true,null,true,30),0f)
        assertEquals(-1f,DesktopDimming.window(true,true,false,true,30),0f)
        assertEquals(.3f,DesktopDimming.window(true,true,true,false,30),0f)
    }
    @Test fun leavingReturnsToSystemAndResumeReapplies() {
        assertEquals(-1f,DesktopDimming.window(false,true,true,true,30),0f)
        assertEquals(.3f,DesktopDimming.window(true,true,true,true,30),0f)
        assertEquals(.1f,DesktopDimming.window(true,true,true,true,0),0f)
        assertEquals(1f,DesktopDimming.window(true,true,true,true,300),0f)
    }
    @Test fun windowOverrideHasPriorityRegardlessOfSystemSchedule() {
        listOf(false,true).forEach { schedule ->
            val d=DisplaySettings(autoSchedule=schedule)
            assertEquals(.4f,DesktopDimming.window(true,true,null,DesktopDimming.night(d,1200),40),0f)
            assertEquals(-1f,DesktopDimming.window(false,true,null,true,40),0f)
        }
    }
}
