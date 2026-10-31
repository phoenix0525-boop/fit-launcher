package tw.fitcar.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
@OptIn(ExperimentalFoundationApi::class)
fun SectionNavLayout(
    items: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    footer: (@Composable () -> Unit)? = null,
    /** 呼叫端保存的內容捲動位置（例如從檢測返回設定時還原）；未提供時切換分類即歸零。 */
    contentScrollState: ScrollState? = null,
    /** 寬螢幕側欄的群組小標：分類索引 → 標題；窄螢幕分頁列以分隔線區隔。 */
    groupLabels: Map<Int, String> = emptyMap(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = LocalPalette.current
    val ownScroll = remember(selected) { ScrollState(0) }
    val contentScroll = contentScrollState ?: ownScroll
    // 分類清單會捲動：還原或切換後，確保選取的分類在可見範圍內
    val selectedRequester = remember(selected) { BringIntoViewRequester() }
    LaunchedEffect(selected) { runCatching { selectedRequester.bringIntoView() } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 900.dp && maxWidth > maxHeight * 0.95f
        val sidebarWidth = if (maxWidth < 1200.dp) 200.dp else 250.dp
        if (wide) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(
                    Modifier.width(sidebarWidth).fillMaxHeight().clip(RadiusLg)
                        .background(p.surface).border(1.dp, p.line, RadiusLg).padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items.forEachIndexed { i, name ->
                        val on = i == selected
                        groupLabels[i]?.let { group ->
                            Text(group, Modifier.fillMaxWidth().padding(start = 16.dp, top = if (i == 0) 4.dp else 14.dp, bottom = 2.dp)
                                .semantics { heading() },
                                color = p.textSoft, fontSize = TypeScale.NOTE.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                        Text(
                            name,
                            Modifier.fillMaxWidth().then(if (on) Modifier.bringIntoViewRequester(selectedRequester) else Modifier).clip(RadiusMd)
                                .background(if (on) p.accentSoft else Color.Transparent)
                                .clickable { onSelect(i) }
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            color = if (on) p.accentStrong else p.text,
                            fontSize = 17.sp,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                    }
                    footer?.invoke()
                }
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(contentScroll),
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
                        if (i > 0 && groupLabels.containsKey(i))
                            Box(Modifier.align(Alignment.CenterVertically).width(1.dp).height(28.dp).background(p.line))
                        Text(
                            name,
                            Modifier.then(if (on) Modifier.bringIntoViewRequester(selectedRequester) else Modifier).clip(RadiusMd)
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
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(contentScroll),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content,
                )
            }
        }
    }
}
