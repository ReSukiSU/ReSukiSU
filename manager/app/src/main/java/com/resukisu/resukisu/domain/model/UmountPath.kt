package com.resukisu.resukisu.domain.model

data class UmountPath(
    val path: String,
    val flags: Int,
    val persistent: Boolean,
)

data class UmountState(
    val paths: List<UmountPath> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
)
