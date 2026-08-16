package com.resukisu.resukisu.domain.usecase

import com.resukisu.resukisu.data.module.ModuleRepository

class SetModuleEnabledUseCase(
    private val repository: ModuleRepository,
) {
    suspend operator fun invoke(moduleId: String, enabled: Boolean) =
        repository.setModuleEnabled(moduleId, enabled)
}

class SetModuleRemovedUseCase(
    private val repository: ModuleRepository,
) {
    suspend operator fun invoke(moduleId: String, removed: Boolean) =
        repository.setModuleRemoved(moduleId, removed)
}
