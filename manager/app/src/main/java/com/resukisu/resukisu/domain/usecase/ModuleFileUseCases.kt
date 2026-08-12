package com.resukisu.resukisu.domain.usecase

import com.resukisu.resukisu.data.file.ModuleFileRepository

class IsModuleUriAccessibleUseCase(private val repository: ModuleFileRepository) {
    operator fun invoke(uri: String) = repository.isUriAccessible(uri)
}

class TakeModuleUriPermissionUseCase(private val repository: ModuleFileRepository) {
    operator fun invoke(uri: String) = repository.takePersistableUriPermission(uri)
}

class ExtractModuleNameUseCase(private val repository: ModuleFileRepository) {
    operator fun invoke(uri: String) = repository.extractModuleName(uri)
}

class ExtractModuleIdUseCase(private val repository: ModuleFileRepository) {
    operator fun invoke(uri: String) = repository.extractModuleId(uri)
}
