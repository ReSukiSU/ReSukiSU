package com.resukisu.resukisu.domain.usecase

import com.resukisu.resukisu.data.logging.BugreportRepository
import java.io.File

class GenerateBugreportUseCase(
    private val repository: BugreportRepository,
) {
    operator fun invoke(): File = repository.create()
}
