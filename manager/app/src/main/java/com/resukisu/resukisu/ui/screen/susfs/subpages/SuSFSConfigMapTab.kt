package com.resukisu.resukisu.ui.screen.susfs.subpages

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.resukisu.resukisu.R
import com.resukisu.resukisu.data.susfs.SuSFSConfigHelper
import com.resukisu.resukisu.ui.component.settings.SettingsJumpPageWidget
import com.resukisu.resukisu.ui.component.settings.SettingsTextFieldWidget
import com.resukisu.resukisu.ui.screen.susfs.RegisterSuSFSRefresh
import com.resukisu.resukisu.ui.screen.susfs.SuSFSRefreshRegistrar
import com.resukisu.resukisu.ui.screen.susfs.component.EntryDetailDialog
import com.resukisu.resukisu.ui.screen.susfs.component.ManualAddDialog
import com.resukisu.resukisu.ui.screen.susfs.component.SuSFSDescriptionCard
import com.resukisu.resukisu.ui.screen.susfs.component.susfsEntryList
import com.resukisu.resukisu.ui.screen.susfs.component.toImportedEntryLines
import com.resukisu.resukisu.ui.util.LocalSnackbarHost
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SusMapTab(
    nestedScrollConnection: NestedScrollConnection,
    topPadding: Dp,
    onRegisterRefresh: SuSFSRefreshRegistrar,
) {
    val snackbarHost = LocalSnackbarHost.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var entries by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isLoading by remember { mutableStateOf(false) }

    var showManualAdd by remember { mutableStateOf(false) }
    var detailItem by remember { mutableStateOf<String?>(null) }

    val manualPath = remember { TextFieldState() }

    val subtypeSusMap = stringResource(R.string.susfs_map_subtype)
    val subtypes = listOf(subtypeSusMap)

    RegisterSuSFSRefresh(onRegisterRefresh) { config, _ ->
        entries = config.sus_map
    }

    LaunchedEffect(showManualAdd) {
        if (!showManualAdd) {
            manualPath.clearText()
        }
    }

    val manualAddTitle = stringResource(R.string.susfs_entry_manual_add)
    val detailTitle = stringResource(R.string.susfs_entry_detail)
    val pathLabel = stringResource(R.string.susfs_entry_path_label)
    val noEntriesMsg = stringResource(R.string.susfs_entry_no_entries)
    val noEntriesHint = stringResource(R.string.susfs_entry_no_entries_hint)
    val operationFailedMsg = stringResource(R.string.susfs_operation_failed)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
    ) {
        item {
            Spacer(Modifier.height(topPadding))
        }

        item {
            SuSFSDescriptionCard(
                title = stringResource(R.string.sus_maps_description_title),
                description = stringResource(R.string.sus_maps_description_text),
            ) {
                Text(
                    text = stringResource(R.string.sus_maps_warning),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = stringResource(R.string.sus_maps_debug_info),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        susfsEntryList(
            entries = entries.toList(),
            addEntryTitle = manualAddTitle,
            emptyTitle = noEntriesMsg,
            emptyDescription = noEntriesHint,
            entryKey = { it },
            onAddEntry = { showManualAdd = true },
        ) { path ->
            SettingsJumpPageWidget(
                iconPlaceholder = false,
                title = path,
                onClick = { detailItem = path },
            )
        }
    }

    ManualAddDialog(
        showDialog = showManualAdd,
        title = manualAddTitle,
        subtypes = subtypes,
        selectedSubtype = subtypeSusMap,
        onSubtypeChange = {},
        onDismiss = { showManualAdd = false },
        showImportFromFile = true,
        onImportFromFile = { importedPath -> manualPath.setTextAndPlaceCursorAtEnd(importedPath) },
        onConfirm = {
            val paths = manualPath.text.toString().toImportedEntryLines()
            if (paths.isEmpty()) return@ManualAddDialog
            scope.launch {
                isLoading = true
                var snackbarMessage: String? = null
                var successCount = 0
                var failCount = 0
                paths.forEach { path ->
                    if (SuSFSConfigHelper.addSusMap(path)) {
                        successCount++
                    } else {
                        failCount++
                    }
                }
                if (successCount > 0) {
                    entries = SuSFSConfigHelper.refreshConfig().sus_map
                }
                if (paths.size == 1) {
                    if (successCount > 0) {
                        showManualAdd = false
                    } else {
                        snackbarMessage = operationFailedMsg
                    }
                } else {
                    snackbarMessage = context.getString(R.string.susfs_entry_import_success, successCount, failCount)
                    if (failCount == 0) {
                        showManualAdd = false
                    }
                }
                isLoading = false
                snackbarMessage?.let { snackbarHost.showSnackbar(it) }
            }
        },
        formContent = {
            item {
                SettingsTextFieldWidget(
                    state = manualPath,
                    title = pathLabel,
                    useLabelAsPlaceholder = true,
                    enabled = !isLoading,
                    lineLimits = TextFieldLineLimits.MultiLine(
                        minHeightInLines = 4,
                        maxHeightInLines = 8
                    ),
                    renderBackgroundBlur = false
                )
            }
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
                    val ok = SuSFSConfigHelper.removeSusMap(path)
                    if (ok) {
                        entries = SuSFSConfigHelper.refreshConfig().sus_map
                        detailItem = null
                    } else {
                        isLoading = false
                        scope.launch {
                            snackbarHost.showSnackbar(operationFailedMsg)
                        }
                    }
                    isLoading = false
                }
            },
        )
    }
}
