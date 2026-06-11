package com.resukisu.resukisu.ui.screen.kernelFlash

import android.net.Uri
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.resukisu.resukisu.R
import com.resukisu.resukisu.data.appPreferences
import com.resukisu.resukisu.ui.component.KeyEventBlocker
import com.resukisu.resukisu.ui.component.SwipeableSnackbarHost
import com.resukisu.resukisu.ui.navigation.LocalNavigator
import com.resukisu.resukisu.ui.screen.kernelFlash.state.FlashState
import com.resukisu.resukisu.ui.screen.kernelFlash.state.HorizonKernelState
import com.resukisu.resukisu.ui.screen.kernelFlash.state.HorizonKernelWorker
import com.resukisu.resukisu.ui.theme.CardConfig
import com.resukisu.resukisu.ui.util.LocalSnackbarHost
import com.resukisu.resukisu.ui.util.install
import com.resukisu.resukisu.ui.util.reboot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * @author ShirkNeko
 * @date 2025/5/31.
 */
private object KernelFlashStateHolder {
    var currentState: HorizonKernelState? = null
    var currentUri: Uri? = null
    var currentSlot: String? = null
    var isFlashing = false
}

/**
 * Kernel刷写界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KernelFlashScreen(
    kernelUri: Uri,
    selectedSlot: String? = null
) {
    val context = LocalContext.current

    val shouldAutoExit = remember {
        context.appPreferences.getBoolean("auto_exit_after_flash", false)
    }

    val scrollState = rememberScrollState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val snackBarHost = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()
    var logText by rememberSaveable { mutableStateOf("") }
    var showFloatAction by rememberSaveable { mutableStateOf(false) }
    val logContent = rememberSaveable { StringBuilder() }
    val horizonKernelState = remember {
        if (KernelFlashStateHolder.currentState != null &&
            KernelFlashStateHolder.currentUri == kernelUri &&
            KernelFlashStateHolder.currentSlot == selectedSlot) {
            KernelFlashStateHolder.currentState!!
        } else {
            HorizonKernelState().also {
                KernelFlashStateHolder.currentState = it
                KernelFlashStateHolder.currentUri = kernelUri
                KernelFlashStateHolder.currentSlot = selectedSlot
                KernelFlashStateHolder.isFlashing = false
            }
        }
    }

    val flashState by horizonKernelState.state.collectAsState()
    val logSavedString = stringResource(R.string.log_saved)

    val onFlashComplete = {
        showFloatAction = true
        KernelFlashStateHolder.isFlashing = false

        install()

        // 如果需要自动退出，延迟1.5秒后退出
        if (shouldAutoExit) {
            scope.launch {
                delay(1500)
                context.appPreferences.remove("auto_exit_after_flash")
                (context as? ComponentActivity)?.finish()
            }
        }
    }

    val horizonFlashComplete = stringResource(R.string.horizon_flash_complete)

    // 开始刷写
    LaunchedEffect(Unit) {
        if (!KernelFlashStateHolder.isFlashing && !flashState.isCompleted && flashState.error.isEmpty()) {
            withContext(Dispatchers.IO) {
                KernelFlashStateHolder.isFlashing = true
                val worker = HorizonKernelWorker(
                    context = context,
                    state = horizonKernelState,
                    slot = selectedSlot
                )
                worker.uri = kernelUri
                worker.setOnFlashCompleteListener(onFlashComplete)
                worker.start()

                // 监听日志更新
                while (flashState.error.isEmpty()) {
                    if (flashState.logs.isNotEmpty()) {
                        logText = flashState.logs.joinToString("\n")
                        logContent.clear()
                        logContent.append(logText)
                    }
                    delay(100)
                }

                if (flashState.error.isNotEmpty()) {
                    logText += "\n${flashState.error}\n"
                    logContent.append("\n${flashState.error}\n")
                    KernelFlashStateHolder.isFlashing = false
                }
            }
        } else {
            logText = flashState.logs.joinToString("\n")
            if (flashState.error.isNotEmpty()) {
                logText += "\n${flashState.error}\n"
            } else if (flashState.isCompleted) {
                logText += "\n${horizonFlashComplete}\n\n\n"
                showFloatAction = true

                install()
            }
        }
    }

    val navigator = LocalNavigator.current

    val onBack: () -> Unit = {
        if (!flashState.isFlashing || flashState.isCompleted || flashState.error.isNotEmpty()) {
            // 清理全局状态
            if (flashState.isCompleted || flashState.error.isNotEmpty()) {
                KernelFlashStateHolder.currentState = null
                KernelFlashStateHolder.currentUri = null
                KernelFlashStateHolder.currentSlot = null
                KernelFlashStateHolder.isFlashing = false
            }
            navigator.pop()
        }
    }

    DisposableEffect(shouldAutoExit) {
        onDispose {
            if (shouldAutoExit) {
                KernelFlashStateHolder.currentState = null
                KernelFlashStateHolder.currentUri = null
                KernelFlashStateHolder.currentSlot = null
                KernelFlashStateHolder.isFlashing = false
            }
        }
    }

    BackHandler(enabled = true) {
        onBack()
    }

    Scaffold(
        topBar = {
            TopBar(
                flashState = flashState,
                onBack = onBack,
                onSave = {
                    scope.launch {
                        val format = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault())
                        val date = format.format(Date())
                        val file = File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                            "KernelSU_kernel_flash_log_${date}.log"
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
        snackbarHost = { SwipeableSnackbarHost(hostState = snackBarHost) },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        KeyEventBlocker {
            it.key == Key.VolumeDown || it.key == Key.VolumeUp
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        ) {
            FlashProgressIndicator(flashState)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scrollState)
            ) {
                LaunchedEffect(logText) {
                    scrollState.animateScrollTo(scrollState.maxValue)
                }
                Text(
                    modifier = Modifier.padding(16.dp),
                    text = logText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FlashProgressIndicator(
    flashState: FlashState
) {
    val progressColor = when {
        flashState.error.isNotEmpty() -> MaterialTheme.colorScheme.error
        flashState.isCompleted -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    val progress = animateFloatAsState(
        targetValue = flashState.progress,
        label = "FlashProgress"
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = when {
                        flashState.error.isNotEmpty() -> stringResource(R.string.flash_failed)
                        flashState.isCompleted -> stringResource(R.string.flash_success)
                        else -> stringResource(R.string.flashing)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = progressColor
                )

                when {
                    flashState.error.isNotEmpty() -> {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    flashState.isCompleted -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (flashState.currentStep.isNotEmpty()) {
                Text(
                    text = flashState.currentStep,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))
            }

            LinearWavyProgressIndicator(
                progress = { progress.value },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = progressColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            if (flashState.error.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = flashState.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                            shape = MaterialTheme.shapes.small
                        )
                        .padding(8.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    flashState: FlashState,
    onBack: () -> Unit,
    onSave: () -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    val statusColor = when {
        flashState.error.isNotEmpty() -> MaterialTheme.colorScheme.error
        flashState.isCompleted -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    val colorScheme = MaterialTheme.colorScheme
    val cardColor = if (CardConfig.isCustomBackgroundEnabled) {
        colorScheme.surfaceContainerLow
    } else {
        colorScheme.background
    }
    val cardAlpha = CardConfig.cardAlpha

    TopAppBar(
        title = {
            Text(
                text = stringResource(
                    when {
                        flashState.error.isNotEmpty() -> R.string.flash_failed
                        flashState.isCompleted -> R.string.flash_success
                        else -> R.string.kernel_flashing
                    }
                ),
                style = MaterialTheme.typography.titleLarge,
                color = statusColor
            )
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
