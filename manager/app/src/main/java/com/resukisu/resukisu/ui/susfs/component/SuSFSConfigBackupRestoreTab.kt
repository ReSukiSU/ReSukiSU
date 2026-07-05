package com.resukisu.resukisu.ui.susfs.component

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.resukisu.resukisu.R
import com.resukisu.resukisu.data.susfs.SuSFSConfigHelper
import com.resukisu.resukisu.ui.component.settings.SegmentedColumn
import com.resukisu.resukisu.ui.component.settings.SettingsJumpPageWidget
import com.resukisu.resukisu.ui.util.LocalSnackbarHost
import kotlinx.coroutines.launch

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
