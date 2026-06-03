package anhiutangerinee.kittisu.ui.util

import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.compositionLocalOf
import anhiutangerinee.kittisu.ui.activity.PermissionRequestInterface
import top.yukonga.miuix.kmp.blur.LayerBackdrop

val LocalSnackbarHost = compositionLocalOf<SnackbarHostState> {
    error("CompositionLocal LocalSnackbarController not present")
}

val LocalBlurState = compositionLocalOf<LayerBackdrop?> { null }

val LocalPagerState = compositionLocalOf<PagerState> { error("No pager state") }
val LocalHandlePageChange = compositionLocalOf<(Int) -> Unit> { error("No handle page change") }
val LocalSelectedPage = compositionLocalOf<Int> { error("No selected page") }

val LocalPermissionRequestInterface = compositionLocalOf<PermissionRequestInterface> {
    error("CompositionLocal LocalPermissionRequestInterface not present")
}