package com.resukisu.resukisu.domain.model

data class InstallEnvironment(
    val rootAvailable: Boolean = false,
    val isGki: Boolean = false,
    val isAbDevice: Boolean = false,
    val currentKmi: String = "",
    val defaultPartition: String = "boot",
    val availablePartitions: List<String> = emptyList(),
    val activeSlotSuffix: String = "",
    val inactiveSlotSuffix: String = "",
    val supportedKmis: List<String> = emptyList(),
)
