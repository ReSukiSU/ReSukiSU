package com.resukisu.resukisu.ui.screen.susfs.component

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.resukisu.resukisu.R
import com.resukisu.resukisu.ui.component.settings.SegmentedColumn
import com.resukisu.resukisu.ui.component.settings.SegmentedColumnScope
import com.resukisu.resukisu.ui.component.settings.SettingsDropdownWidget
import com.resukisu.resukisu.ui.component.settings.SettingsJumpPageWidget
import com.resukisu.resukisu.ui.util.LocalSnackbarHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Displays an entry's fields in a read-only dialog with a delete action.
 *
 * @param showDialog Whether the dialog is visible.
 * @param title The dialog title.
 * @param fields Read-only fields represented as label-value pairs.
 * @param onDismiss Invoked when the dialog is dismissed.
 * @param onDelete Invoked when the delete action is selected.
 */
@Composable
fun EntryDetailDialog(
    showDialog: Boolean,
    title: String,
    fields: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    if (showDialog) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    fields.forEach { (label, value) ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = value,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

/**
 * Provides the common dialog container for manually adding an entry.
 *
 * When multiple subtypes are available, a dropdown is shown above the caller-provided form.
 *
 * @param showDialog Whether the dialog is visible.
 * @param title The dialog title.
 * @param subtypes The available entry subtypes.
 * @param selectedSubtype The currently selected subtype.
 * @param onSubtypeChange Invoked when the selected subtype changes.
 * @param onDismiss Invoked when the dialog is dismissed.
 * @param onConfirm Invoked when the add action is confirmed.
 * @param showImportFromFile Whether to show the file import action.
 * @param onImportFromFile Invoked with the contents of the selected text file.
 * @param formContent The caller-provided form content.
 */
@Composable
fun ManualAddDialog(
    showDialog: Boolean,
    title: String,
    subtypes: List<String>,
    selectedSubtype: String,
    onSubtypeChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    showImportFromFile: Boolean = false,
    onImportFromFile: (String) -> Unit = {},
    formContent: SegmentedColumnScope.() -> Unit
) {
    val context = LocalContext.current
    val snackbarHost = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()
    val onImportFromFileState by rememberUpdatedState(onImportFromFile)

    val fileReadFailedMsg = stringResource(R.string.susfs_entry_import_file_failed)
    val fileNotTextMsg = stringResource(R.string.susfs_entry_import_file_not_text)
    val importFromFileLabel = stringResource(R.string.susfs_entry_import_from_file)

    val pickFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val decodedContent = withContext(Dispatchers.IO) {
                    runCatching {
                        checkNotNull(context.contentResolver.openInputStream(it)).use { stream ->
                            stream.readBytes().decodeToString(throwOnInvalidSequence = true)
                        }
                    }
                }

                val error = decodedContent.exceptionOrNull()
                if (error != null) {
                    snackbarHost.showSnackbar(
                        if (error is CharacterCodingException) fileNotTextMsg else fileReadFailedMsg
                    )
                    return@launch
                }

                decodedContent.getOrThrow().takeIf { content -> content.isNotEmpty() }?.let {
                    onImportFromFileState(it)
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    SegmentedColumn(contentPadding = PaddingValues(0.dp)) {
                        if (subtypes.size > 1) {
                            item {
                                SettingsDropdownWidget(
                                    title = stringResource(R.string.susfs_entry_select_subtype),
                                    description = selectedSubtype,
                                    iconPlaceholder = false,
                                    choice = subtypes.indexOf(selectedSubtype).coerceAtLeast(0),
                                    data = subtypes,
                                    onChoiceChange = { index -> onSubtypeChange(subtypes[index]) }
                                )
                            }
                        }
                        formContent()
                        if (showImportFromFile) {
                            item {
                                SettingsJumpPageWidget(
                                    icon = Icons.Default.UploadFile,
                                    title = importFromFileLabel,
                                    description = stringResource(R.string.susfs_entry_import_hint),
                                    onClick = { pickFileLauncher.launch(arrayOf("*/*")) },
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = onConfirm
                ) {
                    Text(stringResource(R.string.add))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

fun String.toImportedEntryLines(): List<String> {
    return lineSequence()
        .map { it.replace("\uFEFF", "").trim() }
        .filter { it.isNotEmpty() && !it.startsWith("//") && !it.startsWith("#") }
        .toList()
}
