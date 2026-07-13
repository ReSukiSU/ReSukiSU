package com.resukisu.resukisu.ui.screen.modulerepo

import androidx.compose.runtime.Immutable
import com.resukisu.resukisu.ui.viewmodel.ModuleRepoViewModel.RepoModule
import com.resukisu.resukisu.ui.component.SearchStatus

enum class RepoSort {
    UPDATED,
    CREATED,
    NAME,
    STARS,
}

@Immutable
data class ModuleRepoActions(
    val onBack: () -> Unit,
    val onRefresh: () -> Unit,
    val onSearchTextChange: (String) -> Unit,
    val onClearSearch: () -> Unit,
    val onSearchStatusChange: (SearchStatus) -> Unit,
    val onSetSortOrder: (RepoSort) -> Unit,
    val onOpenRepoDetail: (RepoModule) -> Unit,
)

@Immutable
data class ModuleRepoDetailActions(
    val onBack: () -> Unit,
    val onOpenWebUrl: () -> Unit,
    val onOpenUrl: (String) -> Unit,
    val onInstallModule: (android.net.Uri) -> Unit,
)
