package com.resukisu.resukisu.ui.wear

import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.Article
import androidx.compose.material.icons.twotone.Adb
import androidx.compose.material.icons.twotone.Apps
import androidx.compose.material.icons.twotone.AspectRatio
import androidx.compose.material.icons.twotone.Build
import androidx.compose.material.icons.twotone.BugReport
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.ElectricalServices
import androidx.compose.material.icons.twotone.Extension
import androidx.compose.material.icons.twotone.Fence
import androidx.compose.material.icons.twotone.FolderDelete
import androidx.compose.material.icons.twotone.FolderOff
import androidx.compose.material.icons.twotone.FolderOpen
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material.icons.twotone.FormatSize
import androidx.compose.material.icons.twotone.Language
import androidx.compose.material.icons.twotone.Palette
import androidx.compose.material.icons.twotone.Policy
import androidx.compose.material.icons.twotone.RemoveCircle
import androidx.compose.material.icons.twotone.RemoveModerator
import androidx.compose.material.icons.twotone.RestartAlt
import androidx.compose.material.icons.twotone.Science
import androidx.compose.material.icons.twotone.Security
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material.icons.twotone.Translate
import androidx.compose.material.icons.twotone.Update
import androidx.compose.material.icons.twotone.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Text
import com.resukisu.resukisu.R
import com.resukisu.resukisu.domain.model.KernelStatus
import com.resukisu.resukisu.ui.wear.component.settings.LazySegmentedColumn
import com.resukisu.resukisu.ui.wear.component.settings.SegmentedColumn
import com.resukisu.resukisu.ui.wear.component.settings.WearSettingsJumpPageWidget
import com.resukisu.resukisu.ui.wear.component.WearList
import com.resukisu.resukisu.ui.wear.component.WearPageHeader
import com.resukisu.resukisu.ui.wear.component.WearScaledItem
import com.resukisu.resukisu.ui.wear.component.WearSectionHeader
import com.resukisu.resukisu.ui.wear.component.settings.WearSettingsSwitchWidget
import com.resukisu.resukisu.ui.wear.component.wearGroupGap
import com.resukisu.resukisu.ui.theme.ThemeConfig
import com.resukisu.resukisu.ui.viewmodel.SettingsUiAction
import com.resukisu.resukisu.ui.viewmodel.SettingsUiState
import com.resukisu.resukisu.ui.viewmodel.WearPreferences
import org.koin.compose.koinInject

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

private fun settingsCategoryTitle(category: WearSettingsCategory?): Int = when (category) {
    WearSettingsCategory.General -> R.string.wear_general
    WearSettingsCategory.Security -> R.string.wear_security
    WearSettingsCategory.Advanced -> R.string.advanced_options
    WearSettingsCategory.Display -> R.string.wear_display
    null -> R.string.settings
}

private enum class SettingsSection(val title: Int) {
    Updates(R.string.wear_settings_updates),
    LinksFiles(R.string.wear_settings_links_files),
    AppBehavior(R.string.wear_settings_app_behavior),
    Diagnostics(R.string.wear_settings_diagnostics),
    AccessProfiles(R.string.wear_settings_access_profiles),
    MountIsolation(R.string.wear_settings_mount_isolation),
    LoggingPrivacy(R.string.wear_settings_logging_privacy),
    PrivilegesStartup(R.string.wear_settings_privileges_startup),
    ManagerMounts(R.string.wear_settings_manager_mounts),
    KernelMaintenance(R.string.wear_settings_kernel_maintenance),
}

/** A settings switch row: section, label, phone summary, current value and dispatched action. */
private data class SettingsToggle(
    val section: SettingsSection,
    val label: Int,
    val summary: Int?,
    val checked: Boolean,
    val action: SettingsUiAction,
)

private data class SettingsPageLink(
    val section: SettingsSection,
    val label: Int,
    val route: WearRoute,
    val summary: String? = null,
)

private fun settingsToggleIcon(label: Int): ImageVector = when (label) {
    R.string.settings_check_manager_update -> Icons.TwoTone.Update
    R.string.settings_check_beta_update -> Icons.TwoTone.Science
    R.string.settings_check_module_update -> Icons.TwoTone.Extension
    R.string.settings_soft_reboot -> Icons.TwoTone.RestartAlt
    R.string.icon_switch_title -> Icons.TwoTone.Apps
    R.string.settings_kernel_umount -> Icons.TwoTone.RemoveCircle
    R.string.settings_umount_modules_default -> Icons.TwoTone.FolderDelete
    R.string.settings_sulog -> Icons.AutoMirrored.TwoTone.Article
    R.string.settings_selinux_hide -> Icons.TwoTone.Policy
    R.string.settings_adb_root -> Icons.TwoTone.Adb
    R.string.settings_auto_jailbreak -> Icons.TwoTone.ElectricalServices
    else -> Icons.TwoTone.Settings
}

