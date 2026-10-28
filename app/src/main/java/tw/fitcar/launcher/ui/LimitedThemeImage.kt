package tw.fitcar.launcher.ui
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
internal fun LimitedThemeImage(drawable: Int, description: String, modifier: Modifier, alpha: Float=1f) {
    val name=LocalContext.current.resources.getResourceEntryName(drawable)
    val (w,h)=checkNotNull(ArtworkSize.originals[name]) { "Original image dimensions missing" }
    val density=LocalDensity.current.density
    BoxWithConstraints(modifier,contentAlignment=Alignment.BottomEnd) {
        val (width,height)=ArtworkSize.fit(w,h,maxWidth.value,maxHeight.value,density)
        Image(painterResource(drawable),description,Modifier.size(width.dp,height.dp),alpha=alpha)
    }
}
