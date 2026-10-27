package tw.fitcar.launcher.update

import java.net.URI

object UpdatePolicy {
    const val PACKAGE = "tw.fitcar.launcher"
    const val CERTIFICATE = "52EE06F203BEA1460317B66170B99D07214B366E9A3A1E6F30D873E42C3835C6"
    const val REPO = "phoenix0525-boop/fit-launcher"
    const val MAX_BYTES = 120L * 1024 * 1024
    fun trustedAsset(url: String): Boolean = runCatching {
        val u = URI(url)
        u.scheme == "https" && u.host == "github.com" && u.userInfo == null && u.port == -1 &&
            u.rawQuery == null && u.fragment == null && u.path.startsWith("/$REPO/releases/download/") &&
            !u.path.contains("..") && !u.rawPath.contains('%')
    }.getOrDefault(false)
    fun valid(code: Long, current: Long, size: Long, hash: String, certificate: String, url: String): Boolean =
        code > current && size in 1..MAX_BYTES && Regex("[0-9a-fA-F]{64}").matches(hash) &&
            certificate.equals(CERTIFICATE, true) && trustedAsset(url)
}
data class UpdateOffer(val code: Long, val version: String, val url: String, val sha256: String, val size: Long, val notes: String)
