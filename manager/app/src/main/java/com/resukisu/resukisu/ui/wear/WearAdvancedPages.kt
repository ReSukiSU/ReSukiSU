package com.resukisu.resukisu.ui.wear

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Add
import androidx.compose.material.icons.twotone.Apps
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Fingerprint
import androidx.compose.material.icons.twotone.Flag
import androidx.compose.material.icons.twotone.Folder
import androidx.compose.material.icons.twotone.FormatSize
import androidx.compose.material.icons.twotone.Search
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.Text
import com.resukisu.resukisu.R
import com.resukisu.resukisu.domain.model.UmountPath
import com.resukisu.resukisu.ui.screen.main.UninstallType
import com.resukisu.resukisu.ui.screen.toUmountFlagName
import com.resukisu.resukisu.ui.viewmodel.DynamicManagerOperation
import com.resukisu.resukisu.ui.viewmodel.DynamicManagerUiAction
import com.resukisu.resukisu.ui.viewmodel.DynamicManagerUiEvent
import com.resukisu.resukisu.ui.viewmodel.DynamicManagerViewModel
import com.resukisu.resukisu.ui.viewmodel.UmountManagerScreenViewModel
import com.resukisu.resukisu.ui.viewmodel.UmountManagerUiAction
import com.resukisu.resukisu.ui.viewmodel.UmountManagerUiEvent
import com.resukisu.resukisu.ui.wear.component.WearActionButton
import com.resukisu.resukisu.ui.wear.component.WearInfoCard
import com.resukisu.resukisu.ui.wear.component.WearList
import com.resukisu.resukisu.ui.wear.component.WearPageHeader
import com.resukisu.resukisu.ui.wear.component.WearSubPage
import com.resukisu.resukisu.ui.wear.component.WearTextInputPage
import com.resukisu.resukisu.ui.wear.component.rememberWearConfirmDialog
import com.resukisu.resukisu.ui.wear.component.settings.LazySegmentedColumn
import com.resukisu.resukisu.ui.wear.component.settings.WearSettingsJumpPageWidget
import com.resukisu.resukisu.ui.wear.component.settings.WearSettingsSwitchWidget
import org.koin.compose.viewmodel.koinViewModel

/** A text field of the dynamic manager page, edited on its own input page. */
private enum class DynamicManagerInput { Search, Size, Hash }

@Composable
internal fun WearDynamicManagerPage(onBack: () -> Unit) {
    val viewModel = koinViewModel<DynamicManagerViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var message by remember { mutableStateOf<String?>(null) }
    val failed = stringResource(R.string.operation_failed)
    val succeeded = stringResource(R.string.dynamic_manager_set_success)
    val cleared = stringResource(R.string.dynamic_manager_disabled_success)
    val invalidHash = stringResource(R.string.hash_must_be_64_chars)
    LaunchedEffect(viewModel) {
        viewModel.dispatch(DynamicManagerUiAction.Refresh)
        viewModel.events.collect { event ->
            if (event !is DynamicManagerUiEvent.OperationCompleted) return@collect
            message = when {
                !event.success -> failed
                event.operation == DynamicManagerOperation.Clear -> cleared
                else -> succeeded
            }
        }
    }
    var input by rememberSaveable { mutableStateOf<DynamicManagerInput?>(null) }
    var size by rememberSaveable { mutableStateOf("") }
    var hash by rememberSaveable { mutableStateOf("") }
    // A grant waits for its confirmation dialog.
    var pending by remember { mutableStateOf<DynamicManagerUiAction?>(null) }
    val grant = rememberWearConfirmDialog(
        stringResource(R.string.dynamic_manager_grant_confirm_title),
        stringResource(R.string.dynamic_manager_grant_confirm_message),
    ) {
        pending?.let(viewModel::dispatch)
        pending = null
    }
    val clear = rememberWearConfirmDialog(
        stringResource(R.string.dynamic_manager_clear_confirm_title),
        stringResource(R.string.dynamic_manager_clear_confirm_message),
    ) {
        viewModel.dispatch(DynamicManagerUiAction.Clear)
    }

    val editing = input
    if (editing != null) {
        val title = when (editing) {
            DynamicManagerInput.Search -> R.string.search_apps
            DynamicManagerInput.Size -> R.string.signature_size
            DynamicManagerInput.Hash -> R.string.signature_hash
        }
        val value = when (editing) {
            DynamicManagerInput.Search -> state.search
            DynamicManagerInput.Size -> size
            DynamicManagerInput.Hash -> hash
        }
        WearSubPage({ input = null }) {
            WearTextInputPage(stringResource(title), value) {
                when (editing) {
                    DynamicManagerInput.Search -> viewModel.dispatch(DynamicManagerUiAction.Search(it))
                    DynamicManagerInput.Size -> size = it
                    DynamicManagerInput.Hash -> hash = it
                }
                input = null
            }
        }
        return
    }

    WearList(isLoading = state.isLoading || state.isSubmitting, onBack = onBack) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.dynamic_manager_title)) }
        message?.let { item { WearInfoCard(spec) { Text(it) } } }
        item {
            WearInfoCard(spec) {
                val config = state.config
                Text(stringResource(R.string.dynamic_manager_current_status))
                if (config?.isValid == true) {
                    Text(stringResource(R.string.dynamic_manager_enabled_summary, config.size.toString()))
                    Text(config.hash)
                } else {
                    Text(stringResource(R.string.dynamic_manager_disabled))
                }
            }
        }
        item {
            WearActionButton(spec, Icons.TwoTone.Search, stringResource(R.string.search_apps),
                { input = DynamicManagerInput.Search })
        }
        LazySegmentedColumn(state.apps, { it.packageName }) { app ->
            WearSettingsSwitchWidget(
                spec,
                label = app.label,
                checked = app.isSelected || !app.isChangeable,
                onCheckedChange = { checked ->
                    if (checked) {
                        pending = DynamicManagerUiAction.SelectApp(app)
                        grant.show()
                    }
                },
                enabled = app.isChangeable,
                icon = Icons.TwoTone.Apps,
            )
        }
        item {
            WearSettingsJumpPageWidget(spec, stringResource(R.string.signature_size),
                { input = DynamicManagerInput.Size }, icon = Icons.TwoTone.FormatSize)
        }
        item {
            WearSettingsJumpPageWidget(spec, stringResource(R.string.signature_hash),
                { input = DynamicManagerInput.Hash }, icon = Icons.TwoTone.Fingerprint)
        }
        item {
            WearActionButton(spec, Icons.TwoTone.Settings, stringResource(R.string.dynamic_manager_manual_config), {
                val length = size.toIntOrNull()
                when {
                    length == null || length <= 0 -> message = failed
                    !hash.matches(Regex("[0-9a-fA-F]{64}")) -> message = invalidHash
                    else -> {
                        pending = DynamicManagerUiAction.SetManual(length, hash)
                        grant.show()
                    }
                }
            })
        }
        item {
            WearActionButton(spec, Icons.TwoTone.Delete, stringResource(R.string.dynamic_manager_clear_config), clear::show)
        }
    }
}

