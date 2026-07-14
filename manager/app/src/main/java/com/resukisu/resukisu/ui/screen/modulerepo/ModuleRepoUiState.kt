package com.resukisu.resukisu.ui.screen.modulerepo

import androidx.compose.runtime.Immutable

enum class RepoSort {
    UPDATED,
    CREATED,
    NAME,
    STARS,
}

@Immutable
data class ModuleRepoDetailActions(
    val onBack: () -> Unit,
    val onOpenWebUrl: () -> Unit,
    val onOpenUrl: (String) -> Unit,
    val onInstallModule: (android.net.Uri) -> Unit,
)
