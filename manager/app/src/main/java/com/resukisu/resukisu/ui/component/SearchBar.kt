package com.resukisu.resukisu.ui.component

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.resukisu.resukisu.ui.component.settings.AppBackButton
import com.resukisu.resukisu.ui.theme.CardConfig
import com.resukisu.resukisu.ui.theme.ThemeConfig
import com.resukisu.resukisu.ui.theme.blurEffect
import com.resukisu.resukisu.ui.theme.renderBackgroundBlur
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

private val SearchBarExpandedHeight = 77.dp

@OptIn(ExperimentalMaterial3Api::class)
@Stable
class SearchAppBarScrollBehavior internal constructor(
    private val topAppBarScrollBehavior: TopAppBarScrollBehavior,
    private val searchBarHeight: Float,
) : TopAppBarScrollBehavior {
    private var searchBarHeightOffset by mutableFloatStateOf(0f)
    private var lastSearchBarScrollDelta = 0f

    val searchBarExpandedFraction: Float
        get() = (1f + searchBarHeightOffset / searchBarHeight).coerceIn(0f, 1f)

    override val state = topAppBarScrollBehavior.state
    override val isPinned = topAppBarScrollBehavior.isPinned
    override val snapAnimationSpec: AnimationSpec<Float>?
        get() = topAppBarScrollBehavior.snapAnimationSpec
    override val flingAnimationSpec: DecayAnimationSpec<Float>?
        get() = topAppBarScrollBehavior.flingAnimationSpec

    fun expandSearchBar() {
        searchBarHeightOffset = 0f
    }

    private fun consumeSearchBarScroll(delta: Float): Float {
        val previousOffset = searchBarHeightOffset
        searchBarHeightOffset = (searchBarHeightOffset + delta).coerceIn(-searchBarHeight, 0f)
        val consumed = searchBarHeightOffset - previousOffset
        if (consumed != 0f) lastSearchBarScrollDelta = consumed
        return consumed
    }

    private suspend fun animateSearchBarTo(targetOffset: Float, initialVelocity: Float = 0f) {
        if (searchBarHeightOffset == targetOffset) return

        AnimationState(
            initialValue = searchBarHeightOffset,
            initialVelocity = initialVelocity,
        ).animateTo(
            targetValue = targetOffset,
            animationSpec = snapAnimationSpec ?: spring(),
        ) {
            searchBarHeightOffset = value.coerceIn(-searchBarHeight, 0f)
        }
        lastSearchBarScrollDelta = 0f
    }

    override val nestedScrollConnection: NestedScrollConnection =
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y >= 0f) {
                    return topAppBarScrollBehavior.nestedScrollConnection.onPreScroll(
                        available,
                        source,
                    )
                }

                val previousOffset = searchBarHeightOffset
                consumeSearchBarScroll(available.y)
                if (previousOffset != searchBarHeightOffset) {
                    return available.copy(x = 0f)
                }

                return topAppBarScrollBehavior.nestedScrollConnection.onPreScroll(
                    available,
                    source,
                )
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                val topAppBarConsumed =
                    topAppBarScrollBehavior.nestedScrollConnection.onPostScroll(
                        consumed,
                        available,
                        source,
                    )

                if (available.y <= 0f) return topAppBarConsumed

                val remainingY = available.y - topAppBarConsumed.y
                val searchBarConsumed = consumeSearchBarScroll(remainingY)
                return topAppBarConsumed + Offset(0f, searchBarConsumed)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (available.y < 0f && searchBarHeightOffset > -searchBarHeight) {
                    animateSearchBarTo(
                        targetOffset = -searchBarHeight,
                        initialVelocity = available.y,
                    )
                    return available.copy(x = 0f)
                }

                return topAppBarScrollBehavior.nestedScrollConnection.onPreFling(available)
            }

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity,
            ): Velocity {
                if (searchBarHeightOffset > -searchBarHeight && searchBarHeightOffset < 0f) {
                    val shouldExpand =
                        available.y > 0f ||
                                (available.y == 0f && lastSearchBarScrollDelta > 0f)
                    animateSearchBarTo(
                        targetOffset = if (shouldExpand) 0f else -searchBarHeight,
                        initialVelocity = available.y,
                    )
                    return available.copy(x = 0f)
                }

                val topAppBarConsumed =
                    topAppBarScrollBehavior.nestedScrollConnection.onPostFling(
                        consumed,
                        available,
                    )
                val remainingY = available.y - topAppBarConsumed.y
                if (
                    remainingY > 0f &&
                    state.collapsedFraction < 0.01f &&
                    searchBarHeightOffset < 0f
                ) {
                    animateSearchBarTo(
                        targetOffset = 0f,
                        initialVelocity = remainingY,
                    )
                    return Velocity(topAppBarConsumed.x, available.y)
                }
                return topAppBarConsumed
            }
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberSearchAppBarScrollBehavior(
    topAppBarScrollBehavior: TopAppBarScrollBehavior,
): SearchAppBarScrollBehavior {
    val density = LocalDensity.current
    return remember(topAppBarScrollBehavior, density) {
        SearchAppBarScrollBehavior(
            topAppBarScrollBehavior = topAppBarScrollBehavior,
            searchBarHeight = with(density) { SearchBarExpandedHeight.toPx() },
        )
    }
}

