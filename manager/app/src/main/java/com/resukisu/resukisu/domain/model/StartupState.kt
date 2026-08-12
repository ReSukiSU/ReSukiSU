package com.resukisu.resukisu.domain.model

sealed interface StartupState {
    data object Loading : StartupState
    data object Ready : StartupState
    data class Failed(val message: String) : StartupState
}

