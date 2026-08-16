package com.resukisu.resukisu.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resukisu.resukisu.domain.model.ProfileTemplate
import com.resukisu.resukisu.domain.usecase.ExportProfileTemplatesUseCase
import com.resukisu.resukisu.domain.usecase.ImportProfileTemplatesUseCase
import com.resukisu.resukisu.domain.usecase.ObserveProfileTemplateOfflineUseCase
import com.resukisu.resukisu.domain.usecase.ObserveProfileTemplateRefreshingUseCase
import com.resukisu.resukisu.domain.usecase.ObserveProfileTemplatesUseCase
import com.resukisu.resukisu.domain.usecase.RefreshProfileTemplatesUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TemplateUiState(
    val templateList: List<ProfileTemplate> = emptyList(),
    val profileTemplates: List<String> = emptyList(),
    val profileTemplateNames: List<String> = emptyList(),
    val isRefreshing: Boolean = false,
    val isOffline: Boolean = false,
)

sealed interface TemplateUiAction {
    data class Refresh(val synchronize: Boolean = false) : TemplateUiAction
    data class Import(val json: String) : TemplateUiAction
    data object Export : TemplateUiAction
}

sealed interface TemplateUiEvent {
    data object ImportCompleted : TemplateUiEvent
    data class Exported(val json: String) : TemplateUiEvent
    data object ExportEmpty : TemplateUiEvent
    data class Error(val message: String) : TemplateUiEvent
}

class TemplateViewModel(
    observeTemplates: ObserveProfileTemplatesUseCase,
    observeRefreshing: ObserveProfileTemplateRefreshingUseCase,
    observeOffline: ObserveProfileTemplateOfflineUseCase,
    private val refreshTemplates: RefreshProfileTemplatesUseCase,
    private val importTemplatesUseCase: ImportProfileTemplatesUseCase,
    private val exportTemplatesUseCase: ExportProfileTemplatesUseCase,
) : ViewModel() {
    private val mutableEvents = MutableSharedFlow<TemplateUiEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<TemplateUiEvent> = mutableEvents.asSharedFlow()

    val state: StateFlow<TemplateUiState> = combine(
        observeTemplates(),
        observeRefreshing(),
        observeOffline(),
    ) { templates, refreshing, offline ->
        TemplateUiState(
            templateList = templates,
            profileTemplates = templates.map(ProfileTemplate::id),
            profileTemplateNames = templates.map { template ->
                template.name.ifBlank { template.id }
            },
            isRefreshing = refreshing,
            isOffline = offline,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TemplateUiState())
    val uiState: StateFlow<TemplateUiState> = state

    init {
        dispatch(TemplateUiAction.Refresh())
    }

    suspend fun fetchTemplates(sync: Boolean = false) {
        refreshTemplates(sync).exceptionOrNull()?.let {
            mutableEvents.emit(TemplateUiEvent.Error(it.message.orEmpty()))
        }
    }
    fun dispatch(action: TemplateUiAction) {
        when (action) {
            is TemplateUiAction.Refresh -> viewModelScope.launch { fetchTemplates(action.synchronize) }
            is TemplateUiAction.Import -> viewModelScope.launch {
                val result = importTemplatesUseCase(action.json)
                result.fold(
                    onSuccess = { mutableEvents.emit(TemplateUiEvent.ImportCompleted) },
                    onFailure = { mutableEvents.emit(TemplateUiEvent.Error(it.message.orEmpty())) },
                )
            }

            TemplateUiAction.Export -> viewModelScope.launch {
                exportTemplatesUseCase().fold(
                    onSuccess = { mutableEvents.emit(TemplateUiEvent.Exported(it)) },
                    onFailure = { mutableEvents.emit(TemplateUiEvent.ExportEmpty) },
                )
            }
        }
    }
}
