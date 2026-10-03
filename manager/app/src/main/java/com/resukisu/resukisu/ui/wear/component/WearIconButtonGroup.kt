package com.resukisu.resukisu.ui.wear.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.material3.ButtonGroup
import androidx.wear.compose.material3.ButtonGroupDefaults
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

/** One icon action of a [WearIconButtonGroup]; [label] is its accessibility description. */
data class WearIconAction(val icon: ImageVector, val label: String, val onClick: () -> Unit)


@Composable
fun TransformingLazyColumnItemScope.WearIconButtonGroup(
    transformationSpec: TransformationSpec,
    actions: List<WearIconAction>,
) {
    ButtonGroup(
        modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec)
            .minimumVerticalContentPadding(ButtonGroupDefaults.minimumVerticalListContentPadding),
        transformation = SurfaceTransformation(transformationSpec),
    ) {
        actions.forEach { action ->
            val interactionSource = remember { MutableInteractionSource() }
            FilledTonalIconButton(
                onClick = action.onClick,
                modifier = Modifier.animateWidth(interactionSource),
                interactionSource = interactionSource,
            ) { Icon(action.icon, contentDescription = action.label) }
        }
    }
}
