package tw.fitcar.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tw.fitcar.launcher.data.AppEntry

/**
 * 全螢幕 App 清單（「所有 App」與「選擇 App」共用）。
 * 不透明背景、右上固定大尺寸返回按鈕。
 */
@Composable
fun FullScreenAppList(
    title: String,
    subtitle: String,
    apps: List<AppEntry>,
    backLabel: String,
    onBack: () -> Unit,
    onSelect: (AppEntry) -> Unit,
    selectedKey: String? = null,
    /** 最近使用的 App key（只在「所有 App」提供）。 */
    recentKeys: List<String> = emptyList(),
    /** 是否提供搜尋框；行車中自動隱藏。 */
    searchable: Boolean = true,
) {
    val p = LocalPalette.current
    var query by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf("") }
    val showSearch = searchable && !LocalDriving.current && apps.size > 8
    val filtered = if (showSearch) apps.filter { tw.fitcar.launcher.data.AppUsage.matches(it.label, it.component.packageName, query) } else apps
    val recent = if (query.isBlank()) recentKeys.mapNotNull { k -> apps.firstOrNull { it.key == k } } else emptyList()
    ThemePageFrame(ThemePage.APPS) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = p.text, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                Hint(subtitle, size = 15)
            }
            PrimaryButton(backLabel, onBack, Modifier.heightIn(min = 72.dp), icon = {
                GlyphIcon(Glyph.HOME, size = 26.dp, tint = p.onAccent)
            })
        }
        if (showSearch) {
            Spacer(Modifier.height(12.dp))
            androidx.compose.material3.OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(),
                singleLine = true, label = { Text("搜尋 App 名稱", fontSize = TypeScale.SECONDARY.sp) },
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = TypeScale.BODY.sp))
        }
        Spacer(Modifier.height(18.dp))
        if (apps.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Hint("找不到可啟動的 App。", size = 18)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                modifier = Modifier.weight(1f).fillMaxWidth().clip(RadiusLg).background(p.surface.copy(alpha = if (LocalFullThemeActive.current) effectiveCardOpacity(LocalThemeAppearance.current.clarity.alpha) else 1f)).border(1.dp, p.line, RadiusLg),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (recent.isNotEmpty()) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }, key = "recent-title") {
                        Text("最近使用", color = p.text, fontSize = TypeScale.MAIN.sp, fontWeight = FontWeight.SemiBold)
                    }
                    items(recent, key = { "recent-" + it.key }) { app -> AppTile(app, app.key == selectedKey, onSelect) }
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }, key = "all-title") {
                        Text("全部", color = p.text, fontSize = TypeScale.MAIN.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (filtered.isEmpty()) item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }, key = "empty") {
                    Hint("找不到符合「$query」的 App", size = 18, essential = true)
                }
                items(filtered, key = { it.key }) { app -> AppTile(app, app.key == selectedKey, onSelect) }
            }
        }
        if (p.characterArt && LocalThemeAppearance.current.smallArt) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { ThemeArtwork(80.dp, ThemeArtPose.APPS) }
        }
    }
    }
}

@Composable
private fun AppTile(app: AppEntry, selected: Boolean, onSelect: (AppEntry) -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier
            .clip(RadiusMd)
            .background(if (selected) p.accentSoft else p.surfaceStrong)
            .border(if (selected) 2.dp else 1.dp, if (selected) p.accent else p.line, RadiusMd)
            .clickable { onSelect(app) }
            .padding(vertical = 18.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIcon(app.icon, 72.dp)
        Spacer(Modifier.height(10.dp))
        // 不顯示套件名稱（開發用資訊）；名稱加大
        Text(app.label, color = p.text, fontSize = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, lineHeight = 23.sp, modifier = Modifier.fillMaxWidth())
    }
}

/** 長按捷徑後的操作。移除捷徑不會解除安裝 App。 */
@Composable
fun ShortcutEditDialog(
    label: String,
    canMoveBack: Boolean,
    canMoveForward: Boolean,
    onReplace: () -> Unit,
    onRemove: () -> Unit,
    onMoveBack: () -> Unit,
    onMoveForward: () -> Unit,
    onDismiss: () -> Unit,
) {
    val p = LocalPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.surface,
        title = { Text("捷徑：$label", color = p.text, fontSize = 22.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryButton("更換 App", onReplace, Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SecondaryButton("往前移", onMoveBack, Modifier.weight(1f), enabled = canMoveBack, icon = { GlyphIcon(Glyph.PREV, size = 20.dp) })
                    SecondaryButton("往後移", onMoveForward, Modifier.weight(1f), enabled = canMoveForward, icon = { GlyphIcon(Glyph.NEXT, size = 20.dp) })
                }
                SecondaryButton("移除捷徑（不會解除安裝）", onRemove, Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成", fontSize = 18.sp, color = p.accent) } },
    )
}
