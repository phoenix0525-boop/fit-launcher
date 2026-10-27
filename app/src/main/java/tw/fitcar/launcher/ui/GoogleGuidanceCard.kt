package tw.fitcar.launcher.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tw.fitcar.launcher.nav.GoogleGuidanceState

/** Replaces the map only for the selected Google Maps navigation provider. No route inference. */
@Composable
internal fun GoogleGuidanceCard(state: GoogleGuidanceState, permission: Boolean, now: Long, grant: () -> Unit,
    modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val driving = LocalDriving.current
    Column(modifier.semantics { contentDescription = "Google 導航通知面板" }
        .verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
        Text("Google 地圖導航通知", color = p.textSoft, fontSize = 20.sp, lineHeight = 26.sp)
        val guidance = state.guidance.takeIf { permission && state.fresh(now) }
        if (guidance != null) {
            Text(listOf(guidance.arrow, guidance.primary).filter { it.isNotBlank() }.joinToString(" "),
                color = p.text, fontSize = if (driving) 30.sp else 28.sp,
                lineHeight = if (driving) 38.sp else 36.sp, fontWeight = FontWeight.SemiBold)
            guidance.secondary?.takeIf { it.isNotBlank() }?.let { Text(it, color = p.text, fontSize = 22.sp, lineHeight = 30.sp) }
            guidance.eta?.let { Text("通知到達時間 $it", color = p.textSoft, fontSize = 20.sp, lineHeight = 26.sp) }
        } else {
            Text(when {
                !permission -> "請開啟通知存取以顯示導航提示"
                !state.connected -> "導航通知服務未連線"
                state.activeNavigation && state.guidance != null -> "導航提示已過期，請開啟 Google 地圖確認"
                state.activeNavigation -> "導航提示暫不可用，請開啟 Google 地圖確認"
                else -> "請在 Google 地圖開始導航"
            }, color = p.text, fontSize = 22.sp, lineHeight = 30.sp)
            if (!permission || !state.connected) SecondaryButton("通知權限", grant)
        }
    }
}
