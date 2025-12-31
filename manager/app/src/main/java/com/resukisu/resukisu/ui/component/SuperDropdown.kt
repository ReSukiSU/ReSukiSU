package com.resukisu.resukisu.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    var currentIndex by remember { mutableIntStateOf(selectedIndex) }
    var showDialog by remember { mutableStateOf(false) }

    fun setCurrentIndex(index: Int) {// 快别叫唤未使用了
        currentIndex = index
    }

    fun dismiss() {
        showDialog = false
    }

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
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = if (actualEnabled) colors.arrowColor else colors.disabledArrowColor,
            modifier = Modifier.size(24.dp)
        )
    }

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
                    Text(text = stringResource(id = android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    setCurrentIndex(selectedIndex)
                    dismiss()
                }) {
                    Text(text = stringResource(id = android.R.string.cancel))
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
