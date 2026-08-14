package com.resukisu.resukisu.domain.usecase

import com.resukisu.resukisu.data.module.ModuleCatalogRepository

class ObserveCatalogModulesUseCase(private val repository: ModuleCatalogRepository) {
    operator fun invoke() = repository.modules
}

class ObserveModuleCatalogRefreshingUseCase(private val repository: ModuleCatalogRepository) {
    operator fun invoke() = repository.refreshing
}

class ObserveModuleCatalogOfflineUseCase(private val repository: ModuleCatalogRepository) {
    operator fun invoke() = repository.offline
}

class RefreshModuleCatalogUseCase(private val repository: ModuleCatalogRepository) {
    suspend operator fun invoke() = repository.refresh()
}

class GetCatalogModuleUseCase(private val repository: ModuleCatalogRepository) {
    suspend operator fun invoke(moduleId: String) = repository.get(moduleId)
}
