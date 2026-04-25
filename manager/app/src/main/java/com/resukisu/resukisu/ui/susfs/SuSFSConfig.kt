package com.resukisu.resukisu.ui.susfs

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.resukisu.resukisu.R
import com.resukisu.resukisu.ui.component.SwipeableSnackbarHost
import com.resukisu.resukisu.ui.component.WarningCard
import com.resukisu.resukisu.ui.component.settings.AppBackButton
import com.resukisu.resukisu.ui.component.settings.SettingsBaseWidget
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

private enum class SuSFSTab(val titleRes: Int) {
    Basic(R.string.susfs_tab_basic_settings),
    SusPaths(R.string.susfs_tab_sus_paths),
    SusLoopPaths(R.string.susfs_tab_sus_loop_paths),
    SusMaps(R.string.susfs_tab_sus_maps),
    Kstat(R.string.susfs_tab_kstat_config),
    Features(R.string.susfs_tab_enabled_features),
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SuSFSConfigScreen() {
    val viewModel = viewModel<SuSFSScreenViewModel>()
    val uiState = viewModel.uiState
    val navigator = LocalNavigator.current
    val snackBarHost = LocalSnackbarHost.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    var addPathTarget by remember { mutableStateOf<AddPathTarget?>(null) }
    var showUnameDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(SuSFSTab.Basic) }

