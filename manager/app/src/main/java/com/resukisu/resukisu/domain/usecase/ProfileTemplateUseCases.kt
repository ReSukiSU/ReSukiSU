package com.resukisu.resukisu.domain.usecase

import com.resukisu.resukisu.data.profile.ProfileTemplateRepository
import com.resukisu.resukisu.domain.model.ProfileTemplate

class ObserveProfileTemplatesUseCase(private val repository: ProfileTemplateRepository) {
    operator fun invoke() = repository.templates
}

class ObserveProfileTemplateRefreshingUseCase(private val repository: ProfileTemplateRepository) {
    operator fun invoke() = repository.refreshing
}

class ObserveProfileTemplateOfflineUseCase(private val repository: ProfileTemplateRepository) {
    operator fun invoke() = repository.offline
}

class RefreshProfileTemplatesUseCase(private val repository: ProfileTemplateRepository) {
    suspend operator fun invoke(synchronize: Boolean = false) = repository.refresh(synchronize)
}

class GetProfileTemplateUseCase(private val repository: ProfileTemplateRepository) {
    suspend operator fun invoke(id: String) = repository.get(id)
}

class SaveProfileTemplateUseCase(private val repository: ProfileTemplateRepository) {
    suspend operator fun invoke(template: ProfileTemplate, create: Boolean = false) =
        repository.save(template, create)
}

class DeleteProfileTemplateUseCase(private val repository: ProfileTemplateRepository) {
    suspend operator fun invoke(id: String) = repository.delete(id)
}

class ImportProfileTemplatesUseCase(private val repository: ProfileTemplateRepository) {
    suspend operator fun invoke(json: String) = repository.import(json)
}

class ExportProfileTemplatesUseCase(private val repository: ProfileTemplateRepository) {
    suspend operator fun invoke() = repository.export()
}
