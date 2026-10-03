/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.resukisu.resukisu.ui.wear.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.foundation.pager.PagerState
import androidx.wear.compose.material3.HorizontalPageIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.PageIndicatorDefaults
import androidx.wear.compose.material3.ScrollIndicator
import androidx.wear.compose.material3.ScrollIndicatorDefaults
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.material3.TimeTextDefaults

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

/** M3 chrome is curved; rectangular screens use the same scaffold slots and time source. */
@Composable
fun WearTimeText() {
    if (LocalConfiguration.current.isScreenRound) TimeText()
    else {
        val time = TimeTextDefaults.rememberTimeSource(TimeTextDefaults.timeFormat()).currentTime()
        Box(Modifier.fillMaxWidth().padding(TimeTextDefaults.ContentPadding), contentAlignment = Alignment.TopCenter) {
            Text(time, style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.background(TimeTextDefaults.backgroundColor(), RoundedCornerShape(50))
                    .padding(horizontal = 6.dp).clearAndSetSemantics {})
        }
    }
}

@Composable
fun WearHorizontalPageIndicator(state: PagerState) {
    if (LocalConfiguration.current.isScreenRound) HorizontalPageIndicator(state)
    else {
        val selectedColor = PageIndicatorDefaults.selectedColor
        val unselectedColor = PageIndicatorDefaults.unselectedColor
        val backgroundColor = PageIndicatorDefaults.backgroundColor
        val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        Canvas(Modifier.padding(bottom = 4.dp).size(width = (state.pageCount * 10 + 6).dp, height = 12.dp)
            .clearAndSetSemantics {}) {
            drawLine(backgroundColor, Offset(6.dp.toPx(), center.y), Offset(size.width - 6.dp.toPx(), center.y),
                strokeWidth = size.height, cap = StrokeCap.Round)
            val position = state.currentPage + state.currentPageOffsetFraction
            repeat(state.pageCount) { page ->
                val x = 8.dp.toPx() + (if (rtl) state.pageCount - 1 - page else page) * 10.dp.toPx()
                drawCircle(unselectedColor, 2.dp.toPx(), Offset(x, center.y))
            }
            val selectedX = 8.dp.toPx() + (if (rtl) state.pageCount - 1 - position else position) * 10.dp.toPx()
            drawCircle(selectedColor, 2.dp.toPx(), Offset(selectedX, center.y))
        }
    }
}

/** Visible item fractions follow the AndroidX TransformingLazyColumn indicator adapter. */
@Composable
fun WearScrollIndicator(state: TransformingLazyColumnState) {
    if (LocalConfiguration.current.isScreenRound) ScrollIndicator(state)
    else {
        val colors = ScrollIndicatorDefaults.colors()
        Canvas(Modifier.padding(horizontal = 2.dp).size(5.dp, 50.dp).clearAndSetSemantics {}) {
            val info = state.layoutInfo
            val first = info.visibleItems.firstOrNull() ?: return@Canvas
            val last = info.visibleItems.lastOrNull() ?: return@Canvas
            val startPadding = if (first.index == 0) info.beforeContentPadding else 0
            val endPadding = if (last.index == info.totalItemsCount - 1) info.afterContentPadding else 0
            val start = first.index - (first.offset - startPadding).coerceAtMost(0).toFloat() /
                (first.transformedHeight + startPadding).coerceAtLeast(1)
            val end = last.index + (info.viewportSize.height - last.offset)
                .coerceIn(0, last.transformedHeight + endPadding).toFloat() /
                (last.transformedHeight + endPadding).coerceAtLeast(1)
            val remaining = start + info.totalItemsCount - end
            val position = when {
                !state.canScrollBackward -> 0f
                !state.canScrollForward -> 1f
                remaining <= 0f -> 0f
                else -> (start / remaining).coerceIn(0f, 1f)
            }
            val fraction = ((end - start) / info.totalItemsCount.coerceAtLeast(1)).coerceIn(0.3f, 0.7f)
            val trackHeight = size.height - size.width
            val top = size.width / 2 + position * trackHeight * (1 - fraction)
            drawLine(colors.trackColor, Offset(center.x, size.width / 2),
                Offset(center.x, size.height - size.width / 2), size.width, StrokeCap.Round)
            drawLine(colors.indicatorColor, Offset(center.x, top),
                Offset(center.x, top + trackHeight * fraction), size.width, StrokeCap.Round)
        }
    }
}