    LaunchedEffect(viewModel.toastMessage) {
        val message = viewModel.toastMessage ?: return@LaunchedEffect
        snackBarHost.showSnackbar(message)
        viewModel.consumeToastMessage()
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                modifier = Modifier.haze(scrollBehavior.state.collapsedFraction),
                title = {
                    Text(text = stringResource(R.string.susfs_config_title))
                },
                navigationIcon = {
                    AppBackButton(
                        onClick = { navigator.pop() }
                    )
                },
                actions = {
                    IconButton(
                        enabled = !uiState.commandRunning,
                        onClick = { viewModel.refresh() }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.refresh)
                        )
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
        },
        snackbarHost = {
            SwipeableSnackbarHost(hostState = snackBarHost)
        },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) { innerPadding ->
        if (uiState.isLoading) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .hazeSource(),
                contentPadding = PaddingValues(top = innerPadding.calculateTopPadding())
            ) {
                item {
                    LoadingIndicator(
                        modifier = Modifier.padding(top = 48.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .hazeSource(),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + 5.dp,
                    start = 0.dp,
                    end = 0.dp,
                    bottom = innerPadding.calculateBottomPadding() + 15.dp
                )
            ) {
                if (uiState.loadError != null) {
                    item {
                        WarningCard(
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .padding(top = 8.dp, bottom = 12.dp),
                            message = uiState.loadError,
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = CardConfig.cardAlpha),
                        )
                    }
                }

                item {
                    SplicedColumnGroup(
                        title = stringResource(R.string.susfs_config_title)
                    ) {
                        item {
                            SettingsBaseWidget(
                                icon = Icons.Filled.Info,
                                title = stringResource(R.string.susfs_config_description),
                                description = "/data/adb/ksu/.susfs.json",
                            ) {}
                        }
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
                            SettingsBaseWidget(
                                icon = Icons.Filled.VisibilityOff,
                                title = stringResource(R.string.susfs_hide_mounts_for_all_procs_label),
                                description = when {
                                    !uiState.hideMountsControlSupported -> {
                                        stringResource(R.string.feature_status_unsupported_summary)
                                    }
                                    uiState.hideSusMountsForAllProcs -> {
                                        stringResource(R.string.susfs_hide_mounts_for_all_procs_enabled_description)
                                    }
                                    else -> {
                                        stringResource(R.string.susfs_hide_mounts_for_all_procs_disabled_description)
                                    }
                                },
                                enabled = !uiState.commandRunning && uiState.hideMountsControlSupported,
                            ) {
                                Switch(
                                    checked = uiState.hideSusMountsForAllProcs,
                                    enabled = !uiState.commandRunning && uiState.hideMountsControlSupported,
                                    onCheckedChange = viewModel::setHideSusMountsForAllProcs,
                                )
                            }
                        }
                    }
                }

                item {
                    PrimaryScrollableTabRow(
                        selectedTabIndex = selectedTab.ordinal
                    ) {
                        SuSFSTab.entries.forEach { tab ->
                            Tab(
                                selected = selectedTab == tab,
                                onClick = { selectedTab = tab },
                                text = { Text(stringResource(tab.titleRes)) },
                            )
                        }
                    }
                }

                when (selectedTab) {
                    SuSFSTab.Basic -> {
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
                                            enabled = !uiState.commandRunning,
                                            onClick = { showUnameDialog = true }
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
                                    SettingsBaseWidget(
                                        icon = Icons.Filled.Settings,
                                        title = stringResource(R.string.avc_log_spoofing),
                                        description = stringResource(R.string.avc_log_spoofing_description),
                                        enabled = !uiState.commandRunning,
                                    ) {
                                        Switch(
                                            checked = uiState.avcLogSpoofing,
                                            enabled = !uiState.commandRunning,
                                            onCheckedChange = viewModel::setAvcLogSpoofing
                                        )
                                    }
                                }
                                item {
                                    SettingsBaseWidget(
                                        icon = Icons.Filled.Delete,
                                        title = stringResource(R.string.susfs_reset_to_default),
                                        description = null,
                                        enabled = !uiState.commandRunning,
                                        onClick = {
                                            viewModel.setUnameAndBuildTime("", "")
                                        }
                                    ) {}
                                }
                            }
                        }
                    }

                    SuSFSTab.SusPaths -> {
                        item {
                            PathGroup(
                                title = stringResource(R.string.susfs_tab_sus_paths),
                                addTitle = stringResource(R.string.susfs_add_sus_path),
                                emptyText = stringResource(R.string.susfs_no_paths_configured),
                                paths = uiState.susPaths,
                                enabled = !uiState.commandRunning,
                                onAddClick = { addPathTarget = AddPathTarget.SusPath },
                                onDelete = viewModel::removeSusPath,
                            )
                        }
                    }

                    SuSFSTab.SusLoopPaths -> {
                        item {
                            PathGroup(
                                title = stringResource(R.string.susfs_tab_sus_loop_paths),
                                addTitle = stringResource(R.string.susfs_add_sus_loop_path),
                                emptyText = stringResource(R.string.susfs_no_loop_paths_configured),
                                paths = uiState.susLoopPaths,
                                enabled = !uiState.commandRunning,
                                onAddClick = { addPathTarget = AddPathTarget.SusLoopPath },
                                onDelete = viewModel::removeSusLoopPath,
                            )
                        }
                    }

                    SuSFSTab.SusMaps -> {
                        item {
                            PathGroup(
                                title = stringResource(R.string.susfs_tab_sus_maps),
                                addTitle = stringResource(R.string.susfs_add_sus_map),
                                emptyText = stringResource(R.string.susfs_no_sus_maps_configured),
                                paths = uiState.susMaps,
                                enabled = !uiState.commandRunning,
                                onAddClick = { addPathTarget = AddPathTarget.SusMap },
                                onDelete = viewModel::removeSusMap,
                            )
                        }
                    }

                    SuSFSTab.Kstat -> {
                        item {
                            PathGroup(
                                title = stringResource(R.string.kstat_path_management),
                                addTitle = stringResource(R.string.add_kstat_path_title),
                                emptyText = stringResource(R.string.no_kstat_config_message),
                                paths = uiState.kstatPaths,
                                enabled = !uiState.commandRunning,
                                onAddClick = { addPathTarget = AddPathTarget.KstatPath },
                                onDelete = viewModel::removeKstatPath,
                            )
                        }
                        item {
                            PathGroup(
                                title = stringResource(R.string.update),
                                addTitle = stringResource(R.string.update),
                                emptyText = stringResource(R.string.no_kstat_config_message),
                                paths = uiState.kstatUpdatedPaths,
                                enabled = !uiState.commandRunning,
                                onAddClick = { addPathTarget = AddPathTarget.KstatUpdate },
                                onDelete = viewModel::removeKstatUpdatePath,
                            )
                        }
                        item {
                            PathGroup(
                                title = stringResource(R.string.susfs_update_full_clone),
                                addTitle = stringResource(R.string.susfs_update_full_clone),
                                emptyText = stringResource(R.string.no_kstat_config_message),
                                paths = uiState.kstatFullClonePaths,
                                enabled = !uiState.commandRunning,
                                onAddClick = { addPathTarget = AddPathTarget.KstatFullClone },
                                onDelete = viewModel::removeKstatFullClonePath,
                            )
                        }
                        item {
                            StaticKstatGroup(
                                title = stringResource(R.string.static_kstat_config),
                                entries = uiState.staticKstatEntries,
                                enabled = !uiState.commandRunning,
                                onAddClick = { addPathTarget = AddPathTarget.KstatStatic },
                                onDelete = viewModel::removeStaticKstat,
                            )
                        }
                    }

                    SuSFSTab.Features -> {
                        item {
                            FeatureGroup(
                                features = uiState.featureStatus
                            )
                        }
                    }
                }
            }
        }
    }

    val currentTarget = addPathTarget
    if (currentTarget != null) {
        PathEditDialog(
            title = stringResource(currentTarget.titleRes),
            label = stringResource(currentTarget.labelRes),
            placeholder = stringResource(currentTarget.placeholderRes),
            onDismiss = { addPathTarget = null },
            onConfirm = { value ->
                when (currentTarget) {
                    AddPathTarget.SusPath -> viewModel.addSusPath(value)
                    AddPathTarget.SusLoopPath -> viewModel.addSusLoopPath(value)
                    AddPathTarget.SusMap -> viewModel.addSusMap(value)
                    AddPathTarget.KstatPath -> viewModel.addKstatPath(value)
                    AddPathTarget.KstatUpdate -> viewModel.addKstatUpdatePath(value)
                    AddPathTarget.KstatFullClone -> viewModel.addKstatFullClonePath(value)
                    AddPathTarget.KstatStatic -> viewModel.addStaticKstatPath(value)
                }
                addPathTarget = null
            }
        )
    }

    if (showUnameDialog) {
        UnameDialog(
            initialUname = uiState.unameValue,
            initialBuildTime = uiState.buildTimeValue,
            onDismiss = { showUnameDialog = false },
            onConfirm = { uname, buildTime ->
                viewModel.setUnameAndBuildTime(uname, buildTime)
                showUnameDialog = false
            }
        )
    }
}

@Composable
private fun PathGroup(
    title: String,
    addTitle: String,
    emptyText: String,
    paths: List<String>,
    enabled: Boolean,
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
                enabled = enabled,
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
                        enabled = enabled,
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
    enabled: Boolean,
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
                enabled = enabled,
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
                        enabled = enabled,
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
