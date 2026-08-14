package com.resukisu.resukisu.domain.model

data class DynamicManagerConfig(
    val size: Int = 0,
    val hash: String = "",
) {
    val isValid: Boolean
        get() = size > 0 && hash.length == 64

}

data class DynamicManagerApp(
    val label: String,
    val packageName: String,
    val uid: Int,
    val apkPath: String,
    val isSelected: Boolean = false,
    val managerSignatureIndex: Int? = null,
    val isChangeable: Boolean = true,
)

data class DynamicManagerState(
    val config: DynamicManagerConfig? = null,
    val apps: List<DynamicManagerApp> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isSubmitting: Boolean = false,
)
