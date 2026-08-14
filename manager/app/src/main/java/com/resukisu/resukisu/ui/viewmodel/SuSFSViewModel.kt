package com.resukisu.resukisu.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resukisu.resukisu.domain.model.SuSFSConfig
import com.resukisu.resukisu.domain.model.SuSFSSlotInfo
import com.resukisu.resukisu.domain.model.SuSFSStatusInfo
import com.resukisu.resukisu.domain.model.SusKstatStatically
import com.resukisu.resukisu.domain.model.UidScheme
import com.resukisu.resukisu.domain.usecase.SuSFSConfigUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SuSFSUiState(
    val config: SuSFSConfig? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val statusInfo: SuSFSStatusInfo? = null,
    val slotInfo: List<SuSFSSlotInfo>? = null,
)

enum class SusKstatOperation {
    Normal,
    FullClone,
    Statically,
}

sealed interface SuSFSUiAction {
    data class Load(val reply: SuSFSCommandReply? = null) : SuSFSUiAction
    data class Refresh(val reply: SuSFSCommandReply? = null) : SuSFSUiAction
    data class SetEnabled(
        val enabled: Boolean,
        val reply: SuSFSCommandReply? = null,
    ) : SuSFSUiAction

    data class RestoreDefault(val reply: SuSFSCommandReply? = null) : SuSFSUiAction
    data class LoadStatusInfo(
        val forceRefresh: Boolean = false,
        val reply: SuSFSCommandReply? = null,
    ) : SuSFSUiAction

    data class AddSusPath(
        val path: String,
        val loop: Boolean,
        val reply: SuSFSCommandReply? = null,
    ) : SuSFSUiAction

    data class RemoveSusPath(val path: String, val reply: SuSFSCommandReply? = null) : SuSFSUiAction
    data class AddSusKstat(
        val path: String,
        val type: SusKstatOperation,
        val values: SusKstatStatically? = null,
        val reply: SuSFSCommandReply? = null,
    ) : SuSFSUiAction

    data class RemoveSusKstat(val path: String, val reply: SuSFSCommandReply? = null) :
        SuSFSUiAction

    data class SetUname(
        val release: String,
        val version: String,
        val reply: SuSFSCommandReply? = null,
    ) : SuSFSUiAction

    data class LoadSlotInfo(val reply: SuSFSCommandReply? = null) : SuSFSUiAction
    data class EnableLog(val enabled: Boolean, val reply: SuSFSCommandReply? = null) : SuSFSUiAction
    data class HideSusMnts(val enabled: Boolean, val reply: SuSFSCommandReply? = null) :
        SuSFSUiAction

    data class EnableAvcLogSpoofing(
        val enabled: Boolean,
        val reply: SuSFSCommandReply? = null,
    ) : SuSFSUiAction

    data class SetCmdlineOrBootconfig(
        val path: String,
        val reply: SuSFSCommandReply? = null,
    ) : SuSFSUiAction

    data class AddOpenRedirect(
        val targetPath: String,
        val redirectedPath: String,
        val uidScheme: UidScheme,
        val reply: SuSFSCommandReply? = null,
    ) : SuSFSUiAction

    data class RemoveOpenRedirect(
        val targetPath: String,
        val reply: SuSFSCommandReply? = null,
    ) : SuSFSUiAction

    data class AddSusMap(val path: String, val reply: SuSFSCommandReply? = null) : SuSFSUiAction
    data class RemoveSusMap(val path: String, val reply: SuSFSCommandReply? = null) : SuSFSUiAction
    data class ExportConfig(val uri: String, val reply: SuSFSCommandReply? = null) : SuSFSUiAction
    data class ImportConfig(val uri: String, val reply: SuSFSCommandReply? = null) : SuSFSUiAction
}

sealed interface SuSFSCommandResult {
    data class BooleanValue(val value: Boolean) : SuSFSCommandResult
    data class ConfigValue(val value: SuSFSConfig) : SuSFSCommandResult
    data class StatusInfoValue(val value: SuSFSStatusInfo) : SuSFSCommandResult
    data class SlotInfoValue(val value: List<SuSFSSlotInfo>?) : SuSFSCommandResult
}

typealias SuSFSCommandReply = CompletableDeferred<SuSFSCommandResult>

sealed interface SuSFSUiEvent {
    data class Error(val message: String) : SuSFSUiEvent
}

