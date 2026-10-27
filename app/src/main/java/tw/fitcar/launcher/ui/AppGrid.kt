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
) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxSize().background(p.bg).padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = p.text, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                Hint(subtitle, size = 15)
            }
            if (p.characterArt) {
                PompompurinArtwork(80.dp, PompompurinPose.CHEER)
                Spacer(Modifier.padding(horizontal = 8.dp))
            }
            PrimaryButton(backLabel, onBack, Modifier.heightIn(min = 72.dp), icon = {
                GlyphIcon(Glyph.HOME, size = 26.dp, tint = if (p.isNight) p.bgDeep else androidx.compose.ui.graphics.Color.White)
            })
        }
        Spacer(Modifier.height(18.dp))
        if (apps.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Hint("找不到可啟動的 App。", size = 18)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                modifier = Modifier.fillMaxSize().clip(RadiusLg).background(p.surface).border(1.dp, p.line, RadiusLg),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(apps, key = { it.key }) { app ->
                    val selected = app.key == selectedKey
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
                        Hint(app.component.packageName, size = 11)
                        Text(app.label, color = p.text, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, lineHeight = 20.sp, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
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
