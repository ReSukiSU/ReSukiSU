package com.resukisu.resukisu.ui.susfs.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import com.resukisu.resukisu.ui.susfs.util.OpenRedirectItem
import com.resukisu.resukisu.ui.susfs.util.SuSFSConfig
import com.resukisu.resukisu.ui.susfs.util.SuSFSConfigHelper
import com.resukisu.resukisu.ui.susfs.util.SusKstatItem
import com.resukisu.resukisu.ui.susfs.util.SusPathItem
import com.resukisu.resukisu.ui.susfs.util.UidScheme
import com.resukisu.resukisu.ui.util.LocalSnackbarHost
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

// ==================== 通用骨架组件与占位 Tab（Task 5 新增） ====================

/**
 * 条目页通用骨架组件
 *
 * 用于 SUS Path / SUS Kstat / Open Redirect / SUS Map 等以"条目列表"为主的标签页。
 * 提供统一的滚动行为、顶部留白、固定头部区与空状态展示。
 *
 * @param nestedScrollConnection 由主框架传入的嵌套滚动连接，用于与 TopBar 联动
 * @param topPadding 顶部留白高度（通常为 innerPadding.calculateTopPadding()）
 * @param pinnedHeader 固定条目区，作为 LazyColumn 的头部内容（如说明卡片、操作按钮等）
 * @param emptyText 列表为空时显示的提示文案
 * @param items 列表项数据
 * @param key 列表项 key 计算函数，用于稳定项身份
 * @param itemContent 单个列表项的渲染逻辑
 */
@Composable
fun EntryListPageScaffold(
    nestedScrollConnection: NestedScrollConnection,
    topPadding: Dp,
    pinnedHeader: LazyListScope.() -> Unit,
    emptyText: String,
    items: List<Any>,
    key: ((Any) -> Any)? = null,
    itemContent: @Composable (Any) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
    ) {
        item {
            Spacer(Modifier.height(topPadding))
        }
        pinnedHeader()
        if (items.isEmpty()) {
            item {
                EmptyStateCard(message = emptyText)
            }
        } else {
            items(items, key = key) { entry ->
                itemContent(entry)
            }
        }
    }
}

// ==================== 占位 Tab 组件（后续任务填充） ====================

/**
 * 状态总览标签页
 *
 * 展示 SUSFS 的版本、变体与已启用功能。
 * 三个 show 接口通过 coroutineScope + async 并发调用，awaitAll 等待全部完成。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StatusTab(
    nestedScrollConnection: NestedScrollConnection,
    topPadding: Dp
) {
    var version by remember { mutableStateOf("") }
    var variant by remember { mutableStateOf("") }
    var enabledFeatures by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    suspend fun loadAll() {
        coroutineScope {
            val results = listOf(
                async { SuSFSConfigHelper.showVersion() },
                async { SuSFSConfigHelper.showVariant() },
                async { SuSFSConfigHelper.showEnabledFeatures() }
            ).awaitAll()
            version = results[0]
            variant = results[1]
            enabledFeatures = results[2]
        }
    }

    LaunchedEffect(Unit) {
        isLoading = true
        try {
            loadAll()
        } finally {
            isLoading = false
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(Modifier.height(topPadding))
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            try {
                                loadAll()
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    enabled = !isLoading
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.susfs_refresh)
                    )
                }
            }
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
                StatusInfoCard(
                    title = stringResource(R.string.susfs_status_version),
                    value = version
                )
            }
            item {
                StatusInfoCard(
                    title = stringResource(R.string.susfs_status_variant),
                    value = variant
                )
            }
            item {
                StatusInfoCard(
                    title = stringResource(R.string.susfs_status_enabled_features),
                    value = enabledFeatures
                )
            }
        }
    }
}

/**
 * 状态页信息卡片
 */
