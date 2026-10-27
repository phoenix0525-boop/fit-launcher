package tw.fitcar.launcher.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class AppIconTest {
    @get:Rule val compose = createComposeRule()

    @Test fun backgroundIconConversionDisplaysCompletedBitmap() {
        val drawn = AtomicBoolean(false)
        val onMain = AtomicBoolean(false)
        val icon = object : Drawable() {
            override fun draw(canvas: Canvas) {
                onMain.set(Looper.myLooper() == Looper.getMainLooper())
                canvas.drawRect(bounds, Paint().apply { color = Color.GREEN })
                drawn.set(true)
            }
            override fun setAlpha(alpha: Int) = Unit
            override fun setColorFilter(colorFilter: ColorFilter?) = Unit
            @Deprecated("Drawable API") override fun getOpacity() = PixelFormat.OPAQUE
        }
        compose.setContent {
            FitTheme(DayPalette) {
                Box(Modifier.testTag("icon")) { AppIcon(icon, 48.dp) }
            }
        }
        compose.waitUntil(5_000) { drawn.get() }
        // Wait for the asynchronous bitmap to be published into the composition.
        compose.waitUntil(5_000) {
            val pixels = compose.onNodeWithTag("icon").captureToImage().toPixelMap()
            pixels[pixels.width / 2, pixels.height / 2].green > 0.95f &&
                pixels[pixels.width / 2, pixels.height / 2].red < 0.05f
        }
        assertFalse("Drawable conversion must stay off the main thread", onMain.get())
        val pixels = compose.onNodeWithTag("icon").captureToImage().toPixelMap()
        assertEquals(1f, pixels[pixels.width / 2, pixels.height / 2].green, 0.01f)
    }
}
