package com.resukisu.resukisu.ui.susfs.subpages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Add
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
import androidx.compose.ui.unit.dp
import com.resukisu.resukisu.R
import com.resukisu.resukisu.data.susfs.SuSFSConfigHelper
import com.resukisu.resukisu.ui.component.EmptyStateCard
import com.resukisu.resukisu.ui.component.EntryDetailDialog
import com.resukisu.resukisu.ui.component.ManualAddDialog
import com.resukisu.resukisu.ui.component.settings.SegmentedColumn
import com.resukisu.resukisu.ui.component.settings.SettingsBaseWidget
import com.resukisu.resukisu.ui.component.settings.SettingsJumpPageWidget
import com.resukisu.resukisu.ui.component.settings.SettingsTextFieldWidget
import com.resukisu.resukisu.ui.component.settings.lazySegmentColumn
import com.resukisu.resukisu.ui.component.toImportedEntryLines
import com.resukisu.resukisu.ui.util.LocalSnackbarHost
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SusMapTab(
    nestedScrollConnection: NestedScrollConnection,
    topPadding: Dp,
    refreshToken: Int
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

    LaunchedEffect(refreshToken) {
        entries = SuSFSConfigHelper.loadConfig().sus_map
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
            SegmentedColumn {
                item {
                    SettingsBaseWidget(
                        iconPlaceholder = false,
                        title = stringResource(R.string.sus_maps_description_title),
                        description = stringResource(R.string.sus_maps_description_text),
                        descriptionColumnContent = {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.sus_maps_warning),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = stringResource(R.string.sus_maps_debug_info),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    )
                }

                item {
                    SettingsJumpPageWidget(
                        iconPlaceholder = false,
                        title = manualAddTitle,
                        enabled = !isLoading,
                        trailingIcon = Icons.TwoTone.Add,
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
        isLoading = isLoading,
        formContent = {
            SettingsTextFieldWidget(
                state = manualPath,
                title = pathLabel,
                useLabelAsPlaceholder = true,
                enabled = !isLoading,
                lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 4, maxHeightInLines = 8),
                renderBackgroundBlur = false
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
                        isLoading = false
                        scope.launch {
                            snackbarHost.showSnackbar(operationFailedMsg)
                        }
                    }
                    isLoading = false
                }
            },
            isLoading = isLoading
        )
    }
}
