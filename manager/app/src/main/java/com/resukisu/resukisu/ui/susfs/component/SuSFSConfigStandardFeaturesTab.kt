package com.resukisu.resukisu.ui.susfs.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import com.resukisu.resukisu.data.susfs.SuSFSConfigHelper
import com.resukisu.resukisu.ui.component.settings.SegmentedColumn
import com.resukisu.resukisu.ui.component.settings.SettingsJumpPageWidget
import com.resukisu.resukisu.ui.component.settings.SettingsSwitchWidget
import com.resukisu.resukisu.ui.util.LocalSnackbarHost
import kotlinx.coroutines.launch

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

    var hasLoadedConfig by remember { mutableStateOf(false) }
    var loggingEnabled by remember { mutableStateOf(false) }
    var avcLogSpoofingEnabled by remember { mutableStateOf(false) }
    var hideSusMntsEnabled by remember { mutableStateOf(false) }
    var loggingBusy by remember { mutableStateOf(false) }
    var avcLogSpoofingBusy by remember { mutableStateOf(false) }
    var hideSusMntsBusy by remember { mutableStateOf(false) }
    var unameVersion by remember { mutableStateOf("default") }
    var unameRelease by remember { mutableStateOf("default") }
    var cmdlineOrBootconfig by remember { mutableStateOf("") }
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
            val config = SuSFSConfigHelper.loadConfig()
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
                loggingBusy = true
                try {
                    val ok = SuSFSConfigHelper.enableLog(newValue)
                    if (ok) {
                        loggingEnabled = newValue
                    } else {
                        snackbarHost.showSnackbar(operationFailedMsg)
                    }
                } finally {
                    loggingBusy = false
                }
            }
        }
    }

    val handleAvcLogSpoofingChange: (Boolean) -> Unit =
        remember(scope, snackbarHost, operationFailedMsg) {
            { newValue: Boolean ->
                scope.launch {
                    avcLogSpoofingBusy = true
                    try {
                        val ok = SuSFSConfigHelper.enableAvcLogSpoofing(newValue)
                        if (ok) {
                            avcLogSpoofingEnabled = newValue
                        } else {
                            snackbarHost.showSnackbar(operationFailedMsg)
                        }
                    } finally {
                        avcLogSpoofingBusy = false
                    }
                }
            }
        }

    val handleHideSusMntsChange: (Boolean) -> Unit =
        remember(scope, snackbarHost, operationFailedMsg) {
            { newValue: Boolean ->
                scope.launch {
                    hideSusMntsBusy = true
                    try {
                        val ok = SuSFSConfigHelper.hideSusMntsForNonSuProcs(newValue)
                        if (ok) {
                            hideSusMntsEnabled = newValue
                        } else {
                            snackbarHost.showSnackbar(operationFailedMsg)
                        }
                    } finally {
                        hideSusMntsBusy = false
                    }
                }
            }
        }

    val handleUnameSave: () -> Unit = remember(scope, snackbarHost, operationFailedMsg) {
        {
            val v = unameVersionInput.trim()
            val r = unameReleaseInput.trim()
            scope.launch {
                isLoading = true
                val ok = SuSFSConfigHelper.setUname(v, r)
                if (ok) {
                    unameVersion = v
                    unameRelease = r
                    showUnameDialog = false
                } else {
                    snackbarHost.showSnackbar(operationFailedMsg)
                }
                isLoading = false
            }
        }
    }

    val handleCmdlineSave: () -> Unit = remember(scope, snackbarHost, operationFailedMsg) {
        {
            val p = cmdlineInput.trim()
            scope.launch {
                isLoading = true
                val ok = SuSFSConfigHelper.setCmdlineOrBootconfig(p)
                if (ok) {
                    cmdlineOrBootconfig = p
                    showCmdlineDialog = false
                } else {
                    snackbarHost.showSnackbar(operationFailedMsg)
                }
                isLoading = false
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
        ) {
            item {
                Spacer(Modifier.height(topPadding))
            }

            if (hasLoadedConfig) {
                item {
                    SegmentedColumn {
                        item {
                            SettingsSwitchWidget(
                                iconPlaceholder = false,
                                title = stringResource(R.string.susfs_standard_logging),
                                description = stringResource(R.string.susfs_standard_logging_desc),
                                checked = loggingEnabled,
                                enabled = !isLoading && !loggingBusy,
                                onCheckedChange = handleLoggingChange
                            )
                        }

                        item {
                            SettingsSwitchWidget(
                                iconPlaceholder = false,
                                title = stringResource(R.string.susfs_standard_avc_log_spoofing),
                                description = stringResource(R.string.susfs_standard_avc_log_spoofing_desc),
                                checked = avcLogSpoofingEnabled,
                                enabled = !isLoading && !avcLogSpoofingBusy,
                                onCheckedChange = handleAvcLogSpoofingChange
                            )
                        }

                        item {
                            SettingsSwitchWidget(
                                iconPlaceholder = false,
                                title = stringResource(R.string.susfs_standard_hide_sus_mnts),
                                description = stringResource(R.string.susfs_standard_hide_sus_mnts_desc),
                                checked = hideSusMntsEnabled,
                                enabled = !isLoading && !hideSusMntsBusy,
                                onCheckedChange = handleHideSusMntsChange
                            )
                        }

                        item {
                            SettingsJumpPageWidget(
                                iconPlaceholder = false,
                                title = stringResource(R.string.susfs_standard_uname),
                                description = stringResource(
                                    R.string.susfs_standard_current_value,
                                    "$unameVersion / $unameRelease"
                                ),
                                enabled = !isLoading,
                                onClick = {
                                    unameVersionInput = unameVersion
                                    unameReleaseInput = unameRelease
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
                                    cmdlineOrBootconfig.ifBlank { "—" }
                                ),
                                enabled = !isLoading,
                                onClick = {
                                    cmdlineInput = cmdlineOrBootconfig
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
                        onClick = handleUnameSave,
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
                        onClick = handleCmdlineSave,
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
