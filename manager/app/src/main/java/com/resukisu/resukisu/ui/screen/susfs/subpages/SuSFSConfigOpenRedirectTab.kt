package com.resukisu.resukisu.ui.screen.susfs.subpages

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.resukisu.resukisu.R
import com.resukisu.resukisu.data.susfs.OpenRedirectItem
import com.resukisu.resukisu.data.susfs.SuSFSConfigHelper
import com.resukisu.resukisu.data.susfs.UidScheme
import com.resukisu.resukisu.ui.component.EntryDetailDialog
import com.resukisu.resukisu.ui.component.ManualAddDialog
import com.resukisu.resukisu.ui.component.settings.SettingsDropdownWidget
import com.resukisu.resukisu.ui.component.settings.SettingsJumpPageWidget
import com.resukisu.resukisu.ui.component.settings.SettingsTextFieldWidget
import com.resukisu.resukisu.ui.component.susfs.SuSFSDescriptionCard
import com.resukisu.resukisu.ui.component.susfs.susfsEntryList
import com.resukisu.resukisu.ui.util.LocalSnackbarHost
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenRedirectTab(
    nestedScrollConnection: NestedScrollConnection,
    topPadding: Dp,
    refreshToken: Int,
) {
    val snackbarHost = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()

    var entries by remember { mutableStateOf<Set<OpenRedirectItem>>(emptySet()) }
    var isLoading by remember { mutableStateOf(false) }
    var showManualAdd by remember { mutableStateOf(false) }
    var detailItem by remember { mutableStateOf<OpenRedirectItem?>(null) }

    val manualTarget = remember { TextFieldState() }
    val manualRedirected = remember { TextFieldState() }
    var manualUidScheme by remember { mutableStateOf(UidScheme.NonApp) }

    val uidSchemeOptions = listOf(
        UidScheme.NonApp to stringResource(R.string.susfs_uid_scheme_non_app),
        UidScheme.RootExceptSu to stringResource(R.string.susfs_uid_scheme_root_except_su),
        UidScheme.NonSu to stringResource(R.string.susfs_uid_scheme_non_su),
        UidScheme.UnmountedApp to stringResource(R.string.susfs_uid_scheme_unmounted_app),
        UidScheme.Unmounted to stringResource(R.string.susfs_uid_scheme_unmounted),
    )

    LaunchedEffect(refreshToken) {
        entries = SuSFSConfigHelper.loadConfig().open_redirect
    }

    LaunchedEffect(showManualAdd) {
        if (!showManualAdd) {
            manualTarget.clearText()
            manualRedirected.clearText()
            manualUidScheme = UidScheme.NonApp
        }
    }

    val manualAddTitle = stringResource(R.string.susfs_entry_manual_add)
    val detailTitle = stringResource(R.string.susfs_entry_detail)
    val targetPathLabel = stringResource(R.string.susfs_redirect_target_path)
    val redirectedPathLabel = stringResource(R.string.susfs_redirect_redirected_path)
    val uidSchemeLabel = stringResource(R.string.susfs_redirect_uid_scheme)
    val noEntriesMsg = stringResource(R.string.susfs_entry_no_entries)
    val noEntriesHint = stringResource(R.string.susfs_entry_no_entries_hint)
    val operationFailedMsg = stringResource(R.string.susfs_operation_failed)
    val selectedUidLabel = uidSchemeOptions.first { it.first == manualUidScheme }.second

    fun UidScheme.localizedLabel(): String {
        return uidSchemeOptions.first { it.first == this }.second
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection),
    ) {
        item {
            Spacer(Modifier.height(topPadding))
        }
        item {
            SuSFSDescriptionCard(
                title = stringResource(R.string.susfs_tab_open_redirect),
                description = stringResource(R.string.susfs_redirect_description),
            ) {
                Text(
                    text = stringResource(R.string.susfs_redirect_uid_schemes_description),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = stringResource(R.string.susfs_redirect_important_notes),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        susfsEntryList(
            entries = entries.toList(),
            addEntryTitle = manualAddTitle,
            emptyTitle = noEntriesMsg,
            emptyDescription = noEntriesHint,
            entryKey = { "${it.target_path}|${it.redirected_path}|${it.uid_scheme.value}" },
            onAddEntry = { showManualAdd = true },
        ) { item ->
            SettingsJumpPageWidget(
                iconPlaceholder = false,
                title = item.target_path,
                description = stringResource(
                    R.string.susfs_redirect_entry_description,
                    item.redirected_path,
                    item.uid_scheme.localizedLabel(),
                ),
                onClick = { detailItem = item },
            )
        }
    }

    ManualAddDialog(
        showDialog = showManualAdd,
        title = manualAddTitle,
        subtypes = emptyList(),
        selectedSubtype = "",
        onSubtypeChange = {},
        onDismiss = { showManualAdd = false },
        onConfirm = {
            val target = manualTarget.text.toString().trim()
            val redirected = manualRedirected.text.toString().trim()
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
        formContent = {
            item {
                SettingsTextFieldWidget(
                    state = manualTarget,
                    title = targetPathLabel,
                    useLabelAsPlaceholder = true,
                    enabled = !isLoading,
                    lineLimits = TextFieldLineLimits.SingleLine,
                    renderBackgroundBlur = false,
                )
            }
            item {
                SettingsTextFieldWidget(
                    state = manualRedirected,
                    title = redirectedPathLabel,
                    useLabelAsPlaceholder = true,
                    enabled = !isLoading,
                    lineLimits = TextFieldLineLimits.SingleLine,
                    renderBackgroundBlur = false,
                )
            }
            item {
                SettingsDropdownWidget(
                    title = uidSchemeLabel,
                    description = selectedUidLabel,
                    iconPlaceholder = false,
                    choice = uidSchemeOptions.indexOfFirst { it.first == manualUidScheme }
                        .coerceAtLeast(0),
                    data = uidSchemeOptions.map { it.second },
                    onChoiceChange = { index -> manualUidScheme = uidSchemeOptions[index].first },
                )
            }
        },
    )

    detailItem?.let { item ->
        EntryDetailDialog(
            showDialog = true,
            title = detailTitle,
            fields = listOf(
                targetPathLabel to item.target_path,
                redirectedPathLabel to item.redirected_path,
                uidSchemeLabel to item.uid_scheme.localizedLabel(),
            ),
            onDismiss = { detailItem = null },
            onDelete = {
                scope.launch {
                    isLoading = true
                    val ok = SuSFSConfigHelper.removeOpenRedirect(item.target_path)
                    if (ok) {
                        entries = SuSFSConfigHelper.refreshConfig().open_redirect
                        detailItem = null
                    } else {
                        snackbarHost.showSnackbar(operationFailedMsg)
                    }
                    isLoading = false
                }
            },
        )
    }
}
