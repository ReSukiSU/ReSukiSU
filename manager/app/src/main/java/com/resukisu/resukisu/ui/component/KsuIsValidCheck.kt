package com.resukisu.resukisu.ui.component

import androidx.compose.runtime.Composable
import com.resukisu.resukisu.Natives
import com.resukisu.resukisu.ksuApp

@Composable
fun KsuIsValid(
    content: @Composable () -> Unit
) {
    val isManager = Natives.isManager
    val ksuVersion = if (isManager) Natives.version else null

    if (ksuVersion != null) {
        content()
    }
}