package com.resukisu.resukisu.ui.wear.component

import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.core.snap
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.LocalContentColor
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.CircularProgressIndicatorDefaults
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalScrollCaptureInProgress
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.LocalReduceMotion
import androidx.wear.compose.foundation.LocalScreenIsActive
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.TransformationVariableSpec
import androidx.wear.compose.material3.lazy.ResponsiveTransformationSpec
import com.resukisu.resukisu.R
import kotlinx.coroutines.launch

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

/**
 * A Wear screen whose crown and vertical touch gestures scroll only its own content.
 *
 * The list uses the [ScreenScaffold] content padding (5.2% side and 10% vertical margins); items
 * raise the top and bottom padding with `minimumVerticalContentPadding` when they sit at an edge.
 * [snap] centers items after a fling or crown rotation, for lists of similarly sized items.
 */
@Composable
fun WearList(
    isLoading: Boolean = false,
    isRefreshing: Boolean = false,
    isBusy: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    onConfirm: (() -> Unit)? = null,
    backToTop: Boolean = false,
    onOpenPanel: (() -> Unit)? = null,
    onClosePanel: (() -> Unit)? = null,
    panelLabel: String? = null,
    snap: Boolean = false,
    listState: TransformingLazyColumnState = rememberTransformingLazyColumnState(),
    content: TransformingLazyColumnScope.(TransformationSpec) -> Unit,
) {
    val scope = rememberCoroutineScope()
    // Round screens scale and fade items at the edges; square screens (including the screen shape
    // setting) keep items at full size, as nothing is cut off there.
    val transformationSpec = if (LocalConfiguration.current.isScreenRound) rememberTransformationSpec()
        else rememberTransformationSpec(SquareSmallScreenSpec, SquareLargeScreenSpec)
    val pullOffset = remember { mutableFloatStateOf(0f) }
    val pullProgress = remember { mutableFloatStateOf(0f) }
    val motion = MaterialTheme.motionScheme
    val reduceMotion = LocalReduceMotion.current
    val screenActive = LocalScreenIsActive.current
    LaunchedEffect(screenActive) {
        if (!screenActive) {
            pullOffset.floatValue = 0f
            pullProgress.floatValue = 0f
        }
    }
    // The list follows a boundary pull closely and springs back with a small bounce when it ends.
    val animatedOffset by animateFloatAsState(pullOffset.floatValue,
        animationSpec = if (reduceMotion) snap()
            else if (pullOffset.floatValue == 0f) motion.defaultSpatialSpec()
            else motion.fastSpatialSpec(), label = "wear-boundary-pull")
    // The gesture covers the whole screen, including the edge button shown at the end of the list.
    val gestureModifier = Modifier.wearRefreshGesture(
        listState,
        pullOffset,
        pullProgress,
        enabled = screenActive && !isLoading && !isRefreshing && !isBusy,
        onRefresh = onRefresh,
        onOpenPanel = onOpenPanel,
        onClosePanel = onClosePanel,
        panelLabel = panelLabel,
    )
    // Loading shows a progress indicator instead of the content, as the phone does; the branded
    // loading screen is only for the manager's first load.
    val listContent: @Composable (PaddingValues) -> Unit = { contentPadding ->
        Box(Modifier.fillMaxSize()) {
            TransformingLazyColumn(
                modifier = Modifier.fillMaxSize().graphicsLayer { translationY = animatedOffset },
                state = listState,
                contentPadding = contentPadding,
                flingBehavior = if (snap) TransformingLazyColumnDefaults.snapFlingBehavior(listState)
                    else ScrollableDefaults.flingBehavior(),
                rotaryScrollableBehavior = if (snap) RotaryScrollableDefaults.snapBehavior(listState)
                    else RotaryScrollableDefaults.behavior(listState),
            ) {
                if (isLoading) wearLoadingItem(transformationSpec)
                else content(transformationSpec)
            }
            // Pulling down at the top shows the panel indicator in the room the list makes for it.
            if (onOpenPanel != null) WearPullIndicator(
                pullProgress.floatValue, closing = onClosePanel != null,
                Modifier.align(Alignment.TopCenter).padding(top = 28.dp),
            )
        }
    }
    if (onBack != null || onConfirm != null || backToTop) {
        ScreenScaffold(scrollState = listState, modifier = gestureModifier,
            scrollIndicator = { if (!LocalScrollCaptureInProgress.current) WearScrollIndicator(listState) }, edgeButton = {
            EdgeButton(
                onClick = {
                    // While refreshing, the edge button only shows the progress of the pull refresh.
                    if (isRefreshing) Unit
                    else if (onClosePanel != null) onClosePanel()
                    else if (onConfirm != null) onConfirm()
                    else if (backToTop) scope.launch { listState.animateScrollToItem(0) }
                    else onBack?.invoke()
                },
                // Dragging on the edge button keeps scrolling the list, as in the M3 list guidance.
                modifier = Modifier.scrollable(
                    listState,
                    orientation = Orientation.Vertical,
                    reverseDirection = true,
                    overscrollEffect = rememberOverscrollEffect(),
                ),
            ) {
                // The pull refresh starts at the end of the list, so its progress shows here, where
                // the finger is: a ring that fills with the pull, then a spinner while refreshing.
                if (onClosePanel != null && pullProgress.floatValue < 0f) WearPullIndicator(
                    -pullProgress.floatValue, closing = true)
                else if (pullProgress.floatValue < 0f) CircularProgressIndicator(
                    progress = { (-pullProgress.floatValue).coerceIn(0f, 1f) }, modifier = Modifier.size(24.dp),
                    colors = ProgressIndicatorDefaults.colors(indicatorColor = LocalContentColor.current),
                    // The pull ring keeps the thin stroke of the spinner that follows it.
                    strokeWidth = CircularProgressIndicatorDefaults.IndeterminateStrokeWidth)
                else if (isRefreshing) CircularProgressIndicator(Modifier.size(24.dp),
                    colors = ProgressIndicatorDefaults.colors(indicatorColor = LocalContentColor.current))
                else Icon(
                    if (onClosePanel != null) Icons.Default.KeyboardArrowDown
                    else if (onConfirm != null) Icons.Default.Check
                    else if (backToTop) Icons.Default.VerticalAlignTop else Icons.AutoMirrored.Default.ArrowBack,
                    contentDescription = stringResource(if (onClosePanel != null) R.string.wear_close_panel
                        else if (onConfirm != null) R.string.confirm else if (backToTop) R.string.scroll_to_top else R.string.back),
                )
            }
        }) { listContent(it) }
    } else ScreenScaffold(scrollState = listState, modifier = gestureModifier,
        scrollIndicator = { if (!LocalScrollCaptureInProgress.current) WearScrollIndicator(listState) }) { listContent(it) }
}

private val SquareItemSpec = TransformationVariableSpec(1f)
private val SquareSmallScreenSpec = ResponsiveTransformationSpec.smallScreen(
    containerAlpha = SquareItemSpec, contentAlpha = SquareItemSpec, scale = SquareItemSpec)
private val SquareLargeScreenSpec = ResponsiveTransformationSpec.largeScreen(
    containerAlpha = SquareItemSpec, contentAlpha = SquareItemSpec, scale = SquareItemSpec)
