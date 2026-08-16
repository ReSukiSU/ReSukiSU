package com.resukisu.resukisu.ui.screen.susfs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.resukisu.resukisu.domain.model.SuSFSConfig
import com.resukisu.resukisu.ui.viewmodel.SuSFSViewModel
import com.resukisu.resukisu.ui.viewmodel.SuSFSUiAction
import com.resukisu.resukisu.ui.viewmodel.awaitSuSFSConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal typealias SuSFSRefreshCallback = suspend (SuSFSConfig, Boolean) -> Unit
internal typealias SuSFSRefreshRegistrar = (SuSFSRefreshCallback) -> (() -> Unit)

internal class SuSFSRefreshCoordinator(
    private val coroutineScope: CoroutineScope,
    private val configHelper: SuSFSViewModel,
) {
    private val callbacks = mutableListOf<SuSFSRefreshCallback>()
    private val refreshMutex = Mutex()
    private var initialized = false

    fun register(callback: SuSFSRefreshCallback): () -> Unit {
        callbacks += callback

        val initialLoadJob: Job? = if (initialized) {
            coroutineScope.launch {
                refreshMutex.withLock {
                    if (callback in callbacks) {
                        awaitSuSFSConfig(configHelper) { reply ->
                            SuSFSUiAction.Load(reply)
                        }?.let { callback(it, false) }
                    }
                }
            }
        } else {
            null
        }

        return {
            initialLoadJob?.cancel()
            callbacks.remove(callback)
        }
    }

    suspend fun refresh(
        forceRefresh: Boolean,
        onConfigLoaded: (SuSFSConfig) -> Unit,
    ) {
        refreshMutex.withLock {
            val config = awaitSuSFSConfig(configHelper) { reply ->
                if (forceRefresh) SuSFSUiAction.Refresh(reply) else SuSFSUiAction.Load(reply)
            } ?: return
            onConfigLoaded(config)

            val callbacksToRefresh = callbacks.toList()
            initialized = true
            callbacksToRefresh.forEach { callback ->
                if (callback in callbacks) {
                    callback(config, forceRefresh)
                }
            }
        }
    }
}

@Composable
internal fun RegisterSuSFSRefresh(
    onRegisterRefresh: SuSFSRefreshRegistrar,
    onRefresh: SuSFSRefreshCallback,
) {
    val currentOnRefresh by rememberUpdatedState(onRefresh)

    DisposableEffect(onRegisterRefresh) {
        val callback: SuSFSRefreshCallback = { config, forceRefresh ->
            currentOnRefresh(config, forceRefresh)
        }
        val unregister = onRegisterRefresh(callback)
        onDispose { unregister() }
    }
}
