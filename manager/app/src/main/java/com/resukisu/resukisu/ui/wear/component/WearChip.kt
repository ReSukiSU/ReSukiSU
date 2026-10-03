package com.resukisu.resukisu.ui.wear.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.LocalTextStyle
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

/** Chip emphasis levels; a screen should lead with at most one [HIGH] chip. [ERROR] marks a failed state in red. */
enum class WearChipEmphasis { HIGH, MEDIUM, OUTLINED, LOW, ERROR }


@Composable
fun TransformingLazyColumnItemScope.WearChip(
    transformationSpec: TransformationSpec,
    label: String,
    modifier: Modifier = Modifier,
    secondaryLabel: String? = null,
    icon: ImageVector? = null,
    emphasis: WearChipEmphasis = WearChipEmphasis.MEDIUM,
    headlineBadges: List<String> = emptyList(),
    secondaryLabelStyle: TextStyle? = null,
    onClick: (() -> Unit)? = null,
) {
    val labelLines = if (secondaryLabel != null) 1 else if (onClick != null) 3 else 2
    val centered = icon == null && secondaryLabel == null
    val border = if (emphasis == WearChipEmphasis.OUTLINED) ButtonDefaults.outlinedButtonBorder(enabled = true) else null
    if (onClick != null) {
        Button(
            onClick = onClick,
            modifier = modifier.fillMaxWidth().transformedHeight(this, transformationSpec)
                .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding),
            transformation = SurfaceTransformation(transformationSpec),
            colors = when (emphasis) {
                WearChipEmphasis.HIGH -> ButtonDefaults.buttonColors()
                WearChipEmphasis.MEDIUM -> ButtonDefaults.filledTonalButtonColors()
                WearChipEmphasis.OUTLINED -> ButtonDefaults.outlinedButtonColors()
                WearChipEmphasis.LOW -> ButtonDefaults.childButtonColors()
                WearChipEmphasis.ERROR -> ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    secondaryContentColor = MaterialTheme.colorScheme.onErrorContainer,
                    iconColor = MaterialTheme.colorScheme.onErrorContainer,
                )
            },
            border = border,
            icon = icon?.let { { Icon(it, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize)) } },
            // Keep the description to two lines; mode badges are measured separately in the headline.
            // A [secondaryLabelStyle] from the theme typography lets a dense summary fit one line.
            secondaryLabel = secondaryLabel?.let { text ->
                { Text(text, style = secondaryLabelStyle ?: LocalTextStyle.current, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            },
        ) {
            WearChipHeadline(label, labelLines, centered, headlineBadges)
        }
        return
    }

    val scheme = MaterialTheme.colorScheme
    val (container, content, secondaryContent) = when (emphasis) {
        WearChipEmphasis.HIGH -> Triple(scheme.primary, scheme.onPrimary, scheme.onPrimary)
        // A null container keeps the surface container and the custom background of other cards.
        WearChipEmphasis.MEDIUM -> Triple(null, scheme.onSurface, scheme.onSurfaceVariant)
        WearChipEmphasis.OUTLINED -> Triple(Color.Transparent, scheme.primary, scheme.onSurfaceVariant)
        WearChipEmphasis.LOW -> Triple(Color.Transparent, scheme.onSurface, scheme.onSurfaceVariant)
        WearChipEmphasis.ERROR -> Triple(scheme.errorContainer, scheme.onErrorContainer, scheme.onErrorContainer)
    }
    WearInfoCard(
        transformationSpec,
        modifier = modifier.heightIn(min = ButtonDefaults.Height),
        containerColor = container,
        border = border,
        shape = ButtonDefaults.shape,
        contentPadding = ButtonDefaults.ContentPadding,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            }
            Column(Modifier.weight(1f)) {
                WearChipHeadline(label, labelLines, centered, headlineBadges, content, MaterialTheme.typography.labelMedium)
                if (secondaryLabel != null) Text(
                    secondaryLabel, color = secondaryContent, style = secondaryLabelStyle ?: MaterialTheme.typography.labelSmall,
                    maxLines = 3, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun WearChipHeadline(
    label: String,
    maxLines: Int,
    centered: Boolean,
    badges: List<String>,
    contentColor: Color = Color.Unspecified,
    style: TextStyle = LocalTextStyle.current,
) {
    if (badges.isEmpty()) {
        Text(
            label, color = contentColor, style = style, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
            modifier = if (centered) Modifier.fillMaxWidth() else Modifier,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        )
        return
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            label, color = contentColor, style = style, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.CenterVertically),
        )
        badges.forEach { badge ->
            Text(
                badge,
                modifier = Modifier.align(Alignment.CenterVertically)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
