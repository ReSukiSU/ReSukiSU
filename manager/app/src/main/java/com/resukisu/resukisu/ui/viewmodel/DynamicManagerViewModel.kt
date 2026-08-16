package com.resukisu.resukisu.ui.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resukisu.resukisu.domain.model.DynamicManagerApp
import com.resukisu.resukisu.domain.model.DynamicManagerConfig
import com.resukisu.resukisu.domain.usecase.ClearDynamicManagerUseCase
import com.resukisu.resukisu.domain.usecase.ObserveDynamicManagerStateUseCase
import com.resukisu.resukisu.domain.usecase.RefreshDynamicManagerUseCase
import com.resukisu.resukisu.domain.usecase.SelectDynamicManagerUseCase
import com.resukisu.resukisu.domain.usecase.SetManualDynamicManagerUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

typealias DynamicManagerAppItem = DynamicManagerApp

@Immutable
data class DynamicManagerUiState(
    val config: DynamicManagerConfig? = null,
    val apps: List<DynamicManagerApp> = emptyList(),
    val search: String = "",
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isSubmitting: Boolean = false,
)

sealed interface DynamicManagerUiAction {
    data object Refresh : DynamicManagerUiAction
    data class Search(val query: String) : DynamicManagerUiAction
    data class SelectApp(val app: DynamicManagerApp) : DynamicManagerUiAction
    data class SetManual(val size: Int, val hash: String) : DynamicManagerUiAction
    data object Clear : DynamicManagerUiAction
}

enum class DynamicManagerOperation { Set, Clear }

sealed interface DynamicManagerUiEvent {
    data class OperationCompleted(
        val operation: DynamicManagerOperation,
        val success: Boolean,
    ) : DynamicManagerUiEvent
}

class DynamicManagerViewModel(
    observeState: ObserveDynamicManagerStateUseCase,
    private val refreshDynamicManager: RefreshDynamicManagerUseCase,
    private val selectDynamicManager: SelectDynamicManagerUseCase,
    private val setManualDynamicManager: SetManualDynamicManagerUseCase,
    private val clearDynamicManager: ClearDynamicManagerUseCase,
) : ViewModel() {
    private val search = MutableStateFlow("")
    private val mutableEvents = MutableSharedFlow<DynamicManagerUiEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<DynamicManagerUiEvent> = mutableEvents.asSharedFlow()

    val state: StateFlow<DynamicManagerUiState> = combine(observeState(), search) { source, query ->
        val normalized = query.trim()
        DynamicManagerUiState(
            config = source.config,
            apps = if (normalized.isEmpty()) {
                source.apps
            } else {
                source.apps.filter { app ->
                    app.label.contains(normalized, ignoreCase = true) ||
                            app.packageName.contains(normalized, ignoreCase = true)
                }
            },
            search = query,
            isLoading = source.isLoading,
            isRefreshing = source.isRefreshing,
            isSubmitting = source.isSubmitting,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = DynamicManagerUiState(),
    )
    val uiState: StateFlow<DynamicManagerUiState> = state

    fun dispatch(action: DynamicManagerUiAction) {
        when (action) {
            DynamicManagerUiAction.Refresh -> viewModelScope.launch { refresh() }
            is DynamicManagerUiAction.Search -> search.value = action.query
            is DynamicManagerUiAction.SelectApp -> submit(DynamicManagerOperation.Set) {
                selectDynamicManager(action.app.apkPath)
            }

            is DynamicManagerUiAction.SetManual -> submit(DynamicManagerOperation.Set) {
                setManualDynamicManager(action.size, action.hash)
            }

            DynamicManagerUiAction.Clear -> submit(DynamicManagerOperation.Clear) {
                clearDynamicManager()
            }
        }
    }

    suspend fun refresh(): Result<Unit> = refreshDynamicManager()

    private fun submit(
        operation: DynamicManagerOperation,
        command: suspend () -> Result<Unit>,
    ) {
        viewModelScope.launch {
            val success = command().isSuccess
            mutableEvents.emit(DynamicManagerUiEvent.OperationCompleted(operation, success))
        }
    }
}
