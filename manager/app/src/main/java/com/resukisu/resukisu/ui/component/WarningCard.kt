package com.resukisu.resukisu.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.resukisu.resukisu.ui.component.settings.LocalSegmentedItemShape
import com.resukisu.resukisu.ui.component.settings.SettingsBaseWidget

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WarningCard(
    modifier: Modifier = Modifier,
    renderBackground: Boolean = true,
    shape: Shape = RoundedCornerShape(16.dp),
    message: String,
    content: (@Composable () -> Unit) = {},
    color: Color? = null,
    onClick: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null
) {
    WarningCardInner(
        modifier = modifier,
        renderBackground = renderBackground,
        shape = shape,
        content = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMediumEmphasized,
                modifier = Modifier
                    .wrapContentHeight(Alignment.CenterVertically)
            )
        },
        color = color,
        end = content,
        onClick = onClick,
        onClose = onClose,
        icon = icon
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WarningCard(
    modifier: Modifier = Modifier,
    renderBackground: Boolean = true,
    shape: Shape = RoundedCornerShape(16.dp),
    message: AnnotatedString,
    content: (@Composable () -> Unit) = {},
    color: Color? = null,
    onClick: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null
) {
    WarningCardInner(
        modifier = modifier,
        renderBackground = renderBackground,
        shape = shape,
        content = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMediumEmphasized,
                modifier = Modifier
                    .wrapContentHeight(Alignment.CenterVertically)
            )
        },
        color = color,
        end = content,
        onClick = onClick,
        onClose = onClose,
        icon = icon
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WarningCardInner(
    modifier: Modifier = Modifier,
    renderBackground: Boolean = true,
    shape: Shape = CardDefaults.elevatedShape,
    content: (@Composable () -> Unit),
    end: (@Composable () -> Unit),
    color: Color? = null,
    onClick: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null
) {
    CompositionLocalProvider(
        LocalSegmentedItemShape provides shape
    ) {
        SettingsBaseWidget(
            modifier = modifier,
            title = null,
            renderBackgroundBlur = renderBackground,
            containerColor = color ?: MaterialTheme.colorScheme.errorContainer,
            leadingContent = icon,
            foreContent = {
                content()
            },
            trailingContent = {
                onClose?.let {
                    Icon(
                        imageVector = Icons.TwoTone.Close,
                        contentDescription = stringResource(android.R.string.cancel),
                        modifier = Modifier
                            .clickable {
                                onClose()
                            }
                            .size(18.dp)
                            .align(Alignment.TopEnd)
                    )
                }

                end()
            },
            iconPlaceholder = false,
            onClick = {
                onClick?.invoke()
            }
        )
    }
}

@Preview
@Composable
private fun WarningCardPreview() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        WarningCard(message = "Warning message")
        WarningCard(message = "Warning message", onClose = {})
        WarningCard(
            message = "Warning message ",
            color = MaterialTheme.colorScheme.outlineVariant,
        ) {}
    }
}
