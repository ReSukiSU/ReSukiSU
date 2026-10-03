package com.resukisu.resukisu.ui.wear.component.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

/** A settings switch; per the Wear switch button guidance the label takes up to 3 lines and the summary up to 2. */
@Composable
fun TransformingLazyColumnItemScope.WearSettingsSwitchWidget(
    transformationSpec: TransformationSpec,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    secondaryLabel: String? = null,
) {
    SwitchButton(
        modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec)
            .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding),
        transformation = SurfaceTransformation(transformationSpec),
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        icon = icon?.let { { Icon(it, contentDescription = null) } },
        secondaryLabel = secondaryLabel?.let { text -> { Text(text, maxLines = 2, overflow = TextOverflow.Ellipsis) } },
        label = { Text(label, maxLines = 3, overflow = TextOverflow.Ellipsis) },
    )
}
