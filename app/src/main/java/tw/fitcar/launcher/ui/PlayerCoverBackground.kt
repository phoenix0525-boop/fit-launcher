package tw.fitcar.launcher.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/** Draw only the selected session's current bitmap; no blur allocation, download, or old-track cache. */
internal const val PLAYER_COVER_ALPHA = .10f

@Composable
internal fun BoxScope.PlayerCoverBackground(art: Bitmap) {
    val bitmap = remember(art) { art.asImageBitmap() }
    Image(bitmap, contentDescription = null, modifier = Modifier.matchParentSize()
        .semantics { contentDescription = "無歌詞專輯封面底圖" },
        contentScale = ContentScale.Crop, alpha = PLAYER_COVER_ALPHA)
}
