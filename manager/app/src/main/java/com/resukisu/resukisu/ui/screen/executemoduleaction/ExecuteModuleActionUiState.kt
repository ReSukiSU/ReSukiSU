package com.resukisu.resukisu.ui.screen.executemoduleaction

import androidx.compose.runtime.Immutable

@Immutable
data class ExecuteModuleActionScreenActions(
    val onBack: () -> Unit,
    val onSaveLog: () -> Unit,
    val onClose: () -> Unit = {},
)
