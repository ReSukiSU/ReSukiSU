package com.resukisu.resukisu.ui.wear.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Description
import androidx.compose.material.icons.twotone.Security
import androidx.compose.material.icons.twotone.Save
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.Text
import com.resukisu.resukisu.Natives
import com.resukisu.resukisu.R
import com.resukisu.resukisu.domain.model.AppProfile
import com.resukisu.resukisu.profile.Capabilities
import com.resukisu.resukisu.profile.Groups
import com.resukisu.resukisu.toRawFlags
import com.resukisu.resukisu.toRootProfileFlags
import com.resukisu.resukisu.ui.wear.component.settings.LazySegmentedColumn
import com.resukisu.resukisu.ui.wear.component.settings.WearSettingsSwitchWidget
import com.resukisu.resukisu.ui.wear.component.settings.SegmentedColumn
import com.resukisu.resukisu.ui.wear.component.settings.WearChoicePage
import com.resukisu.resukisu.ui.wear.component.settings.WearSettingsJumpPageWidget
import com.resukisu.resukisu.ui.viewmodel.TemplateUiAction
import com.resukisu.resukisu.ui.viewmodel.TemplateViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

/** Wear presentation of the shared Manager profile modes and fields. */
@Composable
fun WearAppProfileConfig(
    profile: AppProfile,
    defaultUmountModules: Boolean,
    sepolicyValid: Boolean,
    message: String?,
    onValidateSepolicy: (String) -> Unit,
    onProfileChange: (AppProfile) -> Unit,
    onManageTemplates: () -> Unit,
    onViewTemplate: (String) -> Unit,
    onBack: () -> Unit,
) {
    val templateViewModel = koinViewModel<TemplateViewModel>(key = "wear-templates",
        parameters = { parametersOf(true) })
    val templates by templateViewModel.uiState.collectAsStateWithLifecycle()
    val localTemplates = templates.templateList.filter { it.local }
    LaunchedEffect(templateViewModel) { templateViewModel.dispatch(TemplateUiAction.Refresh()) }
    var page by rememberSaveable { mutableStateOf(0) }
    var rootMode by rememberSaveable {
        mutableStateOf(if (profile.rootUseDefault) R.string.profile_default
            else if (profile.rootTemplate != null) R.string.profile_template else R.string.profile_custom)
    }
    var inputError by remember { mutableStateOf(false) }
    var domain by rememberSaveable { mutableStateOf(profile.context) }
    var rules by rememberSaveable { mutableStateOf(profile.rules) }
    var rulesInput by rememberSaveable { mutableStateOf(false) }
    val failed = stringResource(R.string.operation_failed)
    val mode = if (profile.allowSu) rootMode else
        if (profile.nonRootUseDefault) R.string.profile_default else R.string.profile_custom
    val modeChoices = listOf(R.string.profile_default, R.string.profile_template, R.string.profile_custom)
        .filter { profile.allowSu || it != R.string.profile_template }
        .map { it.toString() to stringResource(it) }
    val namespaceChoices = listOf(R.string.profile_namespace_inherited,
        R.string.profile_namespace_global, R.string.profile_namespace_individual)
        .mapIndexed { index, label -> index.toString() to stringResource(label) }
    val back = { page = 0; inputError = false }

    val shownPage = page
    val shownRulesInput = rulesInput
    if (shownPage != 0) WearSubPage(if (shownRulesInput) ({ rulesInput = false }) else back) {
        when (shownPage) {
            R.string.profile -> WearChoicePage(stringResource(R.string.profile), modeChoices, mode.toString(), back) { value ->
                val selected = value.toInt()
                if (profile.allowSu) {
                    rootMode = selected
                    // Template mode is persisted only after a template has been selected.
                    if (selected != R.string.profile_template) onProfileChange(profile.copy(
                        rootUseDefault = selected == R.string.profile_default, rootTemplate = null))
                } else onProfileChange(profile.copy(nonRootUseDefault = selected == R.string.profile_default))
                back()
            }
            R.string.profile_template -> WearChoicePage(stringResource(shownPage),
                localTemplates.map { it.id to it.name.ifBlank { it.id } }, profile.rootTemplate.orEmpty(), back,
                message = message) { id ->
                localTemplates.firstOrNull { it.id == id }?.let { template ->
                    onProfileChange(profile.copy(rootUseDefault = false, rootTemplate = id,
                        uid = template.uid, gid = template.gid, groups = template.groups,
                        capabilities = template.capabilities, context = template.context,
                        rules = template.rules.joinToString("\n"), namespace = template.namespace))
                }
                back()
            }
            R.string.profile_namespace -> WearChoicePage(stringResource(shownPage), namespaceChoices,
                profile.namespace.toString(), back) {
                onProfileChange(profile.copy(namespace = it.toInt(), rootUseDefault = false)); back()
            }
            R.string.wear_uid, R.string.wear_gid -> {
                WearTextInputPage(stringResource(shownPage),
                    (if (shownPage == R.string.wear_uid) profile.uid else profile.gid).toString()) { value ->
                    val number = value.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }?.toIntOrNull()
                    if (number == null || number < 0) { back(); inputError = true }
                    else {
                        onProfileChange(if (shownPage == R.string.wear_uid) profile.copy(uid = number, rootUseDefault = false)
                            else profile.copy(gid = number, rootUseDefault = false))
                        back()
                    }
                }
            }
            R.string.profile_selinux_context -> {
                if (shownRulesInput) WearTextInputPage(stringResource(R.string.profile_selinux_rules), rules, true) {
                    rules = it; onValidateSepolicy(it); rulesInput = false
                } else WearList(onBack = back) { spec ->
                    item { WearPageHeader(spec, stringResource(shownPage)) }
                    message?.let { item { WearInfoCard(spec) { Text(it) } } }
                    SegmentedColumn(listOf(R.string.profile_selinux_domain, R.string.profile_selinux_rules), { it }) { field ->
                        WearSettingsJumpPageWidget(spec, stringResource(field), {
                            if (field == R.string.profile_selinux_rules) rulesInput = true
                            else page = R.string.profile_selinux_domain
                        }, description = if (field == R.string.profile_selinux_rules) rules else domain)
                    }
                    item { WearActionButton(spec, Icons.TwoTone.Save, stringResource(R.string.app_profile_template_save), {
                        onProfileChange(profile.copy(context = domain, rules = rules, rootUseDefault = false)); back()
                    }, enabled = domain.matches(Regex("^[a-z_]+:[a-z0-9_]+:[a-z0-9_]+(:[a-z0-9_]+)?$")) && sepolicyValid) }
                }
            }
            R.string.profile_selinux_domain -> WearTextInputPage(stringResource(shownPage), domain) {
                domain = it; page = R.string.profile_selinux_context
            }
            else -> WearList(onBack = back) { spec ->
                item { WearPageHeader(spec, stringResource(shownPage)) }
                message?.let { item { WearInfoCard(spec) { Text(it) } } }
                when (shownPage) {
                    R.string.profile_groups -> LazySegmentedColumn(Groups.entries.toList(), { it.gid }) { group ->
                        val selected = profile.groups.ifEmpty { listOf(Natives.ROOT_GID) }
                        WearSettingsSwitchWidget(spec, group.display, group.gid in selected, { checked ->
                            val updated = if (checked) selected + group.gid else selected - group.gid
                            onProfileChange(profile.copy(groups = updated.ifEmpty { listOf(Natives.ROOT_GID) }, rootUseDefault = false))
                        }, enabled = group.gid in selected || selected.size < 32, secondaryLabel = group.gid.toString())
                    }
                    R.string.profile_capabilities -> LazySegmentedColumn(Capabilities.entries.toList(), { it.cap }) { cap ->
                        WearSettingsSwitchWidget(spec, cap.display, cap.cap in profile.capabilities, { checked ->
                            onProfileChange(profile.copy(capabilities = if (checked) profile.capabilities + cap.cap
                                else profile.capabilities - cap.cap, rootUseDefault = false))
                        })
                    }
                    R.string.profile_flags -> SegmentedColumn(Natives.Profile.RootProfileFlag.entries.toList(), { it.ordinal }) { flag ->
                        val selected = profile.flags.toRootProfileFlags()
                        WearSettingsSwitchWidget(spec, flag.display, flag in selected, { checked ->
                            onProfileChange(profile.copy(flags = (if (checked) selected + flag else selected - flag).toRawFlags()))
                        }, secondaryLabel = stringResource(flag.desc))
                    }
                }
            }
        }
    } else WearList(onBack = onBack) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.profile)) }
        (message ?: if (inputError) failed else null)?.let { item { WearInfoCard(spec) { Text(it) } } }
        item { WearSettingsJumpPageWidget(spec, stringResource(R.string.profile), { page = R.string.profile },
            description = stringResource(mode), icon = Icons.TwoTone.Security) }
        if (!profile.allowSu) {
            item { WearSettingsSwitchWidget(spec, stringResource(R.string.profile_umount_modules),
                if (profile.nonRootUseDefault) defaultUmountModules else profile.umountModules,
                { onProfileChange(profile.copy(umountModules = it, nonRootUseDefault = false)) },
                enabled = !profile.nonRootUseDefault, secondaryLabel = stringResource(R.string.profile_umount_modules_summary)) }
        } else if (mode == R.string.profile_template) {
            item { WearSettingsJumpPageWidget(spec, stringResource(R.string.profile_template), { page = R.string.profile_template },
                description = templates.templateList.firstOrNull { it.id == profile.rootTemplate }?.name ?: profile.rootTemplate) }
            if (profile.rootTemplate != null) item { WearSettingsJumpPageWidget(spec, stringResource(R.string.app_profile_template_view),
                { onViewTemplate(profile.rootTemplate) }, icon = Icons.TwoTone.Description) }
            item { WearSettingsJumpPageWidget(spec, stringResource(R.string.manage_app_profile), onManageTemplates,
                icon = Icons.TwoTone.Description) }
        } else if (mode == R.string.profile_custom) {
            val fields = listOf(R.string.wear_uid, R.string.wear_gid, R.string.profile_groups,
                R.string.profile_capabilities, R.string.profile_namespace, R.string.profile_flags, R.string.profile_selinux_context)
            SegmentedColumn(fields, { it }) { field ->
                val value = when (field) {
                    R.string.wear_uid -> profile.uid.toString()
                    R.string.wear_gid -> profile.gid.toString()
                    R.string.profile_groups -> profile.groups.joinToString(", ")
                    R.string.profile_capabilities -> profile.capabilities.joinToString(", ")
                    R.string.profile_namespace -> namespaceChoices.getOrNull(profile.namespace)?.second
                    R.string.profile_flags -> profile.flags.toRootProfileFlags().joinToString { it.display }
                    else -> profile.context
                }
                WearSettingsJumpPageWidget(spec, stringResource(field), {
                    if (field == R.string.profile_selinux_context) {
                        domain = profile.context; rules = profile.rules; onValidateSepolicy(rules)
                    }
                    page = field
                }, description = value)
            }
        }
    }
}
