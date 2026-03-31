package com.resukisu.resukisu.ui.animation.predictiveback

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import androidx.navigationevent.NavigationEventTransitionState
import com.resukisu.resukisu.ui.theme.CardConfig

// TODO Add an config page for user to select predictiveBack implement
class KernelSUClassicPredictiveBackAnimation : PredictiveBackAnimationHandler {
    override suspend fun onBackPressed(
        transitionState: NavigationEventTransitionState?,
        currentPageKey: NavKey?
    ) {
        // ignore
    }

    @Composable
    override fun PredictiveBackAnimationDecorator(
        transitionState: NavigationEventTransitionState?,
        contentPageKey: Any,
        currentPageKey: NavKey?,
        content: @Composable (() -> Unit)
    ) {
        val backgroundColor =
            if (CardConfig.isCustomBackgroundEnabled)
                Color.Transparent
            else
                MaterialTheme.colorScheme.surfaceContainer

        Surface(
            color = backgroundColor
        ) {
            content()
        }
    }

    override fun AnimatedContentTransitionScope<Scene<NavKey>>.onPredictivePopTransitionSpec(
        swipeEdge: Int
    ): ContentTransform =
        ContentTransform(
            targetContentEnter = slideInHorizontally(initialOffsetX = { fullWidth -> -fullWidth }),
            initialContentExit = scaleOut(targetScale = 0.9f) + fadeOut(),
            sizeTransform = null
        )

    override fun AnimatedContentTransitionScope<Scene<NavKey>>
            .onPopTransitionSpec(): ContentTransform =
        ContentTransform(
            targetContentEnter = slideInHorizontally(initialOffsetX = { fullWidth -> -fullWidth }),
            initialContentExit = scaleOut(targetScale = 0.9f) + fadeOut(),
            sizeTransform = null
        )

    override fun AnimatedContentTransitionScope<Scene<NavKey>>
            .onTransitionSpec(): ContentTransform =
        ContentTransform(
            targetContentEnter = slideInHorizontally(initialOffsetX = { fullWidth -> fullWidth }),
            initialContentExit = slideOutHorizontally(targetOffsetX = { fullWidth -> -fullWidth }),
            sizeTransform = null
        )
}