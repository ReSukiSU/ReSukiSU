package com.resukisu.resukisu.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resukisu.resukisu.domain.model.ModuleActionUpdate
import com.resukisu.resukisu.domain.usecase.ExecuteModuleActionUseCase
import com.resukisu.resukisu.domain.usecase.SaveModuleActionLogUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExecuteModuleActionUiState(
    val output: String = "",
    val running: Boolean = true,
)

sealed interface ExecuteModuleActionUiAction {
    data object SaveLog : ExecuteModuleActionUiAction
}

sealed interface ExecuteModuleActionUiEvent {
    data class Completed(val successful: Boolean) : ExecuteModuleActionUiEvent
    data class LogSaved(val path: String) : ExecuteModuleActionUiEvent
    data class Error(val message: String) : ExecuteModuleActionUiEvent
}

class ExecuteModuleActionViewModel(
    private val moduleId: String,
    private val executeModuleAction: ExecuteModuleActionUseCase,
    private val saveModuleActionLog: SaveModuleActionLogUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ExecuteModuleActionUiState())
    private val mutableEvents =
        MutableSharedFlow<ExecuteModuleActionUiEvent>(extraBufferCapacity = 1)
    private val log = StringBuilder()
    private var actionJob: Job? = null

    val state: StateFlow<ExecuteModuleActionUiState> = mutableState.asStateFlow()
    val events: SharedFlow<ExecuteModuleActionUiEvent> = mutableEvents.asSharedFlow()

    init {
        execute()
    }

    fun dispatch(action: ExecuteModuleActionUiAction) {
        when (action) {
            ExecuteModuleActionUiAction.SaveLog -> saveLog()
        }
    }

    private fun execute() {
        if (actionJob?.isActive == true) return
        actionJob = viewModelScope.launch {
            executeModuleAction(moduleId).collect { update ->
                when (update) {
                    is ModuleActionUpdate.Output -> appendOutput(update.text, update.isError)
                    is ModuleActionUpdate.Completed -> {
                        mutableState.update { it.copy(running = false) }
                        mutableEvents.emit(ExecuteModuleActionUiEvent.Completed(update.successful))
                    }
                }
            }
        }
    }

    private fun appendOutput(text: String, isError: Boolean) {
        val line = "$text\n"
        log.append(line)
        if (isError) return
        mutableState.update { current ->
            val output =
                if (line.startsWith(CLEAR_SCREEN)) line.removePrefix(CLEAR_SCREEN) else current.output + line
            current.copy(output = output)
        }
    }

    private fun saveLog() {
        if (mutableState.value.running) return
        viewModelScope.launch {
            saveModuleActionLog(log.toString())
                .onSuccess { mutableEvents.tryEmit(ExecuteModuleActionUiEvent.LogSaved(it)) }
                .onFailure { mutableEvents.tryEmit(ExecuteModuleActionUiEvent.Error(it.message.orEmpty())) }
        }
    }

    private companion object {
        const val CLEAR_SCREEN = "\u001B[H\u001B[J"
    }
}
