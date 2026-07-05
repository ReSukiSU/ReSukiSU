package com.resukisu.resukisu.ui.susfs.component

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.resukisu.resukisu.R
import com.resukisu.resukisu.data.susfs.OpenRedirectItem
import com.resukisu.resukisu.data.susfs.SuSFSConfig
import com.resukisu.resukisu.data.susfs.SuSFSConfigHelper
import com.resukisu.resukisu.data.susfs.SuSFSStatusInfo
import com.resukisu.resukisu.data.susfs.SusKstatItem
import com.resukisu.resukisu.data.susfs.SusPathItem
import com.resukisu.resukisu.data.susfs.UidScheme
import com.resukisu.resukisu.ui.component.BatchImportDialog
import com.resukisu.resukisu.ui.component.EmptyStateCard
import com.resukisu.resukisu.ui.component.EntryDetailDialog
import com.resukisu.resukisu.ui.component.ManualAddDialog
import com.resukisu.resukisu.ui.component.settings.SegmentedColumn
import com.resukisu.resukisu.ui.component.settings.SettingsBaseWidget
import com.resukisu.resukisu.ui.component.settings.SettingsJumpPageWidget
import com.resukisu.resukisu.ui.component.settings.SettingsSwitchWidget
import com.resukisu.resukisu.ui.component.settings.lazySegmentColumn
import com.resukisu.resukisu.ui.util.LocalSnackbarHost
import kotlinx.coroutines.launch

// ==================== StatusTab ====================

