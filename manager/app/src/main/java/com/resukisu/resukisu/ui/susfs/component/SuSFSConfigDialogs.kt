package com.resukisu.resukisu.ui.susfs.component

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.resukisu.resukisu.R
import com.resukisu.resukisu.ui.util.LocalSnackbarHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 空状态显示组件
 */
@Composable
fun EmptyStateCard(
    message: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ==================== 通用对话框容器（Task 5 新增） ====================

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
    val importFromFileLabel = stringResource(R.string.susfs_entry_import_from_file)

    val pickFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val content = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(it)?.use { stream ->
                            stream.bufferedReader().readText()
                        } ?: ""
                    }.getOrDefault("")
                }
                if (content.isNotEmpty()) {
                    inputText = if (inputText.isBlank()) content else "${inputText.trim()}\n$content"
                } else {
                    snackbarHost.showSnackbar(fileReadFailedMsg)
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
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
                        shape = RoundedCornerShape(8.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(
                            onClick = { pickFileLauncher.launch(arrayOf("text/plain", "*/*")) },
                            enabled = !isLoading,
                            shape = RoundedCornerShape(8.dp)
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
            },
            confirmButton = {
                Button(
                    onClick = {
                        val lines = inputText.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                        if (lines.isNotEmpty()) {
                            onConfirm(lines)
                            inputText = ""
                        }
                    },
                    enabled = !isLoading && inputText.isNotBlank(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.add))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        onDismiss()
                        inputText = ""
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
            shape = RoundedCornerShape(12.dp)
        )
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
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
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
            },
            confirmButton = {
                Button(
                    onClick = onDelete,
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
            shape = RoundedCornerShape(12.dp)
        )
    }
}

/**
 * 手动添加对话框通用容器
 *
 * 顶部提供子类型下拉选择，下方由调用方通过 [formContent] 提供具体表单内容。
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
@OptIn(ExperimentalMaterial3Api::class)
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
    formContent: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = selectedSubtype,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                            enabled = !isLoading,
                            shape = RoundedCornerShape(8.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            subtypes.forEach { subtype ->
                                DropdownMenuItem(
                                    text = { Text(subtype) },
                                    onClick = {
                                        onSubtypeChange(subtype)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                    formContent()
                }
            },
            confirmButton = {
                Button(
                    onClick = onConfirm,
                    enabled = !isLoading,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.add))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
            shape = RoundedCornerShape(12.dp)
        )
    }
}