/** Coordinates SUSFS operations while keeping the Compose layer independent of the repository. */
class SuSFSViewModel(
    private val configUseCase: SuSFSConfigUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SuSFSUiState())
    private val mutableEvents = MutableSharedFlow<SuSFSUiEvent>(extraBufferCapacity = 1)

    val state: StateFlow<SuSFSUiState> = mutableState.asStateFlow()
    val events: SharedFlow<SuSFSUiEvent> = mutableEvents.asSharedFlow()

    init {
        dispatch(SuSFSUiAction.Load())
    }

    fun dispatch(action: SuSFSUiAction) {
        when (action) {
            is SuSFSUiAction.Load -> runOperation(refresh = false, action.reply)
            is SuSFSUiAction.Refresh -> runOperation(refresh = true, action.reply)
            is SuSFSUiAction.SetEnabled -> viewModelScope.launch {
                executeBoolean(action.reply) { configUseCase.setConfigEnabled(action.enabled) }
                    ?.takeIf { it }
                    ?.let {
                        mutableState.value = mutableState.value.copy(
                            config = mutableState.value.config?.copy(enabled = action.enabled),
                        )
                    }
            }

            is SuSFSUiAction.RestoreDefault -> viewModelScope.launch {
                executeBoolean(action.reply) { configUseCase.restoreDefaultConfig() }
            }

            is SuSFSUiAction.LoadStatusInfo -> viewModelScope.launch {
                executeStatusInfo(action.reply, action.forceRefresh)
            }

            is SuSFSUiAction.AddSusPath -> viewModelScope.launch {
                executeBoolean(action.reply) {
                    if (action.loop) configUseCase.addSusPathLoop(action.path)
                    else configUseCase.addSusPath(action.path)
                }
            }

            is SuSFSUiAction.RemoveSusPath -> viewModelScope.launch {
                executeBoolean(action.reply) { configUseCase.removeSusPath(action.path) }
            }

            is SuSFSUiAction.AddSusKstat -> viewModelScope.launch {
                executeBoolean(action.reply) {
                    when (action.type) {
                        SusKstatOperation.Normal -> configUseCase.addSusKstat(action.path)
                        SusKstatOperation.FullClone -> configUseCase.addSusKstatFullClone(action.path)
                        SusKstatOperation.Statically -> configUseCase.addSusKstatStatically(
                            action.path,
                            action.values ?: SusKstatStatically(
                                null, null, null, null, null, null,
                                null, null, null, null, null, null,
                            ),
                        )
                    }
                }
            }

            is SuSFSUiAction.RemoveSusKstat -> viewModelScope.launch {
                executeBoolean(action.reply) { configUseCase.removeSusKstat(action.path) }
            }

            is SuSFSUiAction.SetUname -> viewModelScope.launch {
                executeBoolean(action.reply) {
                    configUseCase.setUname(
                        action.release,
                        action.version
                    )
                }
            }

            is SuSFSUiAction.LoadSlotInfo -> viewModelScope.launch {
                executeSlotInfo(action.reply)
            }

            is SuSFSUiAction.EnableLog -> viewModelScope.launch {
                executeBoolean(action.reply) { configUseCase.enableLog(action.enabled) }
            }

            is SuSFSUiAction.HideSusMnts -> viewModelScope.launch {
                executeBoolean(action.reply) {
                    configUseCase.hideSusMntsForNonSuProcs(action.enabled)
                }
            }

            is SuSFSUiAction.EnableAvcLogSpoofing -> viewModelScope.launch {
                executeBoolean(action.reply) { configUseCase.enableAvcLogSpoofing(action.enabled) }
            }

            is SuSFSUiAction.SetCmdlineOrBootconfig -> viewModelScope.launch {
                executeBoolean(action.reply) { configUseCase.setCmdlineOrBootconfig(action.path) }
            }

            is SuSFSUiAction.AddOpenRedirect -> viewModelScope.launch {
                executeBoolean(action.reply) {
                    configUseCase.addOpenRedirect(
                        action.targetPath,
                        action.redirectedPath,
                        action.uidScheme,
                    )
                }
            }

            is SuSFSUiAction.RemoveOpenRedirect -> viewModelScope.launch {
                executeBoolean(action.reply) { configUseCase.removeOpenRedirect(action.targetPath) }
            }

            is SuSFSUiAction.AddSusMap -> viewModelScope.launch {
                executeBoolean(action.reply) { configUseCase.addSusMap(action.path) }
            }

            is SuSFSUiAction.RemoveSusMap -> viewModelScope.launch {
                executeBoolean(action.reply) { configUseCase.removeSusMap(action.path) }
            }

            is SuSFSUiAction.ExportConfig -> viewModelScope.launch {
                executeBoolean(action.reply) { configUseCase.exportConfig(action.uri) }
            }

            is SuSFSUiAction.ImportConfig -> viewModelScope.launch {
                executeBoolean(action.reply) { configUseCase.importConfig(action.uri) }
            }
        }
    }

    private fun runOperation(refresh: Boolean, reply: SuSFSCommandReply?) {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(
                isLoading = !refresh,
                isRefreshing = refresh,
            )
            runCatching {
                if (refresh) configUseCase.refreshConfig() else configUseCase.loadConfig()
            }.onSuccess { config ->
                mutableState.value = mutableState.value.copy(
                    config = config,
                    isLoading = false,
                    isRefreshing = false,
                )
                reply?.complete(SuSFSCommandResult.ConfigValue(config))
            }.onFailure {
                mutableState.value =
                    mutableState.value.copy(isLoading = false, isRefreshing = false)
                mutableEvents.tryEmit(SuSFSUiEvent.Error(it.message.orEmpty()))
                reply?.completeExceptionally(it)
            }
        }
    }

    private suspend fun executeBoolean(
        reply: SuSFSCommandReply?,
        operation: suspend () -> Boolean,
    ): Boolean? = runCatching { operation() }
        .onSuccess { value -> reply?.complete(SuSFSCommandResult.BooleanValue(value)) }
        .onFailure { error ->
            reply?.completeExceptionally(error)
            mutableEvents.tryEmit(SuSFSUiEvent.Error(error.message.orEmpty()))
        }
        .getOrNull()

    private suspend fun executeStatusInfo(reply: SuSFSCommandReply?, forceRefresh: Boolean) {
        runCatching { configUseCase.loadStatusInfo(forceRefresh) }
            .onSuccess { value ->
                mutableState.value = mutableState.value.copy(statusInfo = value)
                reply?.complete(SuSFSCommandResult.StatusInfoValue(value))
            }
            .onFailure { error ->
                reply?.completeExceptionally(error)
                mutableEvents.tryEmit(SuSFSUiEvent.Error(error.message.orEmpty()))
            }
    }

    private suspend fun executeSlotInfo(reply: SuSFSCommandReply?) {
        runCatching { configUseCase.loadSlotInfo() }
            .onSuccess { value ->
                mutableState.value = mutableState.value.copy(slotInfo = value)
                reply?.complete(SuSFSCommandResult.SlotInfoValue(value))
            }
            .onFailure { error ->
                reply?.completeExceptionally(error)
                mutableEvents.tryEmit(SuSFSUiEvent.Error(error.message.orEmpty()))
            }
    }
}

