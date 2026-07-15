package com.resukisu.resukisu.ui.component.settings

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsTextFieldWidget(
    modifier: Modifier = Modifier,
    state: TextFieldState,
    onClick: (() -> Unit)? = null,
    title: String = "",
    error: String = "",
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    useLabelAsPlaceholder: Boolean = false,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    renderBackgroundBlur: Boolean = true,
    inputTransformation: InputTransformation? = null,
    textStyle: TextStyle = MaterialTheme.typography.bodyMediumEmphasized.copy(
        color = MaterialTheme.colorScheme.onSurface,
        fontFamily = MaterialTheme.typography.bodySmallEmphasized.fontFamily,
    ),
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onKeyboardAction: KeyboardActionHandler? = null,
    lineLimits: TextFieldLineLimits = TextFieldLineLimits.Default,
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    onTextLayout: (Density.(getResult: () -> TextLayoutResult?) -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
    outputTransformation: OutputTransformation? = null,
    cursorBrush: Brush = SolidColor(MaterialTheme.colorScheme.primary),
    scrollState: ScrollState = rememberScrollState(),
) {
    @Suppress("NAME_SHADOWING")
    val interactionSource = interactionSource ?: remember { MutableInteractionSource() }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val focused by interactionSource.collectIsFocusedAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val isImeVisible = WindowInsets.isImeVisible
    val coroutineScope = rememberCoroutineScope()

    val isClickableMode = onClick != null

    val hasFocusReassignBug = Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1
    var allowFocus by remember { mutableStateOf(!hasFocusReassignBug) }

    LaunchedEffect(pressed) {
        if (pressed && hasFocusReassignBug && !allowFocus) {
            allowFocus = true
        }
    }

    LaunchedEffect(allowFocus) {
        if (allowFocus && hasFocusReassignBug) {
            delay(100)
            focusRequester.requestFocus()
        }
    }

    LaunchedEffect(focused) {
        if (!focused && hasFocusReassignBug) {
            allowFocus = false
        }
    }
    LaunchedEffect(isImeVisible) {
        if (!isImeVisible && focused) {
            if (hasFocusReassignBug) {
                allowFocus = false
                delay(100)
                focusManager.clearFocus()
            } else {
                focusManager.clearFocus()
            }
        }
    }

    val currentOnTextLayout by rememberUpdatedState(onTextLayout)

    val showTitle = if (useLabelAsPlaceholder) state.text.isNotEmpty() else true
    val showPlaceholder = useLabelAsPlaceholder && state.text.isEmpty()

    fun onClickInternal() {
        if (onClick != null) {
            onClick()
            return
        }

        if (!readOnly && enabled) {
            focusRequester.requestFocus()
        }
    }

    SettingsBaseWidget(
        modifier = modifier,
        title = if (showTitle) title else null,
        icon = null,
        iconPlaceholder = false,
        renderBackgroundBlur = renderBackgroundBlur,
        leadingContent = leadingContent,
        onClick = {
            onClickInternal()
        },
        descriptionColumnContent = {
            BasicTextField(
                state = state,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .focusProperties {
                        canFocus = allowFocus && !isClickableMode
                    },
                enabled = enabled,
                readOnly = readOnly,
                textStyle = textStyle,
                cursorBrush = if (error.isBlank()) cursorBrush else SolidColor(MaterialTheme.colorScheme.error),
                keyboardOptions = keyboardOptions,
                onKeyboardAction = {
                    onKeyboardAction?.onKeyboardAction(it)
                    if (hasFocusReassignBug) {
                        coroutineScope.launch {
                            allowFocus = false
                            delay(100)
                            focusManager.clearFocus()
                        }
                    } else {
                        focusManager.clearFocus()
                    }
                },
                lineLimits = lineLimits,
                onTextLayout = currentOnTextLayout,
                interactionSource = interactionSource,
                inputTransformation = inputTransformation,
                outputTransformation = outputTransformation,
                scrollState = scrollState,
                decorator = { innerTextField ->
                    Column {
                        Box(
                            modifier = Modifier.clickable(
                                enabled = onClick != null || !focused
                            ) {
                                onClickInternal()
                            }
                        ) {
                            if (showPlaceholder) {
                                Text(
                                    text = title,
                                    style = textStyle,
                                    color = labelColor.copy(alpha = 0.6f),
                                )
                            }

                            if (error.isNotBlank() && !focused && state.text.isBlank()) {
                                Text(
                                    text = error,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            innerTextField()
                        }

                        AnimatedVisibility(
                            visible = focused,
                            enter = expandHorizontally(
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                expandFrom = Alignment.Start // Unroll downwards like a blind
                            ) + expandVertically(
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                expandFrom = Alignment.Top // Unroll downwards like a blind
                            ),
                            exit = shrinkHorizontally(
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                shrinkTowards = Alignment.Start // Roll up upwards
                            ) + shrinkVertically(
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                shrinkTowards = Alignment.Top // Unroll downwards like a blind
                            )
                        ) {
                            Spacer(modifier = Modifier.height(2.dp))

                            HorizontalDivider(
                                thickness = 2.dp,
                                color = when {
                                    error.isNotBlank() -> MaterialTheme.colorScheme.error
                                    !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                    else -> MaterialTheme.colorScheme.primary
                                }
                            )
                        }
                    }
                }
            )

            AnimatedVisibility(
                visible = error.isNotBlank() && (focused || state.text.isNotBlank()),
                enter = expandHorizontally(
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    expandFrom = Alignment.Start // Unroll downwards like a blind
                ) + expandVertically(
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    expandFrom = Alignment.Top // Unroll downwards like a blind
                ),
                exit = shrinkHorizontally(
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    shrinkTowards = Alignment.Start // Roll up upwards
                ) + shrinkVertically(
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    shrinkTowards = Alignment.Top // Unroll downwards like a blind
                )
            ) {
                Text(
                    modifier = Modifier.padding(top = 2.dp),
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        trailingContent = {
            trailingContent?.invoke()
        }
    )
}
