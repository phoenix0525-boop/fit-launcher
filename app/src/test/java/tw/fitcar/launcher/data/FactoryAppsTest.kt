package tw.fitcar.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FactoryAppsTest {

    @Test fun addsFactoryEntryWhenPackageHasNoLauncherIcon() {
        val merged = FactoryApps.mergeKeys(
            standard = listOf("com.kkbox/.Main"),
            factory = listOf("com.syu.radio/com.syu.radio.Launch"),
        )
        assertEquals(listOf("com.kkbox/.Main", "com.syu.radio/com.syu.radio.Launch"), merged)
    }

    @Test fun keepsDistinctFactoryActivitiesFromSamePackage() {
        val merged = FactoryApps.mergeKeys(
            standard = listOf("com.syu.bt/com.syu.bt.Main"),
            factory = listOf("com.syu.bt/com.syu.bt.BtAct", "com.syu.bt/com.syu.bt.act.ActBtAvStart"),
        )
        assertEquals(
            listOf(
                "com.syu.bt/com.syu.bt.Main",
                "com.syu.bt/com.syu.bt.BtAct",
                "com.syu.bt/com.syu.bt.act.ActBtAvStart",
            ),
            merged,
        )
    }

    @Test fun skipsOnlyTheExactSameComponent() {
        val merged = FactoryApps.mergeKeys(
            standard = listOf("com.syu.radio/com.syu.radio.Launch"),
            factory = listOf("com.syu.radio/com.syu.radio.Launch"),
        )
        assertEquals(listOf("com.syu.radio/com.syu.radio.Launch"), merged)
    }

    @Test fun removesDuplicatesAndBlankKeys() {
        val merged = FactoryApps.mergeKeys(
            standard = emptyList(),
            factory = listOf("com.syu.eq/.Eq", "com.syu.eq/.Eq", "", "broken"),
        )
        assertEquals(listOf("com.syu.eq/.Eq"), merged)
    }

    @Test fun candidatesAreFactoryPackagesOnly() {
        assertTrue(FactoryApps.ACTIVITY_CANDIDATES.all { it.substringBefore('/').startsWith("com.syu.") })
        assertFalse(FactoryApps.PACKAGE_CANDIDATES.isEmpty())
    }

    @Test fun platformDetection() {
        assertEquals(Platform.FYT_SYU, FactoryApps.detectPlatform(setOf("com.syu.ms", "com.android.launcher30")))
        assertEquals(Platform.FYT_SYU, FactoryApps.detectPlatform(setOf("com.syu.radio")))
        assertEquals(Platform.UNKNOWN, FactoryApps.detectPlatform(setOf("com.android.launcher3")))
    }
}
