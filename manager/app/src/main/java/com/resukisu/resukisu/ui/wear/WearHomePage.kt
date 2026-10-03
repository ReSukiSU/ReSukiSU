package com.resukisu.resukisu.ui.wear

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Android
import androidx.compose.material.icons.twotone.Block
import androidx.compose.material.icons.twotone.DeveloperBoard
import androidx.compose.material.icons.twotone.Error
import androidx.compose.material.icons.twotone.Extension
import androidx.compose.material.icons.twotone.FilterList
import androidx.compose.material.icons.twotone.Group
import androidx.compose.material.icons.twotone.Memory
import androidx.compose.material.icons.twotone.Security
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material.icons.twotone.Smartphone
import androidx.compose.material.icons.twotone.Tag
import androidx.compose.material.icons.twotone.TaskAlt
import androidx.compose.material.icons.twotone.Tune
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.MaterialTheme
import com.resukisu.resukisu.BuildConfig
import com.resukisu.resukisu.Natives.KernelPatchImplementation
import com.resukisu.resukisu.R
import com.resukisu.resukisu.domain.model.HomeDashboardState
import com.resukisu.resukisu.ui.wear.component.WearChip
import com.resukisu.resukisu.ui.wear.component.WearChipEmphasis
import com.resukisu.resukisu.ui.wear.component.WearList
import com.resukisu.resukisu.ui.wear.component.WearPageHeader
import com.resukisu.resukisu.ui.wear.component.WearSectionHeader
import com.resukisu.resukisu.ui.wear.component.WearStatusItem
import com.resukisu.resukisu.ui.wear.component.WearStatusTone

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

