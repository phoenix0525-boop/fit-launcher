package tw.fitcar.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tw.fitcar.launcher.nav.GoogleGuidance

@Composable
internal fun GoogleGuidanceStrip(guidance: GoogleGuidance, onOpenMaps: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    Row(
        modifier.fillMaxSize()
            .clip(RadiusMd)
            .background(palette.surfaceStrong.copy(alpha = .96f))
            .border(1.dp, palette.line, RadiusMd)
            .combinedClickableCompat(onClick = onOpenMaps)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .semantics { contentDescription = "Google 導航通知：${guidance.primary}" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(guidance.arrow, color = palette.accent, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                guidance.primary,
                color = palette.text,
                fontSize = 22.sp,
                lineHeight = 27.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            guidance.secondary?.takeIf(String::isNotBlank)?.let {
                Text(
                    it,
                    modifier = Modifier.padding(end = 3.dp),
                    color = palette.textSoft,
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        guidance.distance?.takeIf { !guidance.primary.contains(it) }?.let {
            Text(it, color = palette.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
