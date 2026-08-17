package com.resukisu.resukisu.ui.screen.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.resukisu.resukisu.ui.activity.component.NavigationBar
import com.resukisu.resukisu.ui.rememberMaterial3BlurBackdrop
import com.resukisu.resukisu.ui.screen.BottomBarDestination
import com.resukisu.resukisu.ui.theme.ThemeConfig
import com.resukisu.resukisu.ui.theme.blurSource
import com.resukisu.resukisu.ui.util.LocalBlurState
import com.resukisu.resukisu.ui.util.LocalHandlePageChange
import com.resukisu.resukisu.ui.util.LocalPagerPage
import com.resukisu.resukisu.ui.util.LocalPagerState
import com.resukisu.resukisu.ui.util.LocalSelectedPage
import com.resukisu.resukisu.ui.util.LocalSnackbarHost
import com.resukisu.resukisu.ui.viewmodel.HomeViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel


@Composable
fun MainScreen() {
    val themeConfig: ThemeConfig = koinInject()
    val homeViewModel = koinViewModel<HomeViewModel>()
    val homeState by homeViewModel.state.collectAsStateWithLifecycle()
    val pages = remember(homeState.systemStatus.isValid) {
        BottomBarDestination.getPages(homeState.systemStatus.isValid)
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

    CompositionLocalProvider(
        LocalPagerState provides pagerState,
        LocalHandlePageChange provides handlePageChange,
        LocalSelectedPage provides uiSelectedPage
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            val isPortrait = maxWidth < maxHeight || (maxHeight / maxWidth > 1.4f)
            val content = @Composable { paddingBottom: Dp ->
                HorizontalPager(
                    modifier = Modifier
                        .fillMaxSize(),
                    state = pagerState,
                    userScrollEnabled = userScrollEnabled,
                    beyondViewportPageCount = 1,
                ) { pageIndex ->
                    if (pages.isEmpty()) return@HorizontalPager

                    val snackBarHostState = remember { SnackbarHostState() }
                    CompositionLocalProvider(
                        LocalSnackbarHost provides snackBarHostState,
                        LocalPagerPage provides pageIndex,
                        LocalBlurState provides rememberMaterial3BlurBackdrop(
                            enableBlur = themeConfig.isEnableBlur,
                            pagerState = pagerState,
                            pagerPage = pageIndex,
                        ),
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
                        NavigationBar(
                            destinations = pages,
                            isBottomBar = true,
                        )
                    },
                    containerColor = Color.Transparent,
                ) { innerPadding ->
                    Box(
                        modifier = Modifier.blurSource()
                    ) {
                        content(innerPadding.calculateBottomPadding())
                    }
                }
            } else {
                var navWidth by remember { mutableIntStateOf(0) }
                val density = LocalDensity.current

                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .blurSource()
                    ) {
                        Spacer(
                            modifier = Modifier.width(
                                with(density) { navWidth.toDp() }
                            )
                        )

                        Box(Modifier.weight(1f)) {
                            content(0.dp)
                        }
                    }

                    NavigationBar(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .onSizeChanged {
                                navWidth = it.width
                            },
                        destinations = pages,
                        isBottomBar = false,
                    )
                }
            }
        }
    }
}
