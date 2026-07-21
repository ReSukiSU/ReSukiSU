package com.resukisu.resukisu.ui.screen.susfs

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.resukisu.resukisu.R
import com.resukisu.resukisu.data.susfs.SuSFSConfigHelper
import com.resukisu.resukisu.ui.component.SwipeableSnackbarHost
import com.resukisu.resukisu.ui.component.settings.AppBackButton
import com.resukisu.resukisu.ui.navigation.LocalNavigator
import com.resukisu.resukisu.ui.screen.susfs.subpages.BackupRestoreTab
import com.resukisu.resukisu.ui.screen.susfs.subpages.OpenRedirectTab
import com.resukisu.resukisu.ui.screen.susfs.subpages.StandardFeaturesTab
import com.resukisu.resukisu.ui.screen.susfs.subpages.StatusTab
import com.resukisu.resukisu.ui.screen.susfs.subpages.SusKstatTab
import com.resukisu.resukisu.ui.screen.susfs.subpages.SusMapTab
import com.resukisu.resukisu.ui.screen.susfs.subpages.SusPathTab
import com.resukisu.resukisu.ui.theme.CardConfig
import com.resukisu.resukisu.ui.theme.ThemeConfig
import com.resukisu.resukisu.ui.theme.blurEffect
import com.resukisu.resukisu.ui.theme.blurSource
import com.resukisu.resukisu.ui.util.LocalSnackbarHost
import kotlinx.coroutines.launch

