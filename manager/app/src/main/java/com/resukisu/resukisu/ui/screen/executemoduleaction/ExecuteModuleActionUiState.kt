package com.resukisu.resukisu.ui.screen.executemoduleaction

import androidx.compose.runtime.Immutable

@Immutable
data class ExecuteModuleActionUiState(
    val text: String,
    val isComplete: Boolean = false,
)

@Immutable
data class ExecuteModuleActionScreenActions(
    val onBack: () -> Unit,
    val onSaveLog: () -> Unit,
    val onClose: () -> Unit = {},
)
