package com.resukisu.resukisu.data.system

import com.resukisu.resukisu.domain.model.HomeDashboardState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class HomeStateRepository {
    private val mutableState = MutableStateFlow(HomeDashboardState())
    val state: StateFlow<HomeDashboardState> = mutableState.asStateFlow()

    fun update(transform: (HomeDashboardState) -> HomeDashboardState) {
        mutableState.update(transform)
    }
}
