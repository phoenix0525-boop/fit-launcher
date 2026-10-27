package tw.fitcar.launcher.update
import org.junit.Assert.*
import org.junit.Test
class UpdatePolicyTest {
    private val url = "https://github.com/phoenix0525-boop/fit-launcher/releases/download/v0.4.6/vehicle-launcher.apk"
    private val hash = "a".repeat(64)
    @Test fun onlyNewCorrectlyIdentifiedReleaseCanBeOffered() {
        assertTrue(UpdatePolicy.valid(13, 12, 1000, hash, UpdatePolicy.CERTIFICATE, url))
        assertFalse(UpdatePolicy.valid(12, 12, 1000, hash, UpdatePolicy.CERTIFICATE, url))
        assertFalse(UpdatePolicy.valid(11, 12, 1000, hash, UpdatePolicy.CERTIFICATE, url))
        assertFalse(UpdatePolicy.valid(13, 12, 1000, hash, "b".repeat(64), url))
        assertFalse(UpdatePolicy.valid(13, 12, 0, hash, UpdatePolicy.CERTIFICATE, url))
        assertFalse(UpdatePolicy.valid(13, 12, UpdatePolicy.MAX_BYTES + 1, hash, UpdatePolicy.CERTIFICATE, url))
        assertFalse(UpdatePolicy.valid(13, 12, 1000, "wrong", UpdatePolicy.CERTIFICATE, url))
    }
    @Test fun nonProductionCertificatesNeverQualifyForAutomaticUpdates() {
        // Includes the delivered 0.4.14 Debug certificate. No test-certificate allowlist.
        listOf("0C367B3CB11B164EA1CC77C03551D75FC8E8DE817CF801D8C2788F5671191984", "F".repeat(64)).forEach {
            assertFalse(UpdatePolicy.valid(22, 21, 1000, hash, it, url))
        }
    }
    @Test fun lookalikeHostsOtherReposAndTraversalAreRejected() {
        listOf(url.replace("https:", "http:"), url.replace("github.com", "github.com.attacker.test"), url.replace("phoenix0525-boop", "another-owner"), url.replace("releases/download/", "releases/download/../"), "$url?token=untrusted", url.replace("github.com", "user@github.com"), url.replace("vehicle-launcher.apk", "%2e%2e/file.apk")).forEach { assertFalse(it, UpdatePolicy.trustedAsset(it)) }
    }
}
