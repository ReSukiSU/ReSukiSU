package com.resukisu.resukisu.ui.screen.susfs.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Add
import androidx.compose.material.icons.twotone.Inbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.resukisu.resukisu.ui.component.settings.SettingsJumpPageWidget
import com.resukisu.resukisu.ui.component.settings.lazySegmentColumn

private const val ADD_ENTRY_KEY = "susfs_add_entry"
private const val EMPTY_STATE_KEY = "susfs_empty_state"
private const val ENTRY_KEY_PREFIX = "susfs_entry:"

private sealed interface SuSFSEntryRow<out T> {
    data object Add : SuSFSEntryRow<Nothing>

    data class Entry<T>(val value: T) : SuSFSEntryRow<T>
}

@Composable
internal fun SuSFSDescriptionCard(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Surface(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            content()
        }
    }
}

internal fun <T> LazyListScope.susfsEntryList(
    entries: List<T>,
    addEntryTitle: String,
    emptyTitle: String,
    emptyDescription: String,
    entryKey: (T) -> Any,
    onAddEntry: () -> Unit,
    entryContent: @Composable LazyItemScope.(T) -> Unit,
) {
    val rows: List<SuSFSEntryRow<T>> = buildList {
        add(SuSFSEntryRow.Add)
        entries.forEach { add(SuSFSEntryRow.Entry(it)) }
    }

    lazySegmentColumn(
        items = rows,
        key = { _, row ->
            when (row) {
                SuSFSEntryRow.Add -> ADD_ENTRY_KEY
                is SuSFSEntryRow.Entry -> "$ENTRY_KEY_PREFIX${entryKey(row.value)}"
            }
        },
        contentType = { _, row ->
            when (row) {
                SuSFSEntryRow.Add -> ADD_ENTRY_KEY
                is SuSFSEntryRow.Entry -> "susfs_entry"
            }
        },
    ) { _, row ->
        when (row) {
            SuSFSEntryRow.Add -> SettingsJumpPageWidget(
                iconPlaceholder = false,
                title = addEntryTitle,
                trailingIcon = Icons.TwoTone.Add,
                onClick = { onAddEntry() },
            )

            is SuSFSEntryRow.Entry -> entryContent(row.value)
        }
    }

    if (entries.isEmpty()) {
        item(key = EMPTY_STATE_KEY) {
            Box(
                modifier = Modifier
                    .fillParentMaxWidth()
                    .fillParentMaxHeight(0.5f)
                    .padding(horizontal = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.TwoTone.Inbox,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(bottom = 6.dp)
                            .size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = emptyTitle,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = emptyDescription,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
