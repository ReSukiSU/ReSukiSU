package com.resukisu.resukisu.domain.usecase

import com.resukisu.resukisu.data.application.DynamicManagerRepository

class ObserveDynamicManagerStateUseCase(private val repository: DynamicManagerRepository) {
    operator fun invoke() = repository.state
}

class RefreshDynamicManagerUseCase(private val repository: DynamicManagerRepository) {
    suspend operator fun invoke() = repository.refresh()
}

class SelectDynamicManagerUseCase(private val repository: DynamicManagerRepository) {
    suspend operator fun invoke(apkPath: String) = repository.selectManager(apkPath)
}

class SetManualDynamicManagerUseCase(private val repository: DynamicManagerRepository) {
    suspend operator fun invoke(size: Int, hash: String) = repository.setManual(size, hash)
}

class ClearDynamicManagerUseCase(private val repository: DynamicManagerRepository) {
    suspend operator fun invoke() = repository.clear()
}
