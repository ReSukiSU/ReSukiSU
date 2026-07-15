package com.resukisu.resukisu.ui.component

import androidx.annotation.IntRange
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Composable
fun KeyPointSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    @IntRange(from = 0) steps: Int = 0,
    keyPoints: List<Float>? = null,
    showKeyPoints: Boolean = keyPoints != null || steps > 0,
    magnetThreshold: Float = 0.02f,
    colors: KeyPointSliderColors = keyPointSliderColors(),
    onValueChangeFinished: (() -> Unit)? = null,
) {
    require(steps >= 0) { "steps should be >= 0" }
    require(valueRange.start < valueRange.endInclusive) {
        "valueRange start should be less than endInclusive"
    }
    require(magnetThreshold >= 0f) { "magnetThreshold should be >= 0" }

    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current
    var isDragging by remember { mutableStateOf(false) }

    val tickValues = remember(keyPoints, steps, valueRange) {
        computeTickValues(
            keyPoints = keyPoints,
            steps = steps,
            valueRange = valueRange
        )
    }
    val shouldAlwaysSnapToTick = keyPoints == null && steps > 0

    fun updateValueFromPosition(x: Float, width: Float): Float {
        val rawValue = positionToValue(
            x = x,
            width = width,
            valueRange = valueRange,
            isRtl = layoutDirection == LayoutDirection.Rtl,
            thumbWidthPx = with(density) { 4.dp.toPx() }
        )

        val snappedValue = rawValue.snapToNearestTick(
            valueRange = valueRange,
            ticks = tickValues,
            magnetThreshold = magnetThreshold,
            alwaysSnap = shouldAlwaysSnapToTick
        )
        currentOnValueChange(snappedValue)
        return snappedValue
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .pointerInput(enabled, valueRange, tickValues, magnetThreshold, layoutDirection) {
                if (!enabled) return@pointerInput

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    isDragging = true
                    var latestValue = updateValueFromPosition(down.position.x, size.width.toFloat())

                    horizontalDrag(down.id) { change: PointerInputChange ->
                        latestValue =
                            updateValueFromPosition(change.position.x, size.width.toFloat())
                        if (change.positionChange() != Offset.Zero) {
                            change.consume()
                        }
                    }

                    isDragging = false
                    val snappedValue = latestValue.snapToNearestTick(
                        valueRange = valueRange,
                        ticks = tickValues,
                        magnetThreshold = magnetThreshold,
                        alwaysSnap = shouldAlwaysSnapToTick
                    )
                    if (snappedValue != value) {
                        currentOnValueChange(snappedValue)
                    }
                    currentOnValueChangeFinished?.invoke()
                }
            }
            .semantics {
                if (!enabled) disabled()
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = value.coerceIn(valueRange.start, valueRange.endInclusive),
                    range = valueRange,
                    steps = steps
                )
                setProgress { target ->
                    val snappedValue = target.snapToNearestTick(
                        valueRange = valueRange,
                        ticks = tickValues,
                        magnetThreshold = magnetThreshold,
                        alwaysSnap = shouldAlwaysSnapToTick
                    )
                    if (snappedValue == value) {
                        false
                    } else {
                        currentOnValueChange(snappedValue)
                        currentOnValueChangeFinished?.invoke()
                        true
                    }
                }
            }
    ) {
        drawKeyPointSlider(
            value = value,
            valueRange = valueRange,
            enabled = enabled,
            isRtl = layoutDirection == LayoutDirection.Rtl,
            isDragging = isDragging,
            drawStopIndicator = keyPoints == null,
            tickFractions = if (showKeyPoints) {
                tickValues.map { it.calcFraction(valueRange.start, valueRange.endInclusive) }
            } else {
                emptyList()
            },
            colors = colors
        )
    }
}

@Composable
fun keyPointSliderColors(): KeyPointSliderColors = KeyPointSliderColors(
    thumbColor = MaterialTheme.colorScheme.primary,
    activeTrackColor = MaterialTheme.colorScheme.primary,
    activeTickColor = MaterialTheme.colorScheme.onPrimary,
    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
    inactiveTickColor = MaterialTheme.colorScheme.primary,
    disabledThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
    disabledActiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
    disabledActiveTickColor = MaterialTheme.colorScheme.surface,
    disabledInactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
    disabledInactiveTickColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
)

