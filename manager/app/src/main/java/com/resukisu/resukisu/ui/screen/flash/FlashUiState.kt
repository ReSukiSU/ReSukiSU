package com.resukisu.resukisu.ui.screen.flash

import androidx.compose.runtime.Immutable
import com.resukisu.resukisu.ui.screen.FlashingStatus

@Immutable
data class FlashUiState(
    val text: String,
    val showRebootAction: Boolean,
    val flashingStatus: FlashingStatus,
    val showJailbreakWarning: Boolean,
)

@Immutable
data class FlashScreenActions(
    val onBack: () -> Unit,
    val onSaveLog: () -> Unit,
    val onReboot: () -> Unit,
    val onConfirmJailbreakWarning: () -> Unit,
    val onDismissJailbreakWarning: () -> Unit,
)