/**
 * 状态总览标签页
 *
 * 展示 SUSFS 的版本、变体与已启用功能。
 * 状态信息通过 SuSFSConfigHelper 缓存，页面级刷新会强制重新读取。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StatusTab(
    nestedScrollConnection: NestedScrollConnection,
    topPadding: Dp,
    refreshToken: Int
) {
    var statusInfo by remember { mutableStateOf(SuSFSStatusInfo("", "", "")) }
    var isLoading by remember { mutableStateOf(true) }

    suspend fun loadStatus(forceRefresh: Boolean = false) {
        statusInfo = SuSFSConfigHelper.loadStatusInfo(forceRefresh)
    }

    LaunchedEffect(refreshToken) {
        isLoading = true
        try {
            loadStatus(forceRefresh = refreshToken > 0)
        } finally {
            isLoading = false
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
    ) {
        item {
            Spacer(Modifier.height(topPadding))
        }

        if (isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator()
                }
            }
        } else {
            item {
                SegmentedColumn {
                    item {
                        SettingsBaseWidget(
                            iconPlaceholder = false,
                            title = stringResource(R.string.susfs_status_version),
                            description = statusInfo.version.ifBlank { stringResource(R.string.susfs_status_no_data) }
                        )
                    }
                    item {
                        SettingsBaseWidget(
                            iconPlaceholder = false,
                            title = stringResource(R.string.susfs_status_variant),
                            description = statusInfo.variant.ifBlank { stringResource(R.string.susfs_status_no_data) }
                        )
                    }
                    item {
                        SettingsBaseWidget(
                            iconPlaceholder = false,
                            title = stringResource(R.string.susfs_status_enabled_features),
                            description = statusInfo.enabledFeatures.ifBlank { stringResource(R.string.susfs_status_no_data) }
                        )
                    }
                }
            }
        }
    }
}

// ==================== StandardFeaturesTab ====================

/**
 * 标准功能标签页
 *
 * 通过 SuSFSConfigHelper.loadConfig() 读取配置，渲染：
 *   - logging / avc_log_spoofing / hide_sus_mnts_for_non_su_procs 三个开关
 *   - uname 编辑卡片（点击弹对话框编辑 version + release）
 *   - cmdline_or_bootconfig 编辑卡片（点击弹对话框编辑 path）
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StandardFeaturesTab(
    nestedScrollConnection: NestedScrollConnection,
    topPadding: Dp,
    refreshToken: Int
) {
    val snackbarHost = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()

    var config by remember { mutableStateOf<SuSFSConfig?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    var showUnameDialog by remember { mutableStateOf(false) }
    var showCmdlineDialog by remember { mutableStateOf(false) }
    var unameVersionInput by remember { mutableStateOf("") }
    var unameReleaseInput by remember { mutableStateOf("") }
    var cmdlineInput by remember { mutableStateOf("") }

    val operationFailedMsg = stringResource(R.string.susfs_operation_failed)

    LaunchedEffect(refreshToken) {
        isLoading = true
        try {
            config = SuSFSConfigHelper.loadConfig()
        } finally {
            isLoading = false
        }
    }

    val currentConfig = config

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
        ) {
            item {
                Spacer(Modifier.height(topPadding))
            }

            if (currentConfig != null) {
                item {
                    SegmentedColumn {
                        item {
                            SettingsSwitchWidget(
                                iconPlaceholder = false,
                                title = stringResource(R.string.susfs_standard_logging),
                                description = stringResource(R.string.susfs_standard_logging_desc),
                                checked = currentConfig.logging,
                                enabled = !isLoading,
                                onCheckedChange = { newValue ->
                                    scope.launch {
                                        isLoading = true
                                        val ok = SuSFSConfigHelper.enableLog(newValue)
                                        if (ok) {
                                            config = config?.copy(logging = newValue)
                                        } else {
                                            snackbarHost.showSnackbar(operationFailedMsg)
                                        }
                                        isLoading = false
                                    }
                                }
                            )
                        }

                        item {
                            SettingsSwitchWidget(
                                iconPlaceholder = false,
                                title = stringResource(R.string.susfs_standard_avc_log_spoofing),
                                description = stringResource(R.string.susfs_standard_avc_log_spoofing_desc),
                                checked = currentConfig.avc_log_spoofing,
                                enabled = !isLoading,
                                onCheckedChange = { newValue ->
                                    scope.launch {
                                        isLoading = true
                                        val ok = SuSFSConfigHelper.enableAvcLogSpoofing(newValue)
                                        if (ok) {
                                            config = config?.copy(avc_log_spoofing = newValue)
                                        } else {
                                            snackbarHost.showSnackbar(operationFailedMsg)
                                        }
                                        isLoading = false
                                    }
                                }
                            )
                        }

                        item {
                            SettingsSwitchWidget(
                                iconPlaceholder = false,
                                title = stringResource(R.string.susfs_standard_hide_sus_mnts),
                                description = stringResource(R.string.susfs_standard_hide_sus_mnts_desc),
                                checked = currentConfig.hide_sus_mnts_for_non_su_procs,
                                enabled = !isLoading,
                                onCheckedChange = { newValue ->
                                    scope.launch {
                                        isLoading = true
                                        val ok = SuSFSConfigHelper.hideSusMntsForNonSuProcs(newValue)
                                        if (ok) {
                                            config = config?.copy(hide_sus_mnts_for_non_su_procs = newValue)
                                        } else {
                                            snackbarHost.showSnackbar(operationFailedMsg)
                                        }
                                        isLoading = false
                                    }
                                }
                            )
                        }

                        item {
                            SettingsJumpPageWidget(
                                iconPlaceholder = false,
                                title = stringResource(R.string.susfs_standard_uname),
                                description = stringResource(
                                    R.string.susfs_standard_current_value,
                                    "${currentConfig.uname.version} / ${currentConfig.uname.release}"
                                ),
                                enabled = !isLoading,
                                onClick = {
                                    unameVersionInput = currentConfig.uname.version
                                    unameReleaseInput = currentConfig.uname.release
                                    showUnameDialog = true
                                }
                            )
                        }

                        item {
                            SettingsJumpPageWidget(
                                iconPlaceholder = false,
                                title = stringResource(R.string.susfs_standard_cmdline_or_bootconfig),
                                description = stringResource(
                                    R.string.susfs_standard_current_value,
                                    currentConfig.cmdline_or_bootconfig.ifBlank { "—" }
                                ),
                                enabled = !isLoading,
                                onClick = {
                                    cmdlineInput = currentConfig.cmdline_or_bootconfig
                                    showCmdlineDialog = true
                                }
                            )
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
            }
        }

        if (isLoading && currentConfig == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator()
            }
        }

        if (showUnameDialog) {
            AlertDialog(
                onDismissRequest = { showUnameDialog = false },
                title = {
                    Text(
                        text = stringResource(R.string.susfs_standard_uname),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = unameVersionInput,
                            onValueChange = { unameVersionInput = it },
                            label = { Text(stringResource(R.string.susfs_standard_uname_version)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )
                        OutlinedTextField(
                            value = unameReleaseInput,
                            onValueChange = { unameReleaseInput = it },
                            label = { Text(stringResource(R.string.susfs_standard_uname_release)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val v = unameVersionInput.trim()
                            val r = unameReleaseInput.trim()
                            scope.launch {
                                isLoading = true
                                val ok = SuSFSConfigHelper.setUname(v, r)
                                if (ok) {
                                    config = SuSFSConfigHelper.refreshConfig()
                                    showUnameDialog = false
                                } else {
                                    snackbarHost.showSnackbar(operationFailedMsg)
                                }
                                isLoading = false
                            }
                        },
                        enabled = !isLoading
                    ) {
                        Text(stringResource(R.string.susfs_save))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showUnameDialog = false }) {
                        Text(stringResource(R.string.susfs_entry_cancel))
                    }
                },
                shape = RoundedCornerShape(12.dp)
            )
        }

        if (showCmdlineDialog) {
            AlertDialog(
                onDismissRequest = { showCmdlineDialog = false },
                title = {
                    Text(
                        text = stringResource(R.string.susfs_standard_cmdline_or_bootconfig),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    OutlinedTextField(
                        value = cmdlineInput,
                        onValueChange = { cmdlineInput = it },
                        label = { Text(stringResource(R.string.susfs_standard_cmdline_path)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val p = cmdlineInput.trim()
                            scope.launch {
                                isLoading = true
                                val ok = SuSFSConfigHelper.setCmdlineOrBootconfig(p)
                                if (ok) {
                                    config = SuSFSConfigHelper.refreshConfig()
                                    showCmdlineDialog = false
                                } else {
                                    snackbarHost.showSnackbar(operationFailedMsg)
                                }
                                isLoading = false
                            }
                        },
                        enabled = !isLoading
                    ) {
                        Text(stringResource(R.string.susfs_save))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCmdlineDialog = false }) {
                        Text(stringResource(R.string.susfs_entry_cancel))
                    }
                },
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
}

// ==================== SusPathTab ====================

/**
 * SUS Path 标签页
 *
 * 渲染 sus_path / sus_path_loop 两类条目：
 *   - 两个批量导入入口（分别调用 addSusPath / addSusPathLoop）
 *   - 一个手动添加入口（通过子类型下拉选择 sus_path 或 sus_path_loop）
 *   - 条目列表（展示 path，is_loop=true 时附加 Loop 标签）
 *   - 点击条目弹出详情对话框，可删除（delSusPath）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SusPathTab(
    nestedScrollConnection: NestedScrollConnection,
    topPadding: Dp,
    refreshToken: Int
) {
    val snackbarHost = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()

    var entries by remember { mutableStateOf<Set<SusPathItem>>(emptySet()) }
    var isLoading by remember { mutableStateOf(false) }

    var showBatchImportPath by remember { mutableStateOf(false) }
    var showBatchImportLoop by remember { mutableStateOf(false) }
    var showManualAdd by remember { mutableStateOf(false) }
    var detailItem by remember { mutableStateOf<SusPathItem?>(null) }

    var selectedSubtype by remember { mutableStateOf("") }
    var manualPath by remember { mutableStateOf("") }

    val subtypePath = stringResource(R.string.susfs_path_subtype_path)
    val subtypeLoop = stringResource(R.string.susfs_path_subtype_loop)
    val subtypes = listOf(subtypePath, subtypeLoop)

    LaunchedEffect(refreshToken) {
        entries = SuSFSConfigHelper.loadConfig().sus_path
    }

    LaunchedEffect(showManualAdd) {
        if (showManualAdd) {
            if (selectedSubtype.isEmpty()) selectedSubtype = subtypePath
        } else {
            manualPath = ""
        }
    }

    val importHint = stringResource(R.string.susfs_entry_import_hint)
    val manualAddTitle = stringResource(R.string.susfs_entry_manual_add)
    val detailTitle = stringResource(R.string.susfs_entry_detail)
    val pathLabel = stringResource(R.string.susfs_entry_path_label)
    val isLoopLabel = stringResource(R.string.susfs_path_is_loop)
    val noEntriesMsg = stringResource(R.string.susfs_entry_no_entries)
    val operationFailedMsg = stringResource(R.string.susfs_operation_failed)
    val pathImportTitle = stringResource(R.string.susfs_entry_import_list, "sus_path")
    val loopImportTitle = stringResource(R.string.susfs_entry_import_list, "sus_path_loop")
    val importSuccessTemplate = stringResource(R.string.susfs_entry_import_success)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
    ) {
        item {
            Spacer(Modifier.height(topPadding))
        }

        item {
            SegmentedColumn {
                item {
                    SettingsJumpPageWidget(
                        iconPlaceholder = false,
                        title = pathImportTitle,
                        enabled = !isLoading,
                        onClick = { showBatchImportPath = true }
                    )
                }
                item {
                    SettingsJumpPageWidget(
                        iconPlaceholder = false,
                        title = loopImportTitle,
                        enabled = !isLoading,
                        onClick = { showBatchImportLoop = true }
                    )
                }
                item {
                    SettingsJumpPageWidget(
                        iconPlaceholder = false,
                        title = manualAddTitle,
                        enabled = !isLoading,
                        onClick = { showManualAdd = true }
                    )
                }
            }
        }

        if (entries.isEmpty()) {
            item {
                EmptyStateCard(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    message = noEntriesMsg
                )
            }
        } else {
            item { Spacer(Modifier.height(8.dp)) }
            lazySegmentColumn(
                items = entries.toList(),
                key = { _, it -> it.path }
            ) { _, item ->
                SettingsJumpPageWidget(
                    iconPlaceholder = false,
                    title = item.path,
                    description = if (item.is_loop) isLoopLabel else null,
                    enabled = !isLoading,
                    onClick = { detailItem = item }
                )
            }
        }
    }

    BatchImportDialog(
        showDialog = showBatchImportPath,
        title = pathImportTitle,
        hint = importHint,
        onDismiss = { showBatchImportPath = false },
        onConfirm = { lines ->
            scope.launch {
                isLoading = true
                var success = 0
                var failed = 0
                lines.forEach { line ->
                    if (SuSFSConfigHelper.addSusPath(line)) success++ else failed++
                }
                entries = SuSFSConfigHelper.refreshConfig().sus_path
                snackbarHost.showSnackbar(
                    importSuccessTemplate.format(success, failed)
                )
                isLoading = false
            }
            showBatchImportPath = false
        },
        isLoading = isLoading
    )

    BatchImportDialog(
        showDialog = showBatchImportLoop,
        title = loopImportTitle,
        hint = importHint,
        onDismiss = { showBatchImportLoop = false },
        onConfirm = { lines ->
            scope.launch {
                isLoading = true
                var success = 0
                var failed = 0
                lines.forEach { line ->
                    if (SuSFSConfigHelper.addSusPathLoop(line)) success++ else failed++
                }
                entries = SuSFSConfigHelper.refreshConfig().sus_path
                snackbarHost.showSnackbar(
                    importSuccessTemplate.format(success, failed)
                )
                isLoading = false
            }
            showBatchImportLoop = false
        },
        isLoading = isLoading
    )

    ManualAddDialog(
        showDialog = showManualAdd,
        title = manualAddTitle,
        subtypes = subtypes,
        selectedSubtype = selectedSubtype,
        onSubtypeChange = { selectedSubtype = it },
        onDismiss = { showManualAdd = false },
        onConfirm = {
            val path = manualPath.trim()
            if (path.isEmpty()) return@ManualAddDialog
            scope.launch {
                isLoading = true
                val ok = if (selectedSubtype == subtypeLoop) {
                    SuSFSConfigHelper.addSusPathLoop(path)
                } else {
                    SuSFSConfigHelper.addSusPath(path)
                }
                if (ok) {
                    entries = SuSFSConfigHelper.refreshConfig().sus_path
                    showManualAdd = false
                } else {
                    snackbarHost.showSnackbar(operationFailedMsg)
                }
                isLoading = false
            }
        },
        isLoading = isLoading,
        formContent = {
            OutlinedTextField(
                value = manualPath,
                onValueChange = { manualPath = it },
                label = { Text(pathLabel) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
            )
        }
    )

    detailItem?.let { item ->
        EntryDetailDialog(
            showDialog = true,
            title = detailTitle,
            fields = listOf(
                pathLabel to item.path,
                isLoopLabel to item.is_loop.toString()
            ),
            onDismiss = { detailItem = null },
            onDelete = {
                scope.launch {
                    isLoading = true
                    val ok = SuSFSConfigHelper.delSusPath(item.path)
                    if (ok) {
                        entries = SuSFSConfigHelper.refreshConfig().sus_path
                        detailItem = null
                    } else {
                        snackbarHost.showSnackbar(operationFailedMsg)
                    }
                    isLoading = false
                }
            },
            isLoading = isLoading
        )
    }
}

// ==================== SusKstatTab ====================

/**
 * SUS Kstat 标签页
 *
 * 渲染 sus_kstat 条目，支持三种 spoof_type：
 *   - Normal：批量导入 addSusKstat + updateSusKstat
 *   - FullClone：批量导入 addSusKstat + updateSusKstatFullClone
 *   - Statically：批量导入 addSusKstatStatically(path)（其余字段 null）；
 *     手动添加时可在表单中填写 12 个可选 stat 字段
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SusKstatTab(
    nestedScrollConnection: NestedScrollConnection,
    topPadding: Dp,
    refreshToken: Int
) {
    val snackbarHost = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()

    var entries by remember { mutableStateOf<Set<SusKstatItem>>(emptySet()) }
    var isLoading by remember { mutableStateOf(false) }

    var showBatchImportNormal by remember { mutableStateOf(false) }
    var showBatchImportFullClone by remember { mutableStateOf(false) }
    var showBatchImportStatically by remember { mutableStateOf(false) }
    var showManualAdd by remember { mutableStateOf(false) }
    var detailItem by remember { mutableStateOf<SusKstatItem?>(null) }

    var selectedSubtype by remember { mutableStateOf("") }
    var manualPath by remember { mutableStateOf("") }
    var statIno by remember { mutableStateOf("") }
    var statDev by remember { mutableStateOf("") }
    var statNlink by remember { mutableStateOf("") }
    var statSize by remember { mutableStateOf("") }
    var statAtime by remember { mutableStateOf("") }
    var statAtimeNsec by remember { mutableStateOf("") }
    var statMtime by remember { mutableStateOf("") }
    var statMtimeNsec by remember { mutableStateOf("") }
    var statCtime by remember { mutableStateOf("") }
    var statCtimeNsec by remember { mutableStateOf("") }
    var statBlocks by remember { mutableStateOf("") }
    var statBlksize by remember { mutableStateOf("") }

    val subtypeNormal = stringResource(R.string.susfs_kstat_subtype_normal)
    val subtypeFullClone = stringResource(R.string.susfs_kstat_subtype_full_clone)
    val subtypeStatically = stringResource(R.string.susfs_kstat_subtype_statically)
    val subtypes = listOf(subtypeNormal, subtypeFullClone, subtypeStatically)

    LaunchedEffect(refreshToken) {
        entries = SuSFSConfigHelper.loadConfig().sus_kstat
    }

    LaunchedEffect(showManualAdd) {
        if (showManualAdd) {
            if (selectedSubtype.isEmpty()) selectedSubtype = subtypeNormal
        } else {
            manualPath = ""
            statIno = ""; statDev = ""; statNlink = ""; statSize = ""
            statAtime = ""; statAtimeNsec = ""; statMtime = ""; statMtimeNsec = ""
            statCtime = ""; statCtimeNsec = ""; statBlocks = ""; statBlksize = ""
        }
    }

    val importHint = stringResource(R.string.susfs_entry_import_hint)
    val staticallyImportHint = stringResource(R.string.susfs_kstat_import_statically_hint)
    val manualAddTitle = stringResource(R.string.susfs_entry_manual_add)
    val detailTitle = stringResource(R.string.susfs_entry_detail)
    val pathLabel = stringResource(R.string.susfs_entry_path_label)
    val spoofTypeLabel = stringResource(R.string.susfs_kstat_spoof_type)
    val staticallyFieldsLabel = stringResource(R.string.susfs_kstat_statically_fields)
    val noEntriesMsg = stringResource(R.string.susfs_entry_no_entries)
    val operationFailedMsg = stringResource(R.string.susfs_operation_failed)
    val normalImportTitle = stringResource(R.string.susfs_entry_import_list, "sus_kstat_normal")
    val fullCloneImportTitle = stringResource(R.string.susfs_entry_import_list, "sus_kstat_full_clone")
    val staticallyImportTitle = stringResource(R.string.susfs_entry_import_list, "sus_kstat_statically")
    val fieldInoLabel = stringResource(R.string.susfs_kstat_field_ino)
    val fieldDevLabel = stringResource(R.string.susfs_kstat_field_dev)
    val fieldNlinkLabel = stringResource(R.string.susfs_kstat_field_nlink)
    val fieldSizeLabel = stringResource(R.string.susfs_kstat_field_size)
    val fieldAtimeLabel = stringResource(R.string.susfs_kstat_field_atime)
    val fieldAtimeNsecLabel = stringResource(R.string.susfs_kstat_field_atime_nsec)
    val fieldMtimeLabel = stringResource(R.string.susfs_kstat_field_mtime)
    val fieldMtimeNsecLabel = stringResource(R.string.susfs_kstat_field_mtime_nsec)
    val fieldCtimeLabel = stringResource(R.string.susfs_kstat_field_ctime)
    val fieldCtimeNsecLabel = stringResource(R.string.susfs_kstat_field_ctime_nsec)
    val fieldBlocksLabel = stringResource(R.string.susfs_kstat_field_blocks)
    val fieldBlksizeLabel = stringResource(R.string.susfs_kstat_field_blksize)
    val importSuccessTemplate = stringResource(R.string.susfs_entry_import_success)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
    ) {
        item {
            Spacer(Modifier.height(topPadding))
        }

        item {
            SegmentedColumn {
                item {
                    SettingsJumpPageWidget(
                        iconPlaceholder = false,
                        title = normalImportTitle,
                        enabled = !isLoading,
                        onClick = { showBatchImportNormal = true }
                    )
                }
                item {
                    SettingsJumpPageWidget(
                        iconPlaceholder = false,
                        title = fullCloneImportTitle,
                        enabled = !isLoading,
                        onClick = { showBatchImportFullClone = true }
                    )
                }
                item {
                    SettingsJumpPageWidget(
                        iconPlaceholder = false,
                        title = staticallyImportTitle,
                        enabled = !isLoading,
                        onClick = { showBatchImportStatically = true }
                    )
                }
                item {
                    SettingsJumpPageWidget(
                        iconPlaceholder = false,
                        title = manualAddTitle,
                        enabled = !isLoading,
                        onClick = { showManualAdd = true }
                    )
                }
            }
        }

        if (entries.isEmpty()) {
            item {
                EmptyStateCard(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    message = noEntriesMsg
                )
            }
        } else {
            item { Spacer(Modifier.height(8.dp)) }
            lazySegmentColumn(
                items = entries.toList(),
                key = { _, it -> it.path }
            ) { _, item ->
                SettingsJumpPageWidget(
                    iconPlaceholder = false,
                    title = item.path,
                    description = item.spoof_type.name,
                    enabled = !isLoading,
                    onClick = { detailItem = item }
                )
            }
        }
    }

    BatchImportDialog(
        showDialog = showBatchImportNormal,
        title = normalImportTitle,
        hint = importHint,
        onDismiss = { showBatchImportNormal = false },
        onConfirm = { lines ->
            scope.launch {
                isLoading = true
                var success = 0
                var failed = 0
                lines.forEach { line ->
                    val added = SuSFSConfigHelper.addSusKstat(line)
                    val updated = if (added) SuSFSConfigHelper.updateSusKstat(line) else false
                    if (added && updated) success++ else failed++
                }
                entries = SuSFSConfigHelper.refreshConfig().sus_kstat
                snackbarHost.showSnackbar(
                    importSuccessTemplate.format(success, failed)
                )
                isLoading = false
            }
            showBatchImportNormal = false
        },
        isLoading = isLoading
    )

    BatchImportDialog(
        showDialog = showBatchImportFullClone,
        title = fullCloneImportTitle,
        hint = importHint,
        onDismiss = { showBatchImportFullClone = false },
        onConfirm = { lines ->
            scope.launch {
                isLoading = true
                var success = 0
                var failed = 0
                lines.forEach { line ->
                    val added = SuSFSConfigHelper.addSusKstat(line)
                    val updated = if (added) SuSFSConfigHelper.updateSusKstatFullClone(line) else false
                    if (added && updated) success++ else failed++
                }
                entries = SuSFSConfigHelper.refreshConfig().sus_kstat
                snackbarHost.showSnackbar(
                    importSuccessTemplate.format(success, failed)
                )
                isLoading = false
            }
            showBatchImportFullClone = false
        },
        isLoading = isLoading
    )

    BatchImportDialog(
        showDialog = showBatchImportStatically,
        title = staticallyImportTitle,
        hint = staticallyImportHint,
        onDismiss = { showBatchImportStatically = false },
        onConfirm = { lines ->
            scope.launch {
                isLoading = true
                var success = 0
                var failed = 0
                lines.forEach { line ->
                    val ok = SuSFSConfigHelper.addSusKstatStatically(line)
                    if (ok) success++ else failed++
                }
                entries = SuSFSConfigHelper.refreshConfig().sus_kstat
                snackbarHost.showSnackbar(
                    importSuccessTemplate.format(success, failed)
                )
                isLoading = false
            }
            showBatchImportStatically = false
        },
        isLoading = isLoading
    )

    ManualAddDialog(
        showDialog = showManualAdd,
        title = manualAddTitle,
        subtypes = subtypes,
        selectedSubtype = selectedSubtype,
        onSubtypeChange = { selectedSubtype = it },
        onDismiss = { showManualAdd = false },
        onConfirm = {
            val path = manualPath.trim()
            if (path.isEmpty()) return@ManualAddDialog
            scope.launch {
                isLoading = true
                val ok = when (selectedSubtype) {
                    subtypeFullClone -> {
                        val added = SuSFSConfigHelper.addSusKstat(path)
                        if (added) SuSFSConfigHelper.updateSusKstatFullClone(path) else false
                    }
                    subtypeStatically -> {
                        SuSFSConfigHelper.addSusKstatStatically(
                            path,
                            statIno.trim().toLongOrNull(),
                            statDev.trim().toLongOrNull(),
                            statNlink.trim().toLongOrNull(),
                            statSize.trim().toLongOrNull(),
                            statAtime.trim().toLongOrNull(),
                            statAtimeNsec.trim().toLongOrNull(),
                            statMtime.trim().toLongOrNull(),
                            statMtimeNsec.trim().toLongOrNull(),
                            statCtime.trim().toLongOrNull(),
                            statCtimeNsec.trim().toLongOrNull(),
                            statBlocks.trim().toLongOrNull(),
                            statBlksize.trim().toLongOrNull()
                        )
                    }
                    else -> {
                        val added = SuSFSConfigHelper.addSusKstat(path)
                        if (added) SuSFSConfigHelper.updateSusKstat(path) else false
                    }
                }
                if (ok) {
                    entries = SuSFSConfigHelper.refreshConfig().sus_kstat
                    showManualAdd = false
                } else {
                    snackbarHost.showSnackbar(operationFailedMsg)
                }
                isLoading = false
            }
        },
        isLoading = isLoading,
        formContent = {
            if (selectedSubtype == subtypeStatically) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = manualPath,
                        onValueChange = { manualPath = it },
                        label = { Text(pathLabel) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )
                    Text(
                        text = staticallyFieldsLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = statIno, onValueChange = { statIno = it }, label = { Text(fieldInoLabel) }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(8.dp))
                        OutlinedTextField(value = statDev, onValueChange = { statDev = it }, label = { Text(fieldDevLabel) }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(8.dp))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = statNlink, onValueChange = { statNlink = it }, label = { Text(fieldNlinkLabel) }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(8.dp))
                        OutlinedTextField(value = statSize, onValueChange = { statSize = it }, label = { Text(fieldSizeLabel) }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(8.dp))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = statAtime, onValueChange = { statAtime = it }, label = { Text(fieldAtimeLabel) }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(8.dp))
                        OutlinedTextField(value = statAtimeNsec, onValueChange = { statAtimeNsec = it }, label = { Text(fieldAtimeNsecLabel) }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(8.dp))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = statMtime, onValueChange = { statMtime = it }, label = { Text(fieldMtimeLabel) }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(8.dp))
                        OutlinedTextField(value = statMtimeNsec, onValueChange = { statMtimeNsec = it }, label = { Text(fieldMtimeNsecLabel) }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(8.dp))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = statCtime, onValueChange = { statCtime = it }, label = { Text(fieldCtimeLabel) }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(8.dp))
                        OutlinedTextField(value = statCtimeNsec, onValueChange = { statCtimeNsec = it }, label = { Text(fieldCtimeNsecLabel) }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(8.dp))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = statBlocks, onValueChange = { statBlocks = it }, label = { Text(fieldBlocksLabel) }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(8.dp))
                        OutlinedTextField(value = statBlksize, onValueChange = { statBlksize = it }, label = { Text(fieldBlksizeLabel) }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(8.dp))
                    }
                }
            } else {
                OutlinedTextField(
                    value = manualPath,
                    onValueChange = { manualPath = it },
                    label = { Text(pathLabel) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }
    )

    detailItem?.let { item ->
        val fields = mutableListOf<Pair<String, String>>(
            pathLabel to item.path,
            spoofTypeLabel to item.spoof_type.name
        )
        item.statically?.let { st ->
            fields.add(fieldInoLabel to (st.ino?.toString() ?: "default"))
            fields.add(fieldDevLabel to (st.dev?.toString() ?: "default"))
            fields.add(fieldNlinkLabel to (st.nlink?.toString() ?: "default"))
            fields.add(fieldSizeLabel to (st.size?.toString() ?: "default"))
            fields.add(fieldAtimeLabel to (st.atime?.toString() ?: "default"))
            fields.add(fieldAtimeNsecLabel to (st.atime_nsec?.toString() ?: "default"))
            fields.add(fieldMtimeLabel to (st.mtime?.toString() ?: "default"))
            fields.add(fieldMtimeNsecLabel to (st.mtime_nsec?.toString() ?: "default"))
            fields.add(fieldCtimeLabel to (st.ctime?.toString() ?: "default"))
            fields.add(fieldCtimeNsecLabel to (st.ctime_nsec?.toString() ?: "default"))
            fields.add(fieldBlocksLabel to (st.blocks?.toString() ?: "default"))
            fields.add(fieldBlksizeLabel to (st.blksize?.toString() ?: "default"))
        }
        EntryDetailDialog(
            showDialog = true,
            title = detailTitle,
            fields = fields,
            onDismiss = { detailItem = null },
            onDelete = {
                scope.launch {
                    isLoading = true
                    val ok = SuSFSConfigHelper.delSusKstat(item.path)
                    if (ok) {
                        entries = SuSFSConfigHelper.refreshConfig().sus_kstat
                        detailItem = null
                    } else {
                        snackbarHost.showSnackbar(operationFailedMsg)
                    }
                    isLoading = false
                }
            },
            isLoading = isLoading
        )
    }
}

// ==================== OpenRedirectTab ====================

/**
 * Open Redirect 标签页
 *
 * 渲染 open_redirect 条目：
 *   - 批量导入格式：target_path|redirected_path|uid_scheme_int(0-4)
 *   - 手动添加：target_path + redirected_path + uid_scheme 下拉选择（5 个 UidScheme 变体）
 *   - 条目列表展示 target_path / redirected_path / uid_scheme.name
 *   - 详情对话框可删除（delOpenRedirect）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenRedirectTab(
    nestedScrollConnection: NestedScrollConnection,
    topPadding: Dp,
    refreshToken: Int
) {
    val snackbarHost = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()

    var entries by remember { mutableStateOf<Set<OpenRedirectItem>>(emptySet()) }
    var isLoading by remember { mutableStateOf(false) }

    var showBatchImport by remember { mutableStateOf(false) }
    var showManualAdd by remember { mutableStateOf(false) }
    var detailItem by remember { mutableStateOf<OpenRedirectItem?>(null) }

    var manualTarget by remember { mutableStateOf("") }
    var manualRedirected by remember { mutableStateOf("") }
    var manualUidScheme by remember { mutableStateOf(UidScheme.NonApp) }
    var uidDropdownExpanded by remember { mutableStateOf(false) }

    val subtypeOpenRedirect = stringResource(R.string.susfs_redirect_target_path)
    val subtypes = listOf(subtypeOpenRedirect)

    val uidSchemeOptions = listOf(
        UidScheme.NonApp to stringResource(R.string.susfs_uid_scheme_non_app),
        UidScheme.RootExceptSu to stringResource(R.string.susfs_uid_scheme_root_except_su),
        UidScheme.NonSu to stringResource(R.string.susfs_uid_scheme_non_su),
        UidScheme.UnmountedApp to stringResource(R.string.susfs_uid_scheme_unmounted_app),
        UidScheme.Unmounted to stringResource(R.string.susfs_uid_scheme_unmounted)
    )

    LaunchedEffect(refreshToken) {
        entries = SuSFSConfigHelper.loadConfig().open_redirect
    }

    LaunchedEffect(showManualAdd) {
        if (!showManualAdd) {
            manualTarget = ""
            manualRedirected = ""
            manualUidScheme = UidScheme.NonApp
            uidDropdownExpanded = false
        }
    }

    val importHint = stringResource(R.string.susfs_redirect_import_hint)
    val manualAddTitle = stringResource(R.string.susfs_entry_manual_add)
    val detailTitle = stringResource(R.string.susfs_entry_detail)
    val targetPathLabel = stringResource(R.string.susfs_redirect_target_path)
    val redirectedPathLabel = stringResource(R.string.susfs_redirect_redirected_path)
    val uidSchemeLabel = stringResource(R.string.susfs_redirect_uid_scheme)
    val noEntriesMsg = stringResource(R.string.susfs_entry_no_entries)
    val operationFailedMsg = stringResource(R.string.susfs_operation_failed)
    val importTitle = stringResource(R.string.susfs_entry_import_list, "open_redirect")
    val importSuccessTemplate = stringResource(R.string.susfs_entry_import_success)
    val importParseErrorTemplate = stringResource(R.string.susfs_entry_import_parse_error)
    val selectedUidLabel = uidSchemeOptions.first { it.first == manualUidScheme }.second

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
    ) {
        item {
            Spacer(Modifier.height(topPadding))
        }

        item {
            SegmentedColumn {
                item {
                    SettingsJumpPageWidget(
                        iconPlaceholder = false,
                        title = importTitle,
                        enabled = !isLoading,
                        onClick = { showBatchImport = true }
                    )
                }
                item {
                    SettingsJumpPageWidget(
                        iconPlaceholder = false,
                        title = manualAddTitle,
                        enabled = !isLoading,
                        onClick = { showManualAdd = true }
                    )
                }
            }
        }

        if (entries.isEmpty()) {
            item {
                EmptyStateCard(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    message = noEntriesMsg
                )
            }
        } else {
            item { Spacer(Modifier.height(8.dp)) }
            lazySegmentColumn(
                items = entries.toList(),
                key = { _, it -> "${it.target_path}|${it.redirected_path}|${it.uid_scheme.value}" }
            ) { _, item ->
                SettingsJumpPageWidget(
                    iconPlaceholder = false,
                    title = item.target_path,
                    description = "${item.redirected_path} · ${item.uid_scheme.name}",
                    enabled = !isLoading,
                    onClick = { detailItem = item }
                )
            }
        }
    }

    BatchImportDialog(
        showDialog = showBatchImport,
        title = importTitle,
        hint = importHint,
        onDismiss = { showBatchImport = false },
        onConfirm = { lines ->
            scope.launch {
                isLoading = true
                var success = 0
                var failed = 0
                var skipped = 0
                lines.forEach { line ->
                    val parts = line.split("|")
                    if (parts.size == 3) {
                        val target = parts[0].trim()
                        val redirected = parts[1].trim()
                        val uidInt = parts[2].trim().toIntOrNull()
                        if (uidInt != null && uidInt in 0..4 && target.isNotEmpty() && redirected.isNotEmpty()) {
                            val ok = SuSFSConfigHelper.addOpenRedirect(
                                target, redirected, UidScheme.fromValue(uidInt)
                            )
                            if (ok) success++ else failed++
                        } else {
                            skipped++
                        }
                    } else {
                        skipped++
                    }
                }
                entries = SuSFSConfigHelper.refreshConfig().open_redirect
                snackbarHost.showSnackbar(
                    importSuccessTemplate.format(success, failed)
                )
                if (skipped > 0) {
                    snackbarHost.showSnackbar(
                        importParseErrorTemplate.format(skipped)
                    )
                }
                isLoading = false
            }
            showBatchImport = false
        },
        isLoading = isLoading
    )

    ManualAddDialog(
        showDialog = showManualAdd,
        title = manualAddTitle,
        subtypes = subtypes,
        selectedSubtype = subtypeOpenRedirect,
        onSubtypeChange = {},
        onDismiss = { showManualAdd = false },
        onConfirm = {
            val target = manualTarget.trim()
            val redirected = manualRedirected.trim()
            if (target.isEmpty() || redirected.isEmpty()) return@ManualAddDialog
            scope.launch {
                isLoading = true
                val ok = SuSFSConfigHelper.addOpenRedirect(target, redirected, manualUidScheme)
                if (ok) {
                    entries = SuSFSConfigHelper.refreshConfig().open_redirect
                    showManualAdd = false
                } else {
                    snackbarHost.showSnackbar(operationFailedMsg)
                }
                isLoading = false
            }
        },
        isLoading = isLoading,
        formContent = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = manualTarget,
                    onValueChange = { manualTarget = it },
                    label = { Text(targetPathLabel) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )
                OutlinedTextField(
                    value = manualRedirected,
                    onValueChange = { manualRedirected = it },
                    label = { Text(redirectedPathLabel) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )
                ExposedDropdownMenuBox(
                    expanded = uidDropdownExpanded,
                    onExpandedChange = { uidDropdownExpanded = !uidDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedUidLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(uidSchemeLabel) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = uidDropdownExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                        enabled = !isLoading,
                        shape = RoundedCornerShape(8.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = uidDropdownExpanded,
                        onDismissRequest = { uidDropdownExpanded = false }
                    ) {
                        uidSchemeOptions.forEach { (scheme, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    manualUidScheme = scheme
                                    uidDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    )

    detailItem?.let { item ->
        EntryDetailDialog(
            showDialog = true,
            title = detailTitle,
            fields = listOf(
                targetPathLabel to item.target_path,
                redirectedPathLabel to item.redirected_path,
                uidSchemeLabel to item.uid_scheme.name
            ),
            onDismiss = { detailItem = null },
            onDelete = {
                scope.launch {
                    isLoading = true
                    val ok = SuSFSConfigHelper.delOpenRedirect(item.target_path)
                    if (ok) {
                        entries = SuSFSConfigHelper.refreshConfig().open_redirect
                        detailItem = null
                    } else {
                        snackbarHost.showSnackbar(operationFailedMsg)
                    }
                    isLoading = false
                }
            },
            isLoading = isLoading
        )
    }
}

// ==================== SusMapTab ====================

/**
 * SUS Map 标签页
 *
 * 渲染 sus_map 条目（Set<String>）：
 *   - 批量导入：逐行 addSusMap
 *   - 手动添加：单个 path OutlinedTextField
 *   - 条目列表展示 path
 *   - 详情对话框可删除（delSusMap）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SusMapTab(
    nestedScrollConnection: NestedScrollConnection,
    topPadding: Dp,
    refreshToken: Int
) {
    val snackbarHost = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()

    var entries by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isLoading by remember { mutableStateOf(false) }

    var showBatchImport by remember { mutableStateOf(false) }
    var showManualAdd by remember { mutableStateOf(false) }
    var detailItem by remember { mutableStateOf<String?>(null) }

    var manualPath by remember { mutableStateOf("") }

    val subtypeSusMap = stringResource(R.string.susfs_map_subtype)
    val subtypes = listOf(subtypeSusMap)

    LaunchedEffect(refreshToken) {
        entries = SuSFSConfigHelper.loadConfig().sus_map
    }

    LaunchedEffect(showManualAdd) {
        if (!showManualAdd) {
            manualPath = ""
        }
    }

    val importHint = stringResource(R.string.susfs_entry_import_hint)
    val manualAddTitle = stringResource(R.string.susfs_entry_manual_add)
    val detailTitle = stringResource(R.string.susfs_entry_detail)
    val pathLabel = stringResource(R.string.susfs_entry_path_label)
    val noEntriesMsg = stringResource(R.string.susfs_entry_no_entries)
    val operationFailedMsg = stringResource(R.string.susfs_operation_failed)
    val importTitle = stringResource(R.string.susfs_entry_import_list, "sus_map")
    val importSuccessTemplate = stringResource(R.string.susfs_entry_import_success)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
    ) {
        item {
            Spacer(Modifier.height(topPadding))
        }

        item {
            SegmentedColumn {
                item {
                    SettingsJumpPageWidget(
                        iconPlaceholder = false,
                        title = importTitle,
                        enabled = !isLoading,
                        onClick = { showBatchImport = true }
                    )
                }
                item {
                    SettingsJumpPageWidget(
                        iconPlaceholder = false,
                        title = manualAddTitle,
                        enabled = !isLoading,
                        onClick = { showManualAdd = true }
                    )
                }
            }
        }

        if (entries.isEmpty()) {
            item {
                EmptyStateCard(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    message = noEntriesMsg
                )
            }
        } else {
            item { Spacer(Modifier.height(8.dp)) }
            lazySegmentColumn(
                items = entries.toList(),
                key = { _, it -> it }
            ) { _, path ->
                SettingsJumpPageWidget(
                    iconPlaceholder = false,
                    title = path,
                    enabled = !isLoading,
                    onClick = { detailItem = path }
                )
            }
        }
    }

    BatchImportDialog(
        showDialog = showBatchImport,
        title = importTitle,
        hint = importHint,
        onDismiss = { showBatchImport = false },
        onConfirm = { lines ->
            scope.launch {
                isLoading = true
                var success = 0
                var failed = 0
                lines.forEach { line ->
                    if (SuSFSConfigHelper.addSusMap(line)) success++ else failed++
                }
                entries = SuSFSConfigHelper.refreshConfig().sus_map
                snackbarHost.showSnackbar(
                    importSuccessTemplate.format(success, failed)
                )
                isLoading = false
            }
            showBatchImport = false
        },
        isLoading = isLoading
    )

    ManualAddDialog(
        showDialog = showManualAdd,
        title = manualAddTitle,
        subtypes = subtypes,
        selectedSubtype = subtypeSusMap,
        onSubtypeChange = {},
        onDismiss = { showManualAdd = false },
        onConfirm = {
            val path = manualPath.trim()
            if (path.isEmpty()) return@ManualAddDialog
            scope.launch {
                isLoading = true
                val ok = SuSFSConfigHelper.addSusMap(path)
                if (ok) {
                    entries = SuSFSConfigHelper.refreshConfig().sus_map
                    showManualAdd = false
                } else {
                    snackbarHost.showSnackbar(operationFailedMsg)
                }
                isLoading = false
            }
        },
        isLoading = isLoading,
        formContent = {
            OutlinedTextField(
                value = manualPath,
                onValueChange = { manualPath = it },
                label = { Text(pathLabel) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
            )
        }
    )

    detailItem?.let { path ->
        EntryDetailDialog(
            showDialog = true,
            title = detailTitle,
            fields = listOf(pathLabel to path),
            onDismiss = { detailItem = null },
            onDelete = {
                scope.launch {
                    isLoading = true
                    val ok = SuSFSConfigHelper.delSusMap(path)
                    if (ok) {
                        entries = SuSFSConfigHelper.refreshConfig().sus_map
                        detailItem = null
                    } else {
                        snackbarHost.showSnackbar(operationFailedMsg)
                    }
                    isLoading = false
                }
            },
            isLoading = isLoading
        )
    }
}

// ==================== BackupRestoreTab ====================

/**
 * 备份与还原标签页
 *
 * 提供导出/导入 .susfs.json 配置文件的功能：
 *   - 导出：通过 SAF CreateDocument 选择目标文件，直接复制当前配置文件
 *   - 导入：通过 SAF OpenDocument 选择备份文件，确认后校验版本并替换当前配置
 *   - 恢复默认：删除 .susfs.json 并清空配置缓存
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreTab(
    nestedScrollConnection: NestedScrollConnection,
    topPadding: Dp
) {
    val snackbarHost = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(false) }
    var showImportConfirm by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

    val exportTitle = stringResource(R.string.susfs_backup_export)
    val exportDesc = stringResource(R.string.susfs_backup_export_desc)
    val importTitle = stringResource(R.string.susfs_backup_import)
    val importDesc = stringResource(R.string.susfs_backup_import_desc)
    val exportSuccessMsg = stringResource(R.string.susfs_backup_export_success)
    val exportFailedMsg = stringResource(R.string.susfs_backup_export_failed)
    val importSuccessMsg = stringResource(R.string.susfs_backup_import_success)
    val importFailedMsg = stringResource(R.string.susfs_backup_import_failed)
    val restoreDefaultTitle = stringResource(R.string.susfs_backup_restore_default)
    val restoreDefaultDesc = stringResource(R.string.susfs_backup_restore_default_desc)
    val confirmTitle = stringResource(R.string.susfs_backup_import_confirm_title)
    val confirmMsg = stringResource(R.string.susfs_backup_import_confirm_message)
    val defaultFilename = stringResource(R.string.susfs_backup_default_filename)
    val importLabel = stringResource(R.string.susfs_backup_import_label)
    val cancelLabel = stringResource(R.string.susfs_entry_cancel)
    val operationSuccessMsg = stringResource(R.string.susfs_operation_success)
    val operationFailedMsg = stringResource(R.string.susfs_operation_failed)

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isLoading = true
            val ok = SuSFSConfigHelper.exportConfigToUri(uri)
            if (ok) {
                snackbarHost.showSnackbar(exportSuccessMsg)
            } else {
                snackbarHost.showSnackbar(exportFailedMsg)
            }
            isLoading = false
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        pendingImportUri = uri
        showImportConfirm = true
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
    ) {
        item {
            Spacer(Modifier.height(topPadding))
        }

        item {
            SegmentedColumn {
                item {
                    SettingsJumpPageWidget(
                        iconPlaceholder = false,
                        title = exportTitle,
                        description = exportDesc,
                        enabled = !isLoading,
                        onClick = {
                            exportLauncher.launch(defaultFilename)
                        }
                    )
                }
                item {
                    SettingsJumpPageWidget(
                        iconPlaceholder = false,
                        title = importTitle,
                        description = importDesc,
                        enabled = !isLoading,
                        onClick = {
                            importLauncher.launch(arrayOf("application/json", "*/*"))
                        }
                    )
                }
                item {
                    SettingsJumpPageWidget(
                        iconPlaceholder = false,
                        title = restoreDefaultTitle,
                        description = restoreDefaultDesc,
                        enabled = !isLoading,
                        onClick = {
                            scope.launch {
                                isLoading = true
                                val ok = SuSFSConfigHelper.restoreDefaultConfig()
                                snackbarHost.showSnackbar(if (ok) operationSuccessMsg else operationFailedMsg)
                                isLoading = false
                            }
                        }
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showImportConfirm) {
        AlertDialog(
            onDismissRequest = {
                showImportConfirm = false
                pendingImportUri = null
            },
            title = {
                Text(
                    text = confirmTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(text = confirmMsg)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val uri = pendingImportUri
                        showImportConfirm = false
                        pendingImportUri = null
                        if (uri != null) {
                            scope.launch {
                                isLoading = true
                                val ok = SuSFSConfigHelper.importConfigFromUri(uri)
                                if (ok) {
                                    snackbarHost.showSnackbar(importSuccessMsg)
                                } else {
                                    snackbarHost.showSnackbar(importFailedMsg)
                                }
                                isLoading = false
                            }
                        }
                    },
                    enabled = !isLoading
                ) {
                    Text(importLabel)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showImportConfirm = false
                    pendingImportUri = null
                }) {
                    Text(cancelLabel)
                }
            },
            shape = RoundedCornerShape(12.dp)
        )
    }
}
