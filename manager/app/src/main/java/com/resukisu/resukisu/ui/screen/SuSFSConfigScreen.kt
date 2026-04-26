package com.resukisu.resukisu.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults.rememberTooltipPositionProvider
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTooltipState
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.lifecycle.viewmodel.compose.viewModel
import com.resukisu.resukisu.R
import com.resukisu.resukisu.ui.component.DialogHandle
import com.resukisu.resukisu.ui.component.SwipeableSnackbarHost
import com.resukisu.resukisu.ui.component.WarningCard
import com.resukisu.resukisu.ui.component.rememberCustomDialog
import com.resukisu.resukisu.ui.component.settings.AppBackButton
import com.resukisu.resukisu.ui.component.settings.SettingsBaseWidget
import com.resukisu.resukisu.ui.component.settings.SettingsSwitchWidget
import com.resukisu.resukisu.ui.component.settings.SplicedColumnGroup
import com.resukisu.resukisu.ui.navigation.LocalNavigator
import com.resukisu.resukisu.ui.theme.CardConfig
import com.resukisu.resukisu.ui.theme.ThemeConfig
import com.resukisu.resukisu.ui.theme.haze
import com.resukisu.resukisu.ui.theme.hazeSource
import com.resukisu.resukisu.ui.util.LocalSnackbarHost
import com.resukisu.resukisu.ui.viewmodel.SuSFSFeatureStatus
import com.resukisu.resukisu.ui.viewmodel.SuSFSScreenViewModel
import com.resukisu.resukisu.ui.viewmodel.SuSFSStaticKstatEntry
import kotlinx.coroutines.launch

private enum class AddPathTarget(
    val titleRes: Int,
    val labelRes: Int,
    val placeholderRes: Int,
) {
    SusPath(
        R.string.susfs_add_sus_path,
        R.string.susfs_path_label,
        R.string.susfs_path_placeholder
    ),
    SusLoopPath(
        R.string.susfs_add_sus_loop_path,
        R.string.susfs_loop_path_label,
        R.string.susfs_loop_path_placeholder
    ),
    SusMap(
        R.string.susfs_add_sus_map,
        R.string.susfs_sus_map_label,
        R.string.susfs_sus_map_placeholder
    ),
    KstatPath(
        R.string.add_kstat_path_title,
        R.string.susfs_path_label,
        R.string.susfs_path_placeholder
    ),
    KstatUpdate(
        R.string.update,
        R.string.susfs_path_label,
        R.string.susfs_path_placeholder
    ),
    KstatFullClone(
        R.string.susfs_update_full_clone,
        R.string.susfs_path_label,
        R.string.susfs_path_placeholder
    ),
    KstatStatic(
        R.string.add_kstat_statically_title,
        R.string.susfs_path_label,
        R.string.susfs_path_placeholder
    )
}

private enum class SuSFSTab(
    val titleRes: Int,
    val content: @Composable (
        viewModel: SuSFSScreenViewModel,
        contentPadding: PaddingValues,
        nestedScrollConnection: NestedScrollConnection
    ) -> Unit
) {
    Basic(R.string.susfs_tab_basic_settings, { viewModel, padding, scroll ->
        BasicTab(viewModel, padding, scroll)
    }),
    SusPaths(R.string.susfs_tab_sus_paths, { viewModel, padding, scroll ->
        SuSPathTab(viewModel, padding, scroll)
    }),
    SusLoopPaths(R.string.susfs_tab_sus_loop_paths, { viewModel, padding, scroll ->
        SuSLoopPathTab(viewModel, padding, scroll)
    }),
    SusMaps(R.string.susfs_tab_sus_maps, { viewModel, padding, scroll ->
        SuSMapTab(viewModel, padding, scroll)
    }),
    Kstat(R.string.susfs_tab_kstat_config, { viewModel, padding, scroll ->
        SuSKstatTab(viewModel, padding, scroll)
    }),
    Features(R.string.susfs_tab_enabled_features, { viewModel, padding, scroll ->
        SuSFeaturesTab(viewModel, padding, scroll)
    }),
}

