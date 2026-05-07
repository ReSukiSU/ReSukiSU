package com.resukisu.resukisu.ui.screen.moduleRepo

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.resukisu.resukisu.R
import com.resukisu.resukisu.ui.activity.PermissionRequestInterface
import com.resukisu.resukisu.ui.component.ConfirmResult
import com.resukisu.resukisu.ui.component.GithubMarkdown
import com.resukisu.resukisu.ui.component.SwipeableSnackbarHost
import com.resukisu.resukisu.ui.component.rememberConfirmDialog
import com.resukisu.resukisu.ui.component.settings.AppBackButton
import com.resukisu.resukisu.ui.component.settings.SettingsBaseWidget
import com.resukisu.resukisu.ui.component.settings.SplicedColumnGroup
import com.resukisu.resukisu.ui.navigation.LocalNavigator
import com.resukisu.resukisu.ui.navigation.Navigator
import com.resukisu.resukisu.ui.navigation.Route
import com.resukisu.resukisu.ui.theme.CardConfig
import com.resukisu.resukisu.ui.theme.ThemeConfig
import com.resukisu.resukisu.ui.theme.blurEffect
import com.resukisu.resukisu.ui.theme.blurSource
import com.resukisu.resukisu.ui.util.LocalPermissionRequestInterface
import com.resukisu.resukisu.ui.util.LocalSnackbarHost
import com.resukisu.resukisu.ui.util.module.ReleaseAssetInfo
import com.resukisu.resukisu.ui.util.module.ReleaseInfo
import com.resukisu.resukisu.ui.viewmodel.ModuleRepoViewModel
import com.resukisu.resukisu.ui.viewmodel.formatFileSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * @author AlexLiuDev233
 * @date 2025/12/7
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OnlineModuleDetailScreen(module: ModuleRepoViewModel.RepoModule) {
    val navigator = LocalNavigator.current
    val snackBarHost = LocalSnackbarHost.current
    val topAppBarState = rememberTopAppBarState()
    val coroutineScope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)

    val tabTitles = listOf(stringResource(R.string.readme), stringResource(R.string.release), stringResource(R.string.info))
    val uriHandler = LocalUriHandler.current
    val pagerState = rememberPagerState(pageCount = { tabTitles.size })

    LaunchedEffect(Unit) {
        scrollBehavior.state.heightOffset =
            scrollBehavior.state.heightOffsetLimit
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier.blurEffect(
                )
            ) {
                LargeFlexibleTopAppBar(
                    title = { Text(module.moduleName) },
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
                                uriHandler.openUri("https://modules.kernelsu.org/module/${module.moduleId}")
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.OpenInBrowser,
                                contentDescription = stringResource(R.string.open_module_home_page),
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

                PrimaryTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor =
                        if (ThemeConfig.isEnableBlur)
                            Color.Transparent
                        else
                            MaterialTheme.colorScheme.surfaceContainer.copy(CardConfig.cardAlpha),
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
                    pagerState.currentPage != 0
                ) {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(0)
                    }
                }
            }
        },
        containerColor = Color.Transparent,
        contentColor =  MaterialTheme.colorScheme.onSurface,
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
                    0 -> ReadmeTab(module, scrollBehavior.nestedScrollConnection, innerPadding.calculateTopPadding())
                    1 -> ReleasesTab(
                        module,
                        scrollBehavior.nestedScrollConnection,
                        coroutineScope,
                        innerPadding.calculateTopPadding()
                    )
                    2 -> InfoTab(module, scrollBehavior.nestedScrollConnection, innerPadding.calculateTopPadding())
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun InfoTab(
    module: ModuleRepoViewModel.RepoModule,
    nestedScrollConnection: NestedScrollConnection,
    topPadding: Dp
) {
    val uriHandler = LocalUriHandler.current

    LazyColumn(modifier = Modifier
        .fillMaxSize()
        .padding(vertical = 16.dp)
        .nestedScroll(nestedScrollConnection)
    ) {
        item {
            Spacer(Modifier.height(topPadding))
        }
        item {
            SplicedColumnGroup(
                title = stringResource(R.string.author)
            ) {
                module.authorList.forEach { author ->
                    item {
                        SettingsBaseWidget(
                            icon = Icons.Default.Person,
                            onClick = {
                                uriHandler.openUri(author.link)
                            },
                            title = author.name
                        ) {
                            Icon(
                                modifier = Modifier.size(24.dp),
                                imageVector = Icons.Default.Link,
                                contentDescription = stringResource(R.string.author_link)
                            )
                        }
                    }
                }
            }
        }

        item {
            SplicedColumnGroup(
                title = stringResource(R.string.source_code)
            ) {
                item {
                    SettingsBaseWidget(
                        icon = Icons.Default.Code,
                        title = module.sourceUrl,
                        onClick = {
                            uriHandler.openUri(module.sourceUrl)
                        }
                    ) {}
                }
            }
        }
    }
}

