package tw.fitcar.launcher.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tw.fitcar.launcher.nav.GoogleGuidanceState

@Composable
internal fun GoogleGuidanceCard(state: GoogleGuidanceState, permission: Boolean, now: Long, grant: () -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        val guidance = state.guidance.takeIf { permission && state.fresh(now) }
        if (guidance != null) {
            Text(listOf(guidance.arrow, guidance.primary).filter { it.isNotBlank() }.joinToString(" "),
                color = p.text, fontSize = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Hint(listOfNotNull(guidance.secondary, guidance.eta?.let { "通知到達時間 $it" }).joinToString(" · "), size = 13)
        } else {
            Hint(when {
                !permission -> "Google 導航提示需要通知存取權限"
                !state.connected -> "導航通知服務未連線，請檢查通知存取設定"
                state.activeNavigation -> "Google 地圖導航提示暫不可用或已過期，轉彎請看 Google 地圖"
                else -> "請在 Google 地圖開始導航；收到導航通知後顯示提示"
            }, size = 13)
            if (!permission || !state.connected) SecondaryButton("通知權限", grant)
        }
    }
}
