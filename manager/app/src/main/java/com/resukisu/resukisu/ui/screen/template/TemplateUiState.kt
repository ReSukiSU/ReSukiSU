package com.resukisu.resukisu.ui.screen.template

import androidx.compose.runtime.Immutable
import com.resukisu.resukisu.ui.viewmodel.TemplateViewModel.TemplateInfo

@Immutable
data class TemplateActions(
    val onBack: () -> Unit,
    val onRefresh: (Boolean) -> Unit,
    val onImport: () -> Unit,
    val onExport: () -> Unit,
    val onCreateTemplate: () -> Unit,
    val onOpenTemplate: (TemplateInfo) -> Unit,
)
