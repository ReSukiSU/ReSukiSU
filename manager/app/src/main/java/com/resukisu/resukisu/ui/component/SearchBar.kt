package com.resukisu.resukisu.ui.component

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.resukisu.resukisu.ui.component.settings.AppBackButton
import com.resukisu.resukisu.ui.theme.CardConfig
import com.resukisu.resukisu.ui.theme.ThemeConfig
import com.resukisu.resukisu.ui.theme.blurEffect
import com.resukisu.resukisu.ui.theme.renderBackgroundBlur
import com.resukisu.resukisu.ui.util.LocalPagerPage
import com.resukisu.resukisu.ui.util.LocalSelectedPage
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
    private var isUserScrollInProgress = false
    private var ignoreCurrentScroll = false

    val searchBarExpandedFraction: Float
        get() = (1f + searchBarHeightOffset / searchBarHeight).coerceIn(0f, 1f)

    override val state = topAppBarScrollBehavior.state
    override val isPinned = topAppBarScrollBehavior.isPinned
    override val snapAnimationSpec: AnimationSpec<Float>?
        get() = topAppBarScrollBehavior.snapAnimationSpec
    override val flingAnimationSpec: DecayAnimationSpec<Float>?
        get() = topAppBarScrollBehavior.flingAnimationSpec

    fun expandSearchBar() {
        ignoreCurrentScroll = isUserScrollInProgress
        searchBarHeightOffset = 0f
        lastSearchBarScrollDelta = 0f
    }

    fun collapseSearchBar() {
        searchBarHeightOffset = -searchBarHeight
        lastSearchBarScrollDelta = 0f
        isUserScrollInProgress = false
        ignoreCurrentScroll = false
    }

    fun reset() {
        searchBarHeightOffset = 0f
        lastSearchBarScrollDelta = 0f
        isUserScrollInProgress = false
        ignoreCurrentScroll = false
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
                if (source == NestedScrollSource.UserInput) {
                    isUserScrollInProgress = true
                }
                if (ignoreCurrentScroll || available.y >= 0f) {
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

                if (ignoreCurrentScroll || available.y <= 0f) return topAppBarConsumed

                val remainingY = available.y - topAppBarConsumed.y
                val searchBarConsumed = consumeSearchBarScroll(remainingY)
                return topAppBarConsumed + Offset(0f, searchBarConsumed)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (ignoreCurrentScroll) {
                    return topAppBarScrollBehavior.nestedScrollConnection.onPreFling(available)
                }
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
                isUserScrollInProgress = false
                if (ignoreCurrentScroll) {
                    ignoreCurrentScroll = false
                    return topAppBarScrollBehavior.nestedScrollConnection.onPostFling(
                        consumed,
                        available,
                    )
                }

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
    val lifecycle = LocalLifecycleOwner.current.lifecycle
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
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                focusRequester.requestFocus()
            }
        }
    }

    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            allowFocus = true
            delay(100.milliseconds)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                focusRequester.requestFocus()
            }
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
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
    val pagerPage = LocalPagerPage.current
    val isCurrentPage = pagerPage == null || pagerPage == LocalSelectedPage.current
    val currentIsCurrentPage by rememberUpdatedState(isCurrentPage)
    val isPageActive = isCurrentPage && lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    val searchAppBarScrollBehavior = scrollBehavior as? SearchAppBarScrollBehavior
    val searchBarExpansionFraction =
        searchAppBarScrollBehavior?.searchBarExpandedFraction ?: 1f
    val isSearchBarCollapsing = searchBarExpansionFraction < 0.99f
    val isSearchBarCollapsed = searchBarExpansionFraction <= 0.01f
    var requestSearchFocus by remember { mutableStateOf(false) }
    val currentOnSearchTextChange by rememberUpdatedState(onSearchTextChange)
    val resetSearch by rememberUpdatedState {
        requestSearchFocus = false
        textFieldState.clearText()
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        currentOnSearchTextChange("")
    }

    LaunchedEffect(textFieldState.text) {
        currentOnSearchTextChange(textFieldState.text.toString())
    }

    LaunchedEffect(isPageActive) {
        if (!isPageActive && scrollBehavior?.state?.collapsedFraction?.toDouble() == 1.0) {
            searchAppBarScrollBehavior?.collapseSearchBar()
        }
    }

    LaunchedEffect(isSearchBarCollapsing, isPageActive) {
        if (isSearchBarCollapsing && isPageActive) {
            requestSearchFocus = false
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }

    DisposableEffect(isPageActive) {
        onDispose {
            if (!isPageActive) resetSearch()
        }
    }

    DisposableEffect(isPageActive, searchAppBarScrollBehavior) {
        onDispose {
            if (isPageActive) searchAppBarScrollBehavior?.reset()
        }
    }

    DisposableEffect(lifecycleOwner, searchAppBarScrollBehavior) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && currentIsCurrentPage) {
                resetSearch()
                searchAppBarScrollBehavior?.reset()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
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
                    if (themeConfig.isEnableBlur) Color.Transparent
                    else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = cardConfig.cardAlpha),
                scrolledContainerColor =
                    if (themeConfig.isEnableBlur) Color.Transparent
                    else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = cardConfig.cardAlpha),
            ),
        )

        AnimatedVisibility(
            visible = !isSearchBarCollapsed,
            enter = EnterTransition.None,
            exit = ExitTransition.None,
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
