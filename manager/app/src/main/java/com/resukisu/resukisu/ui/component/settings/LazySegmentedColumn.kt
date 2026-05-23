package com.resukisu.resukisu.ui.component.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
inline fun <T> LazyListScope.lazySegmentColumn(
    items: List<T>,
    noHorizontalPadding: Boolean = false,
    noinline key: ((index: Int, item: T) -> Any)? = null,
    crossinline contentType: (index: Int, item: T) -> Any? = { _, _ -> null },
    crossinline itemContent: @Composable LazyItemScope.(index: Int, item: T) -> Unit,
) {
    items(
        count = items.size,
        key = if (key != null) { index: Int -> key(index, items[index]) } else null,
        contentType = { index: Int -> contentType(index, items[index]) }
    ) { index ->
        val item = items[index]

        val isFirst = index == 0
        val isLast = index == items.lastIndex

        val topRadius = if (isFirst) 16.dp else 5.dp
        val bottomRadius = if (isLast) 16.dp else 5.dp

        val shape = RoundedCornerShape(
            topStart = topRadius,
            topEnd = topRadius,
            bottomStart = bottomRadius,
            bottomEnd = bottomRadius
        )

        val topPadding = if (isFirst) 0.dp else ListItemDefaults.SegmentedGap

        val horizontalPadding = if (noHorizontalPadding) 0.dp else 16.dp

        Box(
            modifier = Modifier
                .padding(top = topPadding)
                .padding(horizontal = horizontalPadding)
        ) {
            CompositionLocalProvider(LocalSegmentedItemShape provides shape) {
                itemContent(index, item)
            }
        }
    }
}