@Composable
private fun rememberPathEditDialog(
    target: AddPathTarget,
    viewModel: SuSFSScreenViewModel
): DialogHandle = rememberCustomDialog { dismiss ->
    PathEditDialog(
        title = stringResource(target.titleRes),
        label = stringResource(target.labelRes),
        placeholder = stringResource(target.placeholderRes),
        onDismiss = dismiss,
        onConfirm = { value ->
            when (target) {
                AddPathTarget.SusPath -> viewModel.addSusPath(value)
                AddPathTarget.SusLoopPath -> viewModel.addSusLoopPath(value)
                AddPathTarget.SusMap -> viewModel.addSusMap(value)
                AddPathTarget.KstatPath -> viewModel.addKstatPath(value)
                AddPathTarget.KstatUpdate -> viewModel.addKstatUpdatePath(value)
                AddPathTarget.KstatFullClone -> viewModel.addKstatFullClonePath(value)
                AddPathTarget.KstatStatic -> viewModel.addStaticKstatPath(value)
            }
            dismiss()
        }
    )
}

@Composable
private fun SuSFeaturesTab(
    viewModel: SuSFSScreenViewModel,
    contentPadding: PaddingValues,
    nestedScrollConnection: NestedScrollConnection
) {
    val uiState = viewModel.uiState

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection),
    ) {
        item {
            Spacer(modifier = Modifier.height(contentPadding.calculateTopPadding()))
        }
        item {
            FeatureGroup(features = uiState.featureStatus)
        }
        item {
            Spacer(modifier = Modifier.height(contentPadding.calculateBottomPadding()))
        }
    }
}

@Composable
private fun SuSKstatTab(
    viewModel: SuSFSScreenViewModel,
    contentPadding: PaddingValues,
    nestedScrollConnection: NestedScrollConnection
) {
    val uiState = viewModel.uiState
    val kstatPathEditDialog = rememberPathEditDialog(AddPathTarget.KstatPath, viewModel)
    val kstatUpdatePathDialog = rememberPathEditDialog(AddPathTarget.KstatUpdate, viewModel)
    val kstatFullClonePathDialog = rememberPathEditDialog(AddPathTarget.KstatFullClone, viewModel)
    val staticKstatPathEditDialog = rememberPathEditDialog(AddPathTarget.KstatStatic, viewModel)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection),
    ) {
        item {
            Spacer(modifier = Modifier.height(contentPadding.calculateTopPadding()))
        }
        item {
            PathGroup(
                title = stringResource(R.string.kstat_path_management),
                addTitle = stringResource(R.string.add_kstat_path_title),
                emptyText = stringResource(R.string.no_kstat_config_message),
                paths = uiState.kstatPaths,
                onAddClick = kstatPathEditDialog::show,
                onDelete = viewModel::removeKstatPath,
            )
        }
        item {
            PathGroup(
                title = stringResource(R.string.update),
                addTitle = stringResource(R.string.update),
                emptyText = stringResource(R.string.no_kstat_config_message),
                paths = uiState.kstatUpdatedPaths,
                onAddClick = kstatUpdatePathDialog::show,
                onDelete = viewModel::removeKstatUpdatePath,
            )
        }
        item {
            PathGroup(
                title = stringResource(R.string.susfs_update_full_clone),
                addTitle = stringResource(R.string.susfs_update_full_clone),
                emptyText = stringResource(R.string.no_kstat_config_message),
                paths = uiState.kstatFullClonePaths,
                onAddClick = kstatFullClonePathDialog::show,
                onDelete = viewModel::removeKstatFullClonePath,
            )
        }
        item {
            StaticKstatGroup(
                title = stringResource(R.string.static_kstat_config),
                entries = uiState.staticKstatEntries,
                onAddClick = staticKstatPathEditDialog::show,
                onDelete = viewModel::removeStaticKstat,
            )
        }
        item {
            Spacer(modifier = Modifier.height(contentPadding.calculateBottomPadding()))
        }
    }
}

@Composable
private fun SuSMapTab(
    viewModel: SuSFSScreenViewModel,
    contentPadding: PaddingValues,
    nestedScrollConnection: NestedScrollConnection
) {
    val uiState = viewModel.uiState
    val pathEditDialog = rememberPathEditDialog(AddPathTarget.SusMap, viewModel)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection),
    ) {
        item {
            Spacer(modifier = Modifier.height(contentPadding.calculateTopPadding()))
        }
        item {
            PathGroup(
                title = stringResource(R.string.susfs_tab_sus_maps),
                addTitle = stringResource(R.string.susfs_add_sus_map),
                emptyText = stringResource(R.string.susfs_no_sus_maps_configured),
                paths = uiState.susMaps,
                onAddClick = pathEditDialog::show,
                onDelete = viewModel::removeSusMap,
            )
        }
        item {
            Spacer(modifier = Modifier.height(contentPadding.calculateBottomPadding()))
        }
    }
}

