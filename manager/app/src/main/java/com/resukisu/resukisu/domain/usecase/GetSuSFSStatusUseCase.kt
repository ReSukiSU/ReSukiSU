package com.resukisu.resukisu.domain.usecase

import com.resukisu.resukisu.data.susfs.SuSFSRepository

class GetSuSFSStatusUseCase(private val repository: SuSFSRepository) {
    suspend operator fun invoke() = repository.getStatus()
}

