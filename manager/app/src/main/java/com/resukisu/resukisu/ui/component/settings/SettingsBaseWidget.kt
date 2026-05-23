package com.resukisu.resukisu.ui.component.settings

import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.resukisu.resukisu.ui.theme.CardConfig

/**
 * A [CompositionLocal] that provides the dynamically calculated [Shape] for items
 * inside a [SegmentedColumn]. Defaults to a rounded corner shape with 16.dp.
 */
val LocalSegmentedItemShape = compositionLocalOf<Shape> { RoundedCornerShape(16.dp) }

/**
 * A base widget component designed for setting items and list entries.
 * It follows Material Design 3 guidelines with support for icons, headlines, supporting text,
 * and custom trailing content.
 *
 * @param modifier The [Modifier] to be applied to the widget.
 * @param icon The [ImageVector] to be displayed at the start of the widget.
 * @param iconColor The color applied to the [icon].
 * @param iconPlaceholder If true, maintains a consistent leading space even when [icon] is null.
 * @param title The primary headline text of the widget.
 * @param titleStyle The [TextStyle] applied to the [title].
 * @param description Optional supporting text displayed below the title.
 * @param descriptionStyle The [TextStyle] applied to the [description].
 * @param descriptionColor Optional color applied to the [description] text.
 * If null, an adaptive color derived from the resolved content color is used.
 * @param enabled Controls the enabled state of the widget.
 * If [onClick] is null, this only affects visual/semantic disabled state.
 * If [onClick] is not null, this also controls clickability.
 * @param isError If true, applies the error color to the description text.
 * @param selected If true, highlights the widget with a primary container background.
 * @param onClick Callback to be invoked when the widget is clicked. If null, the widget is not clickable.
 * @param onLongClick Callback to be invoked when the widget is LONG CLICKED. If null, the widget is not clickable.
 * @param clickHaptic The type of haptic feedback to perform on click. Set to null to disable.
 * @param descriptionColumnContent composable display below the description
 * @param foreContent A composable slot for content displayed alongside/over the headline.
 * @param trailingContent A composable slot for trailing content, e.g. switches, checkboxes, or arrows.
 * @param containerColor Custom container color, if provided, selected/isError will be ignored.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsBaseWidget(
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconColor: Color? = null,
    iconPlaceholder: Boolean = true,
    title: String?,
    titleStyle: TextStyle = MaterialTheme.typography.titleMedium,
    description: String? = null,
    descriptionColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    descriptionStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    enabled: Boolean = true,
    isError: Boolean = false,
    selected: Boolean = false,
    onClick: ((Offset) -> Unit)? = {},
    onLongClick: ((Offset) -> Unit)? = {},
    clickHaptic: HapticFeedbackType? = HapticFeedbackType.ContextClick,
    leadingContent: (@Composable () -> Unit)? = null,
    foreContent: @Composable BoxScope.() -> Unit = {},
    descriptionColumnContent: (@Composable ColumnScope.() -> Unit)? = null,
    containerColor: Color? = null,
    trailingContent: (@Composable BoxScope.(interactionSource: MutableInteractionSource) -> Unit)? = null,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val alpha = if (enabled) 1f else 0.38f

    val interactionSource = remember { MutableInteractionSource() }

    val density = LocalDensity.current
    val dynamicInternalPadding = (4 * density.fontScale).dp

    val baseShape = LocalSegmentedItemShape.current

    val backgroundColor = run {
        if (containerColor != null)
            containerColor
        else {
            val color = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            }

            color.copy(
                alpha = CardConfig.cardAlpha
            )
        }
    }

    val baseContentColor = if (selected) {
        MaterialTheme.colorScheme.contentColorFor(MaterialTheme.colorScheme.primaryContainer)
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val resolvedIconColor = iconColor
        ?: if (selected) {
            baseContentColor
        } else {
            MaterialTheme.colorScheme.onSurface
        }

    val finalDescriptionColor = when {
        isError -> MaterialTheme.colorScheme.error
        else -> descriptionColor
    }

    /*
     * Disabled colors intentionally keep their original alpha here.
     *
     * We apply disabled opacity with Modifier.alpha(alpha) around each slot instead.
     * This avoids double-applying alpha when the clickable ListItem is disabled,
     * and it also lets the non-clickable ListItem share exactly the same disabled look.
     */
    val colors = ListItemDefaults.colors(
        containerColor = backgroundColor,
        contentColor = baseContentColor,
        leadingContentColor = resolvedIconColor,
        trailingContentColor = resolvedIconColor,
        supportingContentColor = finalDescriptionColor,

        selectedContainerColor = backgroundColor,
        selectedContentColor = baseContentColor,
        selectedLeadingContentColor = resolvedIconColor,
        selectedTrailingContentColor = resolvedIconColor,
        selectedSupportingContentColor = finalDescriptionColor,

        disabledContainerColor = backgroundColor,
        disabledContentColor = baseContentColor,
        disabledLeadingContentColor = resolvedIconColor,
        disabledTrailingContentColor = resolvedIconColor,
        disabledSupportingContentColor = finalDescriptionColor
    )

    val shapes = ListItemDefaults.shapes(
        shape = baseShape,
        pressedShape = RoundedCornerShape(16.dp),
        selectedShape = baseShape,
        focusedShape = baseShape,
        hoveredShape = baseShape
    )

    val itemModifier = modifier.fillMaxWidth()

    val finalLeadingContent: (@Composable () -> Unit)? =
        if (leadingContent == null && icon == null && !iconPlaceholder)
            null
        else {
            {
                leadingContent?.invoke()

                if (icon != null || iconPlaceholder) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .alpha(alpha),
                        contentAlignment = Alignment.Center
                    ) {
                        if (icon != null) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = resolvedIconColor
                            )
                        } else {
                            Spacer(modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }

    val supportingContent: (@Composable () -> Unit) = {
        Column {
            description?.let { text ->
                Text(
                    text = text,
                    style = descriptionStyle,
                    modifier = Modifier
                        .alpha(alpha)
                )
            }

            descriptionColumnContent?.invoke(this)

            if (description != null || descriptionColumnContent != null) {
                Spacer(Modifier.height(dynamicInternalPadding))
            }
        }
    }

    val trailing: (@Composable () -> Unit)? = if (trailingContent != null) {
        {
            Box(
                modifier = Modifier.alpha(alpha),
                contentAlignment = Alignment.Center
            ) {
                trailingContent(interactionSource)
            }
        }
    } else null

    val headline: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .alpha(alpha)
                .padding(
                    top = dynamicInternalPadding,
                    bottom = if (description == null) dynamicInternalPadding else 0.dp
                )
        ) {
            title?.let {
                Text(
                    text = it,
                    style = titleStyle
                )
            }

            foreContent()
        }
    }

    if (onClick != null || onLongClick != null) {
        var touchPoint by remember { mutableStateOf(Offset.Zero) }

        ListItem(
            selected = selected,
            modifier = itemModifier
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitFirstDown(requireUnconsumed = false)
                            touchPoint = event.position
                        }
                    }
                },
            onClick = {
                if (clickHaptic != null) {
                    hapticFeedback.performHapticFeedback(clickHaptic)
                }
                onClick?.invoke(touchPoint)
            },
            onLongClick = if (onLongClick != null) {
                {
                    if (clickHaptic != null) {
                        hapticFeedback.performHapticFeedback(clickHaptic)
                    }
                    onLongClick(touchPoint)
                }
            } else null,
            enabled = enabled,
            colors = colors,
            shapes = shapes,
            verticalAlignment = Alignment.CenterVertically,
            leadingContent = finalLeadingContent,
            supportingContent = supportingContent,
            trailingContent = trailing,
            interactionSource = interactionSource,
            content = headline
        )
    } else {
        /*
         * Non-clickable item:
         *
         * Do not use the clickable ListItem overload here.
         * Otherwise, a null onClick would have to be represented as enabled = false,
         * which incorrectly exposes the item as disabled and changes its visual state.
         */
        ListItem(
            headlineContent = headline,
            modifier = itemModifier
                .clip(baseShape)
                .then(
                    if (!enabled) {
                        Modifier.semantics { disabled() }
                    } else {
                        Modifier
                    }
                ),
            colors = colors,
            leadingContent = finalLeadingContent,
            supportingContent = supportingContent,
            trailingContent = trailing
        )
    }
}

@Preview
@Composable
fun SettingsBaseWidgetPreview() {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SettingsBaseWidget(
                title = "I am title",
                description = "I am description, I have iconPlaceholder"
            ) {}
        }

        item {
            SettingsBaseWidget(
                iconPlaceholder = false,
                title = "I am title",
                description = "I am description, I don't have iconPlaceholder"
            ) {}
        }

        item {
            SettingsBaseWidget(
                iconPlaceholder = false,
                title = "I am title",
                descriptionColumnContent = {
                    Text("Hello from descriptionColumnContent")
                }
            ) {}
        }
    }
}