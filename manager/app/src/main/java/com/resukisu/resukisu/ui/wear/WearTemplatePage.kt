package com.resukisu.resukisu.ui.wear

import androidx.compose.material.icons.twotone.Error
import com.resukisu.resukisu.ui.wear.component.WearStatusTone
import com.resukisu.resukisu.ui.wear.component.WearStatusItem
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Add
import androidx.compose.material.icons.twotone.ContentCopy
import androidx.compose.material.icons.twotone.ContentPaste
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Description
import androidx.compose.material.icons.twotone.Edit
import androidx.compose.material.icons.twotone.Flag
import androidx.compose.material.icons.twotone.Group
import androidx.compose.material.icons.twotone.Numbers
import androidx.compose.material.icons.twotone.Person
import androidx.compose.material.icons.twotone.Save
import androidx.compose.material.icons.twotone.SignalWifiOff
import androidx.compose.material.icons.twotone.Security
import androidx.compose.material.icons.twotone.Storage
import androidx.compose.material.icons.twotone.Sync
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.Text
import com.resukisu.resukisu.R
import com.resukisu.resukisu.domain.model.ProfileTemplate
import com.resukisu.resukisu.ui.wear.component.settings.WearSettingsJumpPageWidget
import com.resukisu.resukisu.ui.wear.component.settings.LazySegmentedColumn
import com.resukisu.resukisu.ui.wear.component.settings.SegmentedColumn
import com.resukisu.resukisu.ui.wear.component.*
import com.resukisu.resukisu.ui.viewmodel.*
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

/** The phone template list: local and synchronized templates, import, export and synchronization. */
@Composable
internal fun WearTemplatePage(onBack: () -> Unit, onOpenTemplate: (id: String, readOnly: Boolean, creation: Boolean) -> Unit) {
    val viewModel = koinViewModel<TemplateViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    var message by remember { mutableStateOf<String?>(null) }
    val imported = stringResource(R.string.app_profile_template_import_success)
    val emptyExport = stringResource(R.string.app_profile_template_export_empty)
    val emptyClipboard = stringResource(R.string.app_profile_template_import_empty)
    val exported = stringResource(R.string.app_profile_export_to_clipboard)
    val failed = stringResource(R.string.operation_failed)
    LaunchedEffect(viewModel) {
        viewModel.dispatch(TemplateUiAction.Refresh())
        viewModel.events.collect { event ->
            when (event) {
                TemplateUiEvent.ImportCompleted -> {
                    message = imported
                    viewModel.dispatch(TemplateUiAction.Refresh())
                }
                is TemplateUiEvent.Exported -> {
                    clipboard.setPrimaryClip(ClipData.newPlainText(exported, event.json))
                    message = exported
                }
                TemplateUiEvent.ExportEmpty -> message = emptyExport
                is TemplateUiEvent.Error -> message = event.message.ifBlank { failed }
            }
        }
    }
    WearList(isLoading = state.isRefreshing && state.templateList.isEmpty(), onBack = onBack) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.settings_profile_template)) }
        if (state.isOffline) item {
            WearChip(spec, stringResource(R.string.wear_template_offline),
                secondaryLabel = stringResource(R.string.wear_template_offline_local),
                icon = Icons.TwoTone.SignalWifiOff, emphasis = WearChipEmphasis.HIGH)
        }
        message?.let { item { WearInfoCard(spec) { Text(it) } } }
        item {
            WearActionButton(spec, Icons.TwoTone.Add, stringResource(R.string.app_profile_template_create),
                { onOpenTemplate("", false, true) })
        }
        item {
            WearActionButton(spec, Icons.TwoTone.Sync, stringResource(R.string.app_profile_template_sync), {
                message = null
                viewModel.dispatch(TemplateUiAction.Refresh(synchronize = true))
            }, enabled = !state.isRefreshing)
        }
        item {
            WearActionButton(spec, Icons.TwoTone.ContentPaste, stringResource(R.string.app_profile_import_from_clipboard), {
                val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                if (text.isNullOrBlank()) message = emptyClipboard else viewModel.dispatch(TemplateUiAction.Import(text))
            })
        }
        item { WearActionButton(spec, Icons.TwoTone.ContentCopy, exported, { viewModel.dispatch(TemplateUiAction.Export) }) }
        LazySegmentedColumn(state.templateList, { it.id }) { template ->
            WearSettingsJumpPageWidget(spec, template.name.ifBlank { template.id },
                { onOpenTemplate(template.id, !template.local, false) }, icon = Icons.TwoTone.Description,
                description = template.description.ifBlank { null })
        }
    }
}

