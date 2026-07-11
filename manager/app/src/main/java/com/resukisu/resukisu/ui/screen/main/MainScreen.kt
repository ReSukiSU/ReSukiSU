package com.resukisu.resukisu.ui.screen.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.resukisu.resukisu.ui.LocalMainPagerState
import com.resukisu.resukisu.ui.LocalUiMode
import com.resukisu.resukisu.ui.UiMode
import com.resukisu.resukisu.ui.activity.component.NavigationBar
import com.resukisu.resukisu.ui.component.bottombar.BottomBar
import com.resukisu.resukisu.ui.component.bottombar.SideRail
import com.resukisu.resukisu.ui.component.bottombar.rememberMainPagerState
import com.resukisu.resukisu.ui.rememberMaterial3BlurBackdrop
import com.resukisu.resukisu.ui.screen.BottomBarDestination
import com.resukisu.resukisu.ui.theme.LocalEnableFloatingBottomBar
import com.resukisu.resukisu.ui.theme.LocalEnableFloatingBottomBarBlur
import com.resukisu.resukisu.ui.theme.ThemeConfig
import com.resukisu.resukisu.ui.theme.blurSource
import com.resukisu.resukisu.ui.util.LocalBlurState
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import com.resukisu.resukisu.ui.util.LocalHandlePageChange
import com.resukisu.resukisu.ui.util.LocalPagerState
import com.resukisu.resukisu.ui.util.LocalSelectedPage
import com.resukisu.resukisu.ui.util.LocalSnackbarHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MainScreen() {
    var savedPages by rememberSaveable<MutableState<List<BottomBarDestination>>> {
        mutableStateOf(emptyList())
    }

    val pages by produceState(initialValue = savedPages) {
        value = withContext(Dispatchers.IO) {
            savedPages = BottomBarDestination.getPages()
            return@withContext savedPages
        }
    }

    val coroutineScope = rememberCoroutineScope()
    var uiSelectedPage by rememberSaveable { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(
        initialPage = uiSelectedPage,
        pageCount = { pages.size }
    )
    var userScrollEnabled by remember { mutableStateOf(true) }
    var animating by remember { mutableStateOf(false) }
    var animateJob by remember { mutableStateOf<Job?>(null) }
    var lastRequestedPage by remember { mutableIntStateOf(pagerState.currentPage) }

    val handlePageChange: (Int) -> Unit = remember(pagerState, coroutineScope) {
        { page ->
            uiSelectedPage = page
            if (page == pagerState.currentPage) {
                if (animateJob != null && lastRequestedPage != page) {
                    animateJob?.cancel()
                    animateJob = null
                    animating = false
                    userScrollEnabled = true
                }
                lastRequestedPage = page
            } else {
                if (animateJob != null && lastRequestedPage == page) {
                    // Already animating to the requested page
                } else {
                    animateJob?.cancel()
                    animating = true
                    userScrollEnabled = false
                    val job = coroutineScope.launch {
                        try {
                            pagerState.animateScrollToPage(page)
                        } finally {
                            if (animateJob === this) {
                                userScrollEnabled = true
                                animating = false
                                animateJob = null
                            }
                        }
                    }
                    animateJob = job
                    lastRequestedPage = page
                }
            }
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            if (!animating) uiSelectedPage = page
        }
    }

    BackHandler(pagerState.currentPage != 0) {
        handlePageChange(0)
    }

    val mainPagerState = rememberMainPagerState(pagerState)
    // Keep the Miuix nav bar's selection in sync when the page changes programmatically
    // (e.g. tapping the Superuser/Module cards on Home), not just via the bar itself.
    LaunchedEffect(mainPagerState) {
        snapshotFlow { pagerState.currentPage }.collect { mainPagerState.syncPage() }
    }

    CompositionLocalProvider(
        LocalPagerState provides pagerState,
        LocalHandlePageChange provides handlePageChange,
        LocalSelectedPage provides uiSelectedPage,
        LocalMainPagerState provides mainPagerState,
        // Floating bottom bar (+ liquid-glass blur) is opt-in via the Theme screen (Miuix only).
        LocalEnableFloatingBottomBar provides ThemeConfig.enableFloatingBottomBar,
        LocalEnableFloatingBottomBarBlur provides ThemeConfig.enableFloatingBottomBarBlur,
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            val isPortrait = maxWidth < maxHeight || (maxHeight / maxWidth > 1.4f)
            val isMiuixUi = LocalUiMode.current == UiMode.Miuix
            // The Miuix bars sample the pager content via a backdrop; without capturing the
            // pager into it, the plain nav bar renders transparent and the floating liquid-glass
            // bar renders as a solid surface colour. `miuixBlurBackdrop` frosts the plain nav bar;
            // `miuixBackdrop` (surface base + content, like tiann) feeds the liquid-glass bar.
            val miuixSurfaceColor = MaterialTheme.colorScheme.surface
            val miuixBlurBackdrop = rememberMaterial3BlurBackdrop(ThemeConfig.miuixEnableBlur)
            val miuixBackdrop = rememberLayerBackdrop {
                drawRect(miuixSurfaceColor)
                drawContent()
            }
            val floatingLiquid = ThemeConfig.enableFloatingBottomBar && ThemeConfig.enableFloatingBottomBarBlur
            val content = @Composable { paddingBottom: Dp ->
                HorizontalPager(
                    modifier = Modifier
                        .fillMaxSize()
                        .blurSource()
                        .then(
                            when {
                                !isMiuixUi -> Modifier
                                floatingLiquid -> Modifier.layerBackdrop(miuixBackdrop)
                                !ThemeConfig.enableFloatingBottomBar && miuixBlurBackdrop != null ->
                                    Modifier.layerBackdrop(miuixBlurBackdrop)
                                else -> Modifier
                            }
                        ),
                    state = pagerState,
                    userScrollEnabled = userScrollEnabled,
                    beyondViewportPageCount = 1,
                ) { pageIndex ->
                    if (pages.isEmpty()) return@HorizontalPager

                    val snackBarHostState = remember { SnackbarHostState() }
                    CompositionLocalProvider(
                        LocalSnackbarHost provides snackBarHostState,
                        LocalBlurState provides rememberMaterial3BlurBackdrop(ThemeConfig.isEnableBlur),
                    ) {
                        val destination = pages[pageIndex]
                        destination.direction(paddingBottom)
                    }
                }
            }

            if (isPortrait) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        if (isMiuixUi) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                BottomBar(
                                    blurBackdrop = miuixBlurBackdrop,
                                    backdrop = miuixBackdrop,
                                    modifier = Modifier.align(Alignment.BottomCenter),
                                )
                            }
                        } else {
                            NavigationBar(
                                destinations = pages,
                                isBottomBar = true,
                            )
                        }
                    },
                    containerColor = Color.Transparent,
                ) { innerPadding ->
                    content(innerPadding.calculateBottomPadding())
                }
            } else {
                Row(modifier = Modifier.fillMaxSize()) {
                    if (isMiuixUi) {
                        SideRail(blurBackdrop = miuixBlurBackdrop)
                    } else {
                        NavigationBar(
                            destinations = pages,
                            isBottomBar = false,
                        )
                    }
                    content(0.dp)
                }
            }
        }
    }
}
