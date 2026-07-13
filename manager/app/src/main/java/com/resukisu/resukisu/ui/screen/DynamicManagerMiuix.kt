package com.resukisu.resukisu.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Security
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.resukisu.resukisu.R
import com.resukisu.resukisu.ui.component.dialog.ConfirmResult
import com.resukisu.resukisu.ui.component.SwipeableSnackbarHost
import com.resukisu.resukisu.ui.component.dialog.rememberConfirmDialog
import com.resukisu.resukisu.ui.navigation.LocalNavigator
import com.resukisu.resukisu.ui.theme.LocalEnableBlur
import com.resukisu.resukisu.ui.util.BlurredBar
import com.resukisu.resukisu.ui.util.LocalSnackbarHost
import com.resukisu.resukisu.ui.util.rememberBlurBackdrop
import com.resukisu.resukisu.ui.viewmodel.DynamicManagerAppItem
import com.resukisu.resukisu.ui.viewmodel.DynamicManagerViewModel
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * Miuix rendering of ReSukiSU's dynamic-manager screen. Reuses the same
 * DynamicManagerViewModel and the Material manual-config dialog; only the
 * chrome/status/list is Miuix.
 */
@Composable
fun DynamicManagerScreenMiuix() {
    val navigator = LocalNavigator.current
    val viewModel = viewModel<DynamicManagerViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val enableBlur = LocalEnableBlur.current
    val scrollBehavior = MiuixScrollBehavior()
    val snackbarHost = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()
    val confirmDialog = rememberConfirmDialog()

    val grantConfirmTitle = stringResource(R.string.dynamic_manager_grant_confirm_title)
    val grantConfirmMessage = stringResource(R.string.dynamic_manager_grant_confirm_message)
    val clearConfirmTitle = stringResource(R.string.dynamic_manager_clear_confirm_title)
    val clearConfirmMessage = stringResource(R.string.dynamic_manager_clear_confirm_message)
    val setSuccess = stringResource(R.string.dynamic_manager_set_success)
    val setFailed = stringResource(R.string.dynamic_manager_set_failed)
    val clearSuccess = stringResource(R.string.dynamic_manager_disabled_success)
    val clearFailed = stringResource(R.string.dynamic_manager_clear_failed)
    val confirmText = stringResource(R.string.confirm)

    suspend fun confirmPrivilegeGrant(): Boolean {
        val first = confirmDialog.awaitConfirm(
            title = grantConfirmTitle,
            content = grantConfirmMessage,
            confirm = confirmText
        )
        return first == ConfirmResult.Confirmed
    }

    fun runGrantOperation(operation: suspend () -> Boolean) {
        scope.launch {
            if (!confirmPrivilegeGrant()) return@launch
            val success = operation()
            if (success) viewModel.refresh()
            snackbarHost.showSnackbar(if (success) setSuccess else setFailed)
        }
    }

    fun runClearOperation() {
        scope.launch {
            val confirmed = confirmDialog.awaitConfirm(
                title = clearConfirmTitle,
                content = clearConfirmMessage,
                confirm = confirmText
            )
            if (confirmed != ConfirmResult.Confirmed) return@launch
            val success = viewModel.clearConfig()
            if (success) viewModel.refresh()
            snackbarHost.showSnackbar(if (success) clearSuccess else clearFailed)
        }
    }

    var showManualDialog by remember { mutableStateOf(false) }
    DynamicManualConfigDialogMiuix(
        show = showManualDialog,
        onDismiss = { showManualDialog = false },
        onConfirm = { size, hash ->
            showManualDialog = false
            runGrantOperation { viewModel.setManualConfig(size, hash) }
        }
    )

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface

    Scaffold(
        topBar = {
            TopBar(
                title = stringResource(R.string.dynamic_manager_title),
                onBack = { navigator.pop() },
                scrollBehavior = scrollBehavior,
                backdrop = backdrop,
                barColor = barColor,
            )
        },
        snackbarHost = { SwipeableSnackbarHost(hostState = snackbarHost) },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    InfiniteProgressIndicator()
                }
            } else {
                val pullToRefreshState = rememberPullToRefreshState()
                val refreshTexts = listOf(
                    stringResource(R.string.refresh_pulling),
                    stringResource(R.string.refresh_release),
                    stringResource(R.string.refresh_refresh),
                    stringResource(R.string.refresh_complete),
                )
                PullToRefresh(
                    isRefreshing = uiState.isRefreshing,
                    pullToRefreshState = pullToRefreshState,
                    onRefresh = { scope.launch { viewModel.refresh() } },
                    refreshTexts = refreshTexts,
                    contentPadding = PaddingValues(top = innerPadding.calculateTopPadding()),
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .scrollEndHaptic()
                            .overScrollVertical()
                            .nestedScroll(scrollBehavior.nestedScrollConnection),
                        contentPadding = PaddingValues(
                            top = innerPadding.calculateTopPadding() + 6.dp,
                            bottom = innerPadding.calculateBottomPadding() + 24.dp,
                        ),
                        overscrollEffect = null,
                    ) {
                        item {
                            DynamicManagerStatusCard(
                                config = uiState.config,
                                enabled = !uiState.isSubmitting,
                                onManualConfig = { showManualDialog = true },
                                onClearConfig = { runClearOperation() },
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        item {
                            SmallTitle(text = stringResource(R.string.manage_managers))
                            TextField(
                                value = uiState.search,
                                onValueChange = viewModel::updateSearch,
                                label = stringResource(R.string.search_apps),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp),
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        if (uiState.apps.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    InfiniteProgressIndicator()
                                }
                            }
                        } else {
                            items(
                                uiState.apps,
                                key = { "${it.uid}-${it.packageName}" },
                            ) { app ->
                                DynamicManagerAppCard(
                                    app = app,
                                    onClick = {
                                        if (app.isSelected) {
                                            runClearOperation()
                                        } else {
                                            runGrantOperation { viewModel.setManagerApp(app) }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DynamicManagerStatusCard(
    config: com.resukisu.resukisu.ui.util.DynamicManagerCliConfig?,
    enabled: Boolean,
    onManualConfig: () -> Unit,
    onClearConfig: () -> Unit,
) {
    val currentStatus = if (config?.isValid() == true) {
        stringResource(R.string.dynamic_manager_enabled_summary, config.size.toString())
    } else {
        stringResource(R.string.dynamic_manager_disabled)
    }

    SmallTitle(text = stringResource(R.string.dynamic_manager_title))
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
    ) {
        BasicComponent(
            title = stringResource(R.string.dynamic_manager_current_status),
            summary = currentStatus,
            startAction = { StatusIcon(Icons.Filled.Security) },
        )
        if (config?.isValid() == true) {
            BasicComponent(
                title = stringResource(R.string.signature_hash),
                summary = config.hash,
                startAction = { StatusIcon(Icons.Filled.Security) },
            )
        }
        BasicComponent(
            title = stringResource(R.string.dynamic_manager_manual_config),
            summary = stringResource(R.string.dynamic_manager_manual_config_summary),
            onClick = if (enabled) onManualConfig else null,
            startAction = { StatusIcon(Icons.Filled.Edit) },
        )
        BasicComponent(
            title = stringResource(R.string.dynamic_manager_clear_config),
            summary = stringResource(R.string.dynamic_manager_clear_config_summary),
            onClick = if (enabled) onClearConfig else null,
            startAction = { StatusIcon(Icons.Filled.Delete) },
        )
    }
}

@Composable
private fun StatusIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Icon(
        imageVector = icon,
        tint = colorScheme.onSurface,
        modifier = Modifier.padding(end = 12.dp),
        contentDescription = null,
    )
}

@Composable
private fun DynamicManagerAppCard(
    app: DynamicManagerAppItem,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val summary = if (!app.isChangeable) {
        stringResource(
            R.string.dynamic_manager_fixed_manager_summary,
            app.packageName,
            when (app.managerSignatureIndex ?: 0) {
                254 -> "Debug"
                253 -> "KernelSU Toolkit"
                else -> "Kernel"
            }
        )
    } else {
        app.packageName
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        BasicComponent(
            title = app.label,
            summary = summary,
            onClick = if (app.isChangeable) onClick else null,
            startAction = {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(app.packageInfo)
                        .crossfade(true)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .build(),
                    contentDescription = app.label,
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .size(40.dp)
                )
            },
            endActions = {
                if (app.isSelected || !app.isChangeable) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        tint = colorScheme.primary,
                        contentDescription = null,
                    )
                }
            }
        )
    }
}

@Composable
private fun TopBar(
    title: String,
    onBack: () -> Unit,
    scrollBehavior: ScrollBehavior,
    backdrop: LayerBackdrop?,
    barColor: Color,
) {
    BlurredBar(backdrop) {
        TopAppBar(
            color = barColor,
            title = title,
            navigationIcon = {
                top.yukonga.miuix.kmp.basic.IconButton(onClick = onBack) {
                    val layoutDirection = LocalLayoutDirection.current
                    Icon(
                        modifier = Modifier.graphicsLayer {
                            if (layoutDirection == LayoutDirection.Rtl) scaleX = -1f
                        },
                        imageVector = MiuixIcons.Back,
                        tint = colorScheme.onSurface,
                        contentDescription = null,
                    )
                }
            },
            scrollBehavior = scrollBehavior
        )
    }
}

@Composable
private fun DynamicManualConfigDialogMiuix(
    show: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Int, String) -> Unit,
) {
    var sizeText by remember(show) { mutableStateOf("") }
    var hashText by remember(show) { mutableStateOf("") }
    val sizeValue = sizeText.toIntOrNull()
    val hashValid = hashText.length == 64
    val isValid = sizeValue != null && sizeValue > 0 && hashValid

    OverlayDialog(
        show = show,
        title = stringResource(R.string.dynamic_manager_manual_config),
        onDismissRequest = onDismiss,
        content = {
            TextField(
                value = sizeText,
                onValueChange = { v -> sizeText = v.filter(Char::isDigit) },
                label = stringResource(R.string.signature_size),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
            TextField(
                value = hashText,
                onValueChange = { v -> hashText = v.trim() },
                label = stringResource(R.string.signature_hash),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            )
            if (hashText.isNotEmpty() && !hashValid) {
                Text(
                    text = stringResource(R.string.hash_must_be_64_chars),
                    color = colorScheme.onErrorContainer,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    text = stringResource(android.R.string.cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.size(12.dp))
                TextButton(
                    text = stringResource(android.R.string.ok),
                    onClick = { if (isValid) onConfirm(sizeValue!!, hashText) },
                    enabled = isValid,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    )
}
