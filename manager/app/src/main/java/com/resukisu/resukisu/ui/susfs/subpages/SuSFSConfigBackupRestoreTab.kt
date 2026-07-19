package com.resukisu.resukisu.ui.susfs.subpages

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.resukisu.resukisu.R
import com.resukisu.resukisu.data.susfs.SuSFSConfigHelper
import com.resukisu.resukisu.ui.component.settings.SegmentedColumn
import com.resukisu.resukisu.ui.component.settings.SettingsDialogFrame
import com.resukisu.resukisu.ui.component.settings.SettingsJumpPageWidget
import com.resukisu.resukisu.ui.util.LocalSnackbarHost
import kotlinx.coroutines.launch

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
    val exportDesc = stringResource(R.string.susfs_backup_description)
    val importTitle = stringResource(R.string.susfs_backup_import)
    val importDesc = stringResource(R.string.susfs_restore_description)
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
            isLoading = false
            scope.launch {
                snackbarHost.showSnackbar(if (ok) exportSuccessMsg else exportFailedMsg)
            }
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
                            importLauncher.launch(arrayOf("application/json"))
                        }
                    )
                }
                item {
                    RestoreDefaultRow(
                        title = restoreDefaultTitle,
                        description = restoreDefaultDesc,
                        snackbarHost = snackbarHost,
                        operationSuccessMsg = operationSuccessMsg,
                        operationFailedMsg = operationFailedMsg
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showImportConfirm) {
        SettingsDialogFrame(
            title = confirmTitle,
            onDismissRequest = {
                showImportConfirm = false
                pendingImportUri = null
            },
            buttons = {
                TextButton(onClick = {
                    showImportConfirm = false
                    pendingImportUri = null
                }) {
                    Text(cancelLabel)
                }
                TextButton(
                    onClick = {
                        val uri = pendingImportUri
                        showImportConfirm = false
                        pendingImportUri = null
                        if (uri != null) {
                            scope.launch {
                                isLoading = true
                                val ok = SuSFSConfigHelper.importConfigFromUri(uri)
                                isLoading = false
                                scope.launch {
                                    snackbarHost.showSnackbar(if (ok) importSuccessMsg else importFailedMsg)
                                }
                            }
                        }
                    },
                    enabled = !isLoading
                ) {
                    Text(importLabel)
                }
            }
        ) {
            Text(text = confirmMsg)
        }
    }
}

@Composable
private fun RestoreDefaultRow(
    title: String,
    description: String,
    snackbarHost: SnackbarHostState,
    operationSuccessMsg: String,
    operationFailedMsg: String,
) {
    val scope = rememberCoroutineScope()
    var isRestoring by remember { mutableStateOf(false) }

    SettingsJumpPageWidget(
        iconPlaceholder = false,
        title = title,
        description = description,
        enabled = !isRestoring,
        onClick = {
            scope.launch {
                isRestoring = true
                val ok = SuSFSConfigHelper.restoreDefaultConfig()
                isRestoring = false
                snackbarHost.showSnackbar(if (ok) operationSuccessMsg else operationFailedMsg)
            }
        }
    )
}