@Composable
private fun SuSLoopPathTab(
    viewModel: SuSFSScreenViewModel,
    contentPadding: PaddingValues,
    nestedScrollConnection: NestedScrollConnection
) {
    val uiState = viewModel.uiState
    val pathEditDialog = rememberPathEditDialog(AddPathTarget.SusLoopPath, viewModel)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection),
    ) {
        item {
            Spacer(modifier = Modifier.height(contentPadding.calculateTopPadding()))
        }
        item {
            PathGroup(
                title = stringResource(R.string.susfs_tab_sus_loop_paths),
                addTitle = stringResource(R.string.susfs_add_sus_loop_path),
                emptyText = stringResource(R.string.susfs_no_loop_paths_configured),
                paths = uiState.susLoopPaths,
                onAddClick = pathEditDialog::show,
                onDelete = viewModel::removeSusLoopPath,
            )
        }
        item {
            Spacer(modifier = Modifier.height(contentPadding.calculateBottomPadding()))
        }
    }
}

@Composable
private fun SuSPathTab(
    viewModel: SuSFSScreenViewModel,
    contentPadding: PaddingValues,
    nestedScrollConnection: NestedScrollConnection
) {
    val uiState = viewModel.uiState
    val pathEditDialog = rememberPathEditDialog(AddPathTarget.SusPath, viewModel)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection),
    ) {
        item {
            Spacer(modifier = Modifier.height(contentPadding.calculateTopPadding()))
        }
        item {
            PathGroup(
                title = stringResource(R.string.susfs_tab_sus_paths),
                addTitle = stringResource(R.string.susfs_add_sus_path),
                emptyText = stringResource(R.string.susfs_no_paths_configured),
                paths = uiState.susPaths,
                onAddClick = pathEditDialog::show,
                onDelete = viewModel::removeSusPath,
            )
        }
        item {
            Spacer(modifier = Modifier.height(contentPadding.calculateBottomPadding()))
        }
    }
}

