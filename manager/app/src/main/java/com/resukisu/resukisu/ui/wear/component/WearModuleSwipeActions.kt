package com.resukisu.resukisu.ui.wear.component

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.wear.compose.foundation.GestureInclusion
import androidx.wear.compose.foundation.LocalScreenIsActive
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.RevealDirection
import androidx.wear.compose.material3.RevealValue
import androidx.wear.compose.material3.SwipeToReveal
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.material3.rememberRevealState
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val RevealHoldMillis = 400L

/**
 * Module actions open only by holding the row for 0.4 s and then swiping right to left.
 * Any other touch keeps its ordinary meaning (tap, list scroll, pager swipe).
 */
@Composable
fun TransformingLazyColumnItemScope.WearModuleSwipeActions(
    transformationSpec: TransformationSpec,
    scrolling: Boolean,
    primary: WearIconAction,
    secondary: WearIconAction? = null,
    content: @Composable () -> Unit,
) {
    val state = rememberRevealState()
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val active = LocalScreenIsActive.current
    LaunchedEffect(scrolling, active) {
        if (!active) state.snapTo(RevealValue.Covered)
        else if (scrolling) state.animateTo(RevealValue.Covered)
    }
    // The reveal component never drags the row itself; the gesture below opens and closes it.
    val gestureInclusion = remember {
        object : GestureInclusion {
            override fun ignoreGestureStart(offset: Offset, layoutCoordinates: LayoutCoordinates) = true
        }
    }
    fun run(action: WearIconAction) {
        scope.launch {
            // Finish covering the row before navigation can dispose it or retain it offscreen.
            state.animateTo(RevealValue.Covered)
            action.onClick()
        }
    }
    SwipeToReveal(
        revealState = state,
        revealDirection = RevealDirection.RightToLeft,
        gestureInclusion = gestureInclusion,
        primaryAction = {
            PrimaryActionButton(
                onClick = { run(primary) },
                icon = { Icon(primary.icon, contentDescription = primary.label) },
                text = { Text(primary.label) },
            )
        },
        secondaryAction = secondary?.let { action -> {
            SecondaryActionButton(onClick = { run(action) },
                icon = { Icon(action.icon, contentDescription = action.label) })
        } },
        // The row is never dragged by the component, so a full swipe cannot happen.
        onSwipePrimaryAction = {},
        // Match the Wear 1.7 SwipeToReveal list sample: keep the row transformation, but avoid
        // an offscreen buffer that clips translated content to the original row's bounds.
        modifier = Modifier.transformedHeight(this, transformationSpec).graphicsLayer {
            with(transformationSpec) { applyContainerTransformation(scrollProgress) }
            compositingStrategy = CompositingStrategy.ModulateAlpha
            clip = false
        }.minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding).pointerInput(state) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val slop = viewConfiguration.touchSlop
                if (state.currentValue != RevealValue.Covered) {
                    // An open row closes with a left-to-right swipe.
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val delta = (event.changes.firstOrNull { it.id == down.id } ?: break).position - down.position
                        if (abs(delta.x) > slop / 2 || abs(delta.y) > slop / 2) {
                            if (delta.x > 0 && abs(delta.x) > abs(delta.y)) {
                                scope.launch { state.animateTo(RevealValue.Covered) }
                                event.changes.forEach { it.consume() }
                                do {
                                    awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                                } while (currentEvent.changes.any { it.pressed })
                            }
                            break
                        }
                    } while (event.changes.any { it.pressed })
                    return@awaitEachGesture
                }
                // Lifting or moving before the hold completes leaves the touch to the row, list and pager.
                var position = down.position
                val interrupted = withTimeoutOrNull(RevealHoldMillis) {
                    while (true) {
                        val change = awaitPointerEvent(PointerEventPass.Initial).changes
                            .firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed || (change.position - down.position).getDistance() > slop) break
                        position = change.position
                    }
                    true
                }
                if (interrupted != null) return@awaitEachGesture
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                // After the hold, this gesture belongs to the row: it never clicks, scrolls or pages.
                var revealed = false
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change != null && !revealed) {
                        val delta = change.position - position
                        if (delta.x < -slop / 2 && abs(delta.x) > abs(delta.y)) {
                            revealed = true
                            scope.launch { state.animateTo(RevealValue.RightRevealing) }
                        }
                    }
                    event.changes.forEach { it.consume() }
                } while (event.changes.any { it.pressed })
            }
        }.semantics {
            customActions = listOfNotNull(primary, secondary).map { action ->
                CustomAccessibilityAction(action.label) { run(action); true }
            }
        },
        content = content,
    )
}
