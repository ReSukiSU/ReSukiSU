package com.resukisu.resukisu.ui.screen

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.resukisu.resukisu.R
import com.resukisu.resukisu.ui.component.ConfirmResult
import com.resukisu.resukisu.ui.component.SwipeableSnackbarHost
import com.resukisu.resukisu.ui.component.miuix.WarningCard
import com.resukisu.resukisu.ui.component.rememberConfirmDialog
import com.resukisu.resukisu.ui.navigation.LocalNavigator
import com.resukisu.resukisu.ui.theme.LocalEnableBlur
import com.resukisu.resukisu.ui.util.BlurredBar
import com.resukisu.resukisu.ui.util.LocalSnackbarHost
import com.resukisu.resukisu.ui.util.rememberBlurBackdrop
import com.resukisu.resukisu.ui.viewmodel.UmountManagerScreenViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * Miuix rendering of ReSukiSU's umount-path-manager screen. Reuses the same
 * UmountManagerScreenViewModel and the Material AddUmountPathDialog; only the
 * chrome/list is Miuix.
 */
@Composable
fun UmountManagerScreenMiuix() {
    val viewModel = viewModel<UmountManagerScreenViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val enableBlur = LocalEnableBlur.current
    val scrollBehavior = MiuixScrollBehavior()
    val snackBarHost = LocalSnackbarHost.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val confirmDialog = rememberConfirmDialog()
    var showAddDialog by remember { mutableStateOf(false) }
    val confirmDelete = stringResource(R.string.confirm_delete)

    LaunchedEffect(Unit) {
        viewModel.refreshData(context)
    }

    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface

    // Hide the FAB while scrolling down, reveal it while scrolling up (like the module list).
    val listState = rememberLazyListState()
    var fabVisible by remember { mutableStateOf(true) }
    var scrollDistance by remember { mutableFloatStateOf(0f) }
    val fabNestedScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val isScrolledToEnd =
                    listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ==
                        listState.layoutInfo.totalItemsCount - 1
                val delta = available.y
                if (!isScrolledToEnd) {
                    scrollDistance += delta
                    if (scrollDistance < -50f) {
                        if (fabVisible) fabVisible = false
                        scrollDistance = 0f
                    } else if (scrollDistance > 50f) {
                        if (!fabVisible) fabVisible = true
                        scrollDistance = 0f
                    }
                }
                return Offset.Zero
            }
        }
    }
    val fabOffset by animateDpAsState(
        targetValue = if (fabVisible) 0.dp else 180.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
        animationSpec = tween(durationMillis = 350),
        label = "umount fab offset"
    )

    Scaffold(
        topBar = {
            TopBar(
                title = stringResource(R.string.umount_path_manager),
                onBack = { navigator.pop() },
                scrollBehavior = scrollBehavior,
                backdrop = backdrop,
                barColor = barColor,
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.offset { IntOffset(x = 0, y = fabOffset.roundToPx()) },
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    tint = colorScheme.onPrimary,
                    contentDescription = null,
                )
            }
        },
        snackbarHost = { SwipeableSnackbarHost(hostState = snackBarHost) },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    InfiniteProgressIndicator()
                }
            } else {
                val pullToRefreshState = rememberPullToRefreshState()
                val refreshTexts = listOf(
                    stringResource(R.string.refresh_pulling),
                    stringResource(R.string.refresh_release),
                    stringResource(R.string.refresh_refresh),
                    stringResource(R.string.refresh_complete),
                )
                PullToRefresh(
                    isRefreshing = uiState.isRefreshing,
                    pullToRefreshState = pullToRefreshState,
                    onRefresh = {
                        viewModel.markUmountPathDirty()
                        viewModel.refreshData(context)
                    },
                    refreshTexts = refreshTexts,
                    contentPadding = PaddingValues(top = innerPadding.calculateTopPadding()),
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .scrollEndHaptic()
                            .nestedScroll(scrollBehavior.nestedScrollConnection)
                            .nestedScroll(fabNestedScroll)
                            .overScrollVertical(),
                        contentPadding = PaddingValues(
                            top = innerPadding.calculateTopPadding() + 6.dp,
                            bottom = innerPadding.calculateBottomPadding() + 88.dp,
                        ),
                        overscrollEffect = null,
                    ) {
                        item {
                            WarningCard(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                message = stringResource(R.string.changes_take_effect_immediately),
                                color = colorScheme.primaryContainer,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        if (uiState.umountPaths.isEmpty()) {
                            item {
                                WarningCard(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    message = stringResource(R.string.no_any_umount_path),
                                    color = colorScheme.secondaryContainer,
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }

                        items(uiState.umountPaths, key = { it.path }) { entry ->
                            val confirmDeleteSummary = stringResource(
                                R.string.confirm_delete_umount_path,
                                entry.path
                            )
                            val stateLabel = if (entry.persistent) {
                                stringResource(R.string.persistent)
                            } else {
                                stringResource(R.string.temporary)
                            }
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                            ) {
                                BasicComponent(
                                    title = entry.path,
                                    summary = "$stateLabel · ${entry.flagName}",
                                    startAction = {
                                        Icon(
                                            imageVector = Icons.Filled.Folder,
                                            tint = colorScheme.onSurface,
                                            modifier = Modifier.padding(end = 12.dp),
                                            contentDescription = null,
                                        )
                                    },
                                    endActions = {
                                        IconButton(
                                            onClick = {
                                                scope.launch {
                                                    val confirmResult = confirmDialog.awaitConfirm(
                                                        title = confirmDelete,
                                                        content = confirmDeleteSummary
                                                    )
                                                    if (confirmResult != ConfirmResult.Confirmed) return@launch
                                                    withContext(Dispatchers.IO) {
                                                        viewModel.removePath(entry, snackBarHost, context)
                                                    }
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Delete,
                                                tint = MaterialTheme.colorScheme.error,
                                                contentDescription = null,
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddUmountPathDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { path, flags ->
                showAddDialog = false
                uiState.umountPaths.filter { it.path == path }.forEach {
                    viewModel.removePath(entry = it, snackBarHost = null, context = null)
                }
                viewModel.addPath(
                    path = path,
                    flags = flags,
                    snackBarHost = snackBarHost,
                    context = context
                )
            }
        )
    }
}

@Composable
private fun TopBar(
    title: String,
    onBack: () -> Unit,
    scrollBehavior: ScrollBehavior,
    backdrop: LayerBackdrop?,
    barColor: Color,
) {
    BlurredBar(backdrop) {
        TopAppBar(
            color = barColor,
            title = title,
            navigationIcon = {
                IconButton(onClick = onBack) {
                    val layoutDirection = LocalLayoutDirection.current
                    Icon(
                        modifier = Modifier.graphicsLayer {
                            if (layoutDirection == LayoutDirection.Rtl) scaleX = -1f
                        },
                        imageVector = MiuixIcons.Back,
                        tint = colorScheme.onSurface,
                        contentDescription = null,
                    )
                }
            },
            scrollBehavior = scrollBehavior
        )
    }
}
