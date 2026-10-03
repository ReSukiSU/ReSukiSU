package com.resukisu.resukisu.ui.wear

import androidx.compose.material.icons.twotone.Apps
import androidx.compose.material.icons.twotone.Error
import com.resukisu.resukisu.ui.wear.component.WearStatusTone
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.twotone.Badge
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Extension
import androidx.compose.material.icons.twotone.Folder
import androidx.compose.material.icons.twotone.Language
import androidx.compose.material.icons.twotone.Person
import androidx.compose.material.icons.twotone.PlayArrow
import androidx.compose.material.icons.twotone.Security
import androidx.compose.material.icons.twotone.Tag
import androidx.compose.material.icons.twotone.Update
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.resukisu.resukisu.R
import com.resukisu.resukisu.domain.model.InstalledAppGroup
import com.resukisu.resukisu.domain.model.InstalledModule
import com.resukisu.resukisu.domain.model.AppProfile
import com.resukisu.resukisu.ui.wear.component.settings.WearSettingsJumpPageWidget
import com.resukisu.resukisu.ui.wear.component.WearAppProfileConfig
import com.resukisu.resukisu.ui.wear.component.WearSubPage
import com.resukisu.resukisu.ui.wear.component.WearActionButton
import com.resukisu.resukisu.ui.wear.component.WearDetailField
import com.resukisu.resukisu.ui.wear.component.WearIconText
import com.resukisu.resukisu.ui.wear.component.WearInfoCard
import com.resukisu.resukisu.ui.wear.component.WearModuleInfoCard
import com.resukisu.resukisu.ui.wear.component.WearList
import com.resukisu.resukisu.ui.wear.component.WearPageHeader
import com.resukisu.resukisu.ui.wear.component.WearScaledItem
import com.resukisu.resukisu.ui.wear.component.settings.WearSettingsSwitchWidget
import com.resukisu.resukisu.ui.wear.component.WearStatusItem
import com.resukisu.resukisu.ui.wear.component.rememberWearConfirmDialog
import com.resukisu.resukisu.ui.viewmodel.AppProfileUiAction
import com.resukisu.resukisu.ui.viewmodel.AppProfileUiEvent
import com.resukisu.resukisu.ui.viewmodel.AppProfileViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

