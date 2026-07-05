package com.resukisu.resukisu.ui.susfs.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.resukisu.resukisu.R
import com.resukisu.resukisu.data.susfs.OpenRedirectItem
import com.resukisu.resukisu.data.susfs.SuSFSConfigHelper
import com.resukisu.resukisu.data.susfs.UidScheme
import com.resukisu.resukisu.ui.component.EmptyStateCard
import com.resukisu.resukisu.ui.component.EntryDetailDialog
import com.resukisu.resukisu.ui.component.ManualAddDialog
import com.resukisu.resukisu.ui.component.settings.SegmentedColumn
import com.resukisu.resukisu.ui.component.settings.SettingsJumpPageWidget
import com.resukisu.resukisu.ui.component.settings.lazySegmentColumn
import com.resukisu.resukisu.ui.util.LocalSnackbarHost
import kotlinx.coroutines.launch

/**
 * Open Redirect 标签页
 *
 * 渲染 open_redirect 条目：手动添加、列表展示和详情删除。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenRedirectTab(
    nestedScrollConnection: NestedScrollConnection,
    topPadding: Dp,
    refreshToken: Int
) {
    val snackbarHost = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()

    var entries by remember { mutableStateOf<Set<OpenRedirectItem>>(emptySet()) }
    var isLoading by remember { mutableStateOf(false) }

    var showManualAdd by remember { mutableStateOf(false) }
    var detailItem by remember { mutableStateOf<OpenRedirectItem?>(null) }

    var manualTarget by remember { mutableStateOf("") }
    var manualRedirected by remember { mutableStateOf("") }
    var manualUidScheme by remember { mutableStateOf(UidScheme.NonApp) }
    var uidDropdownExpanded by remember { mutableStateOf(false) }

    val subtypeOpenRedirect = stringResource(R.string.susfs_redirect_target_path)
    val subtypes = listOf(subtypeOpenRedirect)

    val uidSchemeOptions = listOf(
        UidScheme.NonApp to stringResource(R.string.susfs_uid_scheme_non_app),
        UidScheme.RootExceptSu to stringResource(R.string.susfs_uid_scheme_root_except_su),
        UidScheme.NonSu to stringResource(R.string.susfs_uid_scheme_non_su),
        UidScheme.UnmountedApp to stringResource(R.string.susfs_uid_scheme_unmounted_app),
        UidScheme.Unmounted to stringResource(R.string.susfs_uid_scheme_unmounted)
    )

    LaunchedEffect(refreshToken) {
        entries = SuSFSConfigHelper.loadConfig().open_redirect
    }

    LaunchedEffect(showManualAdd) {
        if (!showManualAdd) {
            manualTarget = ""
            manualRedirected = ""
            manualUidScheme = UidScheme.NonApp
            uidDropdownExpanded = false
        }
    }

    val manualAddTitle = stringResource(R.string.susfs_entry_manual_add)
    val detailTitle = stringResource(R.string.susfs_entry_detail)
    val targetPathLabel = stringResource(R.string.susfs_redirect_target_path)
    val redirectedPathLabel = stringResource(R.string.susfs_redirect_redirected_path)
    val uidSchemeLabel = stringResource(R.string.susfs_redirect_uid_scheme)
    val noEntriesMsg = stringResource(R.string.susfs_entry_no_entries)
    val operationFailedMsg = stringResource(R.string.susfs_operation_failed)
    val selectedUidLabel = uidSchemeOptions.first { it.first == manualUidScheme }.second

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
                key = { _, it -> "${it.target_path}|${it.redirected_path}|${it.uid_scheme.value}" }
            ) { _, item ->
                SettingsJumpPageWidget(
                    iconPlaceholder = false,
                    title = item.target_path,
                    description = "${item.redirected_path} · ${item.uid_scheme.name}",
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
        selectedSubtype = subtypeOpenRedirect,
        onSubtypeChange = {},
        onDismiss = { showManualAdd = false },
        onConfirm = {
            val target = manualTarget.trim()
            val redirected = manualRedirected.trim()
            if (target.isEmpty() || redirected.isEmpty()) return@ManualAddDialog
            scope.launch {
                isLoading = true
                val ok = SuSFSConfigHelper.addOpenRedirect(target, redirected, manualUidScheme)
                if (ok) {
                    entries = SuSFSConfigHelper.refreshConfig().open_redirect
                    showManualAdd = false
                } else {
                    snackbarHost.showSnackbar(operationFailedMsg)
                }
                isLoading = false
            }
        },
        isLoading = isLoading,
        formContent = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = manualTarget,
                    onValueChange = { manualTarget = it },
                    label = { Text(targetPathLabel) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )
                OutlinedTextField(
                    value = manualRedirected,
                    onValueChange = { manualRedirected = it },
                    label = { Text(redirectedPathLabel) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )
                ExposedDropdownMenuBox(
                    expanded = uidDropdownExpanded,
                    onExpandedChange = { uidDropdownExpanded = !uidDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedUidLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(uidSchemeLabel) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = uidDropdownExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                        enabled = !isLoading,
                        shape = RoundedCornerShape(8.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = uidDropdownExpanded,
                        onDismissRequest = { uidDropdownExpanded = false }
                    ) {
                        uidSchemeOptions.forEach { (scheme, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    manualUidScheme = scheme
                                    uidDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    )

    detailItem?.let { item ->
        EntryDetailDialog(
            showDialog = true,
            title = detailTitle,
            fields = listOf(
                targetPathLabel to item.target_path,
                redirectedPathLabel to item.redirected_path,
                uidSchemeLabel to item.uid_scheme.name
            ),
            onDismiss = { detailItem = null },
            onDelete = {
                scope.launch {
                    isLoading = true
                    val ok = SuSFSConfigHelper.delOpenRedirect(item.target_path)
                    if (ok) {
                        entries = SuSFSConfigHelper.refreshConfig().open_redirect
                        detailItem = null
                    } else {
                        snackbarHost.showSnackbar(operationFailedMsg)
                    }
                    isLoading = false
                }
            },
            isLoading = isLoading
        )
    }
}
