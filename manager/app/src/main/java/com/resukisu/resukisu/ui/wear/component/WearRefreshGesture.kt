package com.resukisu.resukisu.ui.wear.component

import android.os.SystemClock
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onPreRotaryScrollEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import com.resukisu.resukisu.R
import kotlin.math.abs

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

/**
 * Pulls past the list boundaries. At the top, pulling down (or turning the crown up) opens the page's
 * panel; at the end, pulling up (or, in a panel, turning the crown down) closes the panel, and
 * pulling up refreshes the main list. Releasing past
 * the 48dp threshold commits; ordinary scrolling and shorter pulls do nothing.
 *
 * Apply it to the whole screen (list and edge button), so a pull that starts on the edge button at
 * the end of the list still counts. [displacement] receives the list offset that follows the pull;
 * draw it on the list only, so it does not shift the coordinates this gesture measures.
 * [pullProgress] is the pull relative to the threshold: positive at the top, negative at the end.
 */
@Composable
internal fun Modifier.wearRefreshGesture(
    listState: TransformingLazyColumnState,
    displacement: MutableFloatState,
    pullProgress: MutableFloatState,
    enabled: Boolean,
    onRefresh: (() -> Unit)?,
    onOpenPanel: (() -> Unit)? = null,
    onClosePanel: (() -> Unit)? = null,
    panelLabel: String? = null,
): Modifier {
    // Lists without any boundary action keep their gestures untouched.
    if (onRefresh == null && onOpenPanel == null && onClosePanel == null) return this
    val active by rememberUpdatedState(enabled)
    val refresh by rememberUpdatedState(onRefresh)
    val panel by rememberUpdatedState(onOpenPanel)
    val closePanel by rememberUpdatedState(onClosePanel)
    val haptic = LocalHapticFeedback.current
    val threshold = with(LocalDensity.current) { 48.dp.toPx() }
    val refreshLabel = stringResource(R.string.wear_refresh)
    val backLabel = stringResource(R.string.back)
    // The crown turned past the boundary: three times the touch threshold of extra rotation, with a
    // pause of the crown resetting it.
    val crownTarget = threshold * 3
    val crown = remember { CrownPull() }
    return this.onPreRotaryScrollEvent { event ->
        // A panel closes past its end with the crown turned down; a page opens its panel past the
        // top with the crown turned up, mirroring the touch pulls.
        val closing = closePanel != null
        val action = closePanel ?: panel
        val now = SystemClock.uptimeMillis()
        val atBoundary = if (closing) !listState.canScrollForward else !listState.canScrollBackward
        val delta = if (closing) event.verticalScrollPixels else -event.verticalScrollPixels
        val sign = if (closing) -1f else 1f
        if (action == null || !active || !atBoundary || delta <= 0f || now - crown.lastEvent > 600) crown.accumulated = 0f
        crown.lastEvent = now
        if (action != null && active && atBoundary && delta > 0f) {
            if (crown.accumulated == 0f) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
            crown.accumulated += delta
            if (crown.accumulated >= crownTarget) {
                crown.accumulated = 0f
                displacement.floatValue = 0f
                pullProgress.floatValue = 0f
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                action()
            } else {
                pullProgress.floatValue = sign * crown.accumulated / crownTarget
                displacement.floatValue = pullProgress.floatValue * threshold * 0.75f
            }
        } else {
            displacement.floatValue = 0f
            pullProgress.floatValue = 0f
        }
        // The list still receives the event; past its top it has nothing left to scroll.
        false
    }.pointerInput(listState, threshold) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var pullDown = 0f
            var pullUp = 0f
            var crossed = false
            var canceled = !active
            // Only a gesture that starts at a boundary pulls past it, so a fling that scrolls back
            // to the top does not open the panel with the rest of its travel.
            val startedAtTop = !listState.canScrollBackward
            val startedAtEnd = !listState.canScrollForward
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!pointer.pressed) {
                        if (!canceled && active) {
                            if (pullDown >= threshold) panel?.invoke()
                            else if (pullUp >= threshold) (closePanel ?: refresh)?.invoke()
                        }
                        break
                    }
                    if (event.changes.count { it.pressed } != 1 || !active) canceled = true
                    val dy = pointer.position.y - pointer.previousPosition.y
                    // A pull grows only while its boundary is held, and moving back shrinks it.
                    pullDown = if (panel != null && startedAtTop && !listState.canScrollBackward) (pullDown + dy).coerceAtLeast(0f) else 0f
                    pullUp = if ((closePanel != null || refresh != null) && startedAtEnd && !listState.canScrollForward) (pullUp - dy).coerceAtLeast(0f) else 0f
                    // Fingers drift sideways on a round screen; only a mostly horizontal drag cancels.
                    val dx = abs(pointer.position.x - down.position.x)
                    if (dx > threshold && dx > maxOf(pullDown, pullUp)) canceled = true
                    if (canceled) {
                        displacement.floatValue = 0f
                        pullProgress.floatValue = 0f
                        continue
                    }
                    val pull = maxOf(pullDown, pullUp)
                    if (pull >= threshold && !crossed) haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                    crossed = pull >= threshold
                    val sign = if (pullDown > 0f) 1f else -1f
                    pullProgress.floatValue = sign * pull / threshold
                    // The list follows the pull at reduced speed, making room for the pull indicator.
                    displacement.floatValue = sign * minOf(pull * 0.6f, threshold * 0.75f)
                    if (pull > threshold / 4) pointer.consume()
                }
            } finally {
                displacement.floatValue = 0f
                pullProgress.floatValue = 0f
            }
        }
    }.semantics {
        if (enabled) customActions = buildList {
            if (onRefresh != null) add(CustomAccessibilityAction(refreshLabel) { onRefresh(); true })
            if (onOpenPanel != null && panelLabel != null) add(CustomAccessibilityAction(panelLabel) { onOpenPanel(); true })
            if (onClosePanel != null) add(CustomAccessibilityAction(backLabel) { onClosePanel(); true })
        }
    }
}

/** Crown rotation collected past the list top, and when it last arrived. */
private class CrownPull {
    var accumulated = 0f
    var lastEvent = 0L
}