@Composable
fun ReleasesTab(
    module: ModuleRepoViewModel.RepoModule,
    nestedScrollConnection: NestedScrollConnection,
    coroutineScope: CoroutineScope,
    topPadding: Dp
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(Modifier.height(topPadding))
        }
        items(
            items = module.releases,
            key = { it.tagName }
        ) {
            ReleaseCard(module, it, coroutineScope)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReadmeTab(
    module: ModuleRepoViewModel.RepoModule,
    nestedScrollConnection: NestedScrollConnection,
    topPadding: Dp
) {
    val loading = remember { mutableStateOf(true) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(Modifier.height(topPadding))
            }
            item {
                Surface(
                    modifier = Modifier
                        .padding(16.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(
                        alpha = CardConfig.cardAlpha
                    )
                ) {
                    GithubMarkdown(
                        content = module.readme,
                        backgroundColor = Color.Transparent,
                        loading = loading,
                        callerProvideLoadingIndicator = true
                    )

                    AnimatedVisibility(
                        visible = loading.value,
                        enter = expandVertically(
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            expandFrom = Alignment.Top // Unroll downwards like a blind
                        ) + fadeIn(
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                        ),
                        exit = shrinkVertically(
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            shrinkTowards = Alignment.Top // Roll up upwards
                        ) + fadeOut(
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                        )
                    ) {
                        Spacer(modifier = Modifier.fillParentMaxSize())
                    }
                }
            }
        }
        if (loading.value) {
            LoadingIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReleaseCard(
    module: ModuleRepoViewModel.RepoModule,
    release: ReleaseInfo,
    coroutineScope: CoroutineScope
) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val permissionRequestInterface = LocalPermissionRequestInterface.current
    val confirmInstallTitle =
        stringResource(R.string.confirm_install_module_title, module.moduleName)
    val confirmDialog = rememberConfirmDialog()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 12.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors().copy(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(
                alpha = CardConfig.cardAlpha
            )
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = release.name,
                    style = MaterialTheme.typography.bodyMediumEmphasized,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = release.publishedAt,
                    style = MaterialTheme.typography.bodySmallEmphasized,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(
                    top = 5.dp,
                    bottom = 5.dp
                )
            )
            CollapsibleContent(
                title = stringResource(R.string.show_detail_or_hide_detail),
                enter = EnterTransition.None
            ) {
                GithubMarkdown(
                    content = release.descriptionHTML,
                    backgroundColor = Color.Transparent,
                    callerProvideLoadingIndicator = true
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(
                    top = 5.dp,
                    bottom = 5.dp
                )
            )
            if (release.assets.isEmpty()) return@Card

            Column {
                release.assets.forEach { assetInfo ->
                    val onClick: () -> Unit = {
                        coroutineScope.launch {
                            val result = confirmDialog.awaitConfirm(
                                title = confirmInstallTitle,
                                html = true,
                                content = release.descriptionHTML
                            )

                            if (result == ConfirmResult.Canceled) return@launch

                            downloadAssetAndInstall(
                                context,
                                permissionRequestInterface,
                                module,
                                assetInfo,
                                navigator,
                                coroutineScope
                            )
                        }
                    }
                    SettingsBaseWidget(
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)),
                        title = assetInfo.name,
                        noVerticalPadding = true,
                        onClick = {
                            onClick()
                        },
                        iconPlaceholder = false,
                        description = stringResource(R.string.assert_support_content).format(
                            formatFileSize(assetInfo.size),
                            assetInfo.downloadCount
                        )
                    ) {
                        FilledTonalButton(
                            onClick = onClick,
                            contentPadding = ButtonDefaults.TextButtonContentPadding,
                        ) {
                            Icon(
                                modifier = Modifier.size(20.dp),
                                imageVector = Icons.Outlined.Download,
                                contentDescription = null
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CollapsibleContent(
    modifier: Modifier = Modifier,
    title: String,
    enter: EnterTransition = expandVertically() + fadeIn(),
    exit: ExitTransition = shrinkVertically() + fadeOut(),
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f)

    Column(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { expanded = !expanded }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmallEmphasized,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )

            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.rotate(rotation),
                tint = MaterialTheme.colorScheme.onBackground
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = enter,
            exit = exit
        ) {
            content()
        }
    }
}


@Composable
@Preview
fun ReleaseCardPreview() {
    val release = ReleaseInfo(
        name = "name",
        tagName = "tagName",
        publishedAt = "publishedAt",
        descriptionHTML = "descriptionHTML",
        assets = ArrayList<ReleaseAssetInfo>().apply {
            add(
                ReleaseAssetInfo(
                    name = "name",
                    downloadUrl = "downloadUrl",
                    size = 0,
                    downloadCount = 0
                )
            )
            add(
                ReleaseAssetInfo(
                    name = "name2",
                    downloadUrl = "downloadUrl2",
                    size = 0,
                    downloadCount = 0
                )
            )
        }
    )

    val fakeModule = initFakeRepoModuleForPreview()

    CompositionLocalProvider(
        LocalNavigator provides Navigator(Route.ModuleRepoDetail(fakeModule)),
        LocalPermissionRequestInterface provides object : PermissionRequestInterface {
            override fun requestPermission(
                permission: String,
                callback: (Boolean) -> Unit,
                requestDescription: String
            ) {
            }

            override fun requestPermissions(
                permissions: Array<String>,
                callback: (Map<String, @JvmSuppressWildcards Boolean>) -> Unit,
                requestDescription: Map<String, String>
            ) {
            }
        },
    ) {
        ReleaseCard(fakeModule, release, rememberCoroutineScope())
    }
}