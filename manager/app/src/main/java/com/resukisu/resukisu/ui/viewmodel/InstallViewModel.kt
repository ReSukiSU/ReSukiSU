package com.resukisu.resukisu.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resukisu.resukisu.domain.model.InstallEnvironment
import com.resukisu.resukisu.domain.usecase.GetInstallEnvironmentUseCase
import com.resukisu.resukisu.domain.usecase.RebootUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InstallUiState(
    val environment: InstallEnvironment = InstallEnvironment(),
    val loading: Boolean = true,
)

sealed interface InstallUiAction {
    data object Refresh : InstallUiAction
    data object Reboot : InstallUiAction
}

sealed interface InstallUiEvent {
    data class Error(val message: String) : InstallUiEvent
}

class InstallViewModel(
    private val getInstallEnvironment: GetInstallEnvironmentUseCase,
    private val reboot: RebootUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        getInstallEnvironment.cached()?.let { cached ->
            InstallUiState(environment = cached, loading = false)
        } ?: InstallUiState()
    )
    private val mutableEvents = MutableSharedFlow<InstallUiEvent>(extraBufferCapacity = 1)

    val state: StateFlow<InstallUiState> = mutableState.asStateFlow()
    val events: SharedFlow<InstallUiEvent> = mutableEvents.asSharedFlow()

    init {
        if (mutableState.value.loading) load(forceRefresh = false)
    }

    fun dispatch(action: InstallUiAction) {
        when (action) {
            InstallUiAction.Refresh -> load(forceRefresh = true)
            InstallUiAction.Reboot -> viewModelScope.launch {
                reboot().onFailure {
                    mutableEvents.tryEmit(InstallUiEvent.Error(it.message.orEmpty()))
                }
            }
        }
    }

    private fun load(forceRefresh: Boolean) {
        viewModelScope.launch {
            mutableState.update { it.copy(loading = true) }
            runCatching { getInstallEnvironment(forceRefresh) }
                .onSuccess { environment ->
                    mutableState.value = InstallUiState(environment = environment, loading = false)
                }
                .onFailure { error ->
                    mutableState.update { it.copy(loading = false) }
                    mutableEvents.tryEmit(InstallUiEvent.Error(error.message.orEmpty()))
                }
        }
    }
}
