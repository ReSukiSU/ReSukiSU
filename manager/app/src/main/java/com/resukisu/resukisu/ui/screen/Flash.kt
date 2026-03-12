package com.resukisu.resukisu.ui.screen

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.Parcelable
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.resukisu.resukisu.Natives
import com.resukisu.resukisu.R
import com.resukisu.resukisu.ui.component.KeyEventBlocker
import com.resukisu.resukisu.ui.component.rememberCustomDialog
import com.resukisu.resukisu.ui.navigation.LocalNavigator
import com.resukisu.resukisu.ui.navigation.Route
import com.resukisu.resukisu.ui.theme.CardConfig
import com.resukisu.resukisu.ui.util.LkmSelection
import com.resukisu.resukisu.ui.util.LocalSnackbarHost
import com.resukisu.resukisu.ui.util.flashModule
import com.resukisu.resukisu.ui.util.hasMetaModule
import com.resukisu.resukisu.ui.util.installBoot
import com.resukisu.resukisu.ui.util.module.ModuleUtils
import com.resukisu.resukisu.ui.util.reboot
import com.resukisu.resukisu.ui.util.restoreBoot
import com.resukisu.resukisu.ui.util.uninstallPermanently
import com.resukisu.resukisu.ui.viewmodel.ModuleViewModel
import com.topjohnwu.superuser.io.SuFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * @author ShirkNeko
 * @date 2025/5/31.
 */
enum class FlashingStatus {
    FLASHING,
    SUCCESS,
    FAILED
}

private var currentFlashingStatus = mutableStateOf(FlashingStatus.FLASHING)

// 添加模块安装状态跟踪
data class ModuleInstallStatus(
    val totalModules: Int = 0,
    val currentModule: Int = 0,
    val currentModuleName: String = "",
    val failedModules: MutableList<String> = mutableListOf(),
    val verifiedModules: MutableList<String> = mutableListOf() // 添加已验证模块列表
)

private var moduleInstallStatus = mutableStateOf(ModuleInstallStatus())

fun setFlashingStatus(status: FlashingStatus) {
    currentFlashingStatus.value = status
}

