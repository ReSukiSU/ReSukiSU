package com.resukisu.resukisu.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
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
import com.resukisu.resukisu.ui.theme.blurEffect
import com.resukisu.resukisu.ui.theme.blurSource
import com.resukisu.resukisu.ui.util.LocalSnackbarHost
import com.resukisu.resukisu.ui.viewmodel.SuSFSAppEntry
import com.resukisu.resukisu.ui.viewmodel.SuSFSFeatureStatus
import com.resukisu.resukisu.ui.viewmodel.SuSFSScreenViewModel
import com.resukisu.resukisu.ui.viewmodel.SuSFSStaticKstatEntry
import com.resukisu.resukisu.ui.viewmodel.SuperUserViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class AddPathTarget(
    val titleRes: Int,
    val labelRes: Int,
    val placeholderRes: Int,
    val multiline: Boolean = false,
) {
    SusPath(
        R.string.susfs_add_sus_path,
        R.string.susfs_path_label,
        R.string.susfs_path_placeholder,
        multiline = true
    ),
    SusLoopPath(
        R.string.susfs_add_sus_loop_path,
        R.string.susfs_loop_path_label,
        R.string.susfs_loop_path_placeholder,
        multiline = true
    ),
    SusMap(
        R.string.susfs_add_sus_map,
        R.string.susfs_sus_map_label,
        R.string.susfs_sus_map_placeholder
    ),
    KstatPath(
        R.string.add_kstat_path_title,
        R.string.susfs_path_label,
        R.string.susfs_path_placeholder,
        multiline = true
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
        nestedScrollConnection: NestedScrollConnection,
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
        multiline = target.multiline,
        onDismiss = dismiss,
        onConfirm = { value ->
            when (target) {
                AddPathTarget.SusPath -> viewModel.addSusPathEntries(value)
                AddPathTarget.SusLoopPath -> viewModel.addSusLoopPathEntries(value)
                AddPathTarget.SusMap -> viewModel.addSusMap(value)
                AddPathTarget.KstatPath -> viewModel.addKstatPathEntries(value)
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
    nestedScrollConnection: NestedScrollConnection,
) {
    val uiState = viewModel.uiState

    Column(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection),
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f),
        ) {
            item {
                Spacer(modifier = Modifier.height(contentPadding.calculateTopPadding()))
            }
            item {
                SplicedColumnGroup(
                    title = stringResource(R.string.susfs_tab_enabled_features)
                ) {
                    item {
                        SettingsBaseWidget(
                            icon = Icons.Filled.Info,
                            title = null,
                            description = stringResource(R.string.susfs_enabled_features_description),
                            enabled = false
                        ) {}
                    }
                }
            }
            item {
                FeatureGroup(viewModel = viewModel, features = uiState.featureStatus)
            }
        }
        BottomCenterOutlinedAction(
            modifier = Modifier.padding(bottom = contentPadding.calculateBottomPadding()),
            icon = Icons.Filled.Refresh,
            text = stringResource(R.string.refresh),
            onClick = { viewModel.refresh() },
        )
    }
}

