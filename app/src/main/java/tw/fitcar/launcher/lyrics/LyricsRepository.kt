package tw.fitcar.launcher.lyrics

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext
import kotlin.math.abs

data class LyricsResult(val status: String, val lines: List<LyricLine> = emptyList(), val plain: String? = null, val loading: Boolean = false)
/** Never use album art as a substitute for lyrics that are actually available. */
fun shouldShowLyricsCover(enabled: Boolean, lyricsEnabled: Boolean, usableSession: Boolean, hasArt: Boolean, result: LyricsResult?): Boolean =
    enabled && lyricsEnabled && usableSession && hasArt && result != null && !result.loading &&
        result.lines.none { it.text.isNotBlank() } && result.plain.isNullOrBlank()

object LyricsRepository {
    val enabled = MutableStateFlow(false)
    val coverFallback = MutableStateFlow(true)
    private val mutex = Mutex()
    private val cache = linkedMapOf<String, LyricsResult>()
    private var blockedUntil = 0L
    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences("desktop_lyrics", Context.MODE_PRIVATE)
        enabled.value = prefs.getBoolean("lrclib", false)
        coverFallback.value = prefs.getBoolean("cover_fallback", true)
    }
    fun setCoverFallback(context: Context, value: Boolean) {
        context.getSharedPreferences("desktop_lyrics", Context.MODE_PRIVATE).edit().putBoolean("cover_fallback", value).apply()
        coverFallback.value = value
    }
    fun setEnabled(context: Context, value: Boolean) {
        context.getSharedPreferences("desktop_lyrics", Context.MODE_PRIVATE).edit().putBoolean("lrclib", value).apply()
        enabled.value = value
    }
    private fun normalize(value: String) = value.trim().lowercase().replace(Regex("\\s+"), " ")
    suspend fun lookup(title: String?, artist: String?, album: String?, durationMs: Long): LyricsResult = withContext(Dispatchers.IO) {
        if (!enabled.value) return@withContext LyricsResult("桌面歌詞未啟用")
        if (title.isNullOrBlank() || artist.isNullOrBlank()) return@withContext LyricsResult("缺少歌名或歌手，無法查詢歌詞")
        val key = listOf(title, artist, album.orEmpty(), durationMs.toString()).joinToString("\u0000")
        mutex.withLock {
            cache[key]?.let { return@withLock it }
            if (SystemClock.elapsedRealtime() < blockedUntil) return@withLock LyricsResult("歌詞服務暫時限制查詢，稍後再試")
            coroutineContext.ensureActive()
            val uri = Uri.parse("https://lrclib.net/api/get").buildUpon().appendQueryParameter("track_name", title).appendQueryParameter("artist_name", artist)
            album?.takeIf(String::isNotBlank)?.let { uri.appendQueryParameter("album_name", it) }
            if (durationMs in 1000..3_600_000) uri.appendQueryParameter("duration", (durationMs / 1000.0).toString())
            val c = URL(uri.build().toString()).openConnection() as HttpURLConnection
            c.connectTimeout = 10_000; c.readTimeout = 15_000
            c.setRequestProperty("User-Agent", "VehicleLauncher/0.4.6 (https://github.com/phoenix0525-boop/fit-launcher)")
            try {
                val result = when (c.responseCode) {
                    404 -> LyricsResult("找不到相符歌詞")
                    429 -> {
                        blockedUntil = SystemClock.elapsedRealtime() + (c.getHeaderField("Retry-After")?.toLongOrNull() ?: 60).coerceIn(1, 3600) * 1000
                        LyricsResult("歌詞服務暫時限制查詢，稍後再試")
                    }
                    200 -> {
                        val body = c.inputStream.use { input ->
                            val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(4096)
                            while (true) { val n = input.read(buffer); if (n < 0) break; check(out.size() + n <= 512 * 1024); out.write(buffer, 0, n) }
                            out.toString("UTF-8")
                        }
                        coroutineContext.ensureActive()
                        val data = JSONObject(body)
                        if (normalize(data.optString("trackName")) != normalize(title) || normalize(data.optString("artistName")) != normalize(artist) || durationMs > 0 && abs(data.optDouble("duration", -1.0) * 1000 - durationMs) > 2000)
                            LyricsResult("未找到相符版本的歌詞")
                        else if (data.optBoolean("instrumental")) LyricsResult("純音樂，無歌詞")
                        else {
                            val timed = data.optString("syncedLyrics").takeUnless { it == "null" }.orEmpty()
                            val plain = data.optString("plainLyrics").takeUnless { it == "null" || it.isBlank() }
                            val lines = if (durationMs > 0) Lrc.parse(timed) else emptyList()
                            LyricsResult(if (lines.isNotEmpty()) "同步歌詞 · LRCLIB" else if (plain != null) "文字歌詞（無同步時間）· LRCLIB" else "暫無可顯示歌詞", lines, plain)
                        }
                    }
                    else -> LyricsResult("歌詞服務暫時無法使用")
                }
                coroutineContext.ensureActive()
                if (c.responseCode == 200 || c.responseCode == 404) { cache[key] = result; while (cache.size > 30) cache.remove(cache.keys.first()) }
                result
            } catch (e: java.util.concurrent.CancellationException) { throw e }
            catch (_: Exception) { LyricsResult("歌詞連線失敗，保留歌曲資訊") }
            finally { c.disconnect() }
        }
    }
}
