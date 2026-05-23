package com.resukisu.resukisu.ui.component.settings

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState

/**
 * A setting widget with a [Switch] trailing content.
 *
 * @param icon The [ImageVector] to be displayed at the start of the widget.
 * @param title The primary text displayed in the widget.
 * @param description Optional supporting text displayed below the title.
 * @param enabled Whether the widget is enabled and interactive.
 * @param checked Whether the switch is currently on or off.
 * @param descriptionColumnContent composable display below the description
 * @param onCheckedChange Callback to be invoked when the switch state changes.
 * @param isError If true, applies an error state to the widget.
 * @param containerColor Custom container color, if provided, selected/isError will be ignored.
 */
@Composable
fun SettingsSwitchWidget(
    icon: ImageVector? = null,
    iconPlaceholder: Boolean = true,
    title: String,
    description: String? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    checked: Boolean,
    descriptionColumnContent: (@Composable ColumnScope.() -> Unit)? = null,
    containerColor: Color? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    val haptic = LocalHapticFeedback.current

    val handleCheckedChange: (Boolean) -> Unit = { newValue ->
        if (newValue) {
            haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
        } else {
            haptic.performHapticFeedback(HapticFeedbackType.ToggleOff)
        }
        onCheckedChange(newValue)
    }

    val rowClickAction = {
        if (enabled) {
            handleCheckedChange(!checked)
        }
    }

    SettingsBaseWidget(
        modifier = Modifier.semantics(mergeDescendants = true) {
            role = Role.Switch
            toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
        },
        icon = icon,
        iconPlaceholder = iconPlaceholder,
        title = title,
        enabled = enabled,
        isError = isError,
        onClick = {
            rowClickAction()
        },
        clickHaptic = null,
        description = description,
        descriptionColumnContent = descriptionColumnContent,
        containerColor = containerColor
    ) { interactionSource ->
        Switch(
            modifier = Modifier.clearAndSetSemantics {},
            enabled = enabled,
            checked = checked,
            interactionSource = interactionSource,
            colors = SwitchDefaults.colors(
                checkedIconColor = MaterialTheme.colorScheme.primary,
                uncheckedIconColor = MaterialTheme.colorScheme.surfaceContainerHighest
            ),
            thumbContent = {
                Icon(
                    imageVector = if (checked) Icons.Filled.Check else Icons.Filled.Close,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize)
                )
            },
            // Pass null to disable internal touch handling and let BaseWidget calculate the exact ripple coordinates
            onCheckedChange = null
        )
    }
}