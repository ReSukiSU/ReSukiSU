// Miuix UI merged from tiann/KernelSU (github.com/tiann/KernelSU), originally authored
// by YuKongA (github.com/YuKongA); GPL-3.0. Adapted in place to ReSukiSU's HomeViewModel
// data and choosable cards. See docs/ATTRIBUTION.md.
package com.resukisu.resukisu.ui.screen.home

import androidx.compose.runtime.Immutable
import com.resukisu.resukisu.ui.viewmodel.HomeUiState

// Values the Miuix home screen derives from the shared HomeViewModel state, kept as extensions
// (instead of a parallel UiState) so the screen reads them off ReSukiSU's own uiState.
val HomeUiState.kernelVersion get() = systemStatus.kernelVersion
val HomeUiState.ksuVersion: Int? get() = systemStatus.ksuVersion
val HomeUiState.managerUAPIVersion: Int get() = systemStatus.managerUAPIVersion
val HomeUiState.kernelUAPIVersion: Int? get() = systemStatus.kernelUAPIVersion
val HomeUiState.lkmMode: Boolean? get() = systemStatus.lkmMode
val HomeUiState.isSELinuxPermissive: Boolean get() = systemStatus.isSELinuxPermissive
val HomeUiState.checkUpdateEnabled: Boolean get() = true
val HomeUiState.superuserCount: Int get() = systemInfo.superuserCount
val HomeUiState.moduleCount: Int get() = systemInfo.moduleCount
val HomeUiState.currentManagerVersionCode: Long get() = systemInfo.managerVersion.third.toLong()
val HomeUiState.isLateLoadMode: Boolean get() = false
val HomeUiState.showRequireKernelWarning: Boolean
    get() = systemStatus.isManager && systemStatus.requireNewKernel
val HomeUiState.showUAPIMisMatchWarning: Boolean
    get() = showRequireKernelWarning && systemStatus.uapiMismatch
val HomeUiState.showRootWarning: Boolean
    get() = systemStatus.ksuVersion != null && !systemStatus.isRootAvailable
val HomeUiState.showUnofficialWarning: Boolean
    get() = !systemStatus.isOfficialSignature
val HomeUiState.hasUpdate: Boolean
    get() = latestVersionInfo.versionCode > currentManagerVersionCode

@Immutable
data class HomeActions(
    val onInstallClick: () -> Unit,
    val onSuperuserClick: () -> Unit,
    val onModuleClick: () -> Unit,
    val onOpenUrl: (String) -> Unit,
    val onJailbreakClick: () -> Unit = {},
    val onOpenSusfs: () -> Unit = {},
)
