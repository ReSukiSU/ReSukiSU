package com.resukisu.resukisu.domain.model

data class FlashProgress(
    val isFlashing: Boolean = false,
    val isCompleted: Boolean = false,
    val progress: Float = 0f,
    val currentStep: String = "",
    val logs: List<String> = emptyList(),
    val error: String = "",
)

data class KernelFlashSession(
    val requestUri: String? = null,
    val selectedSlot: String? = null,
    val progress: FlashProgress = FlashProgress(),
    val fullLog: String = "",
)