@Composable
internal fun WearHomePage(
    state: HomeDashboardState,
    error: String?,
    onBack: () -> Unit,
    onRebootPanel: () -> Unit,
    listState: TransformingLazyColumnState = rememberTransformingLazyColumnState(),
    onInstall: (() -> Unit)? = null,
    backToTop: Boolean = false,
) {
    val unknown = stringResource(R.string.unknown)
    val status = state.systemStatus
    val info = state.systemInfo
    // The first load shows the branded loading screen in WearManagerScreen instead of this list.
    WearList(onBack = onBack, onOpenPanel = onRebootPanel.takeIf { status.isRootAvailable },
        panelLabel = stringResource(R.string.reboot), listState = listState, backToTop = backToTop,
    ) { spec ->
        item { WearPageHeader(spec, stringResource(R.string.home)) }
        if (state.isInitialDataLoaded) {
            // Build and compatibility notices are yellow warnings; failing to obtain root is a red error.
            val warnings = buildList {
                if (status.isManager && !status.isFullFeatured) add(
                    if ((status.kernelUAPIVersion ?: 1) > status.managerUAPIVersion) R.string.require_manager_version
                    else if (status.lkmMode == true) R.string.require_kernel_version else R.string.require_kernel_version_gki)
                if (BuildConfig.DEBUG) add(R.string.debug_version_notice)
                if (BuildConfig.IS_PR_BUILD || status.isPrBuild) add(R.string.home_pr_build_warning)
                if (status.kernelPatchImplementation == KernelPatchImplementation.OFFICIAL) add(R.string.conflict_with_apatch)
            }
            // Notices are paragraphs, so they stay full-text cards instead of truncated chip labels.
            if (status.ksuVersion != null && !status.isRootAvailable) item {
                WearStatusItem(spec, Icons.TwoTone.Error, stringResource(R.string.grant_root_failed), tone = WearStatusTone.ERROR, centered = false)
            }
            warnings.forEach { id ->
                item { WearStatusItem(spec, Icons.TwoTone.Warning, stringResource(id), tone = WearStatusTone.WARNING, centered = false) }
            }
            if (!status.isOfficialSignature) item {
                WearStatusItem(spec, Icons.TwoTone.Warning,
                    stringResource(R.string.unofficial_version_notice, stringResource(R.string.app_name)),
                    tone = WearStatusTone.WARNING, centered = false)
            }
            // The running state is the screen's only high-emphasis chip.
            item {
                val working = status.ksuVersion != null
                WearChip(
                    spec,
                    label = stringResource(when {
                        working && status.isSafeMode -> R.string.safe_mode
                        working -> R.string.home_working
                        status.kernelVersion.isGKI() -> R.string.home_not_installed
                        else -> R.string.home_unsupported
                    }),
                    secondaryLabel = stringResource(when {
                        working -> R.string.home_short_info
                        status.kernelVersion.isGKI() -> R.string.home_click_to_install
                        else -> R.string.home_unsupported_reason
                    }, info.superuserCount, info.moduleCount),
                    // Match the phone status card: mode badges belong beside the headline, not in the description.
                    headlineBadges = if (working) listOfNotNull(
                        stringResource(if (status.lkmMode == true) R.string.wear_mode_lkm else R.string.wear_mode_builtin),
                        if (status.isLateLoadMode) stringResource(R.string.jailbreak_mode) else null,
                    ) else emptyList(),
                    // The superuser and module counts fit one line in the theme's smallest body style.
                    secondaryLabelStyle = if (working) MaterialTheme.typography.bodyExtraSmall else null,
                    icon = when {
                        working -> Icons.TwoTone.TaskAlt
                        status.kernelVersion.isGKI() -> Icons.TwoTone.Warning
                        else -> Icons.TwoTone.Block
                    },
                    // Not installed or unsupported is an error state, shown in red.
                    emphasis = if (working) WearChipEmphasis.HIGH else WearChipEmphasis.ERROR,
                    // As on the phone, the status opens installation when root or a GKI kernel allows it.
                    onClick = onInstall.takeIf { status.isRootAvailable || status.kernelVersion.isGKI() },
                )
            }
            item { WearSectionHeader(spec, stringResource(R.string.home_version_info)) }
            val fields = buildList {
                add(Triple(Icons.TwoTone.Smartphone, R.string.home_device_model, info.deviceModel))
                add(Triple(Icons.TwoTone.DeveloperBoard, R.string.home_kernel, info.kernelRelease))
                if (!state.isSimpleMode) add(Triple(Icons.TwoTone.Android, R.string.home_android_version, info.androidVersion))
                if (status.isManager) add(Triple(Icons.TwoTone.Memory, R.string.home_kernel_version, status.ksuFullVersion.orEmpty()))
                add(Triple(Icons.TwoTone.Tag, R.string.home_manager_version,
                    info.managerVersion.let { (name, code, build) -> "$name ($code/$build)" }))
                if (!state.isSimpleMode && info.susfsEnabled && info.susfsVersion.isNotEmpty())
                    add(Triple(Icons.TwoTone.Settings, R.string.home_susfs_version, info.susfsVersion))
            }
            fields.forEach { (icon, label, value) -> item {
                WearChip(spec, stringResource(label), secondaryLabel = value.ifBlank { unknown }, icon = icon)
            } }
            item { WearSectionHeader(spec, stringResource(R.string.home_status_info)) }
            item {
                WearChip(spec, stringResource(R.string.home_selinux_status),
                    secondaryLabel = info.selinuxStatus.ifBlank { unknown }, icon = Icons.TwoTone.Security)
            }
            item {
                WearChip(spec, stringResource(R.string.home_seccomp_status), secondaryLabel = stringResource(when (info.seccompStatus) {
                    -1 -> R.string.seccomp_status_not_supported
                    0 -> R.string.seccomp_status_disabled
                    1 -> R.string.seccomp_status_strict
                    2 -> R.string.seccomp_status_filter
                    else -> R.string.seccomp_status_unknown
                }), icon = Icons.TwoTone.FilterList)
            }
            if (!state.isSimpleMode && info.managersList != null) item {
                val managers = info.managersList.managers.groupBy { it.signatureIndex }.toSortedMap().map { (index, managers) ->
                    val signature = when (index) {
                        0 -> stringResource(R.string.app_name)
                        255 -> stringResource(R.string.dynamic_managerature)
                        else -> if (index >= 1) stringResource(R.string.signature_index, index)
                            else stringResource(R.string.unknown_signature)
                    }
                    managers.joinToString(", ") { it.uid.toString() } + " ($signature)"
                }.joinToString("\n")
                WearChip(spec, stringResource(R.string.multi_manager_list),
                    secondaryLabel = managers.ifEmpty { stringResource(R.string.no_active_manager) }, icon = Icons.TwoTone.Group)
            }
            if (!state.isSimpleMode) {
                val extras = buildList {
                    if (status.isFullFeatured) add(Triple(Icons.TwoTone.Tune, R.string.home_hook_type, status.hookType))
                    if (info.zygiskImplement.isNotEmpty() && info.zygiskImplement != "None")
                        add(Triple(Icons.TwoTone.Extension, R.string.home_zygisk_implement, info.zygiskImplement))
                    if (info.metaModuleImplement.isNotEmpty() && info.metaModuleImplement != "None")
                        add(Triple(Icons.TwoTone.Extension, R.string.home_meta_module_implement, info.metaModuleImplement))
                }
                extras.forEach { (icon, label, value) -> item {
                    WearChip(spec, stringResource(label), secondaryLabel = value, icon = icon)
                } }
            }
        }
        if (!error.isNullOrBlank()) item { WearStatusItem(spec, Icons.TwoTone.Error, error, tone = WearStatusTone.ERROR, centered = false) }
    }
}