@Immutable
data class KeyPointSliderColors(
    val thumbColor: Color,
    val activeTrackColor: Color,
    val activeTickColor: Color,
    val inactiveTrackColor: Color,
    val inactiveTickColor: Color,
    val disabledThumbColor: Color,
    val disabledActiveTrackColor: Color,
    val disabledActiveTickColor: Color,
    val disabledInactiveTrackColor: Color,
    val disabledInactiveTickColor: Color,
)

private fun DrawScope.drawKeyPointSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    isRtl: Boolean,
    isDragging: Boolean,
    drawStopIndicator: Boolean,
    tickFractions: List<Float>,
    colors: KeyPointSliderColors,
) {
    val coercedValue = value.coerceIn(valueRange.start, valueRange.endInclusive)
    val fraction = coercedValue.calcFraction(valueRange.start, valueRange.endInclusive)
    val visualFraction = if (isRtl) 1f - fraction else fraction
    val thumbWidth = 4.dp.toPx()
    val thumbHeight = if (isDragging) 48.dp.toPx() else 44.dp.toPx()
    val trackHeight = 16.dp.toPx()
    val tickSize = 4.dp.toPx()
    val stopIndicatorSize = 4.dp.toPx()
    val trackCorner = trackHeight / 2f
    val trackInsideCorner = 2.dp.toPx()
    val thumbTrackGap = 6.dp.toPx()
    val minX = thumbWidth / 2f
    val maxX = (size.width - thumbWidth / 2f).coerceAtLeast(minX)
    val centerY = size.height / 2f
    val trackTop = centerY - trackHeight / 2f
    val thumbX = minX + (maxX - minX) * visualFraction
    val inactiveTrackColor = colors.trackColorCompat(enabled, active = false)
    val activeTrackColor = colors.trackColorCompat(enabled, active = true)
    val gapStart = (thumbX - thumbWidth / 2f - thumbTrackGap).coerceIn(minX, maxX)
    val gapEnd = (thumbX + thumbWidth / 2f + thumbTrackGap).coerceIn(minX, maxX)

    if (isRtl) {
        drawTrackSegment(
            color = activeTrackColor,
            startX = gapEnd,
            endX = maxX,
            top = trackTop,
            height = trackHeight,
            startCorner = trackInsideCorner,
            endCorner = trackCorner
        )
        drawTrackSegment(
            color = inactiveTrackColor,
            startX = minX,
            endX = gapStart,
            top = trackTop,
            height = trackHeight,
            startCorner = trackCorner,
            endCorner = trackInsideCorner
        )
    } else {
        drawTrackSegment(
            color = activeTrackColor,
            startX = minX,
            endX = gapStart,
            top = trackTop,
            height = trackHeight,
            startCorner = trackCorner,
            endCorner = trackInsideCorner
        )
        drawTrackSegment(
            color = inactiveTrackColor,
            startX = gapEnd,
            endX = maxX,
            top = trackTop,
            height = trackHeight,
            startCorner = trackInsideCorner,
            endCorner = trackCorner
        )
    }

    val stopIndicatorX = if (isRtl) minX + trackCorner else maxX - trackCorner
    if (drawStopIndicator) {
        drawCircle(
            color = activeTrackColor,
            radius = stopIndicatorSize / 2f,
            center = Offset(stopIndicatorX, centerY)
        )
    }

    val tickGapStart = min(gapStart, gapEnd) - tickSize / 2f
    val tickGapEnd = max(gapStart, gapEnd) + tickSize / 2f
    tickFractions.forEach { tickFraction ->
        val visualTickFraction = if (isRtl) 1f - tickFraction else tickFraction
        val x = minX + (maxX - minX) * visualTickFraction

        if (x in tickGapStart..tickGapEnd) return@forEach
        if (drawStopIndicator && abs(x - stopIndicatorX) <= stopIndicatorSize) return@forEach

        val active = tickFraction <= fraction
        drawCircle(
            color = colors.tickColorCompat(enabled, active),
            radius = tickSize / 2f,
            center = Offset(x, centerY)
        )
    }

    drawRoundRect(
        color = colors.thumbColorCompat(enabled),
        topLeft = Offset(thumbX - thumbWidth / 2f, centerY - thumbHeight / 2f),
        size = Size(thumbWidth, thumbHeight),
        cornerRadius = CornerRadius(thumbWidth / 2f, thumbWidth / 2f)
    )
}

