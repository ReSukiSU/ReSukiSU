// Compatibility shims for tiann/KernelSU's Miuix UI (originally authored by YuKongA,
// github.com/YuKongA), backing tiann's theme CompositionLocals with ReSukiSU's ThemeConfig.
// GPL-3.0. See docs/ATTRIBUTION.md.
package com.resukisu.resukisu.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * Whether background blur is enabled. tiann's Miuix screens read this to decide between
 * a frosted bar backdrop and a flat surface. Provided in MainActivity from
 * [ThemeConfig.isEnableBlur]; defaults to false so previews / un-provided scopes compile.
 */
val LocalEnableBlur = compositionLocalOf { false }

/** Whether the Miuix floating bottom bar is used (vs the docked nav bar). */
val LocalEnableFloatingBottomBar = compositionLocalOf { true }

/** Whether the floating bottom bar uses the liquid-glass / blur backdrop. */
val LocalEnableFloatingBottomBarBlur = compositionLocalOf { false }

/** Preset key-color swatches for the Miuix color palette picker. */
val keyColorOptions = listOf(
    Color(0xFFF44336).toArgb(),
    Color(0xFFE91E63).toArgb(),
    Color(0xFF9C27B0).toArgb(),
    Color(0xFF673AB7).toArgb(),
    Color(0xFF3F51B5).toArgb(),
    Color(0xFF2196F3).toArgb(),
    Color(0xFF00BCD4).toArgb(),
    Color(0xFF009688).toArgb(),
    Color(0xFF4FAF50).toArgb(),
    Color(0xFFFFEB3B).toArgb(),
    Color(0xFFFFC107).toArgb(),
    Color(0xFFFF9800).toArgb(),
    Color(0xFF795548).toArgb(),
    Color(0xFF607D8F).toArgb(),
    Color(0xFFFF9CA8).toArgb(),
)
