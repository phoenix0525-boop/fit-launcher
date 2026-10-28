package tw.fitcar.launcher.nav

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Test
import tw.fitcar.launcher.CompanionActivity

/** Runs on Android 13 and API 29: opening the sidebar must also work before WindowMetrics. */
class CompanionPlacementLifecycleTest {
    @Test fun openingSidebarRecordsAnObservation() {
        SplitPlacement.begin()
        ActivityScenario.launch<CompanionActivity>(
            CompanionEntry.intent(ApplicationProvider.getApplicationContext()),
        ).use {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertNotNull(SplitPlacement.observation.value)
        }
    }

    @Test fun newAttemptRecordsAgainWithUnchangedWindowBounds() {
        ActivityScenario.launch<CompanionActivity>(
            CompanionEntry.intent(ApplicationProvider.getApplicationContext()),
        ).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.moveToState(Lifecycle.State.CREATED)
            SplitPlacement.begin()
            scenario.moveToState(Lifecycle.State.RESUMED)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertNotNull("A fresh attempt must not be skipped by the last bounds cache", SplitPlacement.observation.value)
        }
    }
}
