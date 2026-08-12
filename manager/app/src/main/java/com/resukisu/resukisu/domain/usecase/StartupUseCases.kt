package com.resukisu.resukisu.domain.usecase

import com.resukisu.resukisu.data.startup.StartupRepository

class ObserveStartupStateUseCase(
    private val repository: StartupRepository,
) {
    operator fun invoke() = repository.state
}
