package com.resukisu.resukisu.ui.wear

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.AltRoute
import androidx.compose.material.icons.twotone.Folder
import androidx.compose.material.icons.twotone.Map
import androidx.compose.material.icons.twotone.Restore
import androidx.compose.material.icons.twotone.Save
import androidx.compose.material.icons.twotone.Storage
import androidx.compose.material.icons.twotone.Tune
import androidx.compose.material.icons.twotone.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.ConfirmationDialogDefaults
import androidx.wear.compose.material3.FailureConfirmationDialog
import androidx.wear.compose.material3.SuccessConfirmationDialog
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.confirmationDialogCurvedText
import com.resukisu.resukisu.R
import com.resukisu.resukisu.ui.wear.component.settings.LazySegmentedColumn
import com.resukisu.resukisu.ui.wear.component.settings.SegmentedColumn
import com.resukisu.resukisu.ui.wear.component.settings.WearSettingsJumpPageWidget
import com.resukisu.resukisu.ui.wear.component.WearInfoCard
import com.resukisu.resukisu.ui.wear.component.WearList
import com.resukisu.resukisu.ui.wear.component.WearPageHeader
import com.resukisu.resukisu.ui.wear.component.WearSectionHeader
import com.resukisu.resukisu.ui.wear.component.settings.WearSettingsSwitchWidget
import com.resukisu.resukisu.ui.wear.component.WearSubPage
import com.resukisu.resukisu.ui.wear.component.rememberWearConfirmDialog
import com.resukisu.resukisu.ui.util.ActivityResumeEffect
import com.resukisu.resukisu.ui.viewmodel.SuSFSCommandReply
import com.resukisu.resukisu.ui.viewmodel.SuSFSUiAction
import com.resukisu.resukisu.ui.viewmodel.SuSFSUiEvent
import com.resukisu.resukisu.ui.viewmodel.SuSFSViewModel
import com.resukisu.resukisu.ui.viewmodel.awaitSuSFSBoolean
import com.resukisu.resukisu.ui.viewmodel.awaitSuSFSConfig
import com.resukisu.resukisu.ui.viewmodel.awaitSuSFSStatusInfo
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

internal enum class WearSuSFSSection(val title: Int, val icon: ImageVector) {
    Standard(R.string.susfs_tab_standard, Icons.TwoTone.Tune),
    Path(R.string.susfs_tab_sus_path, Icons.TwoTone.Folder),
    Kstat(R.string.susfs_tab_sus_kstat, Icons.TwoTone.Storage),
    Redirect(R.string.susfs_tab_open_redirect, Icons.AutoMirrored.TwoTone.AltRoute),
    Map(R.string.susfs_tab_sus_map, Icons.TwoTone.Map),
}

internal typealias WearSuSFSCommand = (SuSFSCommandReply) -> SuSFSUiAction

