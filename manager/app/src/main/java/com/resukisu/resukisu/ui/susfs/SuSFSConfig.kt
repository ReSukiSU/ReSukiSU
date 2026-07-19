package com.resukisu.resukisu.ui.susfs

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.resukisu.resukisu.R
import com.resukisu.resukisu.data.susfs.SuSFSConfigHelper
import com.resukisu.resukisu.ui.component.SwipeableSnackbarHost
import com.resukisu.resukisu.ui.component.settings.AppBackButton
import com.resukisu.resukisu.ui.navigation.LocalNavigator
import com.resukisu.resukisu.ui.susfs.subpages.BackupRestoreTab
import com.resukisu.resukisu.ui.susfs.subpages.OpenRedirectTab
import com.resukisu.resukisu.ui.susfs.subpages.StandardFeaturesTab
import com.resukisu.resukisu.ui.susfs.subpages.StatusTab
import com.resukisu.resukisu.ui.susfs.subpages.SusKstatTab
import com.resukisu.resukisu.ui.susfs.subpages.SusMapTab
import com.resukisu.resukisu.ui.susfs.subpages.SusPathTab
import com.resukisu.resukisu.ui.theme.CardConfig
import com.resukisu.resukisu.ui.theme.ThemeConfig
import com.resukisu.resukisu.ui.theme.blurEffect
import com.resukisu.resukisu.ui.theme.blurSource
import com.resukisu.resukisu.ui.util.LocalSnackbarHost
import kotlinx.coroutines.launch

/**
 * SuSFS 配置界面主框架
 *
 * 采用 LargeFlexibleTopAppBar + PrimaryScrollableTabRow + HorizontalPager 结构，包含 7 个标签页：
 * - Standard: 标准功能（基本设置，起始页）
 * - SusPath: SUS Path
 * - SusKstat: SUS Kstat
 * - OpenRedirect: Open Redirect
 * - SusMap: SUS Map
 * - BackupRestore: Backup / Restore
 * - Status: 状态总览
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SuSFSConfigScreen() {
    val navigator = LocalNavigator.current
    val snackBarHost = LocalSnackbarHost.current
    val topAppBarState = rememberTopAppBarState()
    val coroutineScope = rememberCoroutineScope()
    var refreshToken by remember { mutableStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)

    val tabTitles = listOf(
        R.string.susfs_tab_standard,
        R.string.susfs_tab_sus_path,
        R.string.susfs_tab_sus_kstat,
        R.string.susfs_tab_open_redirect,
        R.string.susfs_tab_sus_map,
        R.string.susfs_tab_backup_restore,
        R.string.susfs_tab_status
    ).map { stringResource(it) }
    val pagerState = rememberPagerState(pageCount = { tabTitles.size })

    LaunchedEffect(Unit) {
        scrollBehavior.state.heightOffset = scrollBehavior.state.heightOffsetLimit
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
                    actions = {
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    isRefreshing = true
                                    try {
                                        SuSFSConfigHelper.refreshConfig()
                                        refreshToken += 1
                                    } finally {
                                        isRefreshing = false
                                    }
                                }
                            },
                            enabled = !isRefreshing
                        ) {
                            Icon(
                                imageVector = Icons.TwoTone.Refresh,
                                contentDescription = stringResource(R.string.susfs_refresh)
                            )
                        }
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
                    selectedTabIndex = pagerState.currentPage,
                    containerColor =
                        if (ThemeConfig.isEnableBlur)
                            Color.Transparent
                        else
                            MaterialTheme.colorScheme.surfaceContainer.copy(CardConfig.cardAlpha),
                    edgePadding = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    tabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            },
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            text = { Text(title) }
                        )
                    }
                }

                BackHandler(
                    enabled = pagerState.currentPage != 0
                ) {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(0)
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .blurSource()
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> StandardFeaturesTab(scrollBehavior.nestedScrollConnection, innerPadding.calculateTopPadding(), refreshToken)
                    1 -> SusPathTab(scrollBehavior.nestedScrollConnection, innerPadding.calculateTopPadding(), refreshToken)
                    2 -> SusKstatTab(scrollBehavior.nestedScrollConnection, innerPadding.calculateTopPadding(), refreshToken)
                    3 -> OpenRedirectTab(scrollBehavior.nestedScrollConnection, innerPadding.calculateTopPadding(), refreshToken)
                    4 -> SusMapTab(scrollBehavior.nestedScrollConnection, innerPadding.calculateTopPadding(), refreshToken)
                    5 -> BackupRestoreTab(scrollBehavior.nestedScrollConnection, innerPadding.calculateTopPadding())
                    6 -> StatusTab(scrollBehavior.nestedScrollConnection, innerPadding.calculateTopPadding(), refreshToken)
                }
            }
        }
    }
}
