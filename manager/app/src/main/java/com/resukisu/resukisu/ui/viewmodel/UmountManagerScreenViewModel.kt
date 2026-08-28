package com.resukisu.resukisu.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resukisu.resukisu.R
import com.resukisu.resukisu.domain.model.UmountPath
import com.resukisu.resukisu.domain.usecase.AddUmountPathUseCase
import com.resukisu.resukisu.domain.usecase.ObserveUmountStateUseCase
import com.resukisu.resukisu.domain.usecase.RefreshUmountPathsUseCase
import com.resukisu.resukisu.domain.usecase.RemoveUmountPathUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UmountManagerUiState(
    val umountPaths: List<UmountPath> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
)

sealed interface UmountManagerUiAction {
    data class Refresh(val force: Boolean = false) : UmountManagerUiAction
    data class Remove(val entry: UmountPath) : UmountManagerUiAction
    data class Add(val path: String, val flags: Int) : UmountManagerUiAction
}

sealed interface UmountManagerUiEvent {
    data class Message(val message: String) : UmountManagerUiEvent
}

class UmountManagerScreenViewModel(
    private val context: Context,
    observeState: ObserveUmountStateUseCase,
    private val refreshPaths: RefreshUmountPathsUseCase,
    private val addPath: AddUmountPathUseCase,
    private val removePath: RemoveUmountPathUseCase,
) : ViewModel() {
    private val mutableEvents = MutableSharedFlow<UmountManagerUiEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<UmountManagerUiEvent> = mutableEvents.asSharedFlow()

    val state: StateFlow<UmountManagerUiState> = observeState()
        .map { source ->
            UmountManagerUiState(
                umountPaths = source.paths,
                isLoading = source.isLoading,
                isRefreshing = source.isRefreshing,
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, UmountManagerUiState())
    val uiState: StateFlow<UmountManagerUiState> = state

    fun dispatch(action: UmountManagerUiAction) {
        when (action) {
            is UmountManagerUiAction.Refresh -> viewModelScope.launch { refreshPaths() }
            is UmountManagerUiAction.Remove -> submit(
                command = { removePath(action.entry) },
                successMessage = R.string.umount_path_removed,
            )

            is UmountManagerUiAction.Add -> submit(
                command = { addPath(action.path, action.flags) },
                successMessage = R.string.umount_path_added,
            )
        }
    }

    private fun submit(
        command: suspend () -> Result<Unit>,
        successMessage: Int,
    ) {
        viewModelScope.launch {
            val message = if (command().isSuccess) successMessage else R.string.operation_failed
            mutableEvents.emit(UmountManagerUiEvent.Message(context.getString(message)))
        }
    }
}
