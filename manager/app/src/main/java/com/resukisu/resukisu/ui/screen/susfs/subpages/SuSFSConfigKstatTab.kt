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
import com.resukisu.resukisu.data.susfs.SusKstatItem
import com.resukisu.resukisu.data.susfs.SusKstatType
import com.resukisu.resukisu.ui.component.settings.SettingsBaseWidget
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
fun SusKstatTab(
    nestedScrollConnection: NestedScrollConnection,
    topPadding: Dp,
    onRegisterRefresh: SuSFSRefreshRegistrar,
) {
    val snackbarHost = LocalSnackbarHost.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var entries by remember { mutableStateOf<Set<SusKstatItem>>(emptySet()) }
    var isLoading by remember { mutableStateOf(false) }

    var showManualAdd by remember { mutableStateOf(false) }
    var detailItem by remember { mutableStateOf<SusKstatItem?>(null) }

    var selectedSubtype by remember { mutableStateOf("") }
    val manualPath = remember { TextFieldState() }
    val statIno = remember { TextFieldState() }
    val statDev = remember { TextFieldState() }
    val statNlink = remember { TextFieldState() }
    val statSize = remember { TextFieldState() }
    val statAtime = remember { TextFieldState() }
    val statAtimeNsec = remember { TextFieldState() }
    val statMtime = remember { TextFieldState() }
    val statMtimeNsec = remember { TextFieldState() }
    val statCtime = remember { TextFieldState() }
    val statCtimeNsec = remember { TextFieldState() }
    val statBlocks = remember { TextFieldState() }
    val statBlksize = remember { TextFieldState() }

    val subtypeNormal = stringResource(R.string.susfs_kstat_subtype_normal)
    val subtypeFullClone = stringResource(R.string.susfs_kstat_subtype_full_clone)
    val subtypeStatically = stringResource(R.string.susfs_kstat_subtype_statically)
    val subtypes = listOf(subtypeNormal, subtypeFullClone, subtypeStatically)

    RegisterSuSFSRefresh(onRegisterRefresh) { config, _ ->
        entries = config.sus_kstat
    }

    LaunchedEffect(showManualAdd) {
        if (showManualAdd) {
            if (selectedSubtype.isEmpty()) selectedSubtype = subtypeNormal
        } else {
            listOf(
                manualPath,
                statIno,
                statDev,
                statNlink,
                statSize,
                statAtime,
                statAtimeNsec,
                statMtime,
                statMtimeNsec,
                statCtime,
                statCtimeNsec,
                statBlocks,
                statBlksize
            ).forEach { it.clearText() }
        }
    }

    val manualAddTitle = stringResource(R.string.susfs_entry_manual_add)
    val detailTitle = stringResource(R.string.susfs_entry_detail)
    val pathLabel = stringResource(R.string.susfs_entry_path_label)
    val spoofTypeLabel = stringResource(R.string.susfs_kstat_spoof_type)
    val staticallyFieldsLabel = stringResource(R.string.susfs_kstat_statically_fields)
    val noEntriesMsg = stringResource(R.string.susfs_entry_no_entries)
    val noEntriesHint = stringResource(R.string.susfs_entry_no_entries_hint)
    val operationFailedMsg = stringResource(R.string.susfs_operation_failed)
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
    val defaultValueLabel = stringResource(R.string.susfs_value_default)

    fun SusKstatType.localizedLabel(): String {
        return when (this) {
            SusKstatType.Normal -> subtypeNormal
            SusKstatType.FullClone -> subtypeFullClone
            SusKstatType.Statically -> subtypeStatically
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

        item {
            SuSFSDescriptionCard(
                title = stringResource(R.string.kstat_config_description_title),
            ) {
                Text(
                    text = stringResource(R.string.kstat_config_description_add),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = stringResource(R.string.kstat_config_description_update),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = stringResource(R.string.kstat_config_description_update_full_clone),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = stringResource(R.string.kstat_config_description_add_statically),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        susfsEntryList(
            entries = entries.toList(),
            addEntryTitle = manualAddTitle,
            emptyTitle = noEntriesMsg,
            emptyDescription = noEntriesHint,
            entryKey = { it.path },
            onAddEntry = { showManualAdd = true },
        ) { item ->
            SettingsJumpPageWidget(
                iconPlaceholder = false,
                title = item.path,
                description = item.spoof_type.localizedLabel(),
                onClick = { detailItem = item },
            )
        }
    }

    ManualAddDialog(
        showDialog = showManualAdd,
        title = manualAddTitle,
        subtypes = subtypes,
        selectedSubtype = selectedSubtype,
        onSubtypeChange = { selectedSubtype = it },
        onDismiss = { showManualAdd = false },
        showImportFromFile = selectedSubtype != subtypeStatically,
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
                    val ok = when (selectedSubtype) {
                        subtypeFullClone -> SuSFSConfigHelper.addSusKstatFullClone(path)
                        subtypeStatically -> {
                            SuSFSConfigHelper.addSusKstatStatically(
                                path,
                                statIno.text.toString().trim().toLongOrNull(),
                                statDev.text.toString().trim().toLongOrNull(),
                                statNlink.text.toString().trim().toLongOrNull(),
                                statSize.text.toString().trim().toLongOrNull(),
                                statAtime.text.toString().trim().toLongOrNull(),
                                statAtimeNsec.text.toString().trim().toLongOrNull(),
                                statMtime.text.toString().trim().toLongOrNull(),
                                statMtimeNsec.text.toString().trim().toLongOrNull(),
                                statCtime.text.toString().trim().toLongOrNull(),
                                statCtimeNsec.text.toString().trim().toLongOrNull(),
                                statBlocks.text.toString().trim().toLongOrNull(),
                                statBlksize.text.toString().trim().toLongOrNull()
                            )
                        }
                        else -> SuSFSConfigHelper.addSusKstat(path)
                    }
                    if (ok) {
                        successCount++
                    } else {
                        failCount++
                    }
                }
                if (successCount > 0) {
                    entries = SuSFSConfigHelper.refreshConfig().sus_kstat
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
            item(key = "susfs_kstat_path") {
                SettingsTextFieldWidget(
                    state = manualPath,
                    title = pathLabel,
                    useLabelAsPlaceholder = true,
                    enabled = !isLoading,
                    lineLimits = if (selectedSubtype == subtypeStatically) {
                        TextFieldLineLimits.MultiLine(minHeightInLines = 4, maxHeightInLines = 8)
                    } else {
                        TextFieldLineLimits.SingleLine
                    },
                    renderBackgroundBlur = false
                )
            }
            if (selectedSubtype == subtypeStatically) {
                item(key = "susfs_kstat_static_fields") {
                    SettingsBaseWidget(
                        iconPlaceholder = false,
                        title = staticallyFieldsLabel,
                        renderBackgroundBlur = false,
                    )
                }
                listOf(
                    fieldInoLabel to statIno,
                    fieldDevLabel to statDev,
                    fieldNlinkLabel to statNlink,
                    fieldSizeLabel to statSize,
                    fieldAtimeLabel to statAtime,
                    fieldAtimeNsecLabel to statAtimeNsec,
                    fieldMtimeLabel to statMtime,
                    fieldMtimeNsecLabel to statMtimeNsec,
                    fieldCtimeLabel to statCtime,
                    fieldCtimeNsecLabel to statCtimeNsec,
                    fieldBlocksLabel to statBlocks,
                    fieldBlksizeLabel to statBlksize
                ).forEachIndexed { index, (label, state) ->
                    item(key = "susfs_kstat_static_$index") {
                        SettingsTextFieldWidget(
                            state = state,
                            title = label,
                            useLabelAsPlaceholder = true,
                            enabled = !isLoading,
                            lineLimits = TextFieldLineLimits.SingleLine,
                            renderBackgroundBlur = false
                        )
                    }
                }
            }
        }
    )

    detailItem?.let { item ->
        val fields = mutableListOf<Pair<String, String>>(
            pathLabel to item.path,
            spoofTypeLabel to item.spoof_type.localizedLabel()
        )
        item.statically?.let { st ->
            fields.add(fieldInoLabel to (st.ino?.toString() ?: defaultValueLabel))
            fields.add(fieldDevLabel to (st.dev?.toString() ?: defaultValueLabel))
            fields.add(fieldNlinkLabel to (st.nlink?.toString() ?: defaultValueLabel))
            fields.add(fieldSizeLabel to (st.size?.toString() ?: defaultValueLabel))
            fields.add(fieldAtimeLabel to (st.atime?.toString() ?: defaultValueLabel))
            fields.add(fieldAtimeNsecLabel to (st.atime_nsec?.toString() ?: defaultValueLabel))
            fields.add(fieldMtimeLabel to (st.mtime?.toString() ?: defaultValueLabel))
            fields.add(fieldMtimeNsecLabel to (st.mtime_nsec?.toString() ?: defaultValueLabel))
            fields.add(fieldCtimeLabel to (st.ctime?.toString() ?: defaultValueLabel))
            fields.add(fieldCtimeNsecLabel to (st.ctime_nsec?.toString() ?: defaultValueLabel))
            fields.add(fieldBlocksLabel to (st.blocks?.toString() ?: defaultValueLabel))
            fields.add(fieldBlksizeLabel to (st.blksize?.toString() ?: defaultValueLabel))
        }
        EntryDetailDialog(
            showDialog = true,
            title = detailTitle,
            fields = fields,
            onDismiss = { detailItem = null },
            onDelete = {
                scope.launch {
                    isLoading = true
                    val ok = SuSFSConfigHelper.removeSusKstat(item.path)
                    if (ok) {
                        entries = SuSFSConfigHelper.refreshConfig().sus_kstat
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
