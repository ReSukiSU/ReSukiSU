package com.resukisu.resukisu.ui.component.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.resukisu.resukisu.R
import com.resukisu.resukisu.ui.component.TopBarIconEdgeInset
import com.resukisu.resukisu.ui.component.TopBarIconPill

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
@Composable
fun AppBackButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    icon: ImageVector = Icons.AutoMirrored.TwoTone.ArrowBack, // Default icon is ArrowBack
    contentDescription: String = stringResource(id = R.string.back)
) {
    TopBarIconPill(
        onClick = onClick,
        modifier = modifier.padding(start = TopBarIconEdgeInset)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription
        )
    }
}