@Composable
private fun StatusInfoCard(
    title: String,
    value: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = value.ifBlank { stringResource(R.string.susfs_status_no_data) },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 标准功能标签页
 *
 * 通过 SuSFSConfigHelper.loadConfig() 读取配置，渲染：
 *   - logging / avc_log_spoofing / hide_sus_mnts_for_non_su_procs 三个开关
 *   - uname 编辑卡片（点击弹对话框编辑 version + release）
 *   - cmdline_or_bootconfig 编辑卡片（点击弹对话框编辑 path）
 *
 * Switch 切换立即调用对应 Helper，成功后更新本地 state；
 * 字符串字段对话框确认后调用 Helper，成功后 refreshConfig 并刷新本地展示；
 * 失败时通过 LocalSnackbarHost 弹出 snackbar。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StandardFeaturesTab(
    nestedScrollConnection: NestedScrollConnection,
    topPadding: Dp
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

    LaunchedEffect(Unit) {
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
                .nestedScroll(nestedScrollConnection),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(Modifier.height(topPadding))
            }

            if (currentConfig != null) {
                // logging 开关
                item {
                    SwitchFeatureCard(
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

                // avc_log_spoofing 开关
                item {
                    SwitchFeatureCard(
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

                // hide_sus_mnts_for_non_su_procs 开关
                item {
                    SwitchFeatureCard(
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

                // uname 编辑卡片
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clickable(enabled = !isLoading) {
                                unameVersionInput = currentConfig.uname.version
                                unameReleaseInput = currentConfig.uname.release
                                showUnameDialog = true
                            },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.susfs_standard_uname),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = stringResource(
                                    R.string.susfs_standard_current_value,
                                    "${currentConfig.uname.version} / ${currentConfig.uname.release}"
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // cmdline_or_bootconfig 编辑卡片
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clickable(enabled = !isLoading) {
                                cmdlineInput = currentConfig.cmdline_or_bootconfig
                                showCmdlineDialog = true
                            },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.susfs_standard_cmdline_or_bootconfig),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = stringResource(
                                    R.string.susfs_standard_current_value,
                                    currentConfig.cmdline_or_bootconfig.ifBlank { "—" }
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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

/**
 * 标准功能页开关卡片
 */
@Composable
private fun SwitchFeatureCard(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        }
    }
}

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
    topPadding: Dp
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

    LaunchedEffect(Unit) {
        entries = SuSFSConfigHelper.getSusPaths()
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
            PinnedActionCard(
                title = pathImportTitle,
                onClick = { showBatchImportPath = true },
                isLoading = isLoading
            )
        }

        item {
            PinnedActionCard(
                title = loopImportTitle,
                onClick = { showBatchImportLoop = true },
                isLoading = isLoading
            )
        }

        item {
            PinnedActionCard(
                title = manualAddTitle,
                onClick = { showManualAdd = true },
                isLoading = isLoading
            )
        }

        if (entries.isEmpty()) {
            item {
                EmptyStateCard(message = noEntriesMsg)
            }
        } else {
            items(entries.toList(), key = { it.path }) { item ->
                EntryItemCard(
                    title = item.path,
                    subtitle = if (item.is_loop) isLoopLabel else null,
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
                SuSFSConfigHelper.refreshConfig()
                entries = SuSFSConfigHelper.getSusPaths()
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
                SuSFSConfigHelper.refreshConfig()
                entries = SuSFSConfigHelper.getSusPaths()
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
                    SuSFSConfigHelper.refreshConfig()
                    entries = SuSFSConfigHelper.getSusPaths()
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
                        SuSFSConfigHelper.refreshConfig()
                        entries = SuSFSConfigHelper.getSusPaths()
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
    topPadding: Dp
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

    LaunchedEffect(Unit) {
        entries = SuSFSConfigHelper.getSusKstats()
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
            PinnedActionCard(
                title = normalImportTitle,
                onClick = { showBatchImportNormal = true },
                isLoading = isLoading
            )
        }

        item {
            PinnedActionCard(
                title = fullCloneImportTitle,
                onClick = { showBatchImportFullClone = true },
                isLoading = isLoading
            )
        }

        item {
            PinnedActionCard(
                title = staticallyImportTitle,
                onClick = { showBatchImportStatically = true },
                isLoading = isLoading
            )
        }

        item {
            PinnedActionCard(
                title = manualAddTitle,
                onClick = { showManualAdd = true },
                isLoading = isLoading
            )
        }

        if (entries.isEmpty()) {
            item {
                EmptyStateCard(message = noEntriesMsg)
            }
        } else {
            items(entries.toList(), key = { it.path }) { item ->
                EntryItemCard(
                    title = item.path,
                    subtitle = item.spoof_type.name,
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
                SuSFSConfigHelper.refreshConfig()
                entries = SuSFSConfigHelper.getSusKstats()
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
                SuSFSConfigHelper.refreshConfig()
                entries = SuSFSConfigHelper.getSusKstats()
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
                SuSFSConfigHelper.refreshConfig()
                entries = SuSFSConfigHelper.getSusKstats()
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
                    SuSFSConfigHelper.refreshConfig()
                    entries = SuSFSConfigHelper.getSusKstats()
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
                        SuSFSConfigHelper.refreshConfig()
                        entries = SuSFSConfigHelper.getSusKstats()
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
    topPadding: Dp
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

    LaunchedEffect(Unit) {
        entries = SuSFSConfigHelper.getOpenRedirects()
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
            PinnedActionCard(
                title = importTitle,
                onClick = { showBatchImport = true },
                isLoading = isLoading
            )
        }

        item {
            PinnedActionCard(
                title = manualAddTitle,
                onClick = { showManualAdd = true },
                isLoading = isLoading
            )
        }

        if (entries.isEmpty()) {
            item {
                EmptyStateCard(message = noEntriesMsg)
            }
        } else {
            items(entries.toList(), key = { "${it.target_path}|${it.redirected_path}|${it.uid_scheme.value}" }) { item ->
                EntryItemCard(
                    title = item.target_path,
                    subtitle = "${item.redirected_path} · ${item.uid_scheme.name}",
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
                SuSFSConfigHelper.refreshConfig()
                entries = SuSFSConfigHelper.getOpenRedirects()
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
                    SuSFSConfigHelper.refreshConfig()
                    entries = SuSFSConfigHelper.getOpenRedirects()
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
                        SuSFSConfigHelper.refreshConfig()
                        entries = SuSFSConfigHelper.getOpenRedirects()
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
    topPadding: Dp
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

    LaunchedEffect(Unit) {
        entries = SuSFSConfigHelper.getSusMaps()
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
            PinnedActionCard(
                title = importTitle,
                onClick = { showBatchImport = true },
                isLoading = isLoading
            )
        }

        item {
            PinnedActionCard(
                title = manualAddTitle,
                onClick = { showManualAdd = true },
                isLoading = isLoading
            )
        }

        if (entries.isEmpty()) {
            item {
                EmptyStateCard(message = noEntriesMsg)
            }
        } else {
            items(entries.toList(), key = { it }) { path ->
                EntryItemCard(
                    title = path,
                    subtitle = null,
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
                SuSFSConfigHelper.refreshConfig()
                entries = SuSFSConfigHelper.getSusMaps()
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
                    SuSFSConfigHelper.refreshConfig()
                    entries = SuSFSConfigHelper.getSusMaps()
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
                        SuSFSConfigHelper.refreshConfig()
                        entries = SuSFSConfigHelper.getSusMaps()
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

/**
 * 固定条目卡片：用于"导入列表"/"手动添加"等固定操作入口
 */
@Composable
private fun PinnedActionCard(
    title: String,
    onClick: () -> Unit,
    isLoading: Boolean = false
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        onClick = { if (!isLoading) onClick() },
        enabled = !isLoading,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * 条目卡片：用于展示单个条目的标题与可选副标题
 */
@Composable
private fun EntryItemCard(
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        onClick = onClick,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
