package tw.fitcar.launcher.wallpaper

import android.graphics.Bitmap
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class WallpaperStoreTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun image(w: Int = 80, h: Int = 40, jpg: Boolean = false): File {
        val file = File(context.cacheDir, "test_${UUID.randomUUID()}.${if (jpg) "jpg" else "png"}")
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLUE) }
        file.outputStream().use { bitmap.compress(if (jpg) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG, 90, it) }
        bitmap.recycle()
        return file
    }
    @Test fun copiedImageSurvivesSourceRemovalAndStoreRecreation() = runBlocking {
        val namespace = "wallpaper_test_${UUID.randomUUID()}"
        val store = WallpaperStore(context, namespace)
        val source = image(4000, 2000, true)
        try {
            assertTrue(store.importImage(Uri.fromFile(source)).isSuccess)
            assertEquals(2048, store.state.value.bitmap!!.width)
            store.update { it.copy(x = .5f, nightDim = .6f, crop = false, showArt = false) }
            source.delete()
            val reopened = WallpaperStore(context, namespace); reopened.load()
            assertTrue(reopened.state.value.visible)
            assertEquals(.5f, reopened.state.value.options.x, 0f)
            assertFalse(reopened.state.value.options.crop)
            assertFalse(reopened.state.value.options.showArt)
            assertEquals(.6f, reopened.state.value.options.nightDim, 0f)
        } finally { source.delete(); store.reset() }
    }
    @Test fun corruptImportKeepsPreviousImageAndResetLeavesSourceUntouched() = runBlocking {
        val store = WallpaperStore(context, "wallpaper_test_${UUID.randomUUID()}")
        val source = image()
        val bad = File(context.cacheDir, "bad_${UUID.randomUUID()}.png").apply { writeText("invalid image") }
        try {
            assertTrue(store.importImage(Uri.fromFile(source)).isSuccess)
            val previous = store.state.value.bitmap
            assertTrue(store.importImage(Uri.fromFile(bad)).isFailure)
            assertSame(previous, store.state.value.bitmap)
            assertTrue(store.state.value.visible)
            store.update { it.copy(enabled = false) }
            assertFalse(store.state.value.visible)
            store.update { it.copy(enabled = true) }
            assertTrue(store.state.value.visible)
            store.reset()
            assertNull(store.state.value.bitmap)
            assertTrue(source.isFile)
        } finally { source.delete(); bad.delete(); store.reset() }
    }
    @Test fun jpegExifOrientationIsApplied() = runBlocking {
        val store = WallpaperStore(context, "wallpaper_test_${UUID.randomUUID()}")
        val source = image(jpg = true)
        try {
            android.media.ExifInterface(source.path).apply {
                setAttribute(android.media.ExifInterface.TAG_ORIENTATION, "6"); saveAttributes()
            }
            assertTrue(store.importImage(Uri.fromFile(source)).isSuccess)
            assertEquals(40, store.state.value.bitmap!!.width)
            assertEquals(80, store.state.value.bitmap!!.height)
        } finally { source.delete(); store.reset() }
    }
}
