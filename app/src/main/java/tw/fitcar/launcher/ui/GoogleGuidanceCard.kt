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

/** Persistent notification panel above the free position map. No Google route inference or embedding. */
@Composable
internal fun GoogleGuidanceCard(state: GoogleGuidanceState, permission: Boolean, now: Long, grant: () -> Unit,
    modifier: Modifier = Modifier, compact: Boolean = false) {
    val p = LocalPalette.current
    val driving = LocalDriving.current
    Column(modifier.semantics { contentDescription = "Google 導航通知面板" }
        .verticalScroll(rememberScrollState()).padding(if (compact) 8.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 16.dp,
            if (compact) Alignment.Top else Alignment.CenterVertically)) {
        val guidance = state.guidance.takeIf { permission && state.fresh(now) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Google 地圖導航通知", color = p.textSoft, fontSize = 20.sp, lineHeight = 26.sp,
                modifier = Modifier.weight(1f))
            if (compact) guidance?.eta?.let { Text("通知到達時間 $it", color = p.textSoft, fontSize = 20.sp, lineHeight = 26.sp) }
        }
        if (guidance != null) {
            Text(listOf(guidance.arrow, guidance.primary).filter { it.isNotBlank() }.joinToString(" "),
                color = p.text, fontSize = if (compact) (if (driving) 26.sp else 24.sp) else if (driving) 30.sp else 28.sp,
                lineHeight = if (compact) 34.sp else if (driving) 38.sp else 36.sp, fontWeight = FontWeight.SemiBold)
            guidance.secondary?.takeIf { it.isNotBlank() }?.let { Text(it, color = p.text,
                fontSize = if (compact) 20.sp else 22.sp, lineHeight = if (compact) 28.sp else 30.sp) }
            if (!compact) guidance.eta?.let { Text("通知到達時間 $it", color = p.textSoft, fontSize = 20.sp, lineHeight = 26.sp) }
        } else {
            Text(when {
                !permission -> "請開啟通知存取以顯示導航提示"
                !state.connected -> "導航通知服務未連線"
                state.activeNavigation && state.guidance != null -> "導航提示已過期，請開啟 Google 地圖確認"
                state.activeNavigation -> "導航提示暫不可用，請開啟 Google 地圖確認"
                else -> if (compact) "未收到 Google 導航提示，顯示定位地圖" else "請在 Google 地圖開始導航"
            }, color = p.text, fontSize = if (compact) 20.sp else 22.sp, lineHeight = 30.sp)
            if (!permission || !state.connected) SecondaryButton("通知權限", grant)
        }
    }
}
