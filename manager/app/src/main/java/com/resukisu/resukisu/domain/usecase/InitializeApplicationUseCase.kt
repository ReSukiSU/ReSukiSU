package com.resukisu.resukisu.domain.usecase

import com.resukisu.resukisu.data.AppSettingsRepository
import com.resukisu.resukisu.data.startup.ApplicationInitializationRepository
import com.resukisu.resukisu.data.startup.StartupRepository

class InitializeApplicationUseCase(
    private val settingsRepository: AppSettingsRepository,
    private val startupRepository: StartupRepository,
    private val initializationRepository: ApplicationInitializationRepository,
) {
    suspend operator fun invoke() {
        runCatching {
            settingsRepository.preload()
            initializationRepository.initialize()
        }.onSuccess {
            startupRepository.markReady()
        }.onFailure { error ->
            startupRepository.markFailed(error)
        }
    }
}
