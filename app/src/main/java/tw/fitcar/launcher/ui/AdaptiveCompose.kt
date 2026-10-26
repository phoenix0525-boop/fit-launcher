package tw.fitcar.launcher.ui

import androidx.compose.runtime.staticCompositionLocalOf

/** 目前螢幕的版面規格；預設為 ZX09（2000×1200、240 dpi）的完整版面。 */
val LocalLayoutSpec = staticCompositionLocalOf { AdaptiveLayout.of(1333f, 800f) }
