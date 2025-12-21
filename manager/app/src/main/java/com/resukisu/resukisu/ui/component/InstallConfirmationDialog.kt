package com.resukisu.resukisu.ui.component

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.GetApp
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.resukisu.resukisu.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

enum class ZipType {
    MODULE,
    KERNEL,
    UNKNOWN
}

data class ZipFileInfo(
    val uri: Uri,
    val type: ZipType,
    val name: String = "",
    val version: String = "",
    val versionCode: String = "",
    val author: String = "",
    val description: String = "",
    val kernelVersion: String = "",
    val supported: String = ""
)

object ZipFileDetector {

    fun detectZipType(context: Context, uri: Uri): ZipType {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zipStream ->
                    var hasModuleProp = false
                    var hasToolsFolder = false
                    var hasAnykernelSh = false

                    var entry = zipStream.nextEntry
                    while (entry != null) {
                        val entryName = entry.name.lowercase()

                        when {
                            entryName == "module.prop" || entryName.endsWith("/module.prop") -> {
                                hasModuleProp = true
                            }
                            entryName.startsWith("tools/") || entryName == "tools" -> {
                                hasToolsFolder = true
                            }
                            entryName == "anykernel.sh" || entryName.endsWith("/anykernel.sh") -> {
                                hasAnykernelSh = true
                            }
                        }

                        zipStream.closeEntry()
                        entry = zipStream.nextEntry
                    }

                    when {
                        hasModuleProp -> ZipType.MODULE
                        hasToolsFolder && hasAnykernelSh -> ZipType.KERNEL
                        else -> ZipType.UNKNOWN
                    }
                }
            } ?: ZipType.UNKNOWN
        } catch (e: IOException) {
            e.printStackTrace()
            ZipType.UNKNOWN
        }
    }

    fun parseModuleInfo(context: Context, uri: Uri): ZipFileInfo {
        var zipInfo = ZipFileInfo(uri = uri, type = ZipType.MODULE)

        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zipStream ->
                    var entry = zipStream.nextEntry
                    while (entry != null) {
                        if (entry.name.lowercase() == "module.prop" || entry.name.endsWith("/module.prop")) {
                            val reader = BufferedReader(InputStreamReader(zipStream))
                            val props = mutableMapOf<String, String>()

                            var line = reader.readLine()
                            while (line != null) {
                                if (line.contains("=") && !line.startsWith("#")) {
                                    val parts = line.split("=", limit = 2)
                                    if (parts.size == 2) {
                                        props[parts[0].trim()] = parts[1].trim()
                                    }
                                }
                                line = reader.readLine()
                            }

                            zipInfo = zipInfo.copy(
                                name = props["name"] ?: context.getString(R.string.unknown_module),
                                version = props["version"] ?: "",
                                versionCode = props["versionCode"] ?: "",
                                author = props["author"] ?: "",
                                description = props["description"] ?: ""
                            )
                            break
                        }
                        zipStream.closeEntry()
                        entry = zipStream.nextEntry
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return zipInfo
    }

    fun parseKernelInfo(context: Context, uri: Uri): ZipFileInfo {
        var zipInfo = ZipFileInfo(uri = uri, type = ZipType.KERNEL)

        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zipStream ->
                    var entry = zipStream.nextEntry
                    while (entry != null) {
                        if (entry.name.lowercase() == "anykernel.sh" || entry.name.endsWith("/anykernel.sh")) {
                            val reader = BufferedReader(InputStreamReader(zipStream))
                            val props = mutableMapOf<String, String>()

                            var inPropertiesBlock = false
                            var line = reader.readLine()
                            while (line != null) {
                                if (line.contains("properties()")) {
                                    inPropertiesBlock = true
                                } else if (inPropertiesBlock && line.contains("'; }")) {
                                    inPropertiesBlock = false
                                } else if (inPropertiesBlock) {
                                    val propertyLine = line.trim()
                                    if (propertyLine.contains("=") && !propertyLine.startsWith("#")) {
                                        val parts = propertyLine.split("=", limit = 2)
                                        if (parts.size == 2) {
                                            val key = parts[0].trim()
                                            val value = parts[1].trim().removeSurrounding("'").removeSurrounding("\"")
                                            when (key) {
                                                "kernel.string" -> props["name"] = value
                                                "supported.versions" -> props["supported"] = value
                                            }
                                        }
                                    }
                                }

                                // 解析普通变量定义
                                if (line.contains("kernel.string=") && !inPropertiesBlock) {
                                    val value = line.substringAfter("kernel.string=").trim().removeSurrounding("\"")
                                    props["name"] = value
                                }
                                if (line.contains("supported.versions=") && !inPropertiesBlock) {
                                    val value = line.substringAfter("supported.versions=").trim().removeSurrounding("\"")
                                    props["supported"] = value
                                }
                                if (line.contains("kernel.version=") && !inPropertiesBlock) {
                                    val value = line.substringAfter("kernel.version=").trim().removeSurrounding("\"")
                                    props["version"] = value
                                }
                                if (line.contains("kernel.author=") && !inPropertiesBlock) {
                                    val value = line.substringAfter("kernel.author=").trim().removeSurrounding("\"")
                                    props["author"] = value
                                }

                                line = reader.readLine()
                            }

                            zipInfo = zipInfo.copy(
                                name = props["name"] ?: context.getString(R.string.unknown_kernel),
                                version = props["version"] ?: "",
                                author = props["author"] ?: "",
                                supported = props["supported"] ?: "",
                                kernelVersion = props["version"] ?: ""
                            )
                            break
                        }
                        zipStream.closeEntry()
                        entry = zipStream.nextEntry
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return zipInfo
    }

    suspend fun detectAndParseZipFiles(context: Context, zipUris: List<Uri>): List<ZipFileInfo> {
        return withContext(Dispatchers.IO) {
            val zipFileInfos = mutableListOf<ZipFileInfo>()

            for (uri in zipUris) {
                val zipType = detectZipType(context, uri)
                val zipInfo = when (zipType) {
                    ZipType.MODULE -> parseModuleInfo(context, uri)
                    ZipType.KERNEL -> parseKernelInfo(context, uri)
                    ZipType.UNKNOWN -> ZipFileInfo(
                        uri = uri,
                        type = ZipType.UNKNOWN,
                        name = context.getString(R.string.unknown_file)
                    )
                }
                zipFileInfos.add(zipInfo)
            }

            zipFileInfos.filter { it.type != ZipType.UNKNOWN }
        }
    }
}

@Composable
fun InstallConfirmationDialog(
    show: Boolean,
    zipFiles: List<ZipFileInfo>,
    onConfirm: (List<ZipFileInfo>) -> Unit,
    onDismiss: () -> Unit
) {
    if (show && zipFiles.isNotEmpty()) {
        val context = LocalContext.current

        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = if (zipFiles.any { it.type == ZipType.KERNEL })
                            Icons.Default.Memory else Icons.Default.Extension,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (zipFiles.size == 1) {
                            context.getString(R.string.confirm_installation)
                        } else {
                            context.getString(R.string.confirm_multiple_installation, zipFiles.size)
                        },
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
            },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(zipFiles.size) { index ->
                        val zipFile = zipFiles[index]
                        InstallItemCard(zipFile = zipFile)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { onConfirm(zipFiles) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.GetApp,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(context.getString(R.string.install_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(
                        context.getString(android.R.string.cancel),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            modifier = Modifier.widthIn(min = 320.dp, max = 560.dp)
        )
    }
}

@Composable
fun InstallItemCard(zipFile: ZipFileInfo) {
    val context = LocalContext.current

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = when (zipFile.type) {
                ZipType.MODULE -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ZipType.KERNEL -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = when (zipFile.type) {
                        ZipType.MODULE -> Icons.Default.Extension
                        ZipType.KERNEL -> Icons.Default.Memory
                        else -> Icons.AutoMirrored.Filled.Help
                    },
                    contentDescription = null,
                    tint = when (zipFile.type) {
                        ZipType.MODULE -> MaterialTheme.colorScheme.primary
                        ZipType.KERNEL -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = zipFile.name.ifEmpty {
                            when (zipFile.type) {
                                ZipType.MODULE -> context.getString(R.string.unknown_module)
                                ZipType.KERNEL -> context.getString(R.string.unknown_kernel)
                                else -> context.getString(R.string.unknown_file)
                            }
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = when (zipFile.type) {
                            ZipType.MODULE -> context.getString(R.string.module_package)
                            ZipType.KERNEL -> context.getString(R.string.kernel_package)
                            else -> context.getString(R.string.unknown_package)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 详细信息
            if (zipFile.version.isNotEmpty() || zipFile.author.isNotEmpty() ||
                zipFile.description.isNotEmpty() || zipFile.supported.isNotEmpty()) {

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    thickness = 0.5.dp
                )
                Spacer(modifier = Modifier.height(8.dp))

                // 版本信息
                if (zipFile.version.isNotEmpty()) {
                    InfoRow(
                        label = context.getString(R.string.version),
                        value = zipFile.version + if (zipFile.versionCode.isNotEmpty()) " (${zipFile.versionCode})" else ""
                    )
                }

                // 作者信息
                if (zipFile.author.isNotEmpty()) {
                    InfoRow(
                        label = context.getString(R.string.author),
                        value = zipFile.author
                    )
                }

                // 描述信息 (仅模块)
                if (zipFile.description.isNotEmpty() && zipFile.type == ZipType.MODULE) {
                    InfoRow(
                        label = context.getString(R.string.description),
                        value = zipFile.description
                    )
                }

                // 支持设备 (仅内核)
                if (zipFile.supported.isNotEmpty() && zipFile.type == ZipType.KERNEL) {
                    InfoRow(
                        label = context.getString(R.string.supported_devices),
                        value = zipFile.supported
                    )
                }
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(min = 60.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}