@Composable
private fun SuSKstatTab(
    viewModel: SuSFSScreenViewModel,
    contentPadding: PaddingValues,
    nestedScrollConnection: NestedScrollConnection,
) {
    val uiState = viewModel.uiState
    var editingStaticKstatEntry by remember { mutableStateOf<SuSFSStaticKstatEntry?>(null) }
    val kstatPathEditDialog = rememberPathEditDialog(AddPathTarget.KstatPath, viewModel)
    val kstatUpdatePathDialog = rememberPathEditDialog(AddPathTarget.KstatUpdate, viewModel)
    val kstatFullClonePathDialog = rememberPathEditDialog(AddPathTarget.KstatFullClone, viewModel)
    val staticKstatPathEditDialog = rememberCustomDialog { dismiss ->
        StaticKstatEditDialog(
            initialEntry = editingStaticKstatEntry,
            onDismiss = dismiss,
            onConfirm = { path, ino, dev, nlink, size, atime, atimeNsec, mtime, mtimeNsec, ctime, ctimeNsec, blocks, blksize ->
                val editingEntry = editingStaticKstatEntry
                if (editingEntry == null) {
                    viewModel.addStaticKstatEntry(
                        path = path,
                        ino = ino,
                        dev = dev,
                        nlink = nlink,
                        size = size,
                        atime = atime,
                        atimeNsec = atimeNsec,
                        mtime = mtime,
                        mtimeNsec = mtimeNsec,
                        ctime = ctime,
                        ctimeNsec = ctimeNsec,
                        blocks = blocks,
                        blksize = blksize,
                    )
                } else {
                    viewModel.editStaticKstatEntry(
                        oldEntry = editingEntry,
                        path = path,
                        ino = ino,
                        dev = dev,
                        nlink = nlink,
                        size = size,
                        atime = atime,
                        atimeNsec = atimeNsec,
                        mtime = mtime,
                        mtimeNsec = mtimeNsec,
                        ctime = ctime,
                        ctimeNsec = ctimeNsec,
                        blocks = blocks,
                        blksize = blksize,
                    )
                }
                editingStaticKstatEntry = null
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
                title = stringResource(R.string.kstat_config_description_title)
            ) {
                item {
                    SettingsBaseWidget(
                        icon = Icons.Filled.Info,
                        title = null,
                        description = listOf(
                            stringResource(R.string.kstat_config_description_add_statically),
                            stringResource(R.string.kstat_config_description_add),
                            stringResource(R.string.kstat_config_description_update),
                            stringResource(R.string.kstat_config_description_update_full_clone)
                        ).joinToString("\n"),
                        enabled = false
                    ) {}
                }
            }
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
                onAddClick = {
                    editingStaticKstatEntry = null
                    staticKstatPathEditDialog.show()
                },
                onEdit = { entry ->
                    editingStaticKstatEntry = entry
                    staticKstatPathEditDialog.show()
                },
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
    nestedScrollConnection: NestedScrollConnection,
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
            SplicedColumnGroup(
                title = stringResource(R.string.sus_maps_description_title)
            ) {
                item {
                    SettingsBaseWidget(
                        icon = Icons.Filled.Security,
                        title = null,
                        description = stringResource(R.string.sus_maps_description_text),
                        enabled = false
                    ) {}
                }
            }
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
    nestedScrollConnection: NestedScrollConnection,
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
            SplicedColumnGroup(
                title = stringResource(R.string.sus_loop_paths_description_title)
            ) {
                item {
                    SettingsBaseWidget(
                        icon = Icons.Filled.Info,
                        title = null,
                        description = stringResource(R.string.sus_loop_paths_description_text),
                        enabled = false
                    ) {}
                }
            }
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
    nestedScrollConnection: NestedScrollConnection,
) {
    val uiState = viewModel.uiState
    val pathEditDialog = rememberPathEditDialog(AddPathTarget.SusPath, viewModel)
    var showAddAppDialog by remember { mutableStateOf(false) }
    var appEntries by remember { mutableStateOf<List<SuSFSAppEntry>>(emptyList()) }
    var appLoading by remember { mutableStateOf(false) }

    LaunchedEffect(showAddAppDialog) {
        if (showAddAppDialog && appEntries.isEmpty()) {
            appLoading = true
            appEntries = viewModel.loadSelectableApps()
            appLoading = false
        }
    }

    if (showAddAppDialog) {
        AddAppPathDialog(
            apps = appEntries,
            isLoading = appLoading,
            onDismiss = { showAddAppDialog = false },
            onConfirm = { packageNames ->
                viewModel.addAppPaths(packageNames)
                showAddAppDialog = false
            }
        )
    }

    val appPathRegex = remember { Regex(".*/Android/data/([^/]+)/?.*") }
    val uidPathRegex = remember { Regex("/sys/fs/cgroup(?:/[^/]+)*/uid_([0-9]+)") }
    val uidToPackage = remember(appEntries) {
        appEntries.associateBy { it.packageName }
    }
    val grouped = remember(uiState.susPaths, uidToPackage) {
        val appGroups = linkedMapOf<String, MutableList<String>>()
        val others = mutableListOf<String>()
        val packageToLabel = uidToPackage.mapValues { it.value.label }

        uiState.susPaths.forEach { path ->
            val pkgByData = appPathRegex.find(path)?.groupValues?.getOrNull(1)
            val pkg = pkgByData ?: run {
                val uid = uidPathRegex.find(path)?.groupValues?.getOrNull(1)
                if (uid != null) {
                    val found = SuperUserViewModel.apps.firstOrNull {
                        it.packageInfo.applicationInfo?.uid?.toString() == uid
                    }
                    found?.packageName
                } else {
                    null
                }
            }
            if (!pkg.isNullOrBlank()) {
                appGroups.getOrPut(pkg) { mutableListOf() } += path
            } else {
                others += path
            }
        }

        val appSection = appGroups.entries
            .map { entry ->
                val label = packageToLabel[entry.key] ?: entry.key
                label to entry.value.sorted()
            }
            .sortedBy { it.first.lowercase() }
        appSection to others.sorted()
    }
    val appGroups = grouped.first
    val otherPaths = grouped.second

    Column(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection),
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f),
        ) {
            item {
                Spacer(modifier = Modifier.height(contentPadding.calculateTopPadding()))
            }
            item {
                SplicedColumnGroup(title = stringResource(R.string.susfs_tab_sus_paths)) {
                    item {
                        SettingsBaseWidget(
                            icon = Icons.Filled.Apps,
                            title = stringResource(R.string.add_app_path),
                            description = null,
                            onClick = { showAddAppDialog = true }
                        ) {}
                    }
                    item {
                        SettingsBaseWidget(
                            icon = Icons.Filled.Add,
                            title = stringResource(R.string.add_custom_path),
                            description = null,
                            onClick = { pathEditDialog.show() }
                        ) {}
                    }
                }
            }
            if (appGroups.isNotEmpty()) {
                item {
                    SplicedColumnGroup(
                        title = stringResource(R.string.app_paths_section)
                    ) {
                        appGroups.forEach { (label, paths) ->
                            item(key = label) {
                                SettingsBaseWidget(
                                    icon = Icons.Filled.Apps,
                                    title = label,
                                    description = paths.joinToString("\n"),
                                ) {
                                    IconButton(onClick = { paths.forEach(viewModel::removeSusPath) }) {
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
            }
            item {
                PathGroup(
                    title = stringResource(R.string.other_paths_section),
                    addTitle = stringResource(R.string.susfs_add_sus_path),
                    emptyText = stringResource(R.string.susfs_no_paths_configured),
                    paths = otherPaths,
                    onAddClick = pathEditDialog::show,
                    onDelete = viewModel::removeSusPath,
                )
            }
        }
        BottomCenterOutlinedAction(
            modifier = Modifier.padding(bottom = contentPadding.calculateBottomPadding()),
            icon = Icons.Filled.Delete,
            text = stringResource(R.string.susfs_reset_paths_title),
            onClick = { viewModel.resetAllSusPaths() },
        )
    }
}

@Composable
private fun BasicTab(
    viewModel: SuSFSScreenViewModel,
    contentPadding: PaddingValues,
    nestedScrollConnection: NestedScrollConnection,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState = viewModel.uiState
    var showSlotDialog by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var backupFileToRestore by remember { mutableStateOf<String?>(null) }

    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val tempFile = File(
            context.cacheDir,
            "susfs_backup_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.json"
        )
        viewModel.backupCurrentConfig(tempFile.absolutePath) { ok, err ->
            if (!ok) {
                viewModel.postToast(err ?: context.getString(R.string.susfs_backup_failed, ""))
            } else {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        tempFile.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                    tempFile.delete()
                }
                viewModel.postToast(context.getString(R.string.susfs_backup_success, tempFile.name))
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val tempFile = File(context.cacheDir, "susfs_restore_temp.json")
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            viewModel.validateBackup(tempFile.absolutePath) { ok, err ->
                if (!ok) {
                    viewModel.postToast(err ?: context.getString(R.string.operation_failed))
                } else {
                    backupFileToRestore = tempFile.absolutePath
                    showRestoreConfirm = true
                }
            }
        }
    }

    if (showSlotDialog) {
        SlotInfoDialog(
            slotInfoList = viewModel.slotInfoList,
            currentActiveSlot = viewModel.currentActiveSlot,
            isLoading = viewModel.slotInfoLoading,
            onDismiss = { showSlotDialog = false },
            onRefresh = { viewModel.refreshSlotInfo() },
            onUseUname = {
                viewModel.useSlotUname(it)
                showSlotDialog = false
            },
            onUseBuildTime = {
                viewModel.useSlotBuildTime(it)
                showSlotDialog = false
            }
        )
    }

    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text(stringResource(R.string.susfs_restore_confirm_title)) },
            text = { Text(stringResource(R.string.susfs_restore_confirm_description)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val path = backupFileToRestore
                        if (path != null) {
                            viewModel.restoreFromBackupFile(path) { ok, err ->
                                if (!ok) {
                                    viewModel.postToast(err ?: context.getString(R.string.susfs_restore_failed, ""))
                                } else {
                                    viewModel.postToast(context.getString(R.string.susfs_restore_success, "", ""))
                                }
                            }
                        }
                        showRestoreConfirm = false
                    }
                ) { Text(stringResource(R.string.susfs_restore_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection),
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f),
        ) {
            item { Spacer(modifier = Modifier.height(contentPadding.calculateTopPadding())) }
            item {
                SplicedColumnGroup(
                    title = stringResource(R.string.susfs_config_description)
                ) {
                    item {
                        SettingsBaseWidget(
                            icon = Icons.Filled.Info,
                            title = stringResource(R.string.susfs_config_description),
                            description = stringResource(R.string.susfs_config_description_text),
                            enabled = false
                        ) {}
                    }
                }
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
                                else -> stringResource(R.string.susfs_hide_mounts_for_nonsu_procs_description)
                            },
                            checked = uiState.hideSuSMntsForNonSUProcs,
                            onCheckedChange = viewModel::setHideSusMountsForNonSUProcs
                        )
                    }
                    item {
                        SettingsBaseWidget(
                            icon = Icons.Filled.Info,
                            title = null,
                            description = stringResource(
                                R.string.susfs_hide_mounts_current_setting,
                                if (uiState.hideSuSMntsForNonSUProcs) {
                                    stringResource(R.string.susfs_hide_mounts_setting_all)
                                } else {
                                    stringResource(R.string.susfs_hide_mounts_setting_non_ksu)
                                }
                            ),
                            enabled = false
                        ) {}
                    }
                    item {
                        SettingsBaseWidget(
                            icon = Icons.Filled.Info,
                            title = null,
                            description = stringResource(R.string.susfs_hide_mounts_recommendation),
                            enabled = false
                        ) {}
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
                            IconButton(onClick = unameDialog::show) {
                                Icon(imageVector = Icons.Filled.Edit, contentDescription = null)
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
                }
            }

            item {
                SplicedColumnGroup(
                    title = stringResource(R.string.susfs_slot_info_title)
                ) {
                    item {
                        SettingsBaseWidget(
                            icon = Icons.Filled.Storage,
                            title = stringResource(R.string.susfs_slot_info_title),
                            description = stringResource(R.string.susfs_slot_info_description),
                            onClick = {
                                showSlotDialog = true
                                viewModel.refreshSlotInfo()
                            }
                        ) {}
                    }
                }
            }

            item {
                SplicedColumnGroup(
                    title = stringResource(R.string.avc_log_spoofing)
                ) {
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
                            icon = Icons.Filled.Info,
                            title = null,
                            description = stringResource(R.string.avc_log_spoofing_warning),
                            enabled = false
                        ) {}
                    }
                }
            }
            item {
                SettingsBaseWidget(
                    icon = Icons.Filled.Delete,
                    title = stringResource(R.string.susfs_reset_to_default),
                    description = null,
                    onClick = { viewModel.setUnameAndBuildTime("", "") }
                ) {}
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = {
                    val date = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    backupLauncher.launch("SuSFS_Config_$date.susfs_backup")
                }
            ) {
                Icon(imageVector = Icons.Filled.Backup, contentDescription = null)
                Text(
                    modifier = Modifier.padding(start = 6.dp),
                    text = stringResource(R.string.susfs_backup_title)
                )
            }
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = { restoreLauncher.launch(arrayOf("application/json", "*/*")) }
            ) {
                Icon(imageVector = Icons.Filled.Restore, contentDescription = null)
                Text(
                    modifier = Modifier.padding(start = 6.dp),
                    text = stringResource(R.string.restore)
                )
            }
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
                modifier = Modifier.blurEffect(),
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
                    .blurSource(),
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
                        scrollBehavior.nestedScrollConnection,
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomCenterOutlinedAction(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        OutlinedButton(
            shape = RoundedCornerShape(999.dp),
            onClick = onClick,
        ) {
            Icon(imageVector = icon, contentDescription = null)
            Text(
                modifier = Modifier.padding(start = 6.dp),
                text = text,
            )
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
    onEdit: (SuSFSStaticKstatEntry) -> Unit,
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
                    onClick = { onEdit(entry) }
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
private fun FeatureGroup(
    viewModel: SuSFSScreenViewModel,
    features: List<SuSFSFeatureStatus>,
) {
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
                var showLogConfigDialog by remember(feature.key) { mutableStateOf(false) }
                var logEnabled by remember(feature.key) { mutableStateOf(viewModel.uiState.susfsLogEnabled) }

                if (showLogConfigDialog && feature.configurable) {
                    AlertDialog(
                        onDismissRequest = { showLogConfigDialog = false },
                        title = { Text(stringResource(R.string.susfs_log_config_title)) },
                        text = {
                            LazyColumn(
                                modifier = Modifier
                                    .heightIn(max = 325.dp)
                            ) {
                                item {
                                    Text(
                                        text = stringResource(R.string.susfs_log_config_description),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                item {
                                    SettingsSwitchWidget(
                                        icon = Icons.Filled.Settings,
                                        title = stringResource(R.string.susfs_enable_log_label),
                                        checked = logEnabled,
                                        onCheckedChange = { logEnabled = it }
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    viewModel.setSusfsLogEnabled(logEnabled)
                                    showLogConfigDialog = false
                                }
                            ) {
                                Text(text = stringResource(R.string.susfs_apply))
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    logEnabled = viewModel.uiState.susfsLogEnabled
                                    showLogConfigDialog = false
                                }
                            ) {
                                Text(text = stringResource(R.string.cancel))
                            }
                        }
                    )
                }

                SettingsBaseWidget(
                    icon = Icons.Filled.Settings,
                    title = feature.title,
                    description = if (feature.enabled) {
                        stringResource(R.string.susfs_feature_enabled)
                    } else {
                        stringResource(R.string.susfs_feature_disabled)
                    },
                    enabled = feature.configurable,
                    onClick = {
                        if (feature.configurable) {
                            logEnabled = viewModel.uiState.susfsLogEnabled
                            showLogConfigDialog = true
                        }
                    },
                    descriptionColumnContent = {
                        if (feature.configurable) {
                            Text(
                                text = stringResource(R.string.susfs_feature_configurable),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
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
    multiline: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(
                modifier = Modifier
                    .heightIn(max = 325.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        label = { Text(label) },
                        placeholder = { Text(placeholder) },
                        singleLine = !multiline,
                        minLines = if (multiline) 4 else 1
                    )
                }
            }
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
            LazyColumn(
                modifier = Modifier
                    .heightIn(max = 325.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = unameValue,
                        onValueChange = { unameValue = it },
                        label = { Text(stringResource(R.string.susfs_uname_label)) },
                        placeholder = { Text(stringResource(R.string.susfs_uname_placeholder)) },
                        singleLine = true
                    )
                }

                item {
                    OutlinedTextField(
                        modifier = Modifier.padding(top = 8.dp),
                        value = buildTimeValue,
                        onValueChange = { buildTimeValue = it },
                        label = { Text(stringResource(R.string.susfs_build_time_label)) },
                        placeholder = { Text(stringResource(R.string.susfs_build_time_placeholder)) },
                        singleLine = true
                    )
                }
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

@Composable
private fun StaticKstatEditDialog(
    initialEntry: SuSFSStaticKstatEntry? = null,
    onDismiss: () -> Unit,
    onConfirm: (
        path: String,
        ino: String,
        dev: String,
        nlink: String,
        size: String,
        atime: String,
        atimeNsec: String,
        mtime: String,
        mtimeNsec: String,
        ctime: String,
        ctimeNsec: String,
        blocks: String,
        blksize: String,
    ) -> Unit,
) {
    var path by remember(initialEntry) { mutableStateOf(initialEntry?.path ?: "") }
    var ino by remember(initialEntry) { mutableStateOf(initialEntry?.ino ?: "") }
    var dev by remember(initialEntry) { mutableStateOf(initialEntry?.dev ?: "") }
    var nlink by remember(initialEntry) { mutableStateOf(initialEntry?.nlink ?: "") }
    var size by remember(initialEntry) { mutableStateOf(initialEntry?.size ?: "") }
    var atime by remember(initialEntry) { mutableStateOf(initialEntry?.atime ?: "") }
    var atimeNsec by remember(initialEntry) { mutableStateOf(initialEntry?.atimeNsec ?: "") }
    var mtime by remember(initialEntry) { mutableStateOf(initialEntry?.mtime ?: "") }
    var mtimeNsec by remember(initialEntry) { mutableStateOf(initialEntry?.mtimeNsec ?: "") }
    var ctime by remember(initialEntry) { mutableStateOf(initialEntry?.ctime ?: "") }
    var ctimeNsec by remember(initialEntry) { mutableStateOf(initialEntry?.ctimeNsec ?: "") }
    var blocks by remember(initialEntry) { mutableStateOf(initialEntry?.blocks ?: "") }
    var blksize by remember(initialEntry) { mutableStateOf(initialEntry?.blksize ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (initialEntry == null) {
                    stringResource(R.string.add_kstat_statically_title)
                } else {
                    stringResource(R.string.edit_kstat_statically_title)
                }
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .heightIn(max = 325.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = path,
                        onValueChange = { path = it },
                        label = { Text(stringResource(R.string.file_or_directory_path_label)) },
                        placeholder = { Text("/path/to/file_or_directory") },
                        singleLine = true
                    )
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }
                item { KstatPairField("ino", ino, { ino = it }, "dev", dev, { dev = it }) }
                item { KstatPairField("nlink", nlink, { nlink = it }, "size", size, { size = it }) }
                item { KstatPairField("atime", atime, { atime = it }, "atime_nsec", atimeNsec, { atimeNsec = it }) }
                item { KstatPairField("mtime", mtime, { mtime = it }, "mtime_nsec", mtimeNsec, { mtimeNsec = it }) }
                item { KstatPairField("ctime", ctime, { ctime = it }, "ctime_nsec", ctimeNsec, { ctimeNsec = it }) }
                item { KstatPairField("blocks", blocks, { blocks = it }, "blksize", blksize, { blksize = it }) }
                item {
                    Text(
                        modifier = Modifier.padding(top = 8.dp),
                        text = stringResource(R.string.hint_use_default_value),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = path.trim().isNotEmpty(),
                onClick = {
                    onConfirm(
                        path.trim(),
                        ino,
                        dev,
                        nlink,
                        size,
                        atime,
                        atimeNsec,
                        mtime,
                        mtimeNsec,
                        ctime,
                        ctimeNsec,
                        blocks,
                        blksize,
                    )
                }
            ) {
                Text(
                    text = if (initialEntry == null) {
                        stringResource(R.string.add)
                    } else {
                        stringResource(R.string.susfs_apply)
                    }
                )
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
private fun KstatPairField(
    leftLabel: String,
    leftValue: String,
    onLeftChange: (String) -> Unit,
    rightLabel: String,
    rightValue: String,
    onRightChange: (String) -> Unit,
    ) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        OutlinedTextField(
            value = leftValue,
            onValueChange = onLeftChange,
            label = { Text(leftLabel) },
            singleLine = true
        )
        OutlinedTextField(
            modifier = Modifier.padding(top = 8.dp),
            value = rightValue,
            onValueChange = onRightChange,
            label = { Text(rightLabel) },
            singleLine = true
        )
    }
}

@Composable
private fun AddAppPathDialog(
    apps: List<SuSFSAppEntry>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
) {
    var search by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }

    val filtered = remember(apps, search) {
        if (search.isBlank()) apps
        else apps.filter {
            it.label.contains(search, ignoreCase = true) || it.packageName.contains(search, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.susfs_add_app_path)) },
        text = {
            Column {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text(stringResource(R.string.search_apps)) },
                    leadingIcon = {
                        Icon(imageVector = Icons.Filled.Search, contentDescription = null)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .heightIn(max = 360.dp)
                ) {
                    items(filtered, key = { it.packageName }) { app ->
                        val checked = selected.contains(app.packageName)
                        SettingsBaseWidget(
                            icon = Icons.Filled.Apps,
                            title = app.label,
                            description = app.packageName,
                            onClick = {
                                selected = if (checked) selected - app.packageName else selected + app.packageName
                            }
                        ) {
                            if (checked) {
                                Icon(
                                    imageVector = Icons.Filled.Edit,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selected.isNotEmpty() && !isLoading,
                onClick = { onConfirm(selected.toList()) }
            ) {
                Text(stringResource(R.string.add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun SlotInfoDialog(
    slotInfoList: List<com.resukisu.resukisu.ui.viewmodel.SuSFSSlotInfo>,
    currentActiveSlot: String,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onUseUname: (String) -> Unit,
    onUseBuildTime: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.susfs_slot_info_title)) },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 420.dp)
            ) {
                item {
                    Text(
                        text = stringResource(R.string.susfs_current_active_slot, currentActiveSlot),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                if (slotInfoList.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.susfs_slot_info_unavailable),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    items(slotInfoList, key = { it.slotName }) { info ->
                        val currentBadge = if (info.slotName == currentActiveSlot) {
                            " (${stringResource(R.string.susfs_slot_current_badge)})"
                        } else {
                            ""
                        }
                        SettingsBaseWidget(
                            icon = Icons.Filled.Storage,
                            title = info.slotName + currentBadge,
                            description = "${stringResource(R.string.susfs_slot_uname, info.uname)}\n${stringResource(R.string.susfs_slot_build_time, info.buildTime)}",
                        ) {}
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                onClick = { onUseUname(info.uname) }
                            ) {
                                Text(stringResource(R.string.susfs_slot_use_uname))
                            }
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                onClick = { onUseBuildTime(info.buildTime) }
                            ) {
                                Text(stringResource(R.string.susfs_slot_use_build_time))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isLoading,
                onClick = onRefresh
            ) {
                Text(stringResource(R.string.refresh))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Icon(imageVector = Icons.Filled.Close, contentDescription = null)
                Text(
                    modifier = Modifier.padding(start = 6.dp),
                    text = stringResource(R.string.close)
                )
            }
        }
    )
}
