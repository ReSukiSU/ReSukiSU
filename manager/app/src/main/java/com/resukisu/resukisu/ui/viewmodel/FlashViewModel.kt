package com.resukisu.resukisu.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resukisu.resukisu.domain.model.FlashOperation
import com.resukisu.resukisu.domain.model.FlashOperationUpdate
import com.resukisu.resukisu.domain.usecase.CheckFlashModuleMountUseCase
import com.resukisu.resukisu.domain.usecase.ExecuteFlashOperationUseCase
import com.resukisu.resukisu.domain.usecase.RebootUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FlashingStatus { FLASHING, SUCCESS, FAILED }

data class ModuleInstallStatus(
    val totalModules: Int = 0,
    val currentModule: Int = 0,
    val currentModuleName: String = "",
    val failedModules: List<String> = emptyList(),
    val verifiedModules: List<String> = emptyList(),
)

data class FlashUiState(
    val flashingStatus: FlashingStatus = FlashingStatus.FLASHING,
    val moduleInstallStatus: ModuleInstallStatus = ModuleInstallStatus(),
    val output: String = "",
    val showReboot: Boolean = false,
    val exitCode: Int? = null,
)

sealed interface FlashUiAction {
    data class Start(val operation: FlashOperation) : FlashUiAction
    data class SetStatus(val status: FlashingStatus) : FlashUiAction
    data class ResetModules(val totalModules: Int) : FlashUiAction
    data class UpdateModule(
        val totalModules: Int? = null,
        val currentModule: Int? = null,
        val currentModuleName: String? = null,
        val failedModule: String? = null,
        val verifiedModule: String? = null,
    ) : FlashUiAction

    data object Reboot : FlashUiAction
}

sealed interface FlashUiEvent {
    data class Output(val line: String) : FlashUiEvent
    data class ErrorOutput(val line: String) : FlashUiEvent
    data class Error(val message: String) : FlashUiEvent
    data class Completed(
        val showReboot: Boolean,
        val code: Int,
        val moduleNeedsMount: Boolean = false,
    ) : FlashUiEvent
}

class FlashViewModel(
    private val reboot: RebootUseCase,
    private val executeFlashOperation: ExecuteFlashOperationUseCase? = null,
    private val checkFlashModuleMount: CheckFlashModuleMountUseCase? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow(FlashUiState())
    val state: StateFlow<FlashUiState> = mutableState.asStateFlow()

    private val mutableEvents = MutableSharedFlow<FlashUiEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<FlashUiEvent> = mutableEvents.asSharedFlow()
    private var operationJob: Job? = null

    fun dispatch(action: FlashUiAction) {
        when (action) {
            is FlashUiAction.Start -> {
                operationJob?.cancel()
                operationJob = viewModelScope.launch {
                    val useCase = executeFlashOperation
                    if (useCase == null) {
                        mutableEvents.emit(FlashUiEvent.Error("Flash operation is unavailable"))
                        return@launch
                    }
                    mutableState.update {
                        it.copy(
                            flashingStatus = FlashingStatus.FLASHING,
                            output = "",
                            showReboot = false,
                            exitCode = null,
                        )
                    }
                    useCase(action.operation).collect { update ->
                        when (update) {
                            is FlashOperationUpdate.Output -> {
                                mutableState.update {
                                    it.copy(output = it.output + update.line + "\n")
                                }
                                mutableEvents.emit(FlashUiEvent.Output(update.line))
                            }

                            is FlashOperationUpdate.ErrorOutput -> {
                                mutableState.update {
                                    it.copy(output = it.output + update.line + "\n")
                                }
                                mutableEvents.emit(FlashUiEvent.ErrorOutput(update.line))
                            }

                            is FlashOperationUpdate.Completed -> {
                                val moduleNeedsMount = if (
                                    update.code == 0 && action.operation is FlashOperation.Module
                                ) {
                                    checkFlashModuleMount?.invoke(action.operation.uri) == true
                                } else {
                                    false
                                }
                                mutableState.update {
                                    it.copy(
                                        flashingStatus = if (update.code == 0) {
                                            FlashingStatus.SUCCESS
                                        } else {
                                            FlashingStatus.FAILED
                                        },
                                        showReboot = update.showReboot,
                                        exitCode = update.code,
                                    )
                                }
                                mutableEvents.emit(
                                    FlashUiEvent.Completed(
                                        update.showReboot,
                                        update.code,
                                        moduleNeedsMount,
                                    )
                                )
                            }
                        }
                    }
                }
            }

            is FlashUiAction.SetStatus -> mutableState.update {
                it.copy(flashingStatus = action.status)
            }

            is FlashUiAction.ResetModules -> mutableState.update {
                it.copy(
                    flashingStatus = FlashingStatus.FLASHING,
                    moduleInstallStatus = ModuleInstallStatus(
                        totalModules = action.totalModules,
                        currentModule = if (action.totalModules > 0) 1 else 0,
                    ),
                )
            }

            is FlashUiAction.UpdateModule -> mutableState.update { state ->
                val current = state.moduleInstallStatus
                state.copy(
                    moduleInstallStatus = current.copy(
                        totalModules = action.totalModules ?: current.totalModules,
                        currentModule = action.currentModule ?: current.currentModule,
                        currentModuleName = action.currentModuleName ?: current.currentModuleName,
                        failedModules = action.failedModule?.let(current.failedModules::plus)
                            ?: current.failedModules,
                        verifiedModules = action.verifiedModule?.let(current.verifiedModules::plus)
                            ?: current.verifiedModules,
                    )
                )
            }

            FlashUiAction.Reboot -> viewModelScope.launch {
                reboot().onFailure {
                    mutableEvents.tryEmit(FlashUiEvent.Error(it.message.orEmpty()))
                }
            }
        }
    }
}