private fun Modifier.textFieldBackground(color: ColorProducer, shape: Shape): Modifier =
    this.drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        onDrawBehind { drawOutline(outline, color = color()) }
    }

private fun Modifier.collapseWithTopAppBar(expandedFraction: Float): Modifier =
    clipToBounds().layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.copy(minHeight = 0))
        val fraction = expandedFraction.coerceIn(0f, 1f)
        val visibleHeight = (placeable.height * fraction).roundToInt()

        layout(placeable.width, visibleHeight) {
            placeable.placeRelative(
                x = 0,
                y = visibleHeight - placeable.height,
            )
        }
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
    requestFocus: Boolean = false,
    onFocusRequestHandled: () -> Unit = {},
) {
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
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
            delay(100.milliseconds)
            focusRequester.requestFocus()
        }
    }

    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            allowFocus = true
            delay(100.milliseconds)
            focusRequester.requestFocus()
            onFocusRequestHandled()
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
                delay(100.milliseconds)
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
            .background(
                if (themeConfig.isEnableBlurExp) Color.Transparent else
                    MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = cardConfig.cardAlpha)
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
                    delay(100.milliseconds)
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
    title: String,
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    onBackClick: (() -> Unit)? = null,
    dropdownContent: @Composable (() -> Unit)? = null,
    navigationContent: @Composable (() -> Unit)? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    searchBarPlaceHolderText: String,
) {
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
    val textFieldState = rememberTextFieldState(initialText = searchText)
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchAppBarScrollBehavior = scrollBehavior as? SearchAppBarScrollBehavior
    val searchBarExpansionFraction =
        searchAppBarScrollBehavior?.searchBarExpandedFraction ?: 1f
    val isSearchBarCollapsing = searchBarExpansionFraction < 0.99f
    val isSearchBarCollapsed = searchBarExpansionFraction <= 0.01f
    var requestSearchFocus by remember { mutableStateOf(false) }

    LaunchedEffect(textFieldState.text) {
        onSearchTextChange(textFieldState.text.toString())
    }

    LaunchedEffect(isSearchBarCollapsing) {
        if (isSearchBarCollapsing) {
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }

    Column {
        LargeFlexibleTopAppBar(
            modifier = Modifier.blurEffect(),
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
                AnimatedVisibility(
                    visible = isSearchBarCollapsed,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    IconButton(
                        onClick = {
                            searchAppBarScrollBehavior?.expandSearchBar()
                            requestSearchFocus = true
                        }
                    ) {
                        Icon(
                            imageVector = Icons.TwoTone.Search,
                            contentDescription = searchBarPlaceHolderText,
                        )
                    }
                }
                dropdownContent?.invoke()
            },
            windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp)),
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor =
                    if (themeConfig.isEnableBlurExp) Color.Transparent
                    else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = cardConfig.cardAlpha),
                scrolledContainerColor =
                    if (themeConfig.isEnableBlurExp) Color.Transparent
                    else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = cardConfig.cardAlpha),
            ),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(searchBarExpansionFraction)
                .collapseWithTopAppBar(searchBarExpansionFraction),
        ) {
            Column {
                CompactSearchBar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .padding(horizontal = 16.dp)
                        .clip(CircleShape)
                        .renderBackgroundBlur(MaterialTheme.colorScheme.surfaceContainerHighest),
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
                    requestFocus = requestSearchFocus,
                    onFocusRequestHandled = {
                        requestSearchFocus = false
                    },
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
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
