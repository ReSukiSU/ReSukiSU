package com.resukisu.resukisu.ui.wear.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Slider
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

/**
 * A slider wrapped in one card together with its information: an icon, a one-line label and the
 * current value on top, and the Wear Material 3 [Slider] (with its decrease and increase buttons)
 * below, following the inline slider anatomy of the Wear slider guidance.
 */
@Composable
fun TransformingLazyColumnItemScope.WearSliderCard(
    transformationSpec: TransformationSpec,
    icon: ImageVector,
    label: String,
    valueText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    enabled: Boolean = true,
) {
    WearInfoCard(
        transformationSpec,
        contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 12.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            // Numbers use the numeral style, whose tabular digits keep the value from jumping while dragging.
            Text(valueText, style = MaterialTheme.typography.numeralExtraSmall, color = MaterialTheme.colorScheme.primary,
                maxLines = 1)
        }
        Slider(value, onValueChange, steps = steps, valueRange = valueRange, enabled = enabled,
            modifier = Modifier.fillMaxWidth())
    }
}
