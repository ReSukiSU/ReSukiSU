package com.resukisu.resukisu.ui.wear.component.settings

import androidx.compose.runtime.Composable
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope

/**
 * The Wear adapter emits group entries into the existing list, without nesting a second scroller.
 * Entries keep the standard Wear button shape and the list's 4dp in-group spacing instead of the
 * phone's segmented corners.
 */
fun <T> TransformingLazyColumnScope.LazySegmentedColumn(
    entries: List<T>,
    key: (T) -> Any,
    content: @Composable TransformingLazyColumnItemScope.(T) -> Unit,
) {
    items(count = entries.size, key = { key(entries[it]) }) { index -> content(entries[index]) }
}

fun <T> TransformingLazyColumnScope.SegmentedColumn(
    entries: List<T>,
    key: (T) -> Any,
    content: @Composable TransformingLazyColumnItemScope.(T) -> Unit,
) = LazySegmentedColumn(entries, key, content)
