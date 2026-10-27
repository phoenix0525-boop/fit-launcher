package tw.fitcar.launcher.wallpaper

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.nio.ByteBuffer
import java.util.UUID

data class WallpaperState(val options: WallpaperOptions = WallpaperOptions(), val bitmap: Bitmap? = null, val loaded: Boolean = false) {
    val visible get() = options.enabled && bitmap != null
}

/** Owns a bounded local copy, so moved files and unplugged USB storage cannot break the wallpaper. */
class WallpaperStore(context: Context, namespace: String = "desktop_wallpaper") {
    init { require(namespace.matches(Regex("[a-zA-Z0-9_-]+"))) }
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(namespace, Context.MODE_PRIVATE)
    private val directory = File(app.filesDir, namespace)
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(WallpaperState())
    val state = mutable.asStateFlow()
    private fun ownedFile(name: String?): File? = name?.takeIf { it.matches(Regex("image_[a-f0-9-]+\\.png")) }?.let { File(directory, it) }
    private fun readOptions() = WallpaperOptions(prefs.getBoolean("enabled", false), prefs.getBoolean("crop", true),
        prefs.getFloat("x", 0f), prefs.getFloat("y", 0f), prefs.getFloat("night_dim", .35f), prefs.getBoolean("show_art", true)).sanitized()
    suspend fun load() = withContext(Dispatchers.IO) { mutex.withLock {
        if (!mutable.value.loaded) {
            val bitmap = ownedFile(prefs.getString("image", null))?.takeIf(File::isFile)?.let {
                runCatching { decode(it.readBytes()) }.getOrNull()
            }
            mutable.value = WallpaperState(readOptions().let { if (bitmap == null) it.copy(enabled = false) else it }, bitmap, true)
        }
    } }
    fun update(transform: (WallpaperOptions) -> WallpaperOptions) {
        val next = transform(mutable.value.options).sanitized()
        prefs.edit().putBoolean("enabled", next.enabled).putBoolean("crop", next.crop).putFloat("x", next.x)
            .putFloat("y", next.y).putFloat("night_dim", next.nightDim).putBoolean("show_art", next.showArt).apply()
        mutable.value = mutable.value.copy(options = next)
    }
    suspend fun importImage(uri: Uri): Result<Unit> {
        load()
        return withContext(Dispatchers.IO) { mutex.withLock {
            var pending: File? = null
            try {
                val bytes = app.contentResolver.openInputStream(uri)?.use { input ->
                    val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(65536)
                    while (true) { val n = input.read(buffer); if (n < 0) break
                        require(out.size() + n <= WallpaperPolicy.MAX_BYTES) { "請選擇20MB以內的圖片" }; out.write(buffer, 0, n) }
                    out.toByteArray()
                } ?: error("無法讀取選取的圖片")
                val bitmap = decode(bytes)
                currentCoroutineContext().ensureActive()
                directory.mkdirs()
                val file = File(directory, "image_${UUID.randomUUID()}.png"); pending = file
                file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                val old = ownedFile(prefs.getString("image", null))
                withContext(NonCancellable) {
                    check(prefs.edit().putString("image", file.name).putBoolean("enabled", true).commit()) { "無法儲存桌布" }
                    mutable.value = WallpaperState(readOptions(), bitmap, true)
                    pending = null
                    old?.takeIf { it != file }?.delete()
                }
                Result.success(Unit)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { Result.failure(e) }
            catch (_: OutOfMemoryError) { Result.failure(IllegalArgumentException("圖片記憶體不足，請選擇較小圖片")) }
            finally { pending?.delete() }
        } }
    }
    suspend fun reset() = withContext(Dispatchers.IO) { mutex.withLock {
        ownedFile(prefs.getString("image", null))?.delete()
        prefs.edit().clear().commit()
        mutable.value = WallpaperState(loaded = true)
    } }
    private fun decode(bytes: ByteArray): Bitmap {
        require(bytes.size <= WallpaperPolicy.MAX_BYTES)
        if (Build.VERSION.SDK_INT >= 28) return ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
            val (w, h) = WallpaperPolicy.size(info.size.width, info.size.height)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
            decoder.setTargetSize(w, h)
        }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        val (w, h) = WallpaperPolicy.size(options.outWidth, options.outHeight)
        options.inJustDecodeBounds = false
        options.inSampleSize = 1
        while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > WallpaperPolicy.MAX_EDGE * 2) options.inSampleSize *= 2
        var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: error("圖片格式無法讀取")
        if (bitmap.width != w || bitmap.height != h) bitmap = Bitmap.createScaledBitmap(bitmap, w, h, true)
        val orientation = runCatching { android.media.ExifInterface(bytes.inputStream()).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1)
        val matrix = Matrix().apply { when (orientation) {
            2 -> setScale(-1f, 1f); 3 -> setRotate(180f); 4 -> setScale(1f, -1f)
            5 -> { setRotate(90f); postScale(-1f, 1f) }; 6 -> setRotate(90f)
            7 -> { setRotate(270f); postScale(-1f, 1f) }; 8 -> setRotate(270f)
        } }
        return if (matrix.isIdentity) bitmap else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
object WallpaperRepository {
    @Volatile private var instance: WallpaperStore? = null
    fun get(context: Context): WallpaperStore = instance ?: synchronized(this) { instance ?: WallpaperStore(context).also { instance = it } }
}