private fun settingsCategoryIcon(category: WearSettingsCategory): ImageVector = when (category) {
    WearSettingsCategory.General -> Icons.TwoTone.Settings
    WearSettingsCategory.Security -> Icons.TwoTone.Security
    WearSettingsCategory.Advanced -> Icons.TwoTone.Build
    WearSettingsCategory.Display -> Icons.TwoTone.Palette
}

private fun settingsRouteIcon(route: WearRoute): ImageVector = when (route) {
    WearRoute.DynamicManager -> Icons.TwoTone.Security
    WearRoute.Color -> Icons.TwoTone.Palette
    WearRoute.Dpi -> Icons.TwoTone.FormatSize
    WearRoute.Logs -> Icons.AutoMirrored.TwoTone.Article
    WearRoute.Bugreport -> Icons.TwoTone.BugReport
    WearRoute.Templates -> Icons.TwoTone.Fence
    WearRoute.Umount -> Icons.TwoTone.FolderOff
    WearRoute.Uninstall -> Icons.TwoTone.Delete
    is WearRoute.Selection -> when (route.selection) {
        WearSelection.ScreenShape -> Icons.TwoTone.AspectRatio
        WearSelection.Language -> Icons.TwoTone.Translate
        WearSelection.LinkMode -> Icons.TwoTone.Language
        WearSelection.PickerMode -> Icons.TwoTone.FolderOpen
        WearSelection.SuCompat -> Icons.TwoTone.RemoveModerator
    }
    else -> Icons.TwoTone.Settings
}

/**
 * The settings root lists the available categories and About; each category groups its switches and
 * navigation entries by purpose, omitting sections with no available controls.
 */
