package com.resukisu.resukisu.ui.wear.component

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.Text
import com.resukisu.resukisu.ui.component.DialogHandle
import com.resukisu.resukisu.ui.component.rememberCustomDialog

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

@Composable
fun rememberWearConfirmDialog(title: String, message: String, onConfirm: () -> Unit): DialogHandle =
    rememberCustomDialog { dismiss ->
        AlertDialog(visible = true, onDismissRequest = dismiss,
            title = { Text(title) }, text = { Text(message) },
            confirmButton = { AlertDialogDefaults.ConfirmButton(onClick = { dismiss(); onConfirm() }) },
            dismissButton = { AlertDialogDefaults.DismissButton(onClick = dismiss) })
    }
