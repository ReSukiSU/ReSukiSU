package com.resukisu.resukisu.ui.component.settings

import android.R
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Expand
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.resukisu.resukisu.ui.theme.CardConfig

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsBaseWidget(
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconPlaceholder: Boolean = true,
    title: String?,
    description: String? = null,
    descriptionColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    enabled: Boolean = true,
    isError: Boolean = false,
    noVerticalPadding: Boolean = false,
    noHorizontalPadding: Boolean = false,
    onClick: (Offset) -> Unit = {},
    onLongClick: (Offset) -> Unit = {},
    hapticFeedbackType: HapticFeedbackType = HapticFeedbackType.ContextClick,
    rowHeader: @Composable RowScope.() -> Unit = {},
    foreContent: @Composable BoxScope.() -> Unit = {},
    descriptionColumnContent: @Composable ColumnScope.() -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val haptic = LocalHapticFeedback.current

    val interactionSource = remember { MutableInteractionSource() }
    val onClickState = rememberUpdatedState(onClick)
    val onLongClickState = rememberUpdatedState(onLongClick)

    var rowModifier = modifier
        .fillMaxWidth()
        .then(
            if (enabled) {
                Modifier
                    .indication(
                        interactionSource = interactionSource,
                        indication = ripple()
                    )
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                val press = PressInteraction.Press(it)
                                interactionSource.emit(press)

                                try {
                                    tryAwaitRelease()
                                } finally {
                                    interactionSource.emit(
                                        PressInteraction.Release(press)
                                    )
                                }
                            },
                            onLongPress = {
                                haptic.performHapticFeedback(hapticFeedbackType)
                                onLongClickState.value(it)
                            },
                            onTap = {
                                haptic.performHapticFeedback(hapticFeedbackType)
                                onClickState.value(it)
                            }
                        )
                    }
            } else Modifier
        )

    rowModifier = if (!noVerticalPadding) rowModifier.padding(vertical = 16.dp) else rowModifier
    rowModifier = if (!noHorizontalPadding) rowModifier.padding(horizontal = 16.dp) else rowModifier

    Row(
        modifier = rowModifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    )
    {
        rowHeader()
        if (icon != null)
            Icon(
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.CenterVertically),
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        if (icon == null && iconPlaceholder)
            Spacer(modifier = Modifier.size(24.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .align(Alignment.CenterVertically)
        ) {
            Column {
                AnimatedVisibility(
                    visible = title != null,
                    enter = expandVertically(
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        expandFrom = Alignment.Top // Unroll downwards like a blind
                    ),
                    exit = shrinkVertically(
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        shrinkTowards = Alignment.Top // Unroll downwards like a blind
                    )
                ) {
                    title?.let {
                        Text(
                            text = title,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleMediumEmphasized,
                            fontSize = MaterialTheme.typography.titleMediumEmphasized.fontSize,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = MaterialTheme.typography.bodyMediumEmphasized.lineHeight,
                            fontFamily = MaterialTheme.typography.titleMediumEmphasized.fontFamily,
                        )
                    }
                }

                AnimatedVisibility(
                    visible = description != null,
                    enter = expandVertically(
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        expandFrom = Alignment.Top // Unroll downwards like a blind
                    ),
                    exit = shrinkVertically(
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        shrinkTowards = Alignment.Top // Unroll downwards like a blind
                    )
                ) {
                    description?.let {
                        val color = if (isError) MaterialTheme.colorScheme.error
                        else descriptionColor
                        Text(
                            text = it,
                            color = color,
                            style = MaterialTheme.typography.bodyMediumEmphasized,
                            fontSize = MaterialTheme.typography.bodyMediumEmphasized.fontSize,
                            fontFamily = MaterialTheme.typography.bodySmallEmphasized.fontFamily,
                            lineHeight = MaterialTheme.typography.bodyMediumEmphasized.lineHeight,
                            fontWeight = MaterialTheme.typography.bodyMediumEmphasized.fontWeight,
                        )
                    }
                }
                descriptionColumnContent()
            }
            Box {
                foreContent()
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.CenterVertically)
        ) {
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsTextFieldWidget(
    modifier: Modifier = Modifier,
    state: TextFieldState,
    onClick: (() -> Unit)? = null,
    title: String = "",
    error: String = "",
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    useLabelAsPlaceholder: Boolean = false,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    inputTransformation: InputTransformation? = null,
    textStyle: TextStyle = MaterialTheme.typography.bodyMediumEmphasized.copy(
        color = MaterialTheme.colorScheme.onSurface,
        fontFamily = MaterialTheme.typography.bodySmallEmphasized.fontFamily,
    ),
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onKeyboardAction: KeyboardActionHandler? = null,
    lineLimits: TextFieldLineLimits = TextFieldLineLimits.Default,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    onTextLayout: (Density.(getResult: () -> TextLayoutResult?) -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
    outputTransformation: OutputTransformation? = null,
    cursorBrush: Brush = SolidColor(MaterialTheme.colorScheme.primary),
    scrollState: ScrollState = rememberScrollState(),
) {
    @Suppress("NAME_SHADOWING")
    val interactionSource = interactionSource ?: remember { MutableInteractionSource() }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isImeVisible = WindowInsets.isImeVisible

    val isClickableMode = onClick != null

    LaunchedEffect(isImeVisible) {
        if (!isImeVisible && isFocused) {
            focusManager.clearFocus()
        }
    }

    val currentOnTextLayout by rememberUpdatedState(onTextLayout)

    val showTitle = if (useLabelAsPlaceholder) state.text.isNotEmpty() else true
    val showPlaceholder = useLabelAsPlaceholder && state.text.isEmpty()

    fun onClickInternal() {
        if (onClick != null) {
            onClick()
            return
        }

        if (!readOnly && enabled) {
            focusRequester.requestFocus()
        }
    }

    SettingsBaseWidget(
        modifier = modifier,
        title = if (showTitle) title else null,
        icon = null,
        iconPlaceholder = false,
        rowHeader = {
            leadingIcon?.invoke()
        },
        onClick = {
            onClickInternal()
        },
        descriptionColumnContent = {
            BasicTextField(
                state = state,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .focusProperties {
                        canFocus = !isClickableMode
                    }
                    .padding(top = 5.dp),
                enabled = enabled,
                readOnly = readOnly,
                textStyle = textStyle,
                cursorBrush = if (error.isBlank()) cursorBrush else SolidColor(MaterialTheme.colorScheme.error),
                keyboardOptions = keyboardOptions,
                onKeyboardAction = onKeyboardAction,
                lineLimits = lineLimits,
                onTextLayout = currentOnTextLayout,
                interactionSource = interactionSource,
                inputTransformation = inputTransformation,
                outputTransformation = outputTransformation,
                scrollState = scrollState,
                decorator = { innerTextField ->
                    Column {
                        Box(
                            modifier = Modifier.clickable(
                                enabled = onClick != null || !isFocused
                            ) {
                                onClickInternal()
                            }
                        ) {
                            if (showPlaceholder) {
                                Text(
                                    text = title,
                                    style = textStyle,
                                    color = labelColor.copy(alpha = 0.6f),
                                )
                            }

                            if (error.isNotBlank() && !isFocused && state.text.isBlank()) {
                                Text(
                                    text = error,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            innerTextField()
                        }

                        AnimatedVisibility(
                            visible = isFocused,
                            enter = expandHorizontally(
                                animationSpec = spring(stiffness = SharedStiffness),
                                expandFrom = Alignment.Start // Unroll downwards like a blind
                            ) + expandVertically(
                                animationSpec = spring(stiffness = SharedStiffness),
                                expandFrom = Alignment.Top // Unroll downwards like a blind
                            ),
                            exit = shrinkHorizontally(
                                animationSpec = spring(stiffness = SharedStiffness),
                                shrinkTowards = Alignment.Start // Roll up upwards
                            ) + shrinkVertically(
                                animationSpec = spring(stiffness = SharedStiffness),
                                shrinkTowards = Alignment.Top // Unroll downwards like a blind
                            )
                        ) {
                            Spacer(modifier = Modifier.height(2.dp))

                            HorizontalDivider(
                                thickness = 2.dp,
                                color = when {
                                    error.isNotBlank() -> MaterialTheme.colorScheme.error
                                    !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                    else -> MaterialTheme.colorScheme.primary
                                }
                            )
                        }
                    }
                }
            )

            AnimatedVisibility(
                visible = error.isNotBlank() && (isFocused || state.text.isNotBlank()),
                enter = expandHorizontally(
                    animationSpec = spring(stiffness = SharedStiffness),
                    expandFrom = Alignment.Start // Unroll downwards like a blind
                ) + expandVertically(
                    animationSpec = spring(stiffness = SharedStiffness),
                    expandFrom = Alignment.Top // Unroll downwards like a blind
                ),
                exit = shrinkHorizontally(
                    animationSpec = spring(stiffness = SharedStiffness),
                    shrinkTowards = Alignment.Start // Roll up upwards
                ) + shrinkVertically(
                    animationSpec = spring(stiffness = SharedStiffness),
                    shrinkTowards = Alignment.Top // Unroll downwards like a blind
                )
            ) {
                Text(
                    modifier = Modifier.padding(top = 2.dp),
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        content = {
            trailingContent?.invoke()
        }
    )
}

@Composable
fun SettingsJumpPageWidget(
    icon: ImageVector? = null,
    iconPlaceholder: Boolean = true,
    title: String,
    description: String? = null,
    descriptionColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    enabled: Boolean = true,
    isError: Boolean = false,
    onClick: (Offset) -> Unit = {},
    onLongClick: (Offset) -> Unit = {},
    hapticFeedbackType: HapticFeedbackType = HapticFeedbackType.ContextClick,
    rowHeader: @Composable RowScope.() -> Unit = {},
    foreContent: @Composable BoxScope.() -> Unit = {},
    descriptionColumnContent: @Composable ColumnScope.() -> Unit = {},
) {
    SettingsBaseWidget(
        icon = icon,
        iconPlaceholder = iconPlaceholder,
        title = title,
        description = description,
        descriptionColor = descriptionColor,
        enabled = enabled,
        isError = isError,
        onClick = onClick,
        onLongClick = onLongClick,
        hapticFeedbackType = hapticFeedbackType,
        rowHeader = rowHeader,
        foreContent = foreContent,
        descriptionColumnContent = descriptionColumnContent
    ) {
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun SettingsSwitchWidget(
    icon: ImageVector? = null,
    title: String,
    description: String? = null,
    enabled: Boolean = true,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    isError: Boolean = false
) {
    val toggleAction = {
        if (enabled) {
            onCheckedChange(!checked)
        }
    }

    SettingsBaseWidget(
        icon = icon,
        title = title,
        enabled = enabled,
        isError = isError,
        onClick = { toggleAction() },
        hapticFeedbackType = HapticFeedbackType.ToggleOn,
        description = description,
    ) {
        Switch(
            enabled = enabled,
            checked = checked,
            thumbContent = {
                if (checked) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(SwitchDefaults.IconSize),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.size(SwitchDefaults.IconSize),
                    )
                }
            },
            onCheckedChange = null
        )
    }
}

data class SplicedItemData(
    val key: Any?,
    val visible: Boolean,
    val content: @Composable () -> Unit
)

class SplicedGroupScope {
    val items = mutableListOf<SplicedItemData>()

    /**
     * Adds an item to the spliced group.
     * @param key A unique identifier for the item. Crucial for correct animation state during list changes.
     */
    fun item(key: Any? = null, visible: Boolean = true, content: @Composable () -> Unit) {
        items.add(SplicedItemData(key ?: items.size, visible, content))
    }
}

private val CornerRadius = 16.dp
private val ConnectionRadius = 5.dp
private const val SharedStiffness = Spring.StiffnessMediumLow

/**
 * A container that groups items with a spliced, continuous look (similar to M3 Expressive).
 *
 * Features:
 * - **Dynamic Shapes**: Top and bottom corners morph smoothly between rounded (outer) and sharp (inner) based on visibility.
 * - **Blinds Animation**: Items expand/collapse vertically without scaling, simulating a shutter/blinds effect.
 * - **Stacking Order**: Items slide over each other cleanly during exit animations.
 */
@Composable
fun SplicedColumnGroup(
    modifier: Modifier = Modifier,
    title: String = "",
    content: SplicedGroupScope.() -> Unit
) {
    val scope = remember { SplicedGroupScope() }
    scope.items.clear()
    scope.content()

    val allItems = scope.items

    if (allItems.isEmpty()) return

    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (title.isNotEmpty()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
            )
        }

        Column(verticalArrangement = Arrangement.Top) {
            val firstVisibleIndex = allItems.indexOfFirst { it.visible }
            val lastVisibleIndex = allItems.indexOfLast { it.visible }

            // Use a shared stiffness constant for all animations (layout, fade, and shape morphing).
            // This ensures the physics feel connected and synchronized, preventing "ghosting" artifacts
            // where content fades out before the layout collapses.

            allItems.forEachIndexed { index, itemData ->
                // Using a stable key is mandatory for correct AnimatedVisibility behavior in lists.
                key(itemData.key) {
                    // Z-Index Trick:
                    // We invert the visual stacking order so that items lower in the list render ON TOP of items above them.
                    // This ensures that when an item shrinks upwards (shutter effect), the item below it slides 'over'
                    // the gap rather than underneath, creating a solid, card-stacking feel.
                    val zIndex = allItems.size - index.toFloat()

                    AnimatedVisibility(
                        visible = itemData.visible,
                        modifier = Modifier.zIndex(zIndex),
                        enter = expandVertically(
                            animationSpec = spring(stiffness = SharedStiffness),
                            expandFrom = Alignment.Top // Unroll downwards like a blind
                        ) + fadeIn(
                            animationSpec = spring(stiffness = SharedStiffness)
                        ),
                        exit = shrinkVertically(
                            animationSpec = spring(stiffness = SharedStiffness),
                            shrinkTowards = Alignment.Top // Roll up upwards
                        ) + fadeOut(
                            animationSpec = spring(stiffness = SharedStiffness)
                        )
                    ) {
                        val isFirst = index == firstVisibleIndex
                        val isLast = index == lastVisibleIndex

                        // Determine target corner radii based on current visibility position.
                        // Outer boundaries get full CornerRadius; inner connections get smaller ConnectionRadius.
                        val targetTopRadius = if (isFirst) CornerRadius else ConnectionRadius
                        val targetBottomRadius = if (isLast) CornerRadius else ConnectionRadius

                        // Animate shape changes to match the enter/exit physics.
                        val animatedTopRadius by animateDpAsState(
                            targetValue = targetTopRadius,
                            animationSpec = spring(stiffness = SharedStiffness),
                            label = "TopCornerRadius"
                        )
                        val animatedBottomRadius by animateDpAsState(
                            targetValue = targetBottomRadius,
                            animationSpec = spring(stiffness = SharedStiffness),
                            label = "BottomCornerRadius"
                        )

                        val shape = RoundedCornerShape(
                            topStart = animatedTopRadius,
                            topEnd = animatedTopRadius,
                            bottomStart = animatedBottomRadius,
                            bottomEnd = animatedBottomRadius
                        )

                        // Layout Stability Fix:
                        // Instead of placing spacing/padding at the bottom, we apply it to the TOP for all items except the first.
                        // Since our animation shrinks towards the TOP, bottom-padding would be clipped first, causing
                        // the next item to "jump" instantly. By anchoring spacing to the top, it persists until the
                        // very end of the shrink animation.
                        val topPadding = if (index == 0) 0.dp else 2.dp

                        Column(
                            modifier = Modifier
                                .padding(top = topPadding)
                                .clip(shape)
                                .background(
                                    MaterialTheme.colorScheme.surfaceBright.copy(
                                        alpha = CardConfig.cardAlpha
                                    )
                                )
                        ) {
                            itemData.content()
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsDropdownWidget(
    icon: ImageVector? = null,
    iconPlaceholder: Boolean = true,
    title: String,
    description: String? = null,
    descriptionColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    enabled: Boolean = true,
    isError: Boolean = false,
    hapticFeedbackType: HapticFeedbackType = HapticFeedbackType.ContextClick,
    rowHeader: @Composable RowScope.() -> Unit = {},
    foreContent: @Composable BoxScope.() -> Unit = {},
    afterContent: @Composable RowScope.(Int) -> Unit = {},
    items: List<String>,
    selectedIndex: Int,
    maxHeight: Dp? = 400.dp,
    colors: SuperDropdownColors = SuperDropdownDefaults.colors(),
    onSelectedIndexChange: (Int) -> Unit
) {
    val alpha = if (enabled) 1f else 0.38f
    var currentIndex by remember { mutableIntStateOf(selectedIndex) }
    var showDialog by remember { mutableStateOf(false) }

    fun setCurrentIndex(index: Int) {// 快别叫唤未使用了
        currentIndex = index
    }

    fun dismiss() {
        showDialog = false
    }

    val itemsNotEmpty = items.isNotEmpty()

    SettingsBaseWidget(
        icon = icon,
        iconPlaceholder = iconPlaceholder,
        title = title,
        description = description,
        descriptionColor = descriptionColor,
        enabled = enabled,
        isError = isError,
        onClick = {
            showDialog = true
        },
        hapticFeedbackType = hapticFeedbackType,
        rowHeader = rowHeader,
        foreContent = foreContent,
        descriptionColumnContent = {
            val color = if (isError) MaterialTheme.colorScheme.error
            else descriptionColor
            Text(
                text = items[selectedIndex],
                color = color.copy(alpha = alpha),
                style = MaterialTheme.typography.bodyMediumEmphasized,
                fontSize = MaterialTheme.typography.bodyMediumEmphasized.fontSize,
                fontFamily = MaterialTheme.typography.bodySmallEmphasized.fontFamily,
                lineHeight = MaterialTheme.typography.bodyMediumEmphasized.lineHeight,
                fontWeight = MaterialTheme.typography.bodyMediumEmphasized.fontWeight,
            )
        }
    ) {}

    if (showDialog && itemsNotEmpty) {
        AlertDialog(
            onDismissRequest = { dismiss() },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
            },
            text = {
                val dialogMaxHeight = maxHeight ?: 400.dp
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = dialogMaxHeight),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(items.size) { index ->
                        DropdownItem(
                            text = items[index],
                            isSelected = currentIndex == index,
                            colors = colors,
                            afterContent = { item ->
                                afterContent(items.indexOf(item))
                            },
                            onClick = {
                                setCurrentIndex(index)
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onSelectedIndexChange(currentIndex)
                    dismiss()
                }) {
                    Text(text = stringResource(id = R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    setCurrentIndex(selectedIndex)
                    dismiss()
                }) {
                    Text(text = stringResource(id = R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun DropdownItem(
    text: String,
    isSelected: Boolean,
    colors: SuperDropdownColors,
    afterContent: @Composable RowScope.(String) -> Unit = {},
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        colors.selectedBackgroundColor
    } else {
        Color.Transparent
    }

    val contentColor = if (isSelected) {
        colors.selectedContentColor
    } else {
        colors.contentColor
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = null,
            colors = RadioButtonDefaults.colors(
                selectedColor = colors.selectedContentColor,
                unselectedColor = colors.contentColor
            )
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = contentColor,
            modifier = Modifier.weight(1f)
        )
        afterContent(text)
    }
}

@Immutable
data class SuperDropdownColors(
    val titleColor: Color,
    val summaryColor: Color,
    val valueColor: Color,
    val iconColor: Color,
    val arrowColor: Color,
    val disabledTitleColor: Color,
    val disabledSummaryColor: Color,
    val disabledValueColor: Color,
    val disabledIconColor: Color,
    val disabledArrowColor: Color,
    val contentColor: Color,
    val selectedContentColor: Color,
    val selectedBackgroundColor: Color
)

object SuperDropdownDefaults {
    @Composable
    fun colors(
        titleColor: Color = MaterialTheme.colorScheme.onSurface,
        summaryColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
        valueColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
        iconColor: Color = MaterialTheme.colorScheme.primary,
        arrowColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
        disabledTitleColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        disabledSummaryColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
        disabledValueColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
        disabledIconColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
        disabledArrowColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
        contentColor: Color = MaterialTheme.colorScheme.onSurface,
        selectedContentColor: Color = MaterialTheme.colorScheme.primary,
        selectedBackgroundColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    ): SuperDropdownColors {
        return SuperDropdownColors(
            titleColor = titleColor,
            summaryColor = summaryColor,
            valueColor = valueColor,
            iconColor = iconColor,
            arrowColor = arrowColor,
            disabledTitleColor = disabledTitleColor,
            disabledSummaryColor = disabledSummaryColor,
            disabledValueColor = disabledValueColor,
            disabledIconColor = disabledIconColor,
            disabledArrowColor = disabledArrowColor,
            contentColor = contentColor,
            selectedContentColor = selectedContentColor,
            selectedBackgroundColor = selectedBackgroundColor
        )
    }
}

inline fun <T> LazyListScope.splicedLazyColumnGroup(
    items: List<T>,
    noinline key: ((index: Int, item: T) -> Any)? = null,
    crossinline contentType: (index: Int, item: T) -> Any? = { _, _ -> null },
    crossinline itemContent: @Composable LazyItemScope.(index: Int, item: T) -> Unit,
) {
    val sharedStiffness = Spring.StiffnessMediumLow
    val cornerRadius = 16.dp
    val connectionRadius = 4.dp

    itemsIndexed(
        items = items,
        key = key,
        contentType = contentType,
    ) { index, item ->
        val isFirst = index == 0
        val isLast = index == items.size - 1

        val targetTopRadius = if (isFirst) cornerRadius else connectionRadius
        val targetBottomRadius = if (isLast) cornerRadius else connectionRadius

        val animatedTopRadius by animateDpAsState(
            targetValue = targetTopRadius,
            animationSpec = spring(stiffness = sharedStiffness),
            label = "TopCornerRadius"
        )
        val animatedBottomRadius by animateDpAsState(
            targetValue = targetBottomRadius,
            animationSpec = spring(stiffness = sharedStiffness),
            label = "BottomCornerRadius"
        )

        Surface(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(top = 2.dp),
            shape = RoundedCornerShape(
                topStart = animatedTopRadius,
                topEnd = animatedTopRadius,
                bottomStart = animatedBottomRadius,
                bottomEnd = animatedBottomRadius
            ),
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(
                alpha = CardConfig.cardAlpha
            ),
        ) {
            itemContent(index, item)
        }
    }
}
