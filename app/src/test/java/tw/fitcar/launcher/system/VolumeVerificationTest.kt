package tw.fitcar.launcher.system

import org.junit.Assert.*
import org.junit.Test

class VolumeVerificationTest {
    @Test fun fixedPolicyIsNotControllable() {
        assertEquals(VolumeReadback.FIXED, VolumeVerification.check(6, 7, 7, 7, true))
    }
    @Test fun unchangedReadbackIsFailure() {
        assertEquals(VolumeReadback.NOT_APPLIED, VolumeVerification.check(6, 7, 6, 6, false))
    }
    @Test fun delayedReversionIsNotSuccess() {
        assertEquals(VolumeReadback.REVERTED, VolumeVerification.check(6, 7, 7, 6, false))
    }
    @Test fun numericChangeNeverVerifiesAudibleVehicleVolume() {
        val result = VolumeVerification.check(6, 7, 7, 7, false)
        assertEquals(VolumeReadback.NUMERIC_ONLY, result)
        assertFalse(result.verifiesVehicleSound)
    }
    @Test fun noOpAtBoundaryDoesNotBecomeASuccess() {
        assertEquals(VolumeReadback.NO_CHANGE_REQUESTED, VolumeVerification.check(0, 0, 0, 0, false))
    }
}
