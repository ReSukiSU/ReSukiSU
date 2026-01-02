package com.resukisu.resukisu.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AppBarWithSearch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarScrollBehavior
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.resukisu.resukisu.ui.theme.CardConfig
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@ExperimentalMaterial3Api
@Composable
fun pinnedScrollBehavior(
    canScroll: () -> Boolean = { true },
    snapAnimationSpec: AnimationSpec<Float> = MotionScheme.expressive().defaultEffectsSpec(),
    flingAnimationSpec: DecayAnimationSpec<Float> = rememberSplineBasedDecay(),
    reverseLayout: Boolean = false,
): SearchBarScrollBehavior =
    rememberSaveable(
        snapAnimationSpec,
        flingAnimationSpec,
        canScroll,
        reverseLayout,
        saver =
            PinnedScrollBehavior.Saver(
                canScroll = canScroll,
            ),
    ) {
        PinnedScrollBehavior(
            canScroll = canScroll,
        )
    }

@OptIn(ExperimentalMaterial3Api::class)
private class PinnedScrollBehavior(
    val canScroll: () -> Boolean,
) : SearchBarScrollBehavior {
    // Offset remains 0 so the bar never moves vertically
    override var scrollOffset by mutableFloatStateOf(0f)
    override var scrollOffsetLimit by mutableFloatStateOf(0f)

    // Track contentOffset to allow for tonal elevation/color changes on scroll
    override var contentOffset by mutableFloatStateOf(0f)

    override fun Modifier.searchBarScrollBehavior(): Modifier {
        // We remove the .layout { ... } and .draggable blocks
        // that were responsible for hiding/moving the bar.
        return this.onSizeChanged { size ->
            scrollOffsetLimit = -size.height.toFloat()
        }
    }

    override val nestedScrollConnection: NestedScrollConnection =
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!canScroll()) return Offset.Zero

                // We don't modify scrollOffset here because we want it pinned.
                // We only return Offset.Zero to show we aren't consuming any scroll.
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (!canScroll()) return Offset.Zero

                // Update contentOffset so the UI knows how far the user has scrolled
                // This is used for "overlapped" state (changing colors/elevation)
                contentOffset += consumed.y
                return Offset.Zero
            }
        }
    companion object {
        fun Saver(
            canScroll: () -> Boolean
        ): Saver<PinnedScrollBehavior, *> =
            listSaver(
                save = {
                    listOf(
                        it.scrollOffset,
                        it.scrollOffsetLimit,
                        it.contentOffset,
                    )
                },
                restore = {
                    PinnedScrollBehavior(
                        canScroll = canScroll,
                    )
                },
            )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchAppBar(
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    onBackClick: (() -> Unit)? = null,
    dropdownContent: @Composable (() -> Unit)? = null,
    navigationContent: @Composable (() -> Unit)? = null,
    scrollBehavior: SearchBarScrollBehavior? = null,
    searchBarPlaceHolderText: String
) {
    val textFieldState = rememberTextFieldState(
        initialText = searchText
    )
    val searchBarState = rememberSearchBarState()
    val state = rememberSearchBarState()
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var isFocused by remember { mutableStateOf(false) }

    BackHandler(enabled = isFocused || state.currentValue == SearchBarValue.Expanded) {
        if (state.currentValue == SearchBarValue.Expanded) {
            scope.launch { state.animateToCollapsed() }
        }
        keyboardController?.hide()
        focusManager.clearFocus()
    }

    val colorScheme = MaterialTheme.colorScheme
    val cardColor = if (CardConfig.isCustomBackgroundEnabled) colorScheme.surfaceContainerLow else colorScheme.background
    val cardAlpha = CardConfig.cardAlpha

    LaunchedEffect(textFieldState.text) {
        onSearchTextChange(textFieldState.text.toString())
    }

    DisposableEffect(Unit) { onDispose { keyboardController?.hide() } }

    AppBarWithSearch(
        state = state,
        inputField = {
            SearchBarDefaults.InputField(
                modifier = Modifier
                    .clip(SearchBarDefaults.inputFieldShape)
                    .onFocusChanged {
                        isFocused = it.isFocused
                    }
                    .padding(bottom = 5.dp),
                searchBarState = searchBarState,
                textFieldState = textFieldState,
                onSearch = { text ->
                    scope.launch { searchBarState.animateToCollapsed() }
                    onSearchTextChange(text)
                },
                colors = SearchBarDefaults.inputFieldColors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
                placeholder = {
                    if (searchBarState.currentValue == SearchBarValue.Collapsed || !isFocused) {
                        Text(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clearAndSetSemantics {},
                            text = searchBarPlaceHolderText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
        },
        navigationIcon = {
            if (onBackClick != null) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
                }
            } else {
                navigationContent?.invoke()
            }
        },
        actions = {
            dropdownContent?.invoke()
        },
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        scrollBehavior = scrollBehavior,
        colors = SearchBarDefaults.appBarWithSearchColors(
            searchBarColors = SearchBarDefaults.colors(
                containerColor = cardColor.copy(alpha = cardAlpha)
            ),
            scrolledSearchBarContainerColor = cardColor.copy(alpha = cardAlpha),
            appBarContainerColor = cardColor.copy(alpha = cardAlpha),
            scrolledAppBarContainerColor = cardColor.copy(alpha = cardAlpha)
        ),
        modifier = Modifier.statusBarsPadding()
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun SearchAppBarPreview() {
    var searchText by remember { mutableStateOf("") }
    SearchAppBar(
        searchText = "",
        onSearchTextChange = {},
        searchBarPlaceHolderText = ""
    )
}