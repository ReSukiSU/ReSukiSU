package com.resukisu.resukisu.domain.usecase

import com.resukisu.resukisu.data.logging.SulogRepository

class ObserveSulogStateUseCase(private val repository: SulogRepository) {
    operator fun invoke() = repository.state
}

class RefreshSulogUseCase(private val repository: SulogRepository) {
    suspend operator fun invoke(preferredFilePath: String?) = repository.refresh(preferredFilePath)
}

class EnableSulogUseCase(private val repository: SulogRepository) {
    suspend operator fun invoke(enabled: Boolean) = repository.setEnabled(enabled)
}

class CleanSulogUseCase(private val repository: SulogRepository) {
    suspend operator fun invoke(path: String) = repository.clean(path)
}
