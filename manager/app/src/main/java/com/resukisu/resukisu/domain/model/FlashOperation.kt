package com.resukisu.resukisu.domain.model

sealed interface FlashOperation {
    data class Boot(
        val bootUri: String?,
        val lkm: LkmSelection = LkmSelection.KmiNone,
        val ota: Boolean,
        val partition: String?,
    ) : FlashOperation

    data class Module(val uri: String) : FlashOperation
    data object Restore : FlashOperation
    data object Uninstall : FlashOperation
}

sealed interface FlashOperationUpdate {
    data class Output(val line: String) : FlashOperationUpdate
    data class ErrorOutput(val line: String) : FlashOperationUpdate
    data class Completed(val showReboot: Boolean, val code: Int) : FlashOperationUpdate
}