@Composable
internal fun WearSettingsPage(
    state: SettingsUiState,
    status: KernelStatus,
    message: String?,
    onAction: (SettingsUiAction) -> Unit,
    onOpen: (WearRoute) -> Unit,
    onBack: () -> Unit,
    category: WearSettingsCategory? = null,
    preferences: WearPreferences = WearPreferences(),
    susfsAvailable: Boolean = false,
    listState: TransformingLazyColumnState = rememberTransformingLazyColumnState(),
    backToTop: Boolean = false,
) {
    val themeConfig = koinInject<ThemeConfig>()
    val languageSummary = state.currentAppLocale?.let { it.getDisplayName(it) }
        ?: stringResource(R.string.language_system_default)
    val pickerSummary = pickerModeLabel(preferences.picker)
    val linkSummary = linkModeLabel(preferences.link)
    val suCompatSummary = suCompatModeLabel(state.suCompatMode)
    val colorSummary = if (state.useDynamicColor) stringResource(R.string.dynamic_color_title)
        else "#%06X".format(themeConfig.seedColor and 0xFFFFFF)
    val dpiSummary = stringResource(R.string.wear_ui_scale, state.currentDpi.toFloat() / state.systemDpi.coerceAtLeast(1))
    val shapeSummary = screenShapeLabel(preferences.shape)
    val version = wearAppVersion()
    WearList(onBack = onBack, snap = true, listState = listState, backToTop = backToTop) { spec ->
        item { WearPageHeader(spec, stringResource(settingsCategoryTitle(category))) }
        if (!message.isNullOrBlank()) item { WearScaledItem(spec) { Text(message) } }
        if (category == null) {
            val categories = buildList {
                add(WearSettingsCategory.General)
                if (status.isFullFeatured) {
                    add(WearSettingsCategory.Security)
                    add(WearSettingsCategory.Advanced)
                }
                add(WearSettingsCategory.Display)
            }
            LazySegmentedColumn(categories, { it.name }) { entry ->
                WearSettingsJumpPageWidget(spec, stringResource(settingsCategoryTitle(entry)),
                    { onOpen(WearRoute.Settings(entry)) }, icon = settingsCategoryIcon(entry))
            }
            if (status.isFullFeatured && susfsAvailable) item {
                WearSettingsJumpPageWidget(spec, stringResource(R.string.susfs_config_setting_title),
                    { onOpen(WearRoute.SuSFS) }, icon = Icons.TwoTone.VisibilityOff)
            }
            wearGroupGap("about-gap")
            item {
                WearSettingsJumpPageWidget(spec, stringResource(R.string.about), { onOpen(WearRoute.About) },
                    icon = Icons.TwoTone.Info, description = version)
            }
        } else if (category == WearSettingsCategory.Display) {
            item { WearSectionHeader(spec, stringResource(R.string.wear_display_appearance)) }
            item {
                WearSettingsJumpPageWidget(spec, stringResource(R.string.theme_color), { onOpen(WearRoute.Color) },
                    icon = settingsRouteIcon(WearRoute.Color), description = colorSummary)
            }
            item { WearSectionHeader(spec, stringResource(R.string.wear_display_screen_layout)) }
            SegmentedColumn(listOf(WearRoute.Dpi, WearRoute.Selection(WearSelection.ScreenShape)), { it.toString() }) { route ->
                WearSettingsJumpPageWidget(spec,
                    stringResource(if (route == WearRoute.Dpi) R.string.wear_display_scaling else R.string.wear_screen_shape),
                    { onOpen(route) }, icon = settingsRouteIcon(route),
                    description = if (route == WearRoute.Dpi) dpiSummary else shapeSummary)
            }
            // Android 13+ keeps the app language in the system per-app settings, which Wear does not
            // offer, so the in-app choice is only shown where the shared locale handling applies it.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                val language = WearRoute.Selection(WearSelection.Language)
                item { WearSectionHeader(spec, stringResource(R.string.settings_language)) }
                item {
                    WearSettingsJumpPageWidget(spec, stringResource(R.string.settings_language), { onOpen(language) },
                        icon = settingsRouteIcon(language), description = languageSummary)
                }
            }
        } else {
            val toggles = buildList {
                if (category == WearSettingsCategory.General) {
                    add(SettingsToggle(SettingsSection.Updates, R.string.settings_check_manager_update, R.string.settings_check_manager_update_summary,
                        state.checkManagerUpdate, SettingsUiAction.SetManagerUpdateCheck(!state.checkManagerUpdate)))
                    if (state.checkManagerUpdate) add(SettingsToggle(SettingsSection.Updates, R.string.settings_check_beta_update,
                        R.string.settings_check_beta_update_summary, state.checkBetaUpdate,
                        SettingsUiAction.SetBetaUpdateCheck(!state.checkBetaUpdate)))
                    add(SettingsToggle(SettingsSection.Updates, R.string.settings_check_module_update, R.string.settings_check_module_update_summary,
                        state.checkModuleUpdate, SettingsUiAction.SetModuleUpdateCheck(!state.checkModuleUpdate)))
                    if (status.isFullFeatured) add(SettingsToggle(SettingsSection.AppBehavior, R.string.settings_soft_reboot, R.string.settings_soft_reboot_summary,
                        status.isLateLoadMode || state.useSoftReboot, SettingsUiAction.SetUseSoftReboot(!state.useSoftReboot)))
                    add(SettingsToggle(SettingsSection.AppBehavior, R.string.icon_switch_title, R.string.icon_switch_summary,
                        state.useAltIcon, SettingsUiAction.SetAlternateIcon(!state.useAltIcon)))
                }
                if (category == WearSettingsCategory.Security && status.isFullFeatured) {
                    add(SettingsToggle(SettingsSection.MountIsolation, R.string.settings_kernel_umount, R.string.settings_kernel_umount_summary,
                        state.isKernelUmountEnabled, SettingsUiAction.SetKernelUmount(!state.isKernelUmountEnabled)))
                    add(SettingsToggle(SettingsSection.MountIsolation, R.string.settings_umount_modules_default, R.string.settings_umount_modules_default_summary,
                        state.defaultUmountModules, SettingsUiAction.SetDefaultUmountModules(!state.defaultUmountModules)))
                    add(SettingsToggle(SettingsSection.LoggingPrivacy, R.string.settings_sulog, R.string.settings_sulog_summary,
                        state.isSuLogEnabled, SettingsUiAction.SetSuLog(!state.isSuLogEnabled)))
                    // A short Wear summary keeps this row compact; the phone summary wraps around "SELinux".
                    add(SettingsToggle(SettingsSection.LoggingPrivacy, R.string.settings_selinux_hide, R.string.wear_settings_selinux_hide_summary,
                        state.isSelinuxHideEnabled, SettingsUiAction.SetSelinuxHide(!state.isSelinuxHideEnabled)))
                }
                if (category == WearSettingsCategory.Advanced && status.isFullFeatured) {
                    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) add(SettingsToggle(SettingsSection.PrivilegesStartup, R.string.settings_adb_root,
                        R.string.settings_adb_root_summary, state.isAdbRootEnabled, SettingsUiAction.SetAdbRoot(!state.isAdbRootEnabled)))
                    if (status.isLateLoadMode) add(SettingsToggle(SettingsSection.PrivilegesStartup, R.string.settings_auto_jailbreak, R.string.settings_auto_jailbreak_summary,
                        state.autoJailbreakEnabled, SettingsUiAction.SetAutoJailbreak(!state.autoJailbreakEnabled)))
                }
            }
            // Pages that open from this category, with the current value or phone summary as description.
            val pages = buildList {
                if (category == WearSettingsCategory.General) {
                    if (status.isFullFeatured) add(SettingsPageLink(SettingsSection.Diagnostics, R.string.sulog, WearRoute.Logs))
                    add(SettingsPageLink(SettingsSection.Diagnostics, R.string.send_log, WearRoute.Bugreport))
                    add(SettingsPageLink(SettingsSection.LinksFiles, R.string.wear_link_mode,
                        WearRoute.Selection(WearSelection.LinkMode), linkSummary))
                    add(SettingsPageLink(SettingsSection.LinksFiles, R.string.wear_picker_mode,
                        WearRoute.Selection(WearSelection.PickerMode), pickerSummary))
                }
                if (category == WearSettingsCategory.Security && status.isFullFeatured) {
                    add(SettingsPageLink(SettingsSection.AccessProfiles, R.string.settings_sucompat,
                        WearRoute.Selection(WearSelection.SuCompat), suCompatSummary))
                    add(SettingsPageLink(SettingsSection.AccessProfiles, R.string.settings_profile_template, WearRoute.Templates))
                }
                if (category == WearSettingsCategory.Advanced) {
                    if (status.isFullFeatured) {
                        add(SettingsPageLink(SettingsSection.ManagerMounts, R.string.dynamic_manager_title, WearRoute.DynamicManager))
                        if (state.isKernelUmountEnabled) add(SettingsPageLink(SettingsSection.ManagerMounts, R.string.umount_path_manager, WearRoute.Umount))
                    }
                    if (status.isFullFeatured && status.lkmMode == true && !status.isLateLoadMode) add(SettingsPageLink(SettingsSection.KernelMaintenance, R.string.settings_uninstall, WearRoute.Uninstall))
                }
            }
            SettingsSection.entries.forEach { section ->
                val sectionToggles = toggles.filter { it.section == section }
                val sectionPages = pages.filter { it.section == section }
                if (sectionToggles.isNotEmpty() || sectionPages.isNotEmpty()) {
                    item(key = section.name) { WearSectionHeader(spec, stringResource(section.title)) }
                    LazySegmentedColumn(sectionToggles, { it.label }) { toggle ->
                        val enabled = when (toggle.label) {
                            R.string.settings_kernel_umount -> state.kernelUmountStatus == "supported"
                            R.string.settings_sulog -> state.sulogStatus == "supported"
                            R.string.settings_selinux_hide -> state.selinuxHideStatus == "supported"
                            R.string.settings_adb_root -> state.adbRootStatus == "supported"
                            R.string.settings_soft_reboot -> !status.isLateLoadMode
                            else -> true
                        }
                        WearSettingsSwitchWidget(spec, stringResource(toggle.label), toggle.checked,
                            { onAction(toggle.action) }, enabled, icon = settingsToggleIcon(toggle.label),
                            secondaryLabel = toggle.summary?.let { stringResource(it) })
                    }
                    LazySegmentedColumn(sectionPages, { it.label }) { entry ->
                        WearSettingsJumpPageWidget(spec, stringResource(entry.label), { onOpen(entry.route) },
                            icon = settingsRouteIcon(entry.route),
                            description = entry.summary ?: when (entry.route) {
                                WearRoute.Templates -> stringResource(R.string.settings_profile_template_summary)
                                WearRoute.DynamicManager -> stringResource(R.string.dynamic_manager_settings_summary)
                                WearRoute.Umount -> stringResource(R.string.umount_path_manager_summary)
                                else -> null
                            })
                    }
                }
            }
        }
    }
}
