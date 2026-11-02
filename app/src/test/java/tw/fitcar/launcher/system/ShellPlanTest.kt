package tw.fitcar.launcher.system

import org.junit.Assert.*
import org.junit.Test

class ShellPlanTest {
    private val pkg = "tw.fitcar.launcher"

    @Test fun setupOnlyTouchesOwnPackageAndUsesAllowedTools() {
        val steps = ShellPlan.setup(pkg, 33)
        assertTrue(steps.all { it.argv.first() in ShellUserService.ALLOWED })
        assertTrue(steps.all { s -> s.argv.any { it.contains(pkg) } })
        assertTrue(steps.any { it.argv == listOf("cmd", "role", "add-role-holder", "android.app.role.HOME", pkg) })
        assertTrue(steps.any { it.argv.contains("android.permission.BLUETOOTH_CONNECT") })
        assertFalse(ShellPlan.setup(pkg, 28).any { it.argv.contains("role") })
        assertFalse(ShellPlan.setup(pkg, 30).any { it.argv.contains("android.permission.BLUETOOTH_CONNECT") })
        // 不經 sh：沒有任何參數含殼層字元
        assertTrue(steps.flatMap { it.argv }.none { a -> listOf(";", "&", "|", "`", "$(").any { a.contains(it) } })
    }

    @Test fun accessibilityMergeKeepsOthersAndAvoidsDuplicates() {
        val mine = "$pkg/${ShellPlan.ACCESSIBILITY_CLASS}"
        assertEquals(mine, ShellPlan.mergeService(null, pkg, ShellPlan.ACCESSIBILITY_CLASS))
        assertEquals(mine, ShellPlan.mergeService("null", pkg, ShellPlan.ACCESSIBILITY_CLASS))
        assertEquals("a/b.C:$mine", ShellPlan.mergeService("a/b.C", pkg, ShellPlan.ACCESSIBILITY_CLASS))
        assertEquals("a/b.C:$pkg/.system.SystemShortcutService",
            ShellPlan.mergeService("a/b.C:$pkg/.system.SystemShortcutService", pkg, ShellPlan.ACCESSIBILITY_CLASS))
        assertTrue(ShellPlan.containsService("x/y:$pkg/.system.SystemShortcutService", pkg, ShellPlan.ACCESSIBILITY_CLASS))
        assertFalse(ShellPlan.containsService("x/y", pkg, ShellPlan.ACCESSIBILITY_CLASS))
    }

    @Test fun neverOffersCriticalPackagesForDisabling() {
        val homes = listOf("com.android.launcher43", "com.syu.launcher", "com.android.settings", "com.android.launcher3", pkg, "android")
        val c = ShellPlan.disableCandidates(homes, pkg, ShellPlan.recentsPackage("com.android.launcher3/com.android.quickstep.RecentsActivity"))
        assertEquals(listOf("com.android.launcher43", "com.syu.launcher"), c)
        assertFalse(ShellPlan.canDisable(false, "com.android.launcher43", c))
        assertTrue(ShellPlan.canDisable(true, "com.android.launcher43", c))
        assertFalse(ShellPlan.canDisable(true, "com.android.settings", c))
        assertEquals("adb shell pm enable com.syu.launcher", ShellPlan.restoreByAdb("com.syu.launcher"))
        assertEquals(listOf("pm", "disable-user", "--user", "0", "com.syu.launcher"), ShellPlan.disable("com.syu.launcher").argv)
    }

    @Test fun resultFailureDetection() {
        assertTrue(ShellPlan.Result("x", 0, "Package a new state: disabled-user").ok)
        assertFalse(ShellPlan.Result("x", 0, "Exception occurred while executing").ok)
        assertFalse(ShellPlan.Result("x", 255, "").ok)
        assertTrue(ShellPlan.Result("x", 0, "").ok)
    }

    @Test fun unknownRecentsProviderDoesNotOfferDisabling() {
        assertTrue(ShellPlan.disableCandidates(listOf("com.syu.launcher"), pkg, null).isEmpty())
        assertTrue(ShellPlan.disableCandidates(listOf("com.syu.launcher"), pkg, "").isEmpty())
    }
}
