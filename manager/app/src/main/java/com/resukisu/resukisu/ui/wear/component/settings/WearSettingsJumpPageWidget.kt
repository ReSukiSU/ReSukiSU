package com.resukisu.resukisu.ui.wear.component.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight

/**
 * A settings entry that opens another page: a neutral filled tonal button with an optional leading
 * icon and a [description] (summary or current value) as its secondary label.
 */
@Composable
fun TransformingLazyColumnItemScope.WearSettingsJumpPageWidget(
    spec: TransformationSpec,
    title: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    description: String? = null,
) {
    FilledTonalButton(onClick = onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec)
            .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding),
        transformation = SurfaceTransformation(spec),
        icon = icon?.let { { Icon(it, contentDescription = null) } },
        secondaryLabel = description?.let { text -> { Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
    ) { Text(title, maxLines = if (description == null) 3 else 2, overflow = TextOverflow.Ellipsis) }
}
