package com.resukisu.resukisu.ui.screen.settings

import androidx.compose.runtime.Immutable

@Immutable
data class SettingsScreenActions(
    val onSetCheckUpdate: (Boolean) -> Unit,
    val onSetCheckModuleUpdate: (Boolean) -> Unit,
    val onOpenTheme: () -> Unit,
    val onSetUiModeIndex: (Int) -> Unit,
    val onOpenProfileTemplate: () -> Unit,
    val onSetSuCompatMode: (Int) -> Unit,
    val onSetKernelUmountEnabled: (Boolean) -> Unit,
    val onSetSelinuxHideEnabled: (Boolean) -> Unit,
    val onSetSulogEnabled: (Boolean) -> Unit,
    val onSetAdbRootEnabled: (Boolean) -> Unit,
    val onSetDefaultUmountModules: (Boolean) -> Unit,
    val onSetEnableWebDebugging: (Boolean) -> Unit,
    val onSetAutoJailbreak: (Boolean) -> Unit,
    val onOpenAbout: () -> Unit,
    // ReSukiSU-specific: blur toggle + its own tool screens surfaced in Miuix.
    val onSetEnableBlur: (Boolean) -> Unit = {},
    val onOpenDynamicManager: () -> Unit = {},
    val onOpenUmountManager: () -> Unit = {},
)
