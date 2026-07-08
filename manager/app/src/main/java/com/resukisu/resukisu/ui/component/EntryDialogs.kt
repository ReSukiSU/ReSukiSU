package com.resukisu.resukisu.ui.component

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.documentfile.provider.DocumentFile
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.resukisu.resukisu.R
import com.resukisu.resukisu.ui.component.settings.SettingsDialogFrame
import com.resukisu.resukisu.ui.component.settings.SettingsDropdownWidget
import com.resukisu.resukisu.ui.util.LocalSnackbarHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 批量导入对话框
 *
 * 提供多行文本输入，确认时按行分割、trim、过滤空行后回调。
 * 同时支持通过"从文件导入"按钮选择文本文件，将其内容填充到输入框中，便于预览/编辑后再确认。
 *
 * @param showDialog 是否显示
 * @param title 对话框标题
 * @param hint 输入提示文案
 * @param onDismiss 关闭回调
 * @param onConfirm 确认回调，返回非空行列表
 * @param isLoading 是否加载中（加载时禁用交互）
 */
@Composable
fun BatchImportDialog(
    showDialog: Boolean,
    title: String,
    hint: String,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
    isLoading: Boolean = false
) {
    var inputText by remember { mutableStateOf("") }
    val context = LocalContext.current
    val snackbarHost = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()

    val fileReadFailedMsg = stringResource(R.string.susfs_entry_import_file_failed)
    val fileNotTextMsg = stringResource(R.string.susfs_entry_import_file_not_text)
    val importFromFileLabel = stringResource(R.string.susfs_entry_import_from_file)

    val pickFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val content = readTxtDocument(
                    context = context,
                    snackbarHost = snackbarHost,
                    uri = it,
                    fileNotTextMsg = fileNotTextMsg,
                    fileReadFailedMsg = fileReadFailedMsg
                )
                if (content.isNotEmpty()) {
                    inputText = if (inputText.isBlank()) content else "${inputText.trim()}\n$content"
                }
            }
        }
    }

    if (showDialog) {
        SettingsDialogFrame(
            title = title,
            onDismissRequest = onDismiss,
            buttons = {
                TextButton(
                    onClick = {
                        onDismiss()
                        inputText = ""
                    }
                ) {
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    onClick = {
                        val lines = inputText.toImportedEntryLines()
                        if (lines.isNotEmpty()) {
                            onConfirm(lines)
                            inputText = ""
                        }
                    },
                    enabled = !isLoading && inputText.isNotBlank()
                ) {
                    Text(stringResource(R.string.add))
                }
            }
        ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading,
                        singleLine = false,
                        minLines = 4,
                        maxLines = 8
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                            OutlinedButton(
                                onClick = { pickFileLauncher.launch(arrayOf("text/plain")) },
                                enabled = !isLoading
                            ) {
                                Icon(
                                    imageVector = Icons.Default.UploadFile,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                                Text(importFromFileLabel)
                            }
                    }
                }
        }
    }
}

/**
 * 条目详情对话框
 *
 * 以只读方式展示条目的各字段，并提供删除入口。
 *
 * @param showDialog 是否显示
 * @param title 对话框标题
 * @param fields 只读字段列表，每项为 (label, value)
 * @param onDismiss 关闭回调
 * @param onDelete 删除回调
 * @param isLoading 是否加载中（加载时禁用交互）
 */
@Composable
fun EntryDetailDialog(
    showDialog: Boolean,
    title: String,
    fields: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    isLoading: Boolean = false
) {
    if (showDialog) {
        SettingsDialogFrame(
            title = title,
            onDismissRequest = onDismiss,
            buttons = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    onClick = onDelete,
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.delete))
                }
            }
        ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
        }
    }
}

/**
 * 手动添加对话框通用容器
 *
 * 当存在多个子类型选项时，顶部提供下拉选择；否则只展示下方由调用方提供的具体表单内容。
 *
 * @param showDialog 是否显示
 * @param title 对话框标题
 * @param subtypes 子类型选项列表
 * @param selectedSubtype 当前选中的子类型
 * @param onSubtypeChange 子类型变更回调
 * @param onDismiss 关闭回调
 * @param onConfirm 确认添加回调
 * @param isLoading 是否加载中（加载时禁用交互）
 * @param formContent 由调用方提供的表单内容
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
    isLoading: Boolean = false,
    showImportFromFile: Boolean = false,
    onImportFromFile: (String) -> Unit = {},
    formContent: @Composable () -> Unit
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
                val content = readTxtDocument(
                    context = context,
                    snackbarHost = snackbarHost,
                    uri = it,
                    fileNotTextMsg = fileNotTextMsg,
                    fileReadFailedMsg = fileReadFailedMsg
                )
                if (content.isNotEmpty()) {
                    onImportFromFileState(content)
                }
            }
        }
    }

    if (showDialog) {
        SettingsDialogFrame(
            title = title,
            onDismissRequest = onDismiss,
            buttons = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    onClick = onConfirm,
                    enabled = !isLoading
                ) {
                    Text(stringResource(R.string.add))
                }
            }
        ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (subtypes.size > 1) {
                        SettingsDropdownWidget(
                            title = stringResource(R.string.susfs_entry_select_subtype),
                            description = selectedSubtype,
                            iconPlaceholder = false,
                            enabled = !isLoading,
                            choice = subtypes.indexOf(selectedSubtype).coerceAtLeast(0),
                            data = subtypes,
                            onChoiceChange = { index -> onSubtypeChange(subtypes[index]) }
                        )
                    }
                    formContent()
                    if (showImportFromFile) {
                        Text(
                            text = stringResource(R.string.susfs_entry_import_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            OutlinedButton(
                                onClick = { pickFileLauncher.launch(arrayOf("text/plain")) },
                                enabled = !isLoading
                            ) {
                                Icon(
                                    imageVector = Icons.Default.UploadFile,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                                Text(importFromFileLabel)
                            }
                        }
                    }
                }
        }
    }
}

private suspend fun readTxtDocument(
    context: android.content.Context,
    snackbarHost: SnackbarHostState,
    uri: Uri,
    fileNotTextMsg: String,
    fileReadFailedMsg: String,
): String {
    val fileName = DocumentFile.fromSingleUri(context, uri)?.name.orEmpty()
    if (!fileName.endsWith(".txt", ignoreCase = true)) {
        snackbarHost.showSnackbar(fileNotTextMsg)
        return ""
    }

    val content = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader().readText()
            }.orEmpty()
        }.getOrDefault("")
    }

    if (content.isBlank()) {
        snackbarHost.showSnackbar(fileReadFailedMsg)
        return ""
    }

    return content
}

fun String.toImportedEntryLines(): List<String> {
    return lineSequence()
        .map { it.replace("\uFEFF", "").trim() }
        .filter { it.isNotEmpty() && !it.startsWith("//") && !it.startsWith("#") }
        .toList()
}
