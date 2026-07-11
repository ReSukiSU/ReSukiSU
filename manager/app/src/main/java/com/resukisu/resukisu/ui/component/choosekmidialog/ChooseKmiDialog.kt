package com.resukisu.resukisu.ui.component.choosekmidialog

import androidx.compose.runtime.Composable
import com.resukisu.resukisu.ui.LocalUiMode
import com.resukisu.resukisu.ui.UiMode

@Composable
fun ChooseKmiDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onSelected: (String?) -> Unit
) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> ChooseKmiDialogMiuix(show, onDismissRequest, onSelected)
        UiMode.Material -> ChooseKmiDialogMiuix(show, onDismissRequest, onSelected)
    }
}