@Composable
private fun BasicTab(
    viewModel: SuSFSScreenViewModel,
    contentPadding: PaddingValues,
    nestedScrollConnection: NestedScrollConnection
) {
    val uiState = viewModel.uiState
    val unameDialog = rememberCustomDialog { dismiss ->
        UnameDialog(
            initialUname = uiState.unameValue,
            initialBuildTime = uiState.buildTimeValue,
            onDismiss = dismiss,
            onConfirm = { uname, buildTime ->
                viewModel.setUnameAndBuildTime(uname, buildTime)
                dismiss()
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection),
    ) {
        item {
            Spacer(modifier = Modifier.height(contentPadding.calculateTopPadding()))
        }
        item {
            SplicedColumnGroup(
                title = stringResource(R.string.susfs_basic_information)
            ) {
                item {
                    SettingsBaseWidget(
                        icon = Icons.Filled.Settings,
                        title = stringResource(R.string.susfs_tab_enabled_features),
                        description = if (uiState.enabled) {
                            stringResource(R.string.susfs_feature_enabled)
                        } else {
                            stringResource(R.string.susfs_feature_disabled)
                        }
                    ) {}
                }
                item {
                    SettingsBaseWidget(
                        icon = Icons.Filled.Storage,
                        title = stringResource(R.string.home_susfs_version),
                        description = uiState.versionText.ifBlank { stringResource(R.string.unknown) }
                    ) {}
                }
                item {
                    SettingsSwitchWidget(
                        icon = Icons.Filled.VisibilityOff,
                        title = stringResource(R.string.susfs_hide_mounts_for_nonsu_procs),
                        description = when {
                            !uiState.hideMountsControlSupported -> {
                                stringResource(R.string.feature_status_unsupported_summary)
                            }

                            else -> {
                                stringResource(R.string.susfs_hide_mounts_for_nonsu_procs_description)
                            }
                        },
                        checked = uiState.hideSuSMntsForNonSUProcs,
                        onCheckedChange = viewModel::setHideSusMountsForNonSUProcs
                    )
                }
            }
        }

        item {
            SplicedColumnGroup(
                title = stringResource(R.string.susfs_tab_basic_settings)
            ) {
                item {
                    SettingsBaseWidget(
                        icon = Icons.Filled.Edit,
                        title = stringResource(R.string.susfs_uname_label),
                        description = uiState.unameValue,
                    ) {
                        IconButton(
                            onClick = unameDialog::show
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = null
                            )
                        }
                    }
                }
                item {
                    SettingsBaseWidget(
                        icon = Icons.Filled.Edit,
                        title = stringResource(R.string.susfs_build_time_label),
                        description = uiState.buildTimeValue,
                    ) {}
                }
                item {
                    SettingsSwitchWidget(
                        icon = Icons.Filled.Settings,
                        title = stringResource(R.string.avc_log_spoofing),
                        description = stringResource(R.string.avc_log_spoofing_description),
                        checked = uiState.avcLogSpoofing,
                        onCheckedChange = viewModel::setAvcLogSpoofing
                    )
                }
                item {
                    SettingsBaseWidget(
                        icon = Icons.Filled.Delete,
                        title = stringResource(R.string.susfs_reset_to_default),
                        description = null,
                        onClick = {
                            viewModel.setUnameAndBuildTime("", "")
                        }
                    ) {}
                }
            }
        }
        item {
            Spacer(modifier = Modifier.height(contentPadding.calculateBottomPadding()))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SuSFSConfigScreen() {
    val viewModel = viewModel<SuSFSScreenViewModel>()
    val uiState = viewModel.uiState
    val navigator = LocalNavigator.current
    val snackBarHost = LocalSnackbarHost.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        rememberTopAppBarState(
            initialHeightOffset = -154f,
            initialHeightOffsetLimit = -154f // from debugger
        )
    )

    val pagerState = rememberPagerState(pageCount = { SuSFSTab.entries.size })
    val animationScope = rememberCoroutineScope()

    LaunchedEffect(viewModel.toastMessage) {
        val message = viewModel.toastMessage ?: return@LaunchedEffect
        snackBarHost.showSnackbar(message)
        viewModel.consumeToastMessage()
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier.haze(scrollBehavior.state.collapsedFraction),
            ) {
                LargeFlexibleTopAppBar(
                    title = {
                        Text(text = stringResource(R.string.susfs_config_title))
                    },
                    navigationIcon = {
                        AppBackButton(
                            onClick = { navigator.pop() }
                        )
                    },
                    actions = {
                        TooltipBox(
                            positionProvider = rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
                            tooltip = {
                                PlainTooltip {
                                    Text(
                                        text = stringResource(R.string.refresh),
                                    )
                                }
                            },
                            state = rememberTooltipState()
                        ) {
                            IconButton(
                                onClick = { viewModel.refresh() }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = stringResource(R.string.refresh)
                                )
                            }
                        }
                    },
                    windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp)),
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor =
                            if (ThemeConfig.backgroundImageLoaded) Color.Transparent
                            else MaterialTheme.colorScheme.surfaceContainer,
                        scrolledContainerColor =
                            if (ThemeConfig.backgroundImageLoaded) Color.Transparent
                            else MaterialTheme.colorScheme.surfaceContainer,
                    )
                )
                PrimaryScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor =
                        if (CardConfig.isCustomBackgroundEnabled)
                            Color.Transparent
                        else
                            MaterialTheme.colorScheme.surfaceContainer,
                    edgePadding = 0.dp
                ) {
                    SuSFSTab.entries.fastForEachIndexed { index, tab ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = {
                                animationScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            },
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            text = { Text(stringResource(tab.titleRes)) },
                        )
                    }
                }
            }
        },
        snackbarHost = {
            SwipeableSnackbarHost(hostState = snackBarHost)
        },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) { innerPadding ->
        BackHandler(
            pagerState.currentPage != 0
        ) {
            animationScope.launch {
                pagerState.animateScrollToPage(0)
            }
        }

        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(),
            ) {
                if (uiState.loadError != null) {
                    WarningCard(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .padding(
                                top = innerPadding.calculateTopPadding() + 8.dp,
                                bottom = 12.dp
                            ),
                        message = uiState.loadError,
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = CardConfig.cardAlpha),
                    )
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.Top
                ) { page ->
                    val tabPadding = PaddingValues(
                        top = if (uiState.loadError != null) 0.dp else innerPadding.calculateTopPadding() + 5.dp,
                        start = 0.dp,
                        end = 0.dp,
                        bottom = innerPadding.calculateBottomPadding() + 15.dp
                    )

                    SuSFSTab.entries[page].content(
                        viewModel,
                        tabPadding,
                        scrollBehavior.nestedScrollConnection
                    )
                }
            }
        }
    }
}

