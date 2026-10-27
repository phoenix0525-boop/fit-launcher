package tw.fitcar.launcher.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow

enum class ThemeClarity(val id: String, val label: String, val alpha: Float) {
    CLEAR("clear", "清楚", .98f), BALANCED("balanced", "均衡", .94f), AIRY("airy", "通透", .90f);
    companion object { fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: BALANCED }
}
data class ThemeAppearanceOptions(val fullTheme: Boolean = true, val smallArt: Boolean = true, val clarity: ThemeClarity = ThemeClarity.BALANCED, val keepDrivingTransparency: Boolean = false)
object ThemeAppearanceStore {
    val state = MutableStateFlow(ThemeAppearanceOptions())
    fun initialize(context: Context) {
        val sp = context.getSharedPreferences("theme_appearance", Context.MODE_PRIVATE)
        state.value = ThemeAppearanceOptions(sp.getBoolean("full", true), sp.getBoolean("small", true), ThemeClarity.fromId(sp.getString("clarity", null)), sp.getBoolean("keep_driving_transparency", false))
    }
    fun save(context: Context, next: ThemeAppearanceOptions) {
        context.getSharedPreferences("theme_appearance", Context.MODE_PRIVATE).edit()
            .putBoolean("keep_driving_transparency", next.keepDrivingTransparency).putBoolean("full", next.fullTheme).putBoolean("small", next.smallArt).putString("clarity", next.clarity.id).apply()
        state.value = next
    }
}
internal val LocalThemeAppearance = staticCompositionLocalOf { ThemeAppearanceOptions() }
internal val LocalFullThemeActive = staticCompositionLocalOf { false }
internal enum class ThemePage { HOME, APPS, SETTINGS }

/** Decorative only: never receives taps or replaces map / lyrics / actual album art. */
@Composable
internal fun BoxScope.ThemeBackdrop(page: ThemePage, visible: Boolean = true) {
    val p = LocalPalette.current
    if (!visible || !p.characterArt || !LocalThemeAppearance.current.fullTheme) return
    val spider = p.artworkStyle == VisualStyle.SPIDERMAN
    val drawable = if (spider) when (page) {
        ThemePage.HOME -> tw.fitcar.launcher.R.drawable.spiderman_reach
        ThemePage.APPS -> tw.fitcar.launcher.R.drawable.spiderman_perch
        ThemePage.SETTINGS -> tw.fitcar.launcher.R.drawable.spiderman_swing
    } else when (page) {
        ThemePage.HOME -> tw.fitcar.launcher.R.drawable.pompompurin_cheer
        ThemePage.APPS -> tw.fitcar.launcher.R.drawable.pompompurin
        ThemePage.SETTINGS -> tw.fitcar.launcher.R.drawable.pompompurin_sleep
    }
    Canvas(Modifier.matchParentSize()) {
        if (spider) {
            val origin = Offset(size.width * .97f, size.height * .08f)
            for (i in 0..7) drawLine(p.accent.copy(alpha = .07f), origin, Offset(size.width * (.4f + i * .1f), size.height), 1.5.dp.toPx())
            for (i in 1..5) drawArc(p.textSoft.copy(alpha = .05f), 90f, 95f, false,
                Offset(origin.x - size.width * i * .24f, origin.y - size.height * i * .24f),
                androidx.compose.ui.geometry.Size(size.width * i * .48f, size.height * i * .48f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
        }
    }
    if (page == ThemePage.APPS) return // The dedicated corner image keeps the app list free of repeated characters.
    LimitedThemeImage(drawable, (if (spider) "蜘蛛人" else "布丁狗") + "全版背景",
        Modifier.align(Alignment.BottomEnd).fillMaxHeight(if (page == ThemePage.HOME) .88f else .70f).aspectRatio(1.1f), alpha = .85f)
}

@Composable
internal fun ThemePageFrame(page: ThemePage, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    val active = p.characterArt && LocalThemeAppearance.current.fullTheme
    Box(Modifier.fillMaxSize().clipToBounds().background(Brush.linearGradient(listOf(p.bg, p.bgDeep)))) {
        ThemeBackdrop(page)
        CompositionLocalProvider(LocalFullThemeActive provides active) { content() }
    }
}

@Composable
internal fun ThemeAppearanceSettings() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val options by ThemeAppearanceStore.state.collectAsState()
    fun save(next: ThemeAppearanceOptions) = ThemeAppearanceStore.save(context, next)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("全版角色主題", Modifier.weight(1f), color = LocalPalette.current.text, fontSize = 20.sp)
        Switch(options.fullTheme, { save(options.copy(fullTheme = it)) }, Modifier.semantics { contentDescription = "全版角色主題" })
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("顯示小插畫", Modifier.weight(1f), color = LocalPalette.current.text, fontSize = 20.sp)
        Switch(options.smallArt, { save(options.copy(smallArt = it)) }, Modifier.semantics { contentDescription = "顯示小插畫" })
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("行車時保持我的透明度", Modifier.weight(1f), color=LocalPalette.current.text, fontSize=20.sp)
        Switch(options.keepDrivingTransparency, { save(options.copy(keepDrivingTransparency=it)) }, Modifier.semantics { contentDescription="行車時保持我的透明度" })
    }
    Hint("預設行車中卡片自動變清楚（98%不透明），停車後恢復所選透明度，不改寫設定。",size=14)
    Hint("主畫面僅時鐘旁保留小圖；所有 App、設定與關於頁各自配置。自訂桌布取代主畫面角色背景，地圖保持不透明。", size = 14)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ThemeClarity.entries.forEach { choice -> SecondaryButton((if (choice == options.clarity) "✓ " else "") + choice.label,
            { save(options.copy(clarity = choice)) }, Modifier.weight(1f)) }
    }
}

/** Visual-only override: never mutates stored appearance. No size/position animation. */
fun cardOpacity(saved: Float, driving: Boolean, keep: Boolean): Float = if (driving && !keep) maxOf(.98f,saved) else saved
@Composable internal fun effectiveCardOpacity(saved: Float): Float = cardOpacity(saved, LocalDriving.current, LocalThemeAppearance.current.keepDrivingTransparency)
