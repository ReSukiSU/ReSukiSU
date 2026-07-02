package com.resukisu.resukisu.ui.component.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.resukisu.resukisu.R

/**
 * A standardized back button for the application.
 *
 * This composable encapsulates the specific styling for the navigation icon,
 * making it reusable across different screens.
 *
 * @param modifier The Modifier to be applied to this button.
 * @param onClick The lambda to be executed when the button is clicked.
 * @param icon The vector asset to be displayed inside the button. Defaults to a back arrow.
 * @param contentDescription The content description for accessibility.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppBackButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    icon: ImageVector = Icons.AutoMirrored.Rounded.ArrowBack, // Default icon is ArrowBack
    containerColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f),
    contentDescription: String = stringResource(id = R.string.back)
) {
    Row {
        IconButton(
            onClick = onClick,
            modifier = modifier.size(36.dp),
            // Consistent shapes for the button.
            shapes = IconButtonDefaults.shapes(
                // shape = CircleShape
            ),
            // Consistent colors for the button.
            colors = IconButtonDefaults.iconButtonColors(
                // The color of the icon inside the button.
                contentColor = MaterialTheme.colorScheme.onSurface,
                // The background color of the button.
                // Using a more standard color for a filled icon button variant.
                containerColor = containerColor,
            )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription
            )
        }

        Spacer(modifier = Modifier.size(16.dp))
    }
}