@Composable
internal fun WearModuleDetail(
    module: InstalledModule?,
    error: String?,
    onBack: () -> Unit,
    onEnabledChange: (String, Boolean) -> Unit,
    onRemove: (String, Boolean) -> Unit,
    onWebUi: (InstalledModule) -> Unit,
    onExecute: (InstalledModule) -> Unit,
    onUpdate: (InstalledModule) -> Unit,
) {
    if (module == null) {
        WearList(onBack = onBack) { spec ->
            item {
                WearPageHeader(spec, stringResource(R.string.unknown_module))
            }
            if (!error.isNullOrBlank()) item {
                WearStatusItem(spec, Icons.TwoTone.Error, error, tone = WearStatusTone.ERROR)
            }
        }
        return
    }

    val unknown = stringResource(R.string.unknown)
    val removeDialog = rememberWearConfirmDialog(
        title = stringResource(R.string.uninstall),
        message = stringResource(
            if (module.metamodule) R.string.metamodule_uninstall_confirm
            else R.string.module_uninstall_confirm,
            module.name,
        ),
        onConfirm = { onRemove(module.id, true) },
    )
    WearList(onBack = onBack) { spec ->
        item {
            WearPageHeader(spec, module.name)
        }
        if (!error.isNullOrBlank()) item {
            WearStatusItem(spec, Icons.TwoTone.Error, error, tone = WearStatusTone.ERROR)
        }
        item {
            WearModuleInfoCard(spec) {
                WearDetailField(
                    Icons.TwoTone.Tag,
                    stringResource(R.string.module_version),
                    module.version.ifBlank { unknown },
                )
                WearDetailField(
                    Icons.TwoTone.Person,
                    stringResource(R.string.module_author),
                    module.author.ifBlank { unknown },
                )
                WearDetailField(
                    Icons.TwoTone.Folder,
                    stringResource(R.string.module_package),
                    module.id,
                )
            }
        }
        if (module.description.isNotBlank()) {
            item {
                WearModuleInfoCard(spec) {
                    Text(module.description, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item {
            WearSettingsSwitchWidget(
                spec,
                label = stringResource(R.string.wear_enabled),
                checked = module.enabled,
                onCheckedChange = { onEnabledChange(module.id, it) },
                enabled = !module.remove && !module.update,
                icon = Icons.TwoTone.Extension,
            )
        }
        item {
            WearActionButton(
                spec, Icons.TwoTone.Delete, stringResource(R.string.uninstall),
                onClick = removeDialog::show, enabled = !module.remove,
            )
        }
        if (module.hasWebUi) item {
            WearActionButton(
                spec, Icons.TwoTone.Language, stringResource(R.string.wear_webui),
                onClick = { onWebUi(module) }, enabled = module.enabled && !module.remove,
            )
        }
        if (module.hasActionScript) item {
            WearActionButton(
                spec, Icons.TwoTone.PlayArrow, stringResource(R.string.action),
                onClick = { onExecute(module) }, enabled = module.enabled && !module.remove,
            )
        }
        if (module.moduleUpdate?.zipUrl?.isNotBlank() == true) item {
            WearActionButton(
                spec, Icons.TwoTone.Update, stringResource(R.string.module_update),
                onClick = { onUpdate(module) }, enabled = !module.remove,
            )
        }
    }
}

@Composable
internal fun WearAppDetail(
    group: InstalledAppGroup?,
    isManager: Boolean,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onOpen: (WearRoute) -> Unit,
) {
    if (group == null) {
        WearList(onBack = onBack) { spec ->
            item {
                WearPageHeader(spec, stringResource(R.string.profile))
            }
            item { WearStatusItem(spec, Icons.TwoTone.Apps, stringResource(R.string.no_apps_found)) }
        }
        return
    }

    val viewModel = koinViewModel<AppProfileViewModel>(
        key = "wear-app-${group.uid}-${group.primaryPackageName}",
        parameters = { parametersOf(group.uid, group.primaryPackageName) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    var error by remember { mutableStateOf<String?>(null) }
    var editingProfile by rememberSaveable { mutableStateOf(false) }
    val failed = stringResource(R.string.failed_to_update_app_profile, group.mainApp.label)
    val failedSepolicy = stringResource(R.string.failed_to_update_sepolicy, group.mainApp.label)
    val suNotAllowed = stringResource(R.string.su_not_allowed, group.mainApp.label)
    val save: (AppProfile) -> Unit = { profile ->
        if (profile.allowSu && group.uid < 2000 && group.uid != 1000) error = suNotAllowed
        else viewModel.dispatch(AppProfileUiAction.Save(profile))
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            error = when (event) {
                is AppProfileUiEvent.Error -> failed
                AppProfileUiEvent.SepolicyUpdateFailed -> failedSepolicy
                AppProfileUiEvent.Saved -> null
            }
            // The app list refreshes only when a saved profile changed it.
            if (event is AppProfileUiEvent.Saved) onSaved()
        }
    }

    if (editingProfile) WearSubPage({ editingProfile = false }) {
        state.profile?.let { profile ->
            WearAppProfileConfig(profile, state.defaultUmountModules, state.sepolicyValid, error,
                { viewModel.dispatch(AppProfileUiAction.ValidateSepolicy(it)) }, save,
                onManageTemplates = { onOpen(WearRoute.Templates) },
                onViewTemplate = { onOpen(WearRoute.TemplateEditor(it, readOnly = true, creation = false)) },
                onBack = { editingProfile = false })
        }
    } else WearList(isLoading = state.isLoading, onBack = onBack) { spec ->
        item { WearPageHeader(spec, group.mainApp.label) }
        val profile = state.profile
        if (profile != null) {
            item {
                WearInfoCard(spec, modifier = Modifier.fillMaxWidth()) {
                    WearDetailField(Icons.TwoTone.Badge, group.mainApp.label, group.mainApp.displayIdentifier)
                    WearIconText(
                        Icons.TwoTone.Security,
                        stringResource(if (profile.allowSu) R.string.wear_allowed else R.string.wear_denied),
                    )
                }
            }
            item {
                WearSettingsSwitchWidget(
                    spec,
                    label = stringResource(R.string.superuser),
                    checked = profile.allowSu,
                    onCheckedChange = { save(profile.copy(allowSu = it)) },
                    enabled = !isManager && !group.isWebViewZygote,
                    icon = Icons.TwoTone.Security,
                )
            }
            item {
                WearSettingsJumpPageWidget(spec, stringResource(R.string.profile), { editingProfile = true },
                    icon = Icons.TwoTone.Security, enabled = !isManager)
            }
        } else {
            item { WearStatusItem(spec, Icons.TwoTone.Error, stringResource(R.string.operation_failed), tone = WearStatusTone.ERROR) }
        }
        error?.let { item { WearStatusItem(spec, Icons.TwoTone.Error, it, tone = WearStatusTone.ERROR) } }
    }
}
