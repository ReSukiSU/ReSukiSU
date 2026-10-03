package com.resukisu.resukisu.ui.wear.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonColors
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ListHeaderDefaults
import androidx.wear.compose.material3.ListSubHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

/** Titles keep a 7.3% internal side margin so they are not clipped by a round screen, and take at most two lines. */
@Composable
fun TransformingLazyColumnItemScope.WearPageHeader(
    transformationSpec: TransformationSpec,
    title: String,
) {
    val side = maxOf(14.dp, (LocalConfiguration.current.screenWidthDp * 0.073f).dp)
    val defaults = ListHeaderDefaults.ContentPadding
    ListHeader(
        modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec)
            .minimumVerticalContentPadding(ListHeaderDefaults.minimumTopListContentPadding),
        transformation = SurfaceTransformation(transformationSpec),
        contentPadding = PaddingValues(
            start = side, end = side,
            top = defaults.calculateTopPadding(), bottom = defaults.calculateBottomPadding(),
        ),
    ) {
        // Titles keep the ListHeader title style, as the Wear typography guidance assigns titles to way-finding.
        Text(title, textAlign = TextAlign.Center,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun TransformingLazyColumnItemScope.WearSectionHeader(
    transformationSpec: TransformationSpec,
    title: String,
) {
    ListSubHeader(
        modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
        transformation = SurfaceTransformation(transformationSpec),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(title, Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
    }
}

/**
 * A full-width list button. A Wear button is sized for two lines and grows to at most three, so
 * the label takes up to three lines alone, or two above a one-line secondary label.
 */
@Composable
fun TransformingLazyColumnItemScope.WearActionButton(
    transformationSpec: TransformationSpec,
    icon: ImageVector?,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    secondaryText: String? = null,
    colors: ButtonColors = wearButtonColors(),
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec)
            .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding),
        transformation = SurfaceTransformation(transformationSpec),
        colors = colors,
        icon = icon?.let { { Icon(it, contentDescription = null) } },
        secondaryLabel = secondaryText?.let { text -> { Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
    ) { Text(label, maxLines = if (secondaryText == null) 3 else 2, overflow = TextOverflow.Ellipsis) }
}

/**
 * Items are 4dp apart within a group; an empty item adds one more 4dp gap, giving the 8dp spacing
 * the Wear list guidance uses between groups of the same section.
 */
fun TransformingLazyColumnScope.wearGroupGap(key: Any) {
    item(key = key, contentType = "wear-group-gap") { Box(Modifier) }
}

/** Scale a custom or text-only item with the same height and visual spec as Wear Material items. */
@Composable
fun TransformingLazyColumnItemScope.WearScaledItem(
    transformationSpec: TransformationSpec,
    modifier: Modifier = Modifier.fillMaxWidth(),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = Modifier.graphicsLayer {
            with(transformationSpec) { applyContainerTransformation(scrollProgress) }
        }.then(modifier.transformedHeight(this, transformationSpec)).graphicsLayer {
            with(transformationSpec) { applyContentTransformation(scrollProgress) }
        },
        content = content,
    )
}
