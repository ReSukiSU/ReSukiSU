package com.resukisu.resukisu.ui.component.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsDropdownWidget(
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    title: String,
    description: String? = null,
    iconPlaceholder: Boolean = true,
    enabled: Boolean = true,
    isError: Boolean = false,
    choice: Int,
    data: List<String>,
    leadingContent: (@Composable () -> Unit)? = null,
    onChoiceChange: (Int) -> Unit
) {
    var expanded by remember {
        mutableStateOf(false)
    }

    var touchPoint: Offset by remember { mutableStateOf(Offset.Zero) }

    val (offsetX, offsetY) = with(LocalDensity.current) {
        (touchPoint.x.toDp()) to (touchPoint.y.toDp())
    }

    Box {
        SettingsBaseWidget(
            modifier = modifier,
            icon = icon,
            title = title,
            description = description,
            enabled = enabled,
            isError = isError,
            onClick = { offest ->
                touchPoint = offest

                expanded = !expanded
            },
            leadingContent = leadingContent,
            iconPlaceholder = iconPlaceholder,
        ) {}

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(offsetX, offsetY)
        ) {
            // Use DropdownMenuPopup to provide the foundation for building a custom menu
            DropdownMenuPopup(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                // Use DropdownMenuGroup to create a visually distinct group
                DropdownMenuGroup(
                    shapes = MenuDefaults.groupShapes()
                ) {
                    data.forEachIndexed { index, item ->
                        val isSelected = index == choice

                        // Utilize the selectable variation of DropdownMenuItem
                        // MenuDefaults.itemShape(index, count) automatically handles the shapes
                        DropdownMenuItem(
                            selected = isSelected,
                            onClick = {
                                onChoiceChange(index)
                                expanded = false
                            },
                            text = { Text(text = item) },
                            shapes = MenuDefaults.itemShape(
                                index = index,
                                count = data.size
                            )
                        )
                    }
                }
            }
        }
    }
}
