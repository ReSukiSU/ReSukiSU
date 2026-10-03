package com.resukisu.resukisu.ui.wear.component

import androidx.wear.compose.material3.LocalContentColor
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

/**
 * A non-interactive information container using the Wear surface transformation.
 * A null [containerColor] uses the neutral surface; custom images belong to the page background.
 */
@Composable
fun TransformingLazyColumnItemScope.WearInfoCard(
    transformationSpec: TransformationSpec,
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
    border: BorderStroke? = null,
    shape: Shape = MaterialTheme.shapes.large,
    contentPadding: PaddingValues = PaddingValues(10.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(4.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val transformation = SurfaceTransformation(transformationSpec)
    val color = containerColor ?: MaterialTheme.colorScheme.surfaceContainer
    val background = remember(color) { ColorPainter(color) }
    val painter = remember(transformation, shape, background, border) {
        transformation.createContainerPainter(background, shape, border)
    }
    Column(
        modifier = Modifier
            .graphicsLayer { with(transformation) { applyContainerTransformation() } }
            .then(modifier.transformedHeight(this, transformationSpec))
            .fillMaxWidth()
            .drawBehind {
                with(painter) { draw(size) }
            }
            .graphicsLayer {
                this.shape = shape
                clip = true
                with(transformation) { applyContentTransformation() }
            }
            .padding(contentPadding),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

/** How a status card is colored: neutral surface, yellow warning or red error. */
enum class WearStatusTone { NEUTRAL, WARNING, ERROR }

/**
 * Wear Material 3 has no warning color role, so warnings use a fixed yellow with dark text
 * (about 12:1 contrast). Errors use the theme's error container pair.
 */
private val WarningContainer = Color(0xFFFFD54F)
private val OnWarningContainer = Color(0xFF231B00)

/** The container and content colors for [tone]; a null container keeps the neutral card surface. */
@Composable
fun wearStatusColors(tone: WearStatusTone): Pair<Color?, Color> = when (tone) {
    WearStatusTone.NEUTRAL -> null to MaterialTheme.colorScheme.onSurfaceVariant
    WearStatusTone.WARNING -> WarningContainer to OnWarningContainer
    WearStatusTone.ERROR -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
}

/**
 * A single message card for list empty states, read failures and warnings: by default a centered
 * 24dp icon above centered text, so the state reads at a glance on a round screen. With [centered]
 * false it keeps the compact paragraph layout (small icon beside start-aligned text) used by the Home
 * notices. Warnings and errors keep their yellow and red containers.
 */
@Composable
fun TransformingLazyColumnItemScope.WearStatusItem(
    transformationSpec: TransformationSpec,
    icon: ImageVector,
    message: String,
    modifier: Modifier = Modifier,
    tone: WearStatusTone = WearStatusTone.NEUTRAL,
    centered: Boolean = true,
) {
    val (container, content) = wearStatusColors(tone)
    val neutral = tone == WearStatusTone.NEUTRAL
    if (!centered) {
        WearInfoCard(transformationSpec, modifier.fillMaxWidth(), containerColor = container) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp),
                    tint = if (neutral) LocalContentColor.current else content)
                Spacer(Modifier.width(6.dp))
                Text(
                    message,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = content,
                )
            }
        }
        return
    }
    WearInfoCard(
        transformationSpec,
        modifier.fillMaxWidth(),
        containerColor = container,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (neutral) MaterialTheme.colorScheme.onSurfaceVariant else content,
            modifier = Modifier.size(24.dp).align(Alignment.CenterHorizontally))
        Text(message, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium,
            color = if (neutral) MaterialTheme.colorScheme.onSurface else content, textAlign = TextAlign.Center)
    }
}
