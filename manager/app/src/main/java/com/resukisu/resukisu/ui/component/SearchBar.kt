package com.resukisu.resukisu.ui.component

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults.inputFieldColors
import androidx.compose.material3.SearchBarDefaults.inputFieldShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.resukisu.resukisu.ui.component.settings.AppBackButton
import com.resukisu.resukisu.ui.theme.CardConfig
import com.resukisu.resukisu.ui.theme.ThemeConfig
import com.resukisu.resukisu.ui.theme.haze
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun Modifier.textFieldBackground(color: ColorProducer, shape: Shape): Modifier =
    this.drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        onDrawBehind { drawOutline(outline, color = color()) }
    }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CompactSearchBar(
    modifier: Modifier = Modifier,
    onSearch: (String) -> Unit,
    textFieldState: TextFieldState,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
    shape: Shape = inputFieldShape,
) {
    val focusManager = LocalFocusManager.current
    val interactionSource = interactionSource ?: remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val colors = inputFieldColors()
    val coroutineScope = rememberCoroutineScope()

    val isImeVisible = WindowInsets.isImeVisible
    val hasFocusReassignBug = Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1
    var allowFocus by remember { mutableStateOf(!hasFocusReassignBug) }
    val focusRequester = remember { FocusRequester() }

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

    BackHandler(enabled = textFieldState.text.isNotEmpty()) {
        textFieldState.clearText()
    }

    BasicTextField(
        state = textFieldState,
        modifier = modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(
                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = CardConfig.cardAlpha)
            )
            .heightIn(0.dp, 45.dp)
            .focusRequester(focusRequester)
            .focusProperties {
                canFocus = allowFocus
            },
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurface
        ),
        interactionSource = interactionSource,
        onKeyboardAction = {
            onSearch(textFieldState.text.toString())
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
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        lineLimits = TextFieldLineLimits.SingleLine,
        decorator = TextFieldDefaults.decorator(
            state = textFieldState,
            placeholder = placeholder,
            leadingIcon =
                leadingIcon?.let { leading ->
                    { Box(Modifier.offset(x = 4.dp)) { leading() } }
                },
            trailingIcon =
                trailingIcon?.let { trailing ->
                    { Box(Modifier.offset(x = (-4).dp)) { trailing() } }
                },
            colors = colors,
            contentPadding = PaddingValues(),
            container = {
                val containerColor =
                    animateColorAsState(
                        targetValue =
                            colors.containerColor(
                                enabled = true,
                                isError = false,
                                focused = focused,
                            ),
                        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                    )
                Box(Modifier.textFieldBackground(containerColor::value, shape))
            },
            enabled = true,
            lineLimits = TextFieldLineLimits.SingleLine,
            interactionSource = interactionSource,
            outputTransformation = null,
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchAppBar(
    modifier: Modifier = Modifier,
    title: String,
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    onBackClick: (() -> Unit)? = null,
    dropdownContent: @Composable (() -> Unit)? = null,
    navigationContent: @Composable (() -> Unit)? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    searchBarPlaceHolderText: String,
    haze: Boolean = true,
) {
    val textFieldState = rememberTextFieldState(initialText = searchText)
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(textFieldState.text) {
        onSearchTextChange(textFieldState.text.toString())
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (ThemeConfig.backgroundImageLoaded) Color.Transparent
                else MaterialTheme.colorScheme.surfaceContainer
            )
            .then(
                if (haze) {
                    Modifier.haze(
                        scrollBehavior?.state?.collapsedFraction ?: 1f
                    )
                } else Modifier
            )
    ) {
        LargeFlexibleTopAppBar(
            scrollBehavior = scrollBehavior,
            title = {
                Text(
                    text = title
                )
            },
            navigationIcon = {
                if (onBackClick != null) {
                    AppBackButton(
                        onClick = {
                            onBackClick.invoke()
                        }
                    )
                } else {
                    navigationContent?.invoke()
                }
            },
            actions = {
                dropdownContent?.invoke()
            },
            windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp)),
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor =
                    if (ThemeConfig.backgroundImageLoaded) Color.Transparent
                    else MaterialTheme.colorScheme.surfaceContainer,
                scrolledContainerColor =
                    if (ThemeConfig.backgroundImageLoaded) Color.Transparent
                    else MaterialTheme.colorScheme.surfaceContainer,
            ),
        )

        CompactSearchBar(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 0.dp),
            textFieldState = textFieldState,
            onSearch = {
                keyboardController?.hide()
            },
            placeholder = {
                Text(
                    text = searchBarPlaceHolderText,
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            leadingIcon = {
                Icon(
                    Icons.TwoTone.Search,
                    contentDescription = null,
                    modifier = Modifier.padding(start = 8.dp)
                )
            },
        )

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun SearchAppBarPreview() {
    SearchAppBar(
        title = "",
        searchText = "",
        onSearchTextChange = {},
        searchBarPlaceHolderText = "",
    )
}