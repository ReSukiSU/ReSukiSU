package com.resukisu.resukisu.data.count

import com.resukisu.resukisu.data.shell.KsuCliRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

data class CountState(
    val superuserCount: Int = 0,
    val moduleCount: Int = 0,
)

class CountRepository(
    private val ksuCliRepository: KsuCliRepository,
) {
    private val mutableState = MutableStateFlow(CountState())
    val state: StateFlow<CountState> = mutableState.asStateFlow()

    suspend fun refresh() {
        val (superuserCount, moduleCount) = withContext(Dispatchers.IO) {
            ksuCliRepository.getSuperuserCount() to ksuCliRepository.getModuleCount()
        }
        mutableState.update {
            it.copy(superuserCount = superuserCount, moduleCount = moduleCount)
        }
    }
}
