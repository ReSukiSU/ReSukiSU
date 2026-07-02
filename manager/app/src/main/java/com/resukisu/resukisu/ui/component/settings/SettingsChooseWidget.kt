package com.resukisu.resukisu.ui.component.settings

import android.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsChooseWidget(
    icon: ImageVector? = null,
    iconPlaceholder: Boolean = true,
    title: String,
    description: String? = null,
    descriptionColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    enabled: Boolean = true,
    isError: Boolean = false,
    hapticFeedbackType: HapticFeedbackType = HapticFeedbackType.ContextClick,
    leadingContent: (@Composable () -> Unit)? = null,
    foreContent: @Composable BoxScope.() -> Unit = {},
    descriptionColumnContent: (@Composable ColumnScope.() -> Unit)? = null,
    afterContent: @Composable (Int) -> Unit = {},
    items: List<String> = emptyList(),
    itemDescriptions: List<String?> = emptyList(),
    range: IntRange? = null,
    selectedIndex: Int,
    maxHeight: Dp? = 400.dp,
    onSelectedIndexChange: (Int) -> Unit
) {
    val alpha = if (enabled) 1f else 0.38f
    val displayItems = remember(items, range) {
        range?.map { value -> "$value/${range.last}" } ?: items
    }
    var currentIndex by remember { mutableIntStateOf(selectedIndex) }
    var showDialog by remember { mutableStateOf(false) }

    fun dismiss(resetSelection: Boolean = true) {
        if (resetSelection) {
            currentIndex = selectedIndex
        }
        showDialog = false
    }

    val itemsNotEmpty = displayItems.isNotEmpty()

    SettingsBaseWidget(
        icon = icon,
        iconPlaceholder = iconPlaceholder,
        title = title,
        description = description,
        descriptionColor = descriptionColor,
        enabled = enabled,
        isError = isError,
        onClick = {
            currentIndex = selectedIndex
            showDialog = true
        },
        clickHaptic = hapticFeedbackType,
        leadingContent = leadingContent,
        foreContent = foreContent,
        descriptionColumnContent = {
            if (itemsNotEmpty && selectedIndex in displayItems.indices) {
                val color = if (isError) MaterialTheme.colorScheme.error else descriptionColor
                Text(
                    text = displayItems[selectedIndex],
                    color = color.copy(alpha = alpha),
                    style = MaterialTheme.typography.bodyMediumEmphasized,
                    fontSize = MaterialTheme.typography.bodyMediumEmphasized.fontSize,
                    fontFamily = MaterialTheme.typography.bodySmallEmphasized.fontFamily,
                    lineHeight = MaterialTheme.typography.bodyMediumEmphasized.lineHeight,
                    fontWeight = MaterialTheme.typography.bodyMediumEmphasized.fontWeight,
                )
            }
            descriptionColumnContent?.invoke(this)
        }
    ) {}

    if (showDialog && itemsNotEmpty) {
        Dialog(
            onDismissRequest = { dismiss() },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            SettingsChooseDialogFrame(
                title = title,
                maxHeight = maxHeight,
                onDismiss = { dismiss() },
                onConfirm = {
                    onSelectedIndexChange(currentIndex)
                    dismiss(resetSelection = false)
                }
            ) {
                lazySegmentColumn(displayItems, noHorizontalPadding = true) { index, item ->
                    SettingsBaseWidget(
                        title = item,
                        fillMaxWidth = false,
                        renderBackgroundBlur = false,
                        description = itemDescriptions.getOrNull(index),
                        selected = currentIndex == index,
                        onClick = {
                            currentIndex = index
                        },
                        leadingContent = {
                            RadioButton(
                                selected = currentIndex == index,
                                onClick = null,
                            )
                        }
                    ) {
                        afterContent(index)
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsChooseDialog(
    show: Boolean,
    title: String,
    items: List<String>,
    itemDescriptions: List<String?> = emptyList(),
    afterContent: @Composable (Int) -> Unit = {},
    selectedIndex: Int,
    maxHeight: Dp? = 400.dp,
    onDismiss: () -> Unit,
    onSelectedIndexChange: (Int) -> Unit
) {
    if (!show || items.isEmpty()) return

    var currentIndex by remember(show, selectedIndex) { mutableIntStateOf(selectedIndex) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        SettingsChooseDialogFrame(
            title = title,
            maxHeight = maxHeight,
            onDismiss = onDismiss,
            onConfirm = {
                onSelectedIndexChange(currentIndex)
                onDismiss()
            }
        ) {
            lazySegmentColumn(items, noHorizontalPadding = true) { index, item ->
                SettingsBaseWidget(
                    title = item,
                    fillMaxWidth = false,
                    renderBackgroundBlur = false,
                    description = itemDescriptions.getOrNull(index),
                    selected = currentIndex == index,
                    onClick = {
                        currentIndex = index
                    },
                    leadingContent = {
                        RadioButton(
                            selected = currentIndex == index,
                            onClick = null,
                        )
                    }
                ) {
                    afterContent(index)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsChooseWidget(
    icon: ImageVector? = null,
    iconPlaceholder: Boolean = true,
    title: String,
    description: String? = null,
    descriptionColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    enabled: Boolean = true,
    isError: Boolean = false,
    hapticFeedbackType: HapticFeedbackType = HapticFeedbackType.ContextClick,
    leadingContent: (@Composable () -> Unit)? = null,
    foreContent: @Composable BoxScope.() -> Unit = {},
    descriptionColumnContent: (@Composable ColumnScope.() -> Unit)? = null,
    afterContent: @Composable (Int) -> Unit = {},
    items: List<String> = emptyList(),
    itemDescriptions: List<String?> = emptyList(),
    selectedIndices: Set<Int>,
    maxSelected: Int = items.size,
    maxHeight: Dp? = 400.dp,
    onSelectedIndicesChange: (Set<Int>) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    val currentSelection = remember(selectedIndices, showDialog) {
        selectedIndices.sorted().toMutableStateList()
    }
    val itemsNotEmpty = items.isNotEmpty()

    fun dismiss(resetSelection: Boolean = true) {
        if (resetSelection) {
            currentSelection.clear()
            currentSelection.addAll(selectedIndices.sorted())
        }
        showDialog = false
    }

    SettingsBaseWidget(
        icon = icon,
        iconPlaceholder = iconPlaceholder,
        title = title,
        description = description,
        descriptionColor = descriptionColor,
        enabled = enabled,
        isError = isError,
        onClick = {
            currentSelection.clear()
            currentSelection.addAll(selectedIndices.sorted())
            showDialog = true
        },
        clickHaptic = hapticFeedbackType,
        leadingContent = leadingContent,
        foreContent = foreContent,
        descriptionColumnContent = descriptionColumnContent
    ) {}

    if (showDialog && itemsNotEmpty) {
        Dialog(
            onDismissRequest = { dismiss() },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            SettingsChooseDialogFrame(
                title = title,
                maxHeight = maxHeight,
                footerStartContent = {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(
                                MaterialTheme.typography.headlineSmall.copy(
                                    letterSpacing = 0.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                ).toSpanStyle()
                            ) {
                                append(currentSelection.size.toString())
                            }
                            append("/")
                            append(maxSelected.coerceAtLeast(0).toString())
                        },
                        style = MaterialTheme.typography.labelLarge.copy(
                            letterSpacing = 0.5.sp
                        )
                    )
                },
                onDismiss = { dismiss() },
                onConfirm = {
                    onSelectedIndicesChange(currentSelection.toSet())
                    dismiss(resetSelection = false)
                }
            ) {
                lazySegmentColumn(items, noHorizontalPadding = true) { index, item ->
                    val isSelected = index in currentSelection

                    SettingsBaseWidget(
                        title = item,
                        selected = isSelected,
                        fillMaxWidth = false,
                        renderBackgroundBlur = false,
                        description = itemDescriptions.getOrNull(index),
                        onClick = {
                            if (isSelected) {
                                currentSelection.remove(index)
                            } else if (currentSelection.size < maxSelected) {
                                currentSelection.add(index)
                                currentSelection.sort()
                            }
                        },
                        leadingContent = {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = null,
                            )
                        }
                    ) {
                        afterContent(index)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsChooseDialogFrame(
    title: String,
    maxHeight: Dp?,
    footerStartContent: (@Composable () -> Unit)? = null,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    content: LazyListScope.() -> Unit
) {
    Surface(
        modifier = Modifier
            .sizeIn(minWidth = 280.dp, maxWidth = 560.dp)
            .padding(horizontal = 32.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight ?: 400.dp)
                    .padding(top = 16.dp),
                content = content
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (footerStartContent != null) {
                    Spacer(modifier = Modifier.padding(start = 12.dp))
                    Box(modifier = Modifier.padding(bottom = 5.dp)) {
                        footerStartContent()
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(id = R.string.cancel))
                }
                TextButton(onClick = onConfirm) {
                    Text(text = stringResource(id = R.string.ok))
                }
            }
        }
    }
}
