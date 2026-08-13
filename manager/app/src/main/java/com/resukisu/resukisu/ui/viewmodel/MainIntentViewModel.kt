package com.resukisu.resukisu.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resukisu.resukisu.domain.usecase.GetInstallEnvironmentUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainIntentUiState(
    val rootAvailable: Boolean = false,
    val loaded: Boolean = false,
)

sealed interface MainIntentUiAction {
    data object Refresh : MainIntentUiAction
}

sealed interface MainIntentUiEvent {
    data class Error(val message: String) : MainIntentUiEvent
}

class MainIntentViewModel(
    private val getInstallEnvironment: GetInstallEnvironmentUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(MainIntentUiState())
    private val mutableEvents = MutableSharedFlow<MainIntentUiEvent>(extraBufferCapacity = 1)
    val state: StateFlow<MainIntentUiState> = mutableState.asStateFlow()
    val events: SharedFlow<MainIntentUiEvent> = mutableEvents.asSharedFlow()

    init {
        dispatch(MainIntentUiAction.Refresh)
    }

    fun dispatch(action: MainIntentUiAction) {
        when (action) {
            MainIntentUiAction.Refresh -> load()
        }
    }

    private fun load() {
        viewModelScope.launch {
            runCatching { getInstallEnvironment() }
                .onSuccess { environment ->
                    mutableState.value = MainIntentUiState(
                        rootAvailable = environment.rootAvailable,
                        loaded = true,
                    )
                }
                .onFailure { error ->
                    mutableState.update { it.copy(loaded = true, rootAvailable = false) }
                    mutableEvents.tryEmit(MainIntentUiEvent.Error(error.message.orEmpty()))
                }
        }
    }
}
