// From tiann/KernelSU (github.com/tiann/KernelSU) MainActivity — the main pager
// CompositionLocal that the ported Miuix bottom bar / navigation rail read. Provided
// in ReSukiSU's MainActivity over its existing pager. GPL-3.0. See docs/ATTRIBUTION.md.
package com.resukisu.resukisu.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.resukisu.resukisu.ui.component.bottombar.MainPagerState

val LocalMainPagerState = staticCompositionLocalOf<MainPagerState> {
    error("LocalMainPagerState not provided")
}