/** Wear presentation of the phone SUSFS manager, sharing its commands, config and resources. */
@Composable
internal fun WearSuSFSPage(onBack: () -> Unit) {
    val viewModel = koinViewModel<SuSFSViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var page by rememberSaveable { mutableStateOf("") }
    var sectionName by rememberSaveable { mutableStateOf(WearSuSFSSection.Standard.name) }
    val section = WearSuSFSSection.valueOf(sectionName)
    var selectedPath by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var pendingImport by rememberSaveable { mutableStateOf("") }
    val failed = stringResource(R.string.susfs_operation_failed)
    val succeeded = stringResource(R.string.susfs_operation_success)
    val importSuccess = stringResource(R.string.susfs_backup_import_success)
    val exportSuccess = stringResource(R.string.susfs_backup_export_success)
    val noData = stringResource(R.string.susfs_status_no_data)
    val batchResult = stringResource(R.string.susfs_entry_import_success)
    val backupFileName = stringResource(R.string.wear_susfs_backup_file_name)
    val config = state.config
    var confirmSuccessText by remember { mutableStateOf("") }
    var showConfirmSuccess by remember { mutableStateOf(false) }
    var showConfirmFailure by remember { mutableStateOf(false) }

    // Export, import and restoring defaults are one-shot file operations; their result shows as the
    // official confirmation overlay instead of the persistent in-list message the other commands use.
    fun submitConfirmed(command: WearSuSFSCommand, successMessage: String) {
        if (busy) return
        busy = true
        showConfirmFailure = false
        scope.launch {
            try {
                if (awaitSuSFSBoolean(viewModel, command)) {
                    awaitSuSFSConfig(viewModel) { SuSFSUiAction.Load(it) }
                    confirmSuccessText = successMessage
                    showConfirmSuccess = true
                } else showConfirmFailure = true
            } finally {
                busy = false
            }
        }
    }

    fun submit(command: WearSuSFSCommand, successMessage: String = succeeded, onSuccess: () -> Unit = {}) {
        if (busy) return
        busy = true
        message = null
        scope.launch {
            try {
                if (awaitSuSFSBoolean(viewModel, command)) {
                    awaitSuSFSConfig(viewModel) { SuSFSUiAction.Load(it) }
                    message = successMessage
                    onSuccess()
                } else message = failed
            } finally {
                busy = false
            }
        }
    }

    fun addEntries(commands: List<WearSuSFSCommand>) {
        if (busy || commands.isEmpty()) return
        busy = true
        message = null
        scope.launch {
            try {
                var successCount = 0
                commands.forEach { if (awaitSuSFSBoolean(viewModel, it)) successCount++ }
                if (successCount > 0) awaitSuSFSConfig(viewModel) { SuSFSUiAction.Load(it) }
                val failCount = commands.size - successCount
                message = if (commands.size > 1) batchResult.format(successCount, failCount)
                    else if (failCount == 0) succeeded else failed
                if (failCount == 0) page = "section"
            } finally { busy = false }
        }
    }

    ActivityResumeEffect(viewModel) {
        if (!busy) {
            awaitSuSFSConfig(viewModel) { SuSFSUiAction.Refresh(it) }
            awaitSuSFSStatusInfo(viewModel, forceRefresh = true)
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { if (it is SuSFSUiEvent.Error) message = it.message.ifBlank { failed } }
    }
    LaunchedEffect(config?.enabled) {
        if (config?.enabled == false && page in setOf("section", "add", "detail")) page = ""
    }
    val confirmImport = rememberWearConfirmDialog(stringResource(R.string.susfs_backup_import_confirm_title),
        stringResource(R.string.susfs_backup_import_confirm_message)) {
        submitConfirmed({ SuSFSUiAction.ImportConfig(pendingImport, it) }, importSuccess)
    }
    val restore = rememberWearConfirmDialog(stringResource(R.string.susfs_backup_restore_default),
        stringResource(R.string.susfs_backup_restore_default_desc)) {
        submitConfirmed({ SuSFSUiAction.RestoreDefault(it) }, succeeded)
    }
    fun importSelected(uri: String) {
        pendingImport = uri
        confirmImport.show()
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) submitConfirmed({ SuSFSUiAction.ExportConfig(uri.toString(), it) }, exportSuccess)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) importSelected(uri.toString())
    }
    // Plain SAF requests, as on the phone; watches without DocumentsUI get the built-in picker.
    fun launchPicker(export: Boolean) {
        runCatching {
            if (export) exportLauncher.launch(backupFileName) else importLauncher.launch(arrayOf("application/json"))
        }.onFailure { showConfirmFailure = true }
    }
    val route = page
    if (route.isNotEmpty()) {
        WearSubPage({ page = if (page in setOf("add", "detail")) "section" else "" }) {
            when (route) {
                "section" -> if (config != null && config.enabled) {
                    if (section == WearSuSFSSection.Standard) WearSuSFSStandardPage(config, busy, message, viewModel,
                        onBack = { page = "" }, onCommand = { command, onSuccess -> submit(command, onSuccess = onSuccess) })
                    else WearSuSFSEntriesPage(section, config, busy, message, onBack = { page = "" },
                        onAdd = { page = "add" }, onSelect = { selectedPath = it; page = "detail" })
                }
                "add" -> WearSuSFSAddPage(section, busy, message, viewModel,
                    onBack = { page = "section" }, onCommand = ::addEntries)
                "detail" -> if (config != null) WearSuSFSDetailPage(section, config, selectedPath, busy, message,
                    onBack = { page = "section" }) { command -> submit(command) { page = "section" } }
            }
        }
    } else WearList(isLoading = state.isLoading || busy, onBack = onBack) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.susfs_config_title)) }
        message?.let { item { WearInfoCard(spec) { Text(it) } } }
        item { WearSectionHeader(spec, stringResource(R.string.wear_susfs_status_controls)) }
        item {
            WearSettingsSwitchWidget(spec, stringResource(R.string.susfs_enable_config), config?.enabled == true,
                { enabled -> submit({ SuSFSUiAction.SetEnabled(enabled, it) }) }, enabled = config != null && !busy,
                icon = Icons.TwoTone.VisibilityOff,
                secondaryLabel = stringResource(R.string.susfs_enable_config_summary))
        }
        if (config?.enabled == false) item {
            WearInfoCard(spec) { Text(stringResource(R.string.susfs_config_disable_warning)) }
        }
        item {
            WearInfoCard(spec) {
                Text(stringResource(R.string.susfs_status_version))
                Text(state.statusInfo?.version?.ifBlank { noData } ?: noData)
                Text(stringResource(R.string.susfs_status_variant))
                Text(state.statusInfo?.variant?.ifBlank { noData } ?: noData)
                Text(stringResource(R.string.susfs_status_enabled_features))
                Text(state.statusInfo?.enabledFeatures?.ifBlank { noData } ?: noData)
            }
        }
        if (config?.enabled == true) item {
            WearSectionHeader(spec, stringResource(R.string.wear_susfs_features))
        }
        LazySegmentedColumn(if (config?.enabled == true) WearSuSFSSection.entries else emptyList(), { it.name }) { entry ->
            WearSettingsJumpPageWidget(spec, stringResource(entry.title), { sectionName = entry.name; page = "section" },
                icon = entry.icon)
        }
        item { WearSectionHeader(spec, stringResource(R.string.wear_susfs_backup_restore)) }
        SegmentedColumn(listOf("export", "import", "reset"), { it }) { action ->
            when (action) {
                "export" -> WearSettingsJumpPageWidget(spec, stringResource(R.string.susfs_backup_export), { launchPicker(true) },
                    icon = Icons.TwoTone.Save, description = stringResource(R.string.susfs_backup_description))
                "import" -> WearSettingsJumpPageWidget(spec, stringResource(R.string.susfs_backup_import), { launchPicker(false) },
                    icon = Icons.TwoTone.Restore, description = stringResource(R.string.susfs_restore_description))
                "reset" -> WearSettingsJumpPageWidget(spec, stringResource(R.string.susfs_backup_restore_default), restore::show,
                    icon = Icons.TwoTone.Restore, description = stringResource(R.string.susfs_backup_restore_default_desc))
            }
        }
    }
    val confirmationStyle = ConfirmationDialogDefaults.curvedTextStyle
    SuccessConfirmationDialog(visible = showConfirmSuccess, onDismissRequest = { showConfirmSuccess = false },
        curvedText = { confirmationDialogCurvedText(confirmSuccessText, confirmationStyle) })
    FailureConfirmationDialog(visible = showConfirmFailure, onDismissRequest = { showConfirmFailure = false },
        curvedText = { confirmationDialogCurvedText(failed, confirmationStyle) })
}
