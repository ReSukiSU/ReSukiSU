package com.resukisu.resukisu.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuperDropdown(
    items: List<String>,
    selectedIndex: Int,
    title: String,
    summary: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    showValue: Boolean = true,
    maxHeight: Dp? = 400.dp,
    colors: SuperDropdownColors = SuperDropdownDefaults.colors(),
    leftAction: (@Composable () -> Unit)? = null,
    onSelectedIndexChange: (Int) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    val selectedItemText = items.getOrNull(selectedIndex) ?: ""
    val itemsNotEmpty = items.isNotEmpty()
    val actualEnabled = enabled && itemsNotEmpty

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = actualEnabled) { showDialog = true }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        if (leftAction != null) {
            leftAction()
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (actualEnabled) colors.iconColor else colors.disabledIconColor,
                modifier = Modifier
                    .padding(end = 16.dp)
                    .size(24.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (actualEnabled) colors.titleColor else colors.disabledTitleColor
            )
            
            if (summary != null) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (actualEnabled) colors.summaryColor else colors.disabledSummaryColor
                )
            }
            
            if (showValue && itemsNotEmpty) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = selectedItemText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (actualEnabled) colors.valueColor else colors.disabledValueColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = if (actualEnabled) colors.arrowColor else colors.disabledArrowColor,
            modifier = Modifier.size(24.dp)
        )
    }

    if (showDialog && itemsNotEmpty) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { 
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall
                ) 
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
                            isSelected = selectedIndex == index,
                            colors = colors,
                            onClick = {
                                onSelectedIndexChange(index)
                                showDialog = false
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(text = stringResource(id = android.R.string.cancel))
                }
            },
            containerColor = colors.dialogBackgroundColor,
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 4.dp
        )
    }
}

@Composable
private fun DropdownItem(
    text: String,
    isSelected: Boolean,
    colors: SuperDropdownColors,
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
        
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = colors.selectedContentColor,
                modifier = Modifier.size(20.dp)
            )
        }
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
    val dialogBackgroundColor: Color,
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
        dialogBackgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
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
            dialogBackgroundColor = dialogBackgroundColor,
            contentColor = contentColor,
            selectedContentColor = selectedContentColor,
            selectedBackgroundColor = selectedBackgroundColor
        )
    }
}
