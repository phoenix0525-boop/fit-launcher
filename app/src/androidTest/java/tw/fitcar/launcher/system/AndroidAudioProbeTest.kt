package tw.fitcar.launcher.system

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test

class AndroidAudioProbeTest {
    @Test fun publicReadOnlySnapshotHasExpectedStreamsAndRange() {
        val p = AndroidAudioProbe(ApplicationProvider.getApplicationContext<Context>())
        val r = p.read()
        assertEquals(setOf("MUSIC", "SYSTEM", "VOICE_CALL", "RING", "NOTIFICATION", "ALARM"), r.streams.keys)
        assertTrue(r.minimumMusic <= r.music); assertTrue(r.music <= r.maximumMusic)
        assertTrue(r.outputTypes.all { it >= 0 })
        assertFalse(r.text().contains("address="))
    }
}
