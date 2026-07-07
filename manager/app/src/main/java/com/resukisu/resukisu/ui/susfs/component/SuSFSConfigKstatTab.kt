package com.resukisu.resukisu.ui.susfs.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.resukisu.resukisu.data.susfs.SusKstatItem
import com.resukisu.resukisu.ui.component.EmptyStateCard
import com.resukisu.resukisu.ui.component.EntryDetailDialog
import com.resukisu.resukisu.ui.component.ManualAddDialog
import com.resukisu.resukisu.ui.component.toImportedEntryLines
import com.resukisu.resukisu.ui.component.settings.SegmentedColumn
import com.resukisu.resukisu.ui.component.settings.SettingsJumpPageWidget
import com.resukisu.resukisu.ui.component.settings.lazySegmentColumn
import com.resukisu.resukisu.ui.util.LocalSnackbarHost
import kotlinx.coroutines.launch

/**
 * SUS Kstat 标签页
 *
 * 渲染 sus_kstat 条目，支持三种 spoof_type 的手动添加、列表展示和详情删除。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SusKstatTab(
    nestedScrollConnection: NestedScrollConnection,
    topPadding: Dp,
    refreshToken: Int
) {
    val snackbarHost = LocalSnackbarHost.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var entries by remember { mutableStateOf<Set<SusKstatItem>>(emptySet()) }
    var isLoading by remember { mutableStateOf(false) }

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

    LaunchedEffect(refreshToken) {
        entries = SuSFSConfigHelper.loadConfig().sus_kstat
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

    val manualAddTitle = stringResource(R.string.susfs_entry_manual_add)
    val detailTitle = stringResource(R.string.susfs_entry_detail)
    val pathLabel = stringResource(R.string.susfs_entry_path_label)
    val spoofTypeLabel = stringResource(R.string.susfs_kstat_spoof_type)
    val staticallyFieldsLabel = stringResource(R.string.susfs_kstat_statically_fields)
    val noEntriesMsg = stringResource(R.string.susfs_entry_no_entries)
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
                        title = manualAddTitle,
                        enabled = !isLoading,
                        trailingIcon = Icons.Filled.Add,
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
                key = { _, it -> it.path }
            ) { _, item ->
                SettingsJumpPageWidget(
                    iconPlaceholder = false,
                    title = item.path,
                    description = item.spoof_type.name,
                    enabled = !isLoading,
                    onClick = { detailItem = item }
                )
            }
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
        onImportFromFile = { importedPath -> manualPath = importedPath },
        onConfirm = {
            val paths = manualPath.toImportedEntryLines()
            if (paths.isEmpty()) return@ManualAddDialog
            scope.launch {
                isLoading = true
                var snackbarMessage: String? = null
                var successCount = 0
                var failCount = 0
                paths.forEach { path ->
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
                        singleLine = false,
                        minLines = 4,
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
            isLoading = isLoading
        )
    }
}
