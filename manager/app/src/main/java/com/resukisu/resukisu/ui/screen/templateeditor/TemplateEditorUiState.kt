package com.resukisu.resukisu.ui.screen.templateeditor

import androidx.compose.runtime.Immutable
import com.resukisu.resukisu.Natives

@Immutable
data class TemplateEditorActions(
    val onBack: () -> Unit,
    val onDelete: () -> Unit,
    val onSave: () -> Unit,
    val onNameChange: (String) -> Unit,
    val onIdChange: (String) -> Unit,
    val onAuthorChange: (String) -> Unit,
    val onDescriptionChange: (String) -> Unit,
    val onProfileChange: (Natives.Profile) -> Unit,
)