private val trackPath = Path()

private fun DrawScope.drawTrackSegment(
    color: Color,
    startX: Float,
    endX: Float,
    top: Float,
    height: Float,
    startCorner: Float,
    endCorner: Float,
) {
    if (endX <= startX) return

    val segmentWidth = endX - startX
    val leftCorner = min(startCorner, segmentWidth / 2f)
    val rightCorner = min(endCorner, segmentWidth / 2f)

    trackPath.addRoundRect(
        RoundRect(
            rect = Rect(
                offset = Offset(startX, top),
                size = Size(segmentWidth, height)
            ),
            topLeft = CornerRadius(leftCorner, leftCorner),
            bottomLeft = CornerRadius(leftCorner, leftCorner),
            topRight = CornerRadius(rightCorner, rightCorner),
            bottomRight = CornerRadius(rightCorner, rightCorner)
        )
    )
    drawPath(trackPath, color)
    trackPath.rewind()
}

private fun computeTickValues(
    keyPoints: List<Float>?,
    steps: Int,
    valueRange: ClosedFloatingPointRange<Float>,
): List<Float> {
    val rawTicks = keyPoints ?: if (steps > 0) {
        List(steps + 2) { index ->
            lerp(valueRange.start, valueRange.endInclusive, index.toFloat() / (steps + 1))
        }
    } else {
        emptyList()
    }

    return rawTicks
        .map { it.coerceIn(valueRange.start, valueRange.endInclusive) }
        .distinct()
        .sorted()
}

private fun positionToValue(
    x: Float,
    width: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    isRtl: Boolean,
    thumbWidthPx: Float,
): Float {
    val minX = thumbWidthPx / 2f
    val maxX = max(width - thumbWidthPx / 2f, minX)
    val visualFraction = ((x - minX) / (maxX - minX).coerceAtLeast(1f)).coerceIn(0f, 1f)
    val fraction = if (isRtl) 1f - visualFraction else visualFraction

    return lerp(valueRange.start, valueRange.endInclusive, fraction)
}

private fun Float.snapToNearestTick(
    valueRange: ClosedFloatingPointRange<Float>,
    ticks: List<Float>,
    magnetThreshold: Float,
    alwaysSnap: Boolean,
): Float {
    val coerced = coerceIn(valueRange.start, valueRange.endInclusive)
    if (ticks.isEmpty()) return coerced

    val rangeSize = valueRange.endInclusive - valueRange.start
    val thresholdValue = rangeSize * magnetThreshold
    val nearest = ticks.minByOrNull { abs(it - coerced) } ?: return coerced

    return if (alwaysSnap || abs(nearest - coerced) <= thresholdValue) nearest else coerced
}

private fun Float.calcFraction(start: Float, end: Float): Float {
    val range = end - start
    if (range == 0f) return 0f
    return ((this - start) / range).coerceIn(0f, 1f)
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float {
    return start + (stop - start) * fraction
}

private fun KeyPointSliderColors.thumbColorCompat(enabled: Boolean): Color {
    return if (enabled) thumbColor else disabledThumbColor
}

private fun KeyPointSliderColors.trackColorCompat(enabled: Boolean, active: Boolean): Color {
    return when {
        enabled && active -> activeTrackColor
        enabled -> inactiveTrackColor
        active -> disabledActiveTrackColor
        else -> disabledInactiveTrackColor
    }
}

private fun KeyPointSliderColors.tickColorCompat(enabled: Boolean, active: Boolean): Color {
    return when {
        enabled && active -> activeTickColor
        enabled -> inactiveTickColor
        active -> disabledActiveTickColor
        else -> disabledInactiveTickColor
    }
}
