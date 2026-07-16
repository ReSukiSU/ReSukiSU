package com.resukisu.resukisu.ui.component.settings

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

@Composable
fun SettingsJumpPageWidget(
    icon: ImageVector? = null,
    iconPlaceholder: Boolean = true,
    title: String,
    description: String? = null,
    descriptionColor: Color? = null,
    descriptionStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    enabled: Boolean = true,
    renderBackgroundBlur: Boolean = true,
    isError: Boolean = false,
    onClick: ((Offset) -> Unit)? = null,
    onLongClick: ((Offset) -> Unit)? = null,
    hapticFeedbackType: HapticFeedbackType = HapticFeedbackType.ContextClick,
    leadingContent: (@Composable () -> Unit)? = null,
    foreContent: @Composable RowScope.() -> Unit = {},
    descriptionColumnContent: @Composable ColumnScope.() -> Unit = {},
    trailingIcon: ImageVector = Icons.Filled.ChevronRight,
) {
    SettingsBaseWidget(
        icon = icon,
        iconPlaceholder = iconPlaceholder,
        renderBackgroundBlur = renderBackgroundBlur,
        title = title,
        description = description,
        descriptionColor = descriptionColor,
        descriptionStyle = descriptionStyle,
        enabled = enabled,
        isError = isError,
        onClick = onClick,
        onLongClick = onLongClick,
        clickHaptic = hapticFeedbackType,
        leadingContent = leadingContent,
        foreContent = foreContent,
        descriptionColumnContent = descriptionColumnContent
    ) {
        Icon(
            imageVector = trailingIcon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
    }
}
