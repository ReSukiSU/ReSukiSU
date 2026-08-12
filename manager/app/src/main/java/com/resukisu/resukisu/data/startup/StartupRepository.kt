package com.resukisu.resukisu.data.startup

import com.resukisu.resukisu.domain.model.StartupState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class StartupRepository {
    private val mutableState = MutableStateFlow<StartupState>(StartupState.Loading)
    val state: StateFlow<StartupState> = mutableState.asStateFlow()

    fun markReady() {
        mutableState.value = StartupState.Ready
    }

    fun markFailed(error: Throwable) {
        mutableState.value = StartupState.Failed(error.message ?: error::class.java.simpleName)
    }
}