private class SuSFSConfigSubpage(
    val requirePersist: Boolean,
    val title: String,
    val content: @Composable (PaddingValues, NestedScrollConnection) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SuSFSConfigScreen() {
    val navigator = LocalNavigator.current
    val snackBarHost = LocalSnackbarHost.current
    val topAppBarState = rememberTopAppBarState()
    val coroutineScope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    var configEnabled by remember { mutableStateOf<Boolean?>(null) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)
    val operationFailedMsg = stringResource(R.string.susfs_operation_failed)
    val pullRefreshState = rememberPullToRefreshState()
    val refreshCoordinator = remember(coroutineScope) {
        SuSFSRefreshCoordinator(coroutineScope)
    }
    val onRegisterRefresh: SuSFSRefreshRegistrar = remember(refreshCoordinator) {
        refreshCoordinator::register
    }

    fun requestRefresh() {
        if (isRefreshing) return
        isRefreshing = true
        coroutineScope.launch {
            try {
                refreshCoordinator.refresh(forceRefresh = true) { config ->
                    configEnabled = config.enabled
                }
            } finally {
                isRefreshing = false
            }
        }
    }

    val handleConfigEnabledChange: (Boolean) -> Unit = { newValue ->
        coroutineScope.launch {
            if (SuSFSConfigHelper.setConfigEnabled(newValue)) {
                configEnabled = newValue
            } else {
                snackBarHost.showSnackbar(operationFailedMsg)
            }
        }
    }

    val subpages = listOf(
        SuSFSConfigSubpage(
            requirePersist = false,
            title = stringResource(R.string.susfs_tab_status),
        ) { contentPadding, nestedScrollConnection ->
            StatusTab(
                nestedScrollConnection = nestedScrollConnection,
                topPadding = contentPadding.calculateTopPadding(),
                onRegisterRefresh = onRegisterRefresh,
                configEnabled = configEnabled ?: false,
                configEnabledLoaded = configEnabled != null,
                onConfigEnabledChange = handleConfigEnabledChange,
            )
        },
        SuSFSConfigSubpage(
            requirePersist = true,
            title = stringResource(R.string.susfs_tab_standard),
        ) { contentPadding, nestedScrollConnection ->
            StandardFeaturesTab(
                nestedScrollConnection = nestedScrollConnection,
                topPadding = contentPadding.calculateTopPadding(),
                onRegisterRefresh = onRegisterRefresh,
            )
        },
        SuSFSConfigSubpage(
            requirePersist = true,
            title = stringResource(R.string.susfs_tab_backup_restore),
        ) { contentPadding, nestedScrollConnection ->
            BackupRestoreTab(
                nestedScrollConnection = nestedScrollConnection,
                topPadding = contentPadding.calculateTopPadding(),
                onConfigRestored = ::requestRefresh,
            )
        },
        SuSFSConfigSubpage(
            requirePersist = true,
            title = stringResource(R.string.susfs_tab_sus_path),
        ) { contentPadding, nestedScrollConnection ->
            SusPathTab(
                nestedScrollConnection = nestedScrollConnection,
                topPadding = contentPadding.calculateTopPadding(),
                onRegisterRefresh = onRegisterRefresh,
            )
        },
        SuSFSConfigSubpage(
            requirePersist = true,
            title = stringResource(R.string.susfs_tab_sus_kstat),
        ) { contentPadding, nestedScrollConnection ->
            SusKstatTab(
                nestedScrollConnection = nestedScrollConnection,
                topPadding = contentPadding.calculateTopPadding(),
                onRegisterRefresh = onRegisterRefresh,
            )
        },
        SuSFSConfigSubpage(
            requirePersist = true,
            title = stringResource(R.string.susfs_tab_open_redirect),
        ) { contentPadding, nestedScrollConnection ->
            OpenRedirectTab(
                nestedScrollConnection = nestedScrollConnection,
                topPadding = contentPadding.calculateTopPadding(),
                onRegisterRefresh = onRegisterRefresh,
            )
        },
        SuSFSConfigSubpage(
            requirePersist = true,
            title = stringResource(R.string.susfs_tab_sus_map),
        ) { contentPadding, nestedScrollConnection ->
            SusMapTab(
                nestedScrollConnection = nestedScrollConnection,
                topPadding = contentPadding.calculateTopPadding(),
                onRegisterRefresh = onRegisterRefresh,
            )
        },
    )
    val defaultPage = subpages.indexOfFirst { !it.requirePersist }.coerceAtLeast(0)
    val pagerState = rememberPagerState(
        initialPage = defaultPage,
        pageCount = { subpages.size },
    )
    val visibleSubpages = subpages.withIndex().filter { (_, subpage) ->
        !subpage.requirePersist || configEnabled == true
    }
    val selectedTabIndex = visibleSubpages
        .indexOfFirst { it.index == pagerState.currentPage }
        .coerceAtLeast(0)

    LaunchedEffect(Unit) {
        scrollBehavior.state.heightOffset = scrollBehavior.state.heightOffsetLimit
    }

    LaunchedEffect(refreshCoordinator) {
        refreshCoordinator.refresh(forceRefresh = false) { config ->
            configEnabled = config.enabled
        }
    }

    LaunchedEffect(configEnabled, defaultPage) {
        if (configEnabled == false && subpages[pagerState.currentPage].requirePersist) {
            pagerState.scrollToPage(defaultPage)
        }
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.blurEffect()) {
                LargeFlexibleTopAppBar(
                    title = { Text(stringResource(R.string.susfs_config_title)) },
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        AppBackButton(
                            onClick = {
                                navigator.pop()
                            }
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors().copy(
                        containerColor =
                            if (ThemeConfig.isEnableBlur)
                                Color.Transparent
                            else
                                MaterialTheme.colorScheme.surfaceContainer.copy(CardConfig.cardAlpha),
                        scrolledContainerColor =
                            if (ThemeConfig.isEnableBlur)
                                Color.Transparent
                            else
                                MaterialTheme.colorScheme.surfaceContainer.copy(CardConfig.cardAlpha)
                    ),
                    windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp)),
                )

                PrimaryScrollableTabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor =
                        if (ThemeConfig.isEnableBlur)
                            Color.Transparent
                        else
                            MaterialTheme.colorScheme.surfaceContainer.copy(CardConfig.cardAlpha),
                    edgePadding = 0.dp,
                    minTabWidth = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    subpages.forEachIndexed { index, subpage ->
                        val tabVisible = !subpage.requirePersist || configEnabled == true
                        AnimatedVisibility(
                            visible = tabVisible,
                            enter = fadeIn() + expandHorizontally(expandFrom = Alignment.Start),
                            exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.Start),
                        ) {
                            Tab(
                                selected = tabVisible && pagerState.currentPage == index,
                                onClick = {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(index)
                                    }
                                },
                                modifier = Modifier.widthIn(
                                    min = TabRowDefaults.ScrollableTabRowMinTabWidth
                                ),
                                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                text = { Text(subpage.title) }
                            )
                        }
                    }
                }

                BackHandler(
                    enabled = pagerState.currentPage != defaultPage
                ) {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(defaultPage)
                    }
                }
            }
        },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
        ),
        snackbarHost = { SwipeableSnackbarHost(hostState = snackBarHost) }
    ) { innerPadding ->
        PullToRefreshBox(
            state = pullRefreshState,
            isRefreshing = isRefreshing,
            onRefresh = ::requestRefresh,
            modifier = Modifier
                .fillMaxSize()
                .blurSource(),
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    modifier = Modifier
                        .padding(top = innerPadding.calculateTopPadding())
                        .align(Alignment.TopCenter),
                    state = pullRefreshState,
                    isRefreshing = isRefreshing,
                )
            },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .blurSource()
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = configEnabled == true,
                ) { page ->
                    subpages[page].content(innerPadding, scrollBehavior.nestedScrollConnection)
                }
            }
        }
    }
}
