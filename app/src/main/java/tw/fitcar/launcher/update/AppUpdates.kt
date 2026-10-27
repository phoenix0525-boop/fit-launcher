package tw.fitcar.launcher.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

data class UpdateState(val busy: Boolean = false, val message: String = "尚未檢查", val offer: UpdateOffer? = null, val downloaded: File? = null, val progress: Int? = null)
object AppUpdates {
    private val mutable = MutableStateFlow(UpdateState())
    val state = mutable.asStateFlow()
    private val mutex = Mutex()
    fun preferences(context: Context) = context.getSharedPreferences("app_updates", Context.MODE_PRIVATE)
    fun auto(context: Context) = preferences(context).getBoolean("automatic", true)
    @Suppress("DEPRECATION")
    private fun version(info: PackageInfo): Long = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    @Suppress("DEPRECATION")
    private fun flags() = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
    @Suppress("DEPRECATION")
    private fun signatures(info: PackageInfo): Set<String> =
        (if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures).orEmpty()
            .map { hex(MessageDigest.getInstance("SHA-256").digest(it.toByteArray())) }.toSet()
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02X".format(it.toInt() and 255) }
    fun compatible(context: Context) = signatures(context.packageManager.getPackageInfo(context.packageName, flags())) == setOf(UpdatePolicy.CERTIFICATE)
    private fun installedCode(context: Context) = version(context.packageManager.getPackageInfo(context.packageName, 0))
    private fun connection(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000; readTimeout = 20_000
        setRequestProperty("User-Agent", "VehicleLauncher-Updater")
    }
    private fun readJson(url: String): JSONObject? {
        val c = connection(url)
        return try {
            if (c.responseCode == 404) return null
            check(c.responseCode == 200)
            c.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    check(output.size() + n <= 128 * 1024)
                    output.write(buffer, 0, n)
                }
                JSONObject(output.toString("UTF-8"))
            }
        } finally { c.disconnect() }
    }
    suspend fun check(context: Context, force: Boolean = false) = withContext(Dispatchers.IO) {
        val ctx = context.applicationContext
        val pref = preferences(ctx)
        val age = System.currentTimeMillis() - pref.getLong("last_check", 0)
        if (!force && (!auto(ctx) || age in 0 until 24 * 60 * 60_000L)) return@withContext
        if (!mutex.tryLock()) return@withContext
        try {
            mutable.value = mutable.value.copy(busy = true, message = "正在檢查正式更新…")
            val release = readJson("https://api.github.com/repos/${UpdatePolicy.REPO}/releases/latest")
            check(release == null || !release.optBoolean("prerelease") && !release.optBoolean("draft"))
            val assets = release?.optJSONArray("assets")
            val manifestUrl = assets?.let { list -> (0 until list.length()).map { list.getJSONObject(it) }
                .firstOrNull { it.optString("name") == "update.json" }?.optString("browser_download_url") }
            if (manifestUrl.isNullOrBlank()) mutable.value = UpdateState(message = "目前尚無可用的正式更新版本")
            else {
                check(UpdatePolicy.trustedAsset(manifestUrl))
                val manifest = readJson(manifestUrl) ?: error("更新資訊不存在")
                check(manifest.getString("packageName") == ctx.packageName)
                val code = manifest.getLong("versionCode")
                if (code <= installedCode(ctx)) mutable.value = UpdateState(message = "沒有比目前版本更新的正式版本")
                else {
                    val offer = UpdateOffer(code, manifest.getString("versionName").take(40), manifest.getString("apkUrl"), manifest.getString("sha256"), manifest.getLong("size"), manifest.optString("notes").take(4000))
                    check(UpdatePolicy.valid(code, installedCode(ctx), offer.size, offer.sha256, manifest.getString("certificateSha256"), offer.url))
                    mutable.value = UpdateState(message = if (compatible(ctx)) "有新版 ${offer.version}" else "有新版，但目前測試版簽章不同，無法直接覆蓋更新", offer = offer)
                }
            }
            pref.edit().putLong("last_check", System.currentTimeMillis()).apply()
        } catch (e: java.util.concurrent.CancellationException) { throw e }
        catch (_: Exception) { mutable.value = mutable.value.copy(busy = false, message = "無法檢查更新，請確認網路後重試") }
        finally { mutex.unlock(); mutable.value = mutable.value.copy(busy = false) }
    }
    suspend fun download(context: Context) = withContext(Dispatchers.IO) {
        val ctx = context.applicationContext
        val offer = mutable.value.offer ?: return@withContext
        if (!mutex.tryLock()) return@withContext
        val dir = File(ctx.filesDir, "updates").apply { mkdirs() }
        val partial = File(dir, "candidate.part.apk")
        val target = File(dir, "candidate.apk")
        try {
            check(compatible(ctx)) { "目前簽章不同，不能直接更新" }
            check(UpdatePolicy.trustedAsset(offer.url))
            mutable.value = mutable.value.copy(busy = true, downloaded = null, progress = 0, message = "正在下載新版…")
            target.delete()
            val c = connection(offer.url)
            val hash = MessageDigest.getInstance("SHA-256")
            var count = 0L
            try {
                check(c.responseCode == 200) { "下載失敗" }
                c.inputStream.use { input -> partial.outputStream().use { output ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        count += n
                        check(count <= offer.size && count <= UpdatePolicy.MAX_BYTES) { "檔案大小不符" }
                        hash.update(buffer, 0, n); output.write(buffer, 0, n)
                        mutable.value = mutable.value.copy(progress = (count * 100 / offer.size).toInt())
                    }
                } }
            } finally { c.disconnect() }
            check(count == offer.size && hex(hash.digest()).equals(offer.sha256, true)) { "下載檔案驗證失敗" }
            @Suppress("DEPRECATION")
            val apk = ctx.packageManager.getPackageArchiveInfo(partial.absolutePath, flags()) ?: error("APK 無法辨識")
            check(apk.packageName == ctx.packageName && version(apk) == offer.code && version(apk) > installedCode(ctx)) { "APK 版本或套件不符" }
            check(signatures(apk) == setOf(UpdatePolicy.CERTIFICATE)) { "APK 簽章不符" }
            check(partial.renameTo(target)) { "儲存失敗" }
            mutable.value = mutable.value.copy(busy = false, progress = null, downloaded = target, message = "下載及驗證完成，請確認安裝")
        } catch (e: java.util.concurrent.CancellationException) { throw e }
        catch (e: Exception) { mutable.value = mutable.value.copy(message = e.message ?: "下載失敗，請重試", downloaded = null) }
        finally { partial.delete(); mutex.unlock(); mutable.value = mutable.value.copy(busy = false, progress = null) }
    }
    fun install(context: Context) {
        try {
            if (!context.packageManager.canRequestPackageInstalls()) {
                context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
                mutable.value = mutable.value.copy(message = "允許此來源後返回，再按「確認安裝」。")
                return
            }
            val file = mutable.value.downloaded ?: return
            check(file.isFile && compatible(context))
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
            context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } catch (_: Exception) { mutable.value = mutable.value.copy(message = "無法開啟安裝程式，請確認系統允許安裝更新。") }
    }
}
