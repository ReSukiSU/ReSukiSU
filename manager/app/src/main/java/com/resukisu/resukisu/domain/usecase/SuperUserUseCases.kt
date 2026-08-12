package com.resukisu.resukisu.domain.usecase

import com.resukisu.resukisu.data.packageinfo.SuperUserRepository

class ObserveSuperUserStateUseCase(private val repository: SuperUserRepository) {
    operator fun invoke() = repository.state
}

class RefreshSuperUsersUseCase(private val repository: SuperUserRepository) {
    suspend operator fun invoke() = repository.refresh()
}

class BackupAllowlistUseCase(private val repository: SuperUserRepository) {
    suspend operator fun invoke(uri: String) = repository.backupAllowlist(uri)
}

class ImportAllowlistUseCase(private val repository: SuperUserRepository) {
    suspend operator fun invoke(uri: String) = repository.restoreAllowlist(uri)
}
