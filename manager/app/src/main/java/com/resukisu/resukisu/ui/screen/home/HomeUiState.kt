// Miuix UI merged from tiann/KernelSU (github.com/tiann/KernelSU), originally authored
// by YuKongA (github.com/YuKongA); GPL-3.0. Adapted in place to ReSukiSU's HomeViewModel
// data and choosable cards. See docs/ATTRIBUTION.md.
package com.resukisu.resukisu.ui.screen.home

import androidx.compose.runtime.Immutable
import com.resukisu.resukisu.KernelVersion
import com.resukisu.resukisu.ui.util.module.LatestVersionInfo
import com.resukisu.resukisu.ui.viewmodel.HomeViewModel

@Immutable
data class HomeUiState(
    val systemStatus: HomeViewModel.SystemStatus,
    val systemInfo: HomeViewModel.SystemInfo,
    val latestVersionInfo: LatestVersionInfo,
    val checkUpdateEnabled: Boolean,
    val isSimpleMode: Boolean,
    val isHideVersion: Boolean,
    val isHideSusfsStatus: Boolean,
    val isHideZygiskImplement: Boolean,
    val isHideMetaModuleImplement: Boolean,
    val isHideLinkCard: Boolean,
) {
    val kernelVersion: KernelVersion get() = systemStatus.kernelVersion
    val ksuVersion: Int? get() = systemStatus.ksuVersion
    val managerUAPIVersion: Int get() = systemStatus.managerUAPIVersion
    val kernelUAPIVersion: Int? get() = systemStatus.kernelUAPIVersion
    val lkmMode: Boolean? get() = systemStatus.lkmMode
    val superuserCount: Int get() = systemInfo.superuserCount
    val moduleCount: Int get() = systemInfo.moduleCount
    val currentManagerVersionCode: Long get() = systemInfo.managerVersion.third.toLong()

    val isSELinuxPermissive: Boolean get() = systemStatus.isSELinuxPermissive
    val isLateLoadMode: Boolean get() = false

    val showRequireKernelWarning: Boolean
        get() = systemStatus.isManager && systemStatus.requireNewKernel
    val showUAPIMisMatchWarning: Boolean
        get() = showRequireKernelWarning && systemStatus.uapiMismatch
    val showRootWarning: Boolean
        get() = systemStatus.ksuVersion != null && !systemStatus.isRootAvailable
    val showUnofficialWarning: Boolean
        get() = !systemStatus.isOfficialSignature
    val hasUpdate: Boolean
        get() = latestVersionInfo.versionCode > currentManagerVersionCode
}

@Immutable
data class HomeActions(
    val onInstallClick: () -> Unit,
    val onSuperuserClick: () -> Unit,
    val onModuleClick: () -> Unit,
    val onOpenUrl: (String) -> Unit,
    val onJailbreakClick: () -> Unit = {},
    val onOpenSusfs: () -> Unit = {},
)
