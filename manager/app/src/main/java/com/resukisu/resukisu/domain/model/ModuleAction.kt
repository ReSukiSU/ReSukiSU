package com.resukisu.resukisu.domain.model

sealed interface ModuleActionUpdate {
    data class Output(
        val text: String,
        val isError: Boolean = false,
    ) : ModuleActionUpdate

    data class Completed(val successful: Boolean) : ModuleActionUpdate
}