@Composable
internal fun WearTemplateEditor(id: String, readOnly: Boolean, creation: Boolean, onBack: () -> Unit) {
    val viewModel = koinViewModel<TemplateEditorViewModel>(parameters = { parametersOf(id, readOnly, creation) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val failed = stringResource(R.string.app_profile_template_save_failed)
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(viewModel) { viewModel.events.collect { event -> when (event) {
        TemplateEditorUiEvent.Deleted, TemplateEditorUiEvent.Saved -> onBack()
        is TemplateEditorUiEvent.Error -> message = failed
    } } }
    val template = state.template
    val fields = listOf(R.string.app_profile_template_id, R.string.app_profile_template_name,
        R.string.app_profile_template_description, R.string.module_author, R.string.wear_uid, R.string.wear_gid,
        R.string.profile_namespace, R.string.profile_groups, R.string.profile_capabilities,
        R.string.profile_selinux_context, R.string.profile_selinux_rules, R.string.profile_flags)
    var input by rememberSaveable { mutableIntStateOf(0) }
    val delete = rememberWearConfirmDialog(stringResource(R.string.app_profile_template_delete), stringResource(R.string.confirm_delete)) {
        viewModel.dispatch(TemplateEditorUiAction.Delete)
    }
    val shownInput = input
    if (shownInput != 0) {
        WearSubPage({ input = 0 }) {
            if (readOnly || shownInput == R.string.app_profile_template_id && !creation) {
                WearList(onBack = { input = 0 }) { spec ->
                    item { WearPageHeader(spec, stringResource(shownInput)) }
                    template.field(shownInput).chunked(350).forEach { chunk ->
                        item { WearInfoCard(spec) { Text(chunk) } }
                    }
                }
            } else WearTextInputPage(stringResource(shownInput), template.field(shownInput), multiline = shownInput == R.string.profile_selinux_rules) { text ->
                runCatching { template.withField(shownInput, text) }.fold(
                    onSuccess = { viewModel.dispatch(TemplateEditorUiAction.Update(it)) }, onFailure = { message = failed })
                input = 0
            }
        }
    } else WearList(isLoading = state.loading, onBack = onBack) { spec ->
        item { WearPageHeader(spec, stringResource(if (readOnly) R.string.app_profile_template_view else R.string.app_profile_template_edit)) }
        if (state.loadFailure != null) item { WearStatusItem(spec, Icons.TwoTone.Error, failed, tone = WearStatusTone.ERROR) }
        message?.let { item { WearInfoCard(spec) { Text(it) } } }
        if (state.loadFailure == null) {
            // A TransformingLazyColumn item places only its last child, so the value is the widget's description.
            SegmentedColumn(fields, { it }) { field ->
                WearSettingsJumpPageWidget(spec, stringResource(field), { input = field },
                    icon = templateFieldIcon(field),
                    description = template.field(field).ifEmpty { null })
            }
            if (!readOnly) {
                item { WearActionButton(spec, Icons.TwoTone.Save, stringResource(R.string.app_profile_template_save), { viewModel.dispatch(TemplateEditorUiAction.Save) }) }
                if (!creation) item { WearActionButton(spec, Icons.TwoTone.Delete, stringResource(R.string.app_profile_template_delete), delete::show) }
            }
        }
    }
}

private fun templateFieldIcon(field: Int) = when (field) {
    R.string.app_profile_template_id, R.string.wear_uid, R.string.wear_gid -> Icons.TwoTone.Numbers
    R.string.app_profile_template_name -> Icons.TwoTone.Edit
    R.string.app_profile_template_description -> Icons.TwoTone.Description
    R.string.module_author -> Icons.TwoTone.Person
    R.string.profile_namespace -> Icons.TwoTone.Storage
    R.string.profile_groups -> Icons.TwoTone.Group
    R.string.profile_flags -> Icons.TwoTone.Flag
    else -> Icons.TwoTone.Security
}

private fun ProfileTemplate.field(field: Int): String = when (field) {
    R.string.app_profile_template_id -> id
    R.string.app_profile_template_name -> name
    R.string.app_profile_template_description -> description
    R.string.module_author -> author
    R.string.wear_uid -> uid.toString()
    R.string.wear_gid -> gid.toString()
    R.string.profile_namespace -> namespace.toString()
    R.string.profile_groups -> groups.joinToString(", ")
    R.string.profile_capabilities -> capabilities.joinToString(", ")
    R.string.profile_selinux_context -> context
    R.string.profile_selinux_rules -> rules.joinToString("\n")
    else -> flags.joinToString(", ")
}

private fun ProfileTemplate.withField(field: Int, value: String): ProfileTemplate {
    fun numbers(): List<Int> = value.split(Regex("[,\\s]+")).filter { it.isNotBlank() }.map(String::toInt)
    return when (field) {
        R.string.app_profile_template_id -> copy(id = value.trim())
        R.string.app_profile_template_name -> copy(name = value)
        R.string.app_profile_template_description -> copy(description = value)
        R.string.module_author -> copy(author = value)
        R.string.wear_uid -> copy(uid = value.toInt().also { require(it >= 0) })
        R.string.wear_gid -> copy(gid = value.toInt().also { require(it >= 0) })
        R.string.profile_namespace -> copy(namespace = value.toInt().also { require(it in 0..2) })
        R.string.profile_groups -> copy(groups = numbers().onEach { require(it >= 0) })
        R.string.profile_capabilities -> copy(capabilities = numbers().onEach { require(it in 0..40) })
        R.string.profile_selinux_context -> copy(context = value)
        R.string.profile_selinux_rules -> copy(rules = value.lines().filter { it.isNotBlank() })
        else -> copy(flags = numbers().onEach { require(it in 0 until com.resukisu.resukisu.Natives.Profile.RootProfileFlag.entries.size) })
    }
}