fun updateModuleInstallStatus(
    totalModules: Int? = null,
    currentModule: Int? = null,
    currentModuleName: String? = null,
    failedModule: String? = null,
    verifiedModule: String? = null
) {
    val current = moduleInstallStatus.value
    moduleInstallStatus.value = current.copy(
        totalModules = totalModules ?: current.totalModules,
        currentModule = currentModule ?: current.currentModule,
        currentModuleName = currentModuleName ?: current.currentModuleName
    )

    if (failedModule != null) {
        val updatedFailedModules = current.failedModules.toMutableList()
        updatedFailedModules.add(failedModule)
        moduleInstallStatus.value = moduleInstallStatus.value.copy(
            failedModules = updatedFailedModules
        )
    }

    if (verifiedModule != null) {
        val updatedVerifiedModules = current.verifiedModules.toMutableList()
        updatedVerifiedModules.add(verifiedModule)
        moduleInstallStatus.value = moduleInstallStatus.value.copy(
            verifiedModules = updatedVerifiedModules
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashScreen(flashIt: FlashIt) {
    val context = LocalContext.current

    // 是否通过从外部启动的模块安装
    val isExternalInstall = remember {
        when (flashIt) {
            is FlashIt.FlashModule -> {
                (context as? ComponentActivity)?.intent?.let { intent ->
                    intent.action == Intent.ACTION_VIEW || intent.action == Intent.ACTION_SEND
                } ?: false
            }
            is FlashIt.FlashModules -> {
                (context as? ComponentActivity)?.intent?.let { intent ->
                    intent.action == Intent.ACTION_VIEW || intent.action == Intent.ACTION_SEND
                } ?: false
            }
            else -> false
        }
    }

    val navigator = LocalNavigator.current
    var text by rememberSaveable { mutableStateOf("") }
    var tempText: String
    val logContent = rememberSaveable { StringBuilder() }
    var showFloatAction by rememberSaveable { mutableStateOf(false) }
    var shouldWarningUserMetaModule by rememberSaveable { mutableStateOf(false) }
    // 添加状态跟踪是否已经完成刷写
    var hasFlashCompleted by rememberSaveable { mutableStateOf(false) }
    var hasExecuted by rememberSaveable { mutableStateOf(false) }
    // 更新模块状态管理
    var hasUpdateExecuted by rememberSaveable { mutableStateOf(false) }
    var hasUpdateCompleted by rememberSaveable { mutableStateOf(false) }

    val snackBarHost = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val viewModel: ModuleViewModel = viewModel()

    val errorCodeString = stringResource(R.string.error_code)
    val checkLogString = stringResource(R.string.check_log)
    val logSavedString = stringResource(R.string.log_saved)
    val installingModuleString = stringResource(R.string.installing_module)

    val alertDialog = rememberCustomDialog { dismiss: () -> Unit ->
        val uriHandler = LocalUriHandler.current
        AlertDialog(
            onDismissRequest = { dismiss() },
            icon = {
                Icon(Icons.Outlined.Info, contentDescription = null)
            },
            title = {
                Row(modifier = Modifier
                    .fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(text = stringResource(R.string.warning_of_meta_module_title))
                }
            },
            text = {
                Text(text = stringResource(R.string.warning_of_meta_module_summary))
            },
            confirmButton = {
                FilledTonalButton(onClick = { dismiss() }) {
                    Text(text = stringResource(id = android.R.string.ok))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                        uriHandler.openUri("https://kernelsu.org/guide/metamodule.html")
                }) {
                    Text(text = stringResource(id = R.string.learn_more))
                }
            },
        )
    }

    // 当前模块安装状态
    val currentStatus = moduleInstallStatus.value

    // 重置状态
    LaunchedEffect(flashIt) {
        when (flashIt) {
            is FlashIt.FlashModules -> {
                if (flashIt.currentIndex == 0) {
                    moduleInstallStatus.value = ModuleInstallStatus(
                        totalModules = flashIt.uris.size,
                        currentModule = 1
                    )
                    shouldWarningUserMetaModule = false
                    hasFlashCompleted = false
                    hasExecuted = false
                }
            }
            is FlashIt.FlashModuleUpdate -> {
                shouldWarningUserMetaModule = false
                hasUpdateCompleted = false
                hasUpdateExecuted = false
            }
            else -> {
                shouldWarningUserMetaModule = false
                hasFlashCompleted = false
                hasExecuted = false
            }
        }
    }

    // 处理更新模块安装
    LaunchedEffect(flashIt) {
        if (flashIt !is FlashIt.FlashModuleUpdate) return@LaunchedEffect
        if (hasUpdateExecuted || hasUpdateCompleted || text.isNotEmpty()) {
            return@LaunchedEffect
        }

        hasUpdateExecuted = true

        withContext(Dispatchers.IO) {
            setFlashingStatus(FlashingStatus.FLASHING)

            try {
                logContent.append(text).append("\n")
            } catch (_: Exception) {
                logContent.append(text).append("\n")
            }

            flashModuleUpdate(flashIt.uri, onFinish = { showReboot, code ->
                if (code != 0) {
                    text += "$errorCodeString $code.\n$checkLogString\n"
                    setFlashingStatus(FlashingStatus.FAILED)
                } else {
                    setFlashingStatus(FlashingStatus.SUCCESS)

                    viewModel.markNeedRefresh()
                }
                if (showReboot) {
                    text += "\n\n\n"
                    showFloatAction = true

                    // 如果是内部安装，显示重启按钮后不自动返回
                    if (isExternalInstall) {
                        return@flashModuleUpdate
                    }
                }
                hasUpdateCompleted = true

                if (!hasMetaModule() && code == 0) {
                    // 如果没安装 MetaModule，且此模块需要挂载，并且当前模块安装成功，警告用户
                    scope.launch {
                        val mountOldDirectory = SuFile.open("/data/adb/modules/${getModuleIdFromUri(context,flashIt.uri)}/system")
                        val mountNewDirectory = SuFile.open("/data/adb/modules_update/${getModuleIdFromUri(context,flashIt.uri)}/system")
                        if (!(mountNewDirectory.isDirectory) && !(mountOldDirectory.isDirectory)) return@launch
                        shouldWarningUserMetaModule = true

                        alertDialog.show()
                        shouldWarningUserMetaModule = false
                    }
                }
            }, onStdout = {
                tempText = "$it\n"
                if (tempText.startsWith("[H[J")) { // clear command
                    text = tempText.substring(6)
                } else {
                    text += tempText
                }
                logContent.append(it).append("\n")
            }, onStderr = {
                logContent.append(it).append("\n")
            })
        }
    }

    var flashEnabled by rememberSaveable { mutableStateOf(false) }

    val needJailbreakWarning = flashIt is FlashIt.FlashBoot && Natives.isLateLoadMode

    if (needJailbreakWarning && !flashEnabled) {
        JailbreakFlashWarningDialog(
            onConfirm = { flashEnabled = true },
            onDismiss = { navigator.pop() }
        )
    }

    // 安装但排除更新模块
    LaunchedEffect(flashIt, flashEnabled) {
        if (flashIt is FlashIt.FlashModuleUpdate) return@LaunchedEffect
        if (hasExecuted || hasFlashCompleted || text.isNotEmpty()) {
            return@LaunchedEffect
        }

        if (needJailbreakWarning && !flashEnabled) return@LaunchedEffect

        hasExecuted = true

        withContext(Dispatchers.IO) {
            setFlashingStatus(FlashingStatus.FLASHING)

            if (flashIt is FlashIt.FlashModules) {
                try {
                    val currentUri = flashIt.uris[flashIt.currentIndex]
                    val moduleName = getModuleNameFromUri(context, currentUri)
                    updateModuleInstallStatus(
                        currentModuleName = moduleName
                    )
                    text = installingModuleString.format(flashIt.currentIndex + 1, flashIt.uris.size, moduleName)
                    logContent.append(text).append("\n")
                } catch (_: Exception) {
                    text = installingModuleString.format(flashIt.currentIndex + 1, flashIt.uris.size, "Module")
                    logContent.append(text).append("\n")
                }
            }

            flashIt(flashIt, onFinish = { showReboot, code ->
                if (code != 0) {
                    text += "$errorCodeString $code.\n$checkLogString\n"
                    setFlashingStatus(FlashingStatus.FAILED)

                    if (flashIt is FlashIt.FlashModules) {
                        updateModuleInstallStatus(
                            failedModule = moduleInstallStatus.value.currentModuleName
                        )
                    }
                } else {
                    setFlashingStatus(FlashingStatus.SUCCESS)

                    viewModel.markNeedRefresh()
                }
                if (showReboot) {
                    text += "\n\n\n"
                    showFloatAction = true
                }

                hasFlashCompleted = true
                if (!hasMetaModule() && code == 0) {
                    // 没有 MetaModule，且安装成功，检查此模块是否有自动挂载
                    scope.launch {
                        var mountOldDirectory : File
                        var mountNewDirectory : File
                        when (flashIt) {
                            is FlashIt.FlashModules -> {
                                mountOldDirectory = SuFile.open("/data/adb/modules/${getModuleIdFromUri(context,flashIt.uris[flashIt.currentIndex])}/system")
                                mountNewDirectory = SuFile.open("/data/adb/modules_update/${getModuleIdFromUri(context,flashIt.uris[flashIt.currentIndex])}/system")
                            }

                            is FlashIt.FlashModule -> {
                                mountOldDirectory = SuFile.open("/data/adb/modules/${getModuleIdFromUri(context,flashIt.uri)}/system")
                                mountNewDirectory = SuFile.open("/data/adb/modules_update/${getModuleIdFromUri(context,flashIt.uri)}/system")
                            }

                            is FlashIt.FlashModuleUpdate -> {
                                mountOldDirectory = SuFile.open("/data/adb/modules/${getModuleIdFromUri(context,flashIt.uri)}/system")
                                mountNewDirectory = SuFile.open("/data/adb/modules_update/${getModuleIdFromUri(context,flashIt.uri)}/system")
                            }

                            else -> return@launch
                        }
                        if (!mountNewDirectory.isDirectory && !mountOldDirectory.isDirectory) return@launch
                        shouldWarningUserMetaModule = true

                        if (!hasMetaModule() && (flashIt !is FlashIt.FlashModules || flashIt.currentIndex >= flashIt.uris.size - 1)) {
                            // 如果没有 MetaModule，且当前不是多模块刷写或是最后一个需要自动刷写的模块，而且有模块需要挂载，警告用户
                            alertDialog.show()
                        }
                    }
                }

                if (flashIt is FlashIt.FlashModules && flashIt.currentIndex < flashIt.uris.size - 1) {
                    val nextFlashIt = flashIt.copy(
                        currentIndex = flashIt.currentIndex + 1
                    )
                    scope.launch {
                        delay(500)
                        navigator.replace(Route.Flash(nextFlashIt))
                    }
                }
            }, onStdout = {
                tempText = "$it\n"
                if (tempText.startsWith("[H[J")) { // clear command
                    text = tempText.substring(6)
                } else {
                    text += tempText
                }
                logContent.append(it).append("\n")
            }, onStderr = {
                logContent.append(it).append("\n")
            })
        }
    }

    val onBack: () -> Unit = {
        val canGoBack = when (flashIt) {
            is FlashIt.FlashModuleUpdate -> currentFlashingStatus.value != FlashingStatus.FLASHING
            else -> currentFlashingStatus.value != FlashingStatus.FLASHING
        }

        if (canGoBack) {
            if (isExternalInstall) {
                (context as? ComponentActivity)?.finish()
            } else {
                if (flashIt is FlashIt.FlashModules || flashIt is FlashIt.FlashModuleUpdate) {
                    viewModel.markNeedRefresh()
                    viewModel.fetchModuleList()
                    navigator.replaceAll(listOf(Route.Module))
                } else {
                    viewModel.markNeedRefresh()
                    viewModel.fetchModuleList()
                    navigator.pop()
                }
            }
        }
    }

    BackHandler(enabled = true) {
        onBack()
    }

    Scaffold(
        topBar = {
            TopBar(
                currentFlashingStatus.value,
                currentStatus,
                onBack = onBack,
                onSave = {
                    scope.launch {
                        val format = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault())
                        val date = format.format(Date())
                        val file = File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                            "KernelSU_install_log_${date}.log"
                        )
                        file.writeText(logContent.toString())
                        snackBarHost.showSnackbar(logSavedString.format(file.absolutePath))
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            if (showFloatAction) {
                ExtendedFloatingActionButton(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                reboot()
                            }
                        }
                    },
                    icon = {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = stringResource(id = R.string.reboot)
                        )
                    },
                    text = {
                        Text(text = stringResource(id = R.string.reboot))
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    expanded = true
                )
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackBarHost) },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        KeyEventBlocker {
            it.key == Key.VolumeDown || it.key == Key.VolumeUp
        }

        Column(
            modifier = Modifier
                .fillMaxSize(1f)
                .padding(innerPadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        ) {
            if (flashIt is FlashIt.FlashModules) {
                ModuleInstallProgressBar(
                    currentIndex = flashIt.currentIndex + 1,
                    totalCount = flashIt.uris.size,
                    currentModuleName = currentStatus.currentModuleName,
                    status = currentFlashingStatus.value,
                    failedModules = currentStatus.failedModules
                )

                Spacer(modifier = Modifier.height(8.dp))
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scrollState)
            ) {
                LaunchedEffect(text) {
                    scrollState.animateScrollTo(scrollState.maxValue)
                }
                Text(
                    modifier = Modifier.padding(16.dp),
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

private const val JAILBREAK_WARNING_COUNTDOWN = 10

@Composable
fun JailbreakFlashWarningDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var countdown by remember { mutableIntStateOf(JAILBREAK_WARNING_COUNTDOWN) }

    LaunchedEffect(Unit) {
        while (countdown > 0) {
            delay(1000)
            countdown--
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(android.R.string.dialog_alert_title)) },
        text = {
            Text(
                stringResource(R.string.jailbreak_flash_warning),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = countdown == 0
            ) {
                Text(
                    if (countdown > 0)
                        stringResource(R.string.jailbreak_flash_warning_countdown, countdown)
                    else
                        stringResource(R.string.install_next)
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

// 显示模块安装进度条和状态
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ModuleInstallProgressBar(
    currentIndex: Int,
    totalCount: Int,
    currentModuleName: String,
    status: FlashingStatus,
    failedModules: List<String>
) {
    val progressColor = when(status) {
        FlashingStatus.FLASHING -> MaterialTheme.colorScheme.primary
        FlashingStatus.SUCCESS -> MaterialTheme.colorScheme.tertiary
        FlashingStatus.FAILED -> MaterialTheme.colorScheme.error
    }

    val progress = animateFloatAsState(
        targetValue = currentIndex.toFloat() / totalCount.toFloat(),
        label = "InstallProgress"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 模块名称和进度
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = currentModuleName.ifEmpty { stringResource(R.string.module) },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "$currentIndex/$totalCount",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 进度条
            LinearWavyProgressIndicator(
                progress = { progress.value },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = progressColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 失败模块列表
            AnimatedVisibility(
                visible = failedModules.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        Text(
                            text = stringResource(R.string.module_failed_count, failedModules.size),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // 失败模块列表
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                                shape = MaterialTheme.shapes.small
                            )
                            .padding(8.dp)
                    ) {
                        failedModules.forEach { moduleName ->
                            Text(
                                text = "• $moduleName",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    status: FlashingStatus,
    moduleStatus: ModuleInstallStatus = ModuleInstallStatus(),
    onBack: () -> Unit,
    onSave: () -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    val colorScheme = MaterialTheme.colorScheme
    val cardColor = if (CardConfig.isCustomBackgroundEnabled) {
        colorScheme.surfaceContainerLow
    } else {
        colorScheme.background
    }
    val cardAlpha = CardConfig.cardAlpha

    val statusColor = when(status) {
        FlashingStatus.FLASHING -> MaterialTheme.colorScheme.primary
        FlashingStatus.SUCCESS -> MaterialTheme.colorScheme.tertiary
        FlashingStatus.FAILED -> MaterialTheme.colorScheme.error
    }

    TopAppBar(
        title = {
            Column {
                Text(
                    text = stringResource(
                        when (status) {
                            FlashingStatus.FLASHING -> R.string.flashing
                            FlashingStatus.SUCCESS -> R.string.flash_success
                            FlashingStatus.FAILED -> R.string.flash_failed
                        }
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    color = statusColor
                )

                if (moduleStatus.failedModules.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.module_failed_count, moduleStatus.failedModules.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = cardColor.copy(alpha = cardAlpha),
            scrolledContainerColor = cardColor.copy(alpha = cardAlpha)
        ),
        actions = {
            IconButton(onClick = onSave) {
                Icon(
                    imageVector = Icons.Filled.Save,
                    contentDescription = stringResource(id = R.string.save_log),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        scrollBehavior = scrollBehavior
    )
}

suspend fun getModuleNameFromUri(context: Context, uri: Uri): String {
    return withContext(Dispatchers.IO) {
        try {
            if (uri == Uri.EMPTY) {
                return@withContext context.getString(R.string.unknown_module)
            }
            if (!ModuleUtils.isUriAccessible(context, uri)) {
                return@withContext context.getString(R.string.unknown_module)
            }
            ModuleUtils.extractModuleName(context, uri)
        } catch (_: Exception) {
            context.getString(R.string.unknown_module)
        }
    }
}

suspend fun getModuleIdFromUri(context: Context, uri: Uri): String? {
    return withContext(Dispatchers.IO) {
        try {
            if (uri == Uri.EMPTY) {
                return@withContext null
            }
            if (!ModuleUtils.isUriAccessible(context, uri)) {
                return@withContext null
            }
            ModuleUtils.extractModuleId(context, uri)
        } catch (_: Exception) {
            null
        }
    }
}

@Parcelize
sealed class FlashIt : Parcelable {
    data class FlashBoot(val boot: Uri? = null, val lkm: LkmSelection, val ota: Boolean, val partition: String? = null) : FlashIt()
    data class FlashModule(val uri: Uri) : FlashIt()
    data class FlashModules(val uris: List<Uri>, val currentIndex: Int = 0) : FlashIt()
    data class FlashModuleUpdate(val uri: Uri) : FlashIt() // 模块更新
    data object FlashRestore : FlashIt()
    data object FlashUninstall : FlashIt()
}

// 模块更新刷写
fun flashModuleUpdate(
    uri: Uri,
    onFinish: (Boolean, Int) -> Unit,
    onStdout: (String) -> Unit,
    onStderr: (String) -> Unit
) {
    flashModule(uri, onFinish, onStdout, onStderr)
}

fun flashIt(
    flashIt: FlashIt,
    onFinish: (Boolean, Int) -> Unit,
    onStdout: (String) -> Unit,
    onStderr: (String) -> Unit
) {
    when (flashIt) {
        is FlashIt.FlashBoot -> installBoot(
            flashIt.boot,
            flashIt.lkm,
            flashIt.ota,
            flashIt.partition,
            onFinish,
            onStdout,
            onStderr
        )
        is FlashIt.FlashModule -> flashModule(flashIt.uri, onFinish, onStdout, onStderr)
        is FlashIt.FlashModules -> {
            if (flashIt.uris.isEmpty() || flashIt.currentIndex >= flashIt.uris.size) {
                onFinish(false, 0)
                return
            }

            val currentUri = flashIt.uris[flashIt.currentIndex]
            onStdout("\n")

            flashModule(currentUri, onFinish, onStdout, onStderr)
        }
        is FlashIt.FlashModuleUpdate -> {
            onFinish(false, 0)
        }
        FlashIt.FlashRestore -> restoreBoot(onFinish, onStdout, onStderr)
        FlashIt.FlashUninstall -> uninstallPermanently(onFinish, onStdout, onStderr)
    }
}

@Preview
@Composable
fun FlashScreenPreview() {
    FlashScreen(FlashIt.FlashUninstall)
}