@Composable
private fun PathGroup(
    title: String,
    addTitle: String,
    emptyText: String,
    paths: List<String>,
    onAddClick: () -> Unit,
    onDelete: (String) -> Unit,
) {
    SplicedColumnGroup(
        title = title
    ) {
        item {
            SettingsBaseWidget(
                icon = Icons.Filled.Add,
                title = addTitle,
                description = null,
                onClick = { onAddClick() }
            ) {}
        }

        if (paths.isEmpty()) {
            item {
                SettingsBaseWidget(
                    icon = Icons.Filled.Info,
                    title = emptyText,
                    description = null,
                    enabled = false
                ) {}
            }
        }

        paths.forEach { path ->
            item(key = path) {
                SettingsBaseWidget(
                    icon = Icons.Filled.Folder,
                    title = path,
                    description = null,
                ) {
                    IconButton(
                        onClick = { onDelete(path) }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.delete),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StaticKstatGroup(
    title: String,
    entries: List<SuSFSStaticKstatEntry>,
    onAddClick: () -> Unit,
    onDelete: (SuSFSStaticKstatEntry) -> Unit,
) {
    SplicedColumnGroup(
        title = title
    ) {
        item {
            SettingsBaseWidget(
                icon = Icons.Filled.Add,
                title = stringResource(R.string.add_kstat_statically_title),
                description = null,
                onClick = { onAddClick() }
            ) {}
        }

        if (entries.isEmpty()) {
            item {
                SettingsBaseWidget(
                    icon = Icons.Filled.Info,
                    title = stringResource(R.string.no_kstat_config_message),
                    description = null,
                    enabled = false
                ) {}
            }
        }

        entries.forEach { entry ->
            item(key = "${entry.path}:${entry.ino}:${entry.dev}:${entry.size}") {
                SettingsBaseWidget(
                    icon = Icons.Filled.Folder,
                    title = entry.path,
                    description = entry.summary,
                ) {
                    IconButton(
                        onClick = { onDelete(entry) }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.delete),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureGroup(features: List<SuSFSFeatureStatus>) {
    SplicedColumnGroup(
        title = stringResource(R.string.susfs_tab_enabled_features)
    ) {
        if (features.isEmpty()) {
            item {
                SettingsBaseWidget(
                    icon = Icons.Filled.Info,
                    title = stringResource(R.string.susfs_no_features_found),
                    description = null,
                    enabled = false
                ) {}
            }
        }

        features.forEach { feature ->
            item(key = feature.key) {
                SettingsBaseWidget(
                    icon = Icons.Filled.Settings,
                    title = feature.title,
                    description = if (feature.enabled) {
                        stringResource(R.string.susfs_feature_enabled)
                    } else {
                        stringResource(R.string.susfs_feature_disabled)
                    },
                    enabled = false
                ) {}
            }
        }
    }
}

@Composable
private fun PathEditDialog(
    title: String,
    label: String,
    placeholder: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                placeholder = { Text(placeholder) },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                enabled = value.trim().isNotEmpty(),
                onClick = { onConfirm(value) }
            ) {
                Text(text = stringResource(R.string.add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun UnameDialog(
    initialUname: String,
    initialBuildTime: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var unameValue by remember(initialUname) { mutableStateOf(initialUname) }
    var buildTimeValue by remember(initialBuildTime) { mutableStateOf(initialBuildTime) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.susfs_config_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = unameValue,
                    onValueChange = { unameValue = it },
                    label = { Text(stringResource(R.string.susfs_uname_label)) },
                    placeholder = { Text(stringResource(R.string.susfs_uname_placeholder)) },
                    singleLine = true
                )
                OutlinedTextField(
                    modifier = Modifier.padding(top = 8.dp),
                    value = buildTimeValue,
                    onValueChange = { buildTimeValue = it },
                    label = { Text(stringResource(R.string.susfs_build_time_label)) },
                    placeholder = { Text(stringResource(R.string.susfs_build_time_placeholder)) },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(unameValue, buildTimeValue) }
            ) {
                Text(text = stringResource(R.string.susfs_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        }
    )
}