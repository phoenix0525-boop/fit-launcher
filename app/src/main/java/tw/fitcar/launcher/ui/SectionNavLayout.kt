package tw.fitcar.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 設定與實機檢測共用：寬螢幕左側分類清單；窄螢幕（直向或 < 900 dp）改成上方可左右滑動的分頁列，
 * 內容區保留完整寬度。
 */
@Composable
fun SectionNavLayout(
    items: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = LocalPalette.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 900.dp && maxWidth > maxHeight * 0.95f
        if (wide) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(
                    Modifier.width(if (maxWidth < 1200.dp) 200.dp else 250.dp).fillMaxHeight().clip(RadiusLg)
                        .background(p.surface).border(1.dp, p.line, RadiusLg).verticalScroll(rememberScrollState()).padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items.forEachIndexed { i, name ->
                        val on = i == selected
                        Text(
                            name,
                            Modifier.fillMaxWidth().clip(RadiusMd)
                                .background(if (on) p.accentSoft else Color.Transparent)
                                .clickable { onSelect(i) }
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            color = if (on) p.accentStrong else p.text,
                            fontSize = 17.sp,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content,
                )
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    Modifier.fillMaxWidth().clip(RadiusLg).background(p.surface).border(1.dp, p.line, RadiusLg)
                        .horizontalScroll(rememberScrollState()).padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items.forEachIndexed { i, name ->
                        val on = i == selected
                        Text(
                            name,
                            Modifier.clip(RadiusMd)
                                .background(if (on) p.accentSoft else Color.Transparent)
                                .clickable { onSelect(i) }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            color = if (on) p.accentStrong else p.text,
                            fontSize = 16.sp,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                        )
                    }
                }
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content,
                )
            }
        }
    }
}
