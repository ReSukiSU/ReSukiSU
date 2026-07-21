package com.resukisu.resukisu.ui.screen.susfs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.resukisu.resukisu.data.susfs.SuSFSConfig
import com.resukisu.resukisu.data.susfs.SuSFSConfigHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal typealias SuSFSRefreshCallback = suspend (SuSFSConfig, Boolean) -> Unit
internal typealias SuSFSRefreshRegistrar = (SuSFSRefreshCallback) -> (() -> Unit)

internal class SuSFSRefreshCoordinator(
    private val coroutineScope: CoroutineScope,
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
                        callback(SuSFSConfigHelper.loadConfig(), false)
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
            val config = if (forceRefresh) {
                SuSFSConfigHelper.refreshConfig()
            } else {
                SuSFSConfigHelper.loadConfig()
            }
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
