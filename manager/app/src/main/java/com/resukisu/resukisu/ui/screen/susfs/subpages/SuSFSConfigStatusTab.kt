package com.resukisu.resukisu.ui.screen.susfs.subpages

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material.icons.twotone.RestartAlt
import androidx.compose.material.icons.twotone.Restore
import androidx.compose.material.icons.twotone.Save
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material.icons.twotone.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.ui.unit.dp
import com.resukisu.resukisu.R
import com.resukisu.resukisu.data.susfs.SuSFSConfigHelper
import com.resukisu.resukisu.data.susfs.SuSFSStatusInfo
import com.resukisu.resukisu.ui.component.WarningCard
import com.resukisu.resukisu.ui.component.settings.SegmentedColumn
import com.resukisu.resukisu.ui.component.settings.SettingsBaseWidget
import com.resukisu.resukisu.ui.component.settings.SettingsJumpPageWidget
import com.resukisu.resukisu.ui.component.settings.SettingsSwitchWidget
import com.resukisu.resukisu.ui.screen.susfs.RegisterSuSFSRefresh
import com.resukisu.resukisu.ui.screen.susfs.SuSFSRefreshRegistrar
import com.resukisu.resukisu.ui.util.LocalSnackbarHost
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StatusTab(
    nestedScrollConnection: NestedScrollConnection,
    innerPadding: PaddingValues,
    onRegisterRefresh: SuSFSRefreshRegistrar,
    configEnabled: Boolean,
    configEnabledLoaded: Boolean,
    onConfigEnabledChange: (Boolean) -> Unit,
    onConfigRestored: () -> Unit,
) {
    var statusInfo by remember { mutableStateOf(SuSFSStatusInfo("", "", "")) }

    RegisterSuSFSRefresh(onRegisterRefresh) { _, forceRefresh ->
        statusInfo = SuSFSConfigHelper.loadStatusInfo(forceRefresh)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection),
    ) {
        item {
            Spacer(Modifier.height(innerPadding.calculateTopPadding()))
        }

        item {
            AnimatedVisibility(
                visible = configEnabledLoaded && !configEnabled
            ) {
                WarningCard(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    message = stringResource(R.string.susfs_management_disabled_warning),
                )
            }
        }

        item {
            SegmentedColumn {
                item {
                    SettingsSwitchWidget(
                        icon = Icons.TwoTone.Save,
                        title = stringResource(R.string.susfs_standard_config_enabled),
                        description = stringResource(R.string.susfs_standard_config_enabled_desc),
                        checked = configEnabled,
                        enabled = configEnabledLoaded,
                        onCheckedChange = onConfigEnabledChange,
                    )
                }
                item {
                    SettingsBaseWidget(
                        icon = Icons.TwoTone.Info,
                        title = stringResource(R.string.susfs_status_version),
                        description = statusInfo.version.ifBlank {
                            stringResource(R.string.susfs_status_no_data)
                        }
                    )
                }
                item {
                    SettingsBaseWidget(
                        icon = Icons.TwoTone.Settings,
                        title = stringResource(R.string.susfs_status_variant),
                        description = statusInfo.variant.ifBlank {
                            stringResource(R.string.susfs_status_no_data)
                        }
                    )
                }
                item {
                    SettingsBaseWidget(
                        icon = Icons.TwoTone.TaskAlt,
                        title = stringResource(R.string.susfs_status_enabled_features),
                        description = statusInfo.enabledFeatures.ifBlank {
                            stringResource(R.string.susfs_status_no_data)
                        }
                    )
                }
            }
        }

        item {
            BackupRestoreSection(onConfigRestored = onConfigRestored)
        }

        item {
            Spacer(Modifier.height(innerPadding.calculateBottomPadding()))
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreSection(
    onConfigRestored: () -> Unit,
) {
    val snackbarHost = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()

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
            val ok = SuSFSConfigHelper.exportConfigToUri(uri)
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

    SegmentedColumn {
        item {
            SettingsJumpPageWidget(
                icon = Icons.TwoTone.Save,
                title = exportTitle,
                description = exportDesc,
                onClick = {
                    exportLauncher.launch(defaultFilename)
                }
            )
        }
        item {
            SettingsJumpPageWidget(
                icon = Icons.TwoTone.Restore,
                title = importTitle,
                description = importDesc,
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
                operationFailedMsg = operationFailedMsg,
                onConfigRestored = onConfigRestored,
            )
        }
    }

    if (showImportConfirm) {
        AlertDialog(
            onDismissRequest = {
                showImportConfirm = false
                pendingImportUri = null
            },
            title = { Text(confirmTitle) },
            text = { Text(confirmMsg) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val uri = pendingImportUri
                        showImportConfirm = false
                        pendingImportUri = null
                        if (uri != null) {
                            scope.launch {
                                val ok = SuSFSConfigHelper.importConfigFromUri(uri)
                                if (ok) {
                                    onConfigRestored()
                                }
                                scope.launch {
                                    snackbarHost.showSnackbar(if (ok) importSuccessMsg else importFailedMsg)
                                }
                            }
                        }
                    }
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
        )
    }
}

@Composable
private fun RestoreDefaultRow(
    title: String,
    description: String,
    snackbarHost: SnackbarHostState,
    operationSuccessMsg: String,
    operationFailedMsg: String,
    onConfigRestored: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    SettingsJumpPageWidget(
        icon = Icons.TwoTone.RestartAlt,
        title = title,
        description = description,
        onClick = {
            scope.launch {
                val ok = SuSFSConfigHelper.restoreDefaultConfig()
                if (ok) {
                    onConfigRestored()
                }
                snackbarHost.showSnackbar(if (ok) operationSuccessMsg else operationFailedMsg)
            }
        }
    )
}
