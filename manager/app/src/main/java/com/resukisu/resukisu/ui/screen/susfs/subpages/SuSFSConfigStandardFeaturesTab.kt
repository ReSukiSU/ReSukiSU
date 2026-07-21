package com.resukisu.resukisu.ui.screen.susfs.subpages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.BugReport
import androidx.compose.material.icons.twotone.Code
import androidx.compose.material.icons.twotone.Computer
import androidx.compose.material.icons.twotone.Security
import androidx.compose.material.icons.twotone.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import com.resukisu.resukisu.R
import com.resukisu.resukisu.data.susfs.SuSFSConfigHelper
import com.resukisu.resukisu.ui.component.settings.SegmentedColumn
import com.resukisu.resukisu.ui.component.settings.SettingsJumpPageWidget
import com.resukisu.resukisu.ui.component.settings.SettingsSwitchWidget
import com.resukisu.resukisu.ui.component.settings.SettingsTextFieldWidget
import com.resukisu.resukisu.ui.screen.susfs.RegisterSuSFSRefresh
import com.resukisu.resukisu.ui.screen.susfs.SuSFSRefreshRegistrar
import com.resukisu.resukisu.ui.util.LocalSnackbarHost
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StandardFeaturesTab(
    nestedScrollConnection: NestedScrollConnection,
    innerPadding: PaddingValues,
    onRegisterRefresh: SuSFSRefreshRegistrar,
) {
    val snackbarHost = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()

    var hasLoadedConfig by remember { mutableStateOf(false) }
    var loggingEnabled by remember { mutableStateOf(false) }
    var avcLogSpoofingEnabled by remember { mutableStateOf(false) }
    var hideSusMntsEnabled by remember { mutableStateOf(false) }
    var unameVersion by remember { mutableStateOf("") }
    var unameRelease by remember { mutableStateOf("") }
    var cmdlineOrBootconfig by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    var showUnameDialog by remember { mutableStateOf(false) }
    var showCmdlineDialog by remember { mutableStateOf(false) }
    val unameVersionInput = remember { TextFieldState() }
    val unameReleaseInput = remember { TextFieldState() }
    val cmdlineInput = remember { TextFieldState() }

    val operationFailedMsg = stringResource(R.string.susfs_operation_failed)

    RegisterSuSFSRefresh(onRegisterRefresh) { config, _ ->
        isLoading = true
        try {
            loggingEnabled = config.logging
            avcLogSpoofingEnabled = config.avc_log_spoofing
            hideSusMntsEnabled = config.hide_sus_mnts_for_non_su_procs
            unameVersion = config.uname.version
            unameRelease = config.uname.release
            cmdlineOrBootconfig = config.cmdline_or_bootconfig
            hasLoadedConfig = true
        } finally {
            isLoading = false
        }
    }

    val handleLoggingChange: (Boolean) -> Unit = remember(scope, snackbarHost, operationFailedMsg) {
        { newValue: Boolean ->
            scope.launch {
                val ok = SuSFSConfigHelper.enableLog(newValue)
                if (ok) {
                    loggingEnabled = newValue
                } else {
                    snackbarHost.showSnackbar(operationFailedMsg)
                }
            }
        }
    }

    val handleAvcLogSpoofingChange: (Boolean) -> Unit =
        remember(scope, snackbarHost, operationFailedMsg) {
            { newValue: Boolean ->
                scope.launch {
                    val ok = SuSFSConfigHelper.enableAvcLogSpoofing(newValue)
                    if (ok) {
                        avcLogSpoofingEnabled = newValue
                    } else {
                        snackbarHost.showSnackbar(operationFailedMsg)
                    }
                }
            }
        }

    val handleHideSusMntsChange: (Boolean) -> Unit =
        remember(scope, snackbarHost, operationFailedMsg) {
            { newValue: Boolean ->
                scope.launch {
                    val ok = SuSFSConfigHelper.hideSusMntsForNonSuProcs(newValue)
                    if (ok) {
                        hideSusMntsEnabled = newValue
                    } else {
                        snackbarHost.showSnackbar(operationFailedMsg)
                    }
                }
            }
        }

    val handleUnameSave: () -> Unit = remember(scope, snackbarHost, operationFailedMsg) {
        {
            val v = unameVersionInput.text.toString().trim()
            val r = unameReleaseInput.text.toString().trim()
                scope.launch {
                    isLoading = true
                    val ok = SuSFSConfigHelper.setUname(r, v)
                    if (ok) {
                        unameVersion = v
                        unameRelease = r
                        showUnameDialog = false
                    } else {
                        isLoading = false
                        scope.launch {
                            snackbarHost.showSnackbar(operationFailedMsg)
                        }
                    }
                    isLoading = false
                }
        }
    }

    val handleCmdlineSave: () -> Unit = remember(scope, snackbarHost, operationFailedMsg) {
        {
            val p = cmdlineInput.text.toString().trim()
                scope.launch {
                    isLoading = true
                    val ok = SuSFSConfigHelper.setCmdlineOrBootconfig(p)
                    if (ok) {
                        cmdlineOrBootconfig = p
                        showCmdlineDialog = false
                    } else {
                        isLoading = false
                        scope.launch {
                            snackbarHost.showSnackbar(operationFailedMsg)
                        }
                    }
                    isLoading = false
                }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection),
        ) {
            item {
                Spacer(Modifier.height(innerPadding.calculateTopPadding()))
            }

            if (hasLoadedConfig) {
                item {
                    SegmentedColumn {
                        item {
                            SettingsSwitchWidget(
                                icon = Icons.TwoTone.BugReport,
                                title = stringResource(R.string.susfs_standard_logging),
                                description = stringResource(R.string.susfs_standard_logging_desc),
                                checked = loggingEnabled,
                                onCheckedChange = handleLoggingChange
                            )
                        }

                        item {
                            SettingsSwitchWidget(
                                icon = Icons.TwoTone.Security,
                                title = stringResource(R.string.susfs_standard_avc_log_spoofing),
                                description = stringResource(R.string.susfs_standard_avc_log_spoofing_desc),
                                checked = avcLogSpoofingEnabled,
                                onCheckedChange = handleAvcLogSpoofingChange
                            )
                        }

                        item {
                            SettingsSwitchWidget(
                                icon = Icons.TwoTone.VisibilityOff,
                                title = stringResource(R.string.susfs_standard_hide_sus_mnts),
                                description = stringResource(R.string.susfs_standard_hide_sus_mnts_desc),
                                checked = hideSusMntsEnabled,
                                onCheckedChange = handleHideSusMntsChange
                            )
                        }

                        item {
                            SettingsJumpPageWidget(
                                icon = Icons.TwoTone.Computer,
                                title = stringResource(R.string.susfs_standard_uname),
                                description = stringResource(
                                    R.string.susfs_standard_current_value,
                                    "$unameRelease / $unameVersion"
                                ),
                                onClick = {
                                    unameReleaseInput.setTextAndPlaceCursorAtEnd(unameRelease)
                                    unameVersionInput.setTextAndPlaceCursorAtEnd(unameVersion)
                                    showUnameDialog = true
                                }
                            )
                        }

                        item {
                            SettingsJumpPageWidget(
                                icon = Icons.TwoTone.Code,
                                title = stringResource(R.string.susfs_standard_cmdline_or_bootconfig),
                                description = stringResource(
                                    R.string.susfs_standard_current_value,
                                    cmdlineOrBootconfig.ifBlank { stringResource(R.string.susfs_standard_not_set) }
                                ),
                                onClick = {
                                    cmdlineInput.setTextAndPlaceCursorAtEnd(cmdlineOrBootconfig)
                                    showCmdlineDialog = true
                                }
                            )
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(innerPadding.calculateBottomPadding()))
            }
        }

        if (isLoading && !hasLoadedConfig) {
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
                title = { Text(stringResource(R.string.susfs_standard_uname)) },
                text = {
                    SegmentedColumn(contentPadding = PaddingValues(0.dp)) {
                        item {
                            SettingsTextFieldWidget(
                                state = unameReleaseInput,
                                title = stringResource(R.string.susfs_standard_uname_release),
                                useLabelAsPlaceholder = true,
                                enabled = !isLoading,
                                lineLimits = TextFieldLineLimits.SingleLine,
                                renderBackgroundBlur = false
                            )
                        }
                        item {
                            SettingsTextFieldWidget(
                                state = unameVersionInput,
                                title = stringResource(R.string.susfs_standard_uname_version),
                                useLabelAsPlaceholder = true,
                                enabled = !isLoading,
                                lineLimits = TextFieldLineLimits.SingleLine,
                                renderBackgroundBlur = false
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = handleUnameSave
                    ) {
                        Text(stringResource(R.string.susfs_save))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showUnameDialog = false }) {
                        Text(stringResource(R.string.susfs_entry_cancel))
                    }
                },
            )
        }

        if (showCmdlineDialog) {
            AlertDialog(
                onDismissRequest = { showCmdlineDialog = false },
                title = {
                    Text(stringResource(R.string.susfs_standard_cmdline_or_bootconfig))
                },
                text = {
                    SegmentedColumn(contentPadding = PaddingValues(0.dp)) {
                        item {
                            SettingsTextFieldWidget(
                                state = cmdlineInput,
                                title = stringResource(R.string.susfs_standard_cmdline_path),
                                useLabelAsPlaceholder = true,
                                enabled = !isLoading,
                                lineLimits = TextFieldLineLimits.SingleLine,
                                renderBackgroundBlur = false
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = handleCmdlineSave
                    ) {
                        Text(stringResource(R.string.susfs_save))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCmdlineDialog = false }) {
                        Text(stringResource(R.string.susfs_entry_cancel))
                    }
                },
            )
        }
    }
}