internal suspend fun awaitSuSFSCommand(
    viewModel: SuSFSViewModel,
    action: (SuSFSCommandReply) -> SuSFSUiAction,
): SuSFSCommandResult? {
    val reply = CompletableDeferred<SuSFSCommandResult>()
    viewModel.dispatch(action(reply))
    return runCatching { reply.await() }.getOrNull()
}

internal suspend fun awaitSuSFSBoolean(
    viewModel: SuSFSViewModel,
    action: (SuSFSCommandReply) -> SuSFSUiAction,
): Boolean = (awaitSuSFSCommand(viewModel, action) as? SuSFSCommandResult.BooleanValue)
    ?.value == true

internal suspend fun awaitSuSFSConfig(
    viewModel: SuSFSViewModel,
    action: (SuSFSCommandReply) -> SuSFSUiAction,
): SuSFSConfig? = (awaitSuSFSCommand(viewModel, action) as? SuSFSCommandResult.ConfigValue)
    ?.value

internal suspend fun awaitSuSFSStatusInfo(
    viewModel: SuSFSViewModel,
    forceRefresh: Boolean,
): SuSFSStatusInfo? = (awaitSuSFSCommand(viewModel) { reply ->
    SuSFSUiAction.LoadStatusInfo(forceRefresh, reply)
} as? SuSFSCommandResult.StatusInfoValue)?.value

internal suspend fun awaitSuSFSSlotInfo(
    viewModel: SuSFSViewModel,
): List<SuSFSSlotInfo>? = (awaitSuSFSCommand(viewModel) { reply ->
    SuSFSUiAction.LoadSlotInfo(reply)
} as? SuSFSCommandResult.SlotInfoValue)?.value