/** A text field of the umount path page, edited on its own input page. */
private enum class UmountInput { Path, Flags }

@Composable
internal fun WearUmountPage(onBack: () -> Unit) {
    val viewModel = koinViewModel<UmountManagerScreenViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var message by remember { mutableStateOf<String?>(null) }
    val failed = stringResource(R.string.operation_failed)
    var selected by remember { mutableStateOf<UmountPath?>(null) }
    val remove = rememberWearConfirmDialog(stringResource(R.string.delete), stringResource(R.string.confirm_delete)) {
        selected?.let { viewModel.dispatch(UmountManagerUiAction.Remove(it)) }
        selected = null
    }
    LaunchedEffect(viewModel) {
        viewModel.dispatch(UmountManagerUiAction.Refresh())
        viewModel.events.collect { if (it is UmountManagerUiEvent.Message) message = it.message }
    }
    var input by rememberSaveable { mutableStateOf<UmountInput?>(null) }
    var path by rememberSaveable { mutableStateOf("") }
    var flags by rememberSaveable { mutableStateOf("0") }

    val editing = input
    if (editing != null) {
        WearSubPage({ input = null }) {
            WearTextInputPage(
                stringResource(if (editing == UmountInput.Path) R.string.add_umount_path else R.string.umount_flags),
                if (editing == UmountInput.Path) path else flags,
            ) {
                if (editing == UmountInput.Path) path = it else flags = it
                input = null
            }
        }
        return
    }

    WearList(isLoading = state.isLoading, onBack = onBack) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.umount_path_manager)) }
        message?.let { item { WearInfoCard(spec) { Text(it) } } }
        // A TransformingLazyColumn item places only its last child, so each entry is a single button.
        items(state.umountPaths, key = { it.path }) { entry ->
            WearActionButton(spec, Icons.TwoTone.Delete, entry.path, {
                selected = entry
                remove.show()
            }, secondaryText = entry.flags.toUmountFlagName())
        }
        item {
            WearSettingsJumpPageWidget(spec, stringResource(R.string.add_umount_path), { input = UmountInput.Path },
                icon = Icons.TwoTone.Folder, description = path.ifEmpty { null })
        }
        item {
            WearSettingsJumpPageWidget(spec, stringResource(R.string.umount_flags), { input = UmountInput.Flags },
                icon = Icons.TwoTone.Flag, description = flags)
        }
        item { WearInfoCard(spec) { Text(stringResource(R.string.umount_flags_hint)) } }
        item {
            WearActionButton(spec, Icons.TwoTone.Add, stringResource(R.string.add), {
                val parsed = flags.toIntOrNull()
                if (!path.startsWith('/') || parsed == null) message = failed
                else viewModel.dispatch(UmountManagerUiAction.Add(path, parsed))
            })
        }
    }
}

/** Permanent uninstall or restoring the stock image; [onStart] receives whether it restores. */
@Composable
internal fun WearUninstallPage(onBack: () -> Unit, onStart: (restore: Boolean) -> Unit) {
    var selected by remember { mutableStateOf(UninstallType.PERMANENT) }
    val confirm = rememberWearConfirmDialog(stringResource(selected.title), stringResource(selected.message)) {
        onStart(selected == UninstallType.RESTORE_STOCK_IMAGE)
    }
    WearList(onBack = onBack) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.settings_uninstall)) }
        listOf(UninstallType.PERMANENT, UninstallType.RESTORE_STOCK_IMAGE).forEach { option ->
            item {
                WearActionButton(spec, option.icon, stringResource(option.title), {
                    selected = option
                    confirm.show()
                })
            }
        }
    }
}
