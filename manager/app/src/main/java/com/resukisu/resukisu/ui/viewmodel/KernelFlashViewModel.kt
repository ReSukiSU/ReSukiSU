package com.resukisu.resukisu.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resukisu.resukisu.domain.model.FlashProgress
import com.resukisu.resukisu.domain.usecase.GetBooleanPreferenceUseCase
import com.resukisu.resukisu.domain.usecase.ObserveKernelFlashUseCase
import com.resukisu.resukisu.domain.usecase.RebootUseCase
import com.resukisu.resukisu.domain.usecase.RemovePreferenceUseCase
import com.resukisu.resukisu.domain.usecase.StartKernelFlashUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class KernelFlashUiState(
    val requestUri: String? = null,
    val selectedSlot: String? = null,
    val flash: FlashProgress = FlashProgress(),
    val fullLog: String = "",
    val autoExit: Boolean = false,
)

sealed interface KernelFlashUiAction {
    data class Start(val uri: String, val selectedSlot: String?) : KernelFlashUiAction
    data object ConsumeAutoExit : KernelFlashUiAction
    data object Reboot : KernelFlashUiAction
}

sealed interface KernelFlashUiEvent {
    data class Error(val message: String) : KernelFlashUiEvent
}

class KernelFlashViewModel(
    observeKernelFlash: ObserveKernelFlashUseCase,
    private val startKernelFlash: StartKernelFlashUseCase,
    getBooleanPreference: GetBooleanPreferenceUseCase,
    private val removePreference: RemovePreferenceUseCase,
    private val reboot: RebootUseCase,
) : ViewModel() {
    private val autoExit = MutableStateFlow(getBooleanPreference(AUTO_EXIT_KEY, false))
    val state: StateFlow<KernelFlashUiState> = combine(
        observeKernelFlash(),
        autoExit,
    ) { session, shouldAutoExit ->
        KernelFlashUiState(
            requestUri = session.requestUri,
            selectedSlot = session.selectedSlot,
            flash = session.progress,
            fullLog = session.fullLog,
            autoExit = shouldAutoExit,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, KernelFlashUiState(autoExit = autoExit.value))

    private val mutableEvents = MutableSharedFlow<KernelFlashUiEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<KernelFlashUiEvent> = mutableEvents.asSharedFlow()

    fun dispatch(action: KernelFlashUiAction) {
        when (action) {
            is KernelFlashUiAction.Start -> startKernelFlash(action.uri, action.selectedSlot)
            KernelFlashUiAction.ConsumeAutoExit -> {
                removePreference(AUTO_EXIT_KEY)
                autoExit.value = false
            }

            KernelFlashUiAction.Reboot -> viewModelScope.launch {
                reboot().onFailure {
                    mutableEvents.tryEmit(KernelFlashUiEvent.Error(it.message.orEmpty()))
                }
            }
        }
    }

    private companion object {
        const val AUTO_EXIT_KEY = "auto_exit_after_flash"
    }
}
