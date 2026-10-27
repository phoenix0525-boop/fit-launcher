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
    @Test fun lookalikeHostsOtherReposAndTraversalAreRejected() {
        listOf(url.replace("https:", "http:"), url.replace("github.com", "github.com.attacker.test"), url.replace("phoenix0525-boop", "another-owner"), url.replace("releases/download/", "releases/download/../"), "$url?token=untrusted", url.replace("github.com", "user@github.com"), url.replace("vehicle-launcher.apk", "%2e%2e/file.apk")).forEach { assertFalse(it, UpdatePolicy.trustedAsset(it)) }
    }
}
