package com.resukisu.resukisu.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Edit
import androidx.compose.material.icons.twotone.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberTopAppBarState
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.resukisu.resukisu.R
import com.resukisu.resukisu.domain.model.DynamicManagerConfig
import com.resukisu.resukisu.ui.component.ConfirmResult
import com.resukisu.resukisu.ui.component.DialogHandle
import com.resukisu.resukisu.ui.component.PackageIcon
import com.resukisu.resukisu.ui.component.SearchAppBar
import com.resukisu.resukisu.ui.component.SwipeableSnackbarHost
import com.resukisu.resukisu.ui.component.rememberConfirmDialog
import com.resukisu.resukisu.ui.component.rememberCustomDialog
import com.resukisu.resukisu.ui.component.rememberSearchAppBarScrollBehavior
import com.resukisu.resukisu.ui.component.settings.SegmentedColumn
import com.resukisu.resukisu.ui.component.settings.SettingsBaseWidget
import com.resukisu.resukisu.ui.component.settings.SettingsTextFieldWidget
import com.resukisu.resukisu.ui.component.settings.lazySegmentColumn
import com.resukisu.resukisu.ui.navigation.LocalNavigator
import com.resukisu.resukisu.ui.theme.blurSource
import com.resukisu.resukisu.ui.util.ActivityResumeEffect
import com.resukisu.resukisu.ui.util.LocalSnackbarHost
import com.resukisu.resukisu.ui.util.showReplacingSnackbar
import com.resukisu.resukisu.ui.viewmodel.DynamicManagerAppItem
import com.resukisu.resukisu.ui.viewmodel.DynamicManagerOperation
import com.resukisu.resukisu.ui.viewmodel.DynamicManagerUiAction
import com.resukisu.resukisu.ui.viewmodel.DynamicManagerUiEvent
import com.resukisu.resukisu.ui.viewmodel.DynamicManagerViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DynamicManagerScreen() {
    val navigator = LocalNavigator.current
    val viewModel = koinViewModel<DynamicManagerViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = rememberSearchAppBarScrollBehavior(
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    )
    val pullToRefreshState = rememberPullToRefreshState()
    val snackbarHost = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()
    var isUserRefreshing by remember { mutableStateOf(false) }
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
    val manageManagers = stringResource(R.string.manage_managers)

    suspend fun confirmPrivilegeGrant(): Boolean {
        val first = confirmDialog.awaitConfirm(
            title = grantConfirmTitle,
            content = grantConfirmMessage,
            confirm = confirmText
        )
        return first == ConfirmResult.Confirmed
    }

    fun runGrantOperation(action: DynamicManagerUiAction) {
        scope.launch {
            if (!confirmPrivilegeGrant()) return@launch
            viewModel.dispatch(action)
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
            viewModel.dispatch(DynamicManagerUiAction.Clear)
        }
    }

    val manualDialog = rememberDynamicManagerManualDialog { size, hash ->
        runGrantOperation(DynamicManagerUiAction.SetManual(size, hash))
    }

    LaunchedEffect(Unit) {
        scrollBehavior.state.heightOffset = scrollBehavior.state.heightOffsetLimit
        viewModel.events.collectLatest { event ->
            when (event) {
                is DynamicManagerUiEvent.OperationCompleted -> {
                    val message = when (event.operation) {
                        DynamicManagerOperation.Set -> if (event.success) setSuccess else setFailed
                        DynamicManagerOperation.Clear -> if (event.success) clearSuccess else clearFailed
                    }
                    snackbarHost.showReplacingSnackbar(message)
                }
            }
        }
    }

    ActivityResumeEffect {
        viewModel.dispatch(DynamicManagerUiAction.Refresh)
    }

    Scaffold(
        topBar = {
            SearchAppBar(
                title = stringResource(R.string.dynamic_manager_title),
                searchText = uiState.search,
                onSearchTextChange = { viewModel.dispatch(DynamicManagerUiAction.Search(it)) },
                onBackClick = { navigator.pop() },
                scrollBehavior = scrollBehavior,
                searchBarPlaceHolderText = stringResource(R.string.search_apps),
            )
        },
        snackbarHost = { SwipeableSnackbarHost(hostState = snackbarHost) },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentAlignment = Alignment.Center,
            ) {
                LoadingIndicator()
            }
        } else {
            PullToRefreshBox(
                state = pullToRefreshState,
                isRefreshing = isUserRefreshing,
                onRefresh = {
                    scope.launch {
                        isUserRefreshing = true
                        try {
                            viewModel.refresh()
                        } finally {
                            isUserRefreshing = false
                        }
                    }
                },
                indicator = {
                    PullToRefreshDefaults.LoadingIndicator(
                        state = pullToRefreshState,
                        isRefreshing = isUserRefreshing,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = paddingValues.calculateTopPadding()),
                    )
                },
                modifier = Modifier
                    .fillMaxSize()
                    .blurSource()
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(
                        top = paddingValues.calculateTopPadding() + 5.dp,
                        start = 0.dp,
                        end = 0.dp,
                        bottom = paddingValues.calculateBottomPadding() + 16.dp
                    )
                ) {
                    item {
                        DynamicManagerStatusSection(
                            config = uiState.config,
                            enabled = !uiState.isSubmitting,
                            onManualConfig = {
                                manualDialog.show()
                            },
                            onClearConfig = {
                                runClearOperation()
                            }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    if (uiState.apps.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                LoadingIndicator()
                            }
                        }
                    } else {
                        lazySegmentColumn(
                            title = manageManagers,
                            items = uiState.apps,
                            key = { _, app -> "${app.uid}-${app.packageName}" },
                            contentType = { _, _ -> "DynamicManagerAppItem" }
                        ) { _, app ->
                            DynamicManagerAppItem(
                                app = app,
                                onClick = {
                                    if (app.isSelected) {
                                        runClearOperation()
                                    } else {
                                        runGrantOperation(DynamicManagerUiAction.SelectApp(app))
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

@Composable
private fun rememberDynamicManagerManualDialog(
    onConfirm: (Int, String) -> Unit,
): DialogHandle {
    return rememberCustomDialog { dismiss ->
        DynamicManagerManualDialog(
            onDismiss = dismiss,
            onConfirm = { size, hash ->
                dismiss()
                onConfirm(size, hash)
            }
        )
    }
}

@Composable
private fun DynamicManagerStatusSection(
    config: DynamicManagerConfig?,
    enabled: Boolean,
    onManualConfig: () -> Unit,
    onClearConfig: () -> Unit,
) {
    val currentStatus = if (config?.isValid == true) {
        stringResource(R.string.dynamic_manager_enabled_summary, config.size.toString())
    } else {
        stringResource(R.string.dynamic_manager_disabled)
    }

    SegmentedColumn(
        title = stringResource(R.string.dynamic_manager_title),
    ) {
        item {
            SettingsBaseWidget(
                icon = Icons.TwoTone.Security,
                title = stringResource(R.string.dynamic_manager_current_status),
                description = currentStatus,
                onClick = {}
            )
        }

        item(visible = config?.isValid == true) {
            SettingsBaseWidget(
                icon = Icons.TwoTone.Security,
                title = stringResource(R.string.signature_hash),
                description = config?.hash.orEmpty(),
                onClick = {}
            )
        }

        item {
            SettingsBaseWidget(
                icon = Icons.TwoTone.Edit,
                title = stringResource(R.string.dynamic_manager_manual_config),
                description = stringResource(R.string.dynamic_manager_manual_config_summary),
                enabled = enabled,
                onClick = { onManualConfig() },
            )
        }

        item {
            SettingsBaseWidget(
                icon = Icons.TwoTone.Delete,
                title = stringResource(R.string.dynamic_manager_clear_config),
                description = stringResource(R.string.dynamic_manager_clear_config_summary),
                enabled = enabled,
                onClick = { onClearConfig() },
            )
        }
    }
}

@Composable
private fun DynamicManagerAppItem(
    app: DynamicManagerAppItem,
    onClick: () -> Unit,
) {
    SettingsBaseWidget(
        enabled = app.isChangeable,
        onClick = {
            onClick()
        },
        title = app.label,
        description = if (!app.isChangeable) {
            stringResource(
                R.string.dynamic_manager_fixed_manager_summary,
                app.packageName,
                if ((app.managerSignatureIndex ?: 0) == 254)
                    "Debug"
                else if ((app.managerSignatureIndex ?: 0) == 253)
                    "KernelSU Toolkit"
                else
                    "Kernel"
            )
        } else {
            app.packageName
        },
        leadingContent = {
            PackageIcon(
                packageName = app.packageName,
                contentDescription = app.label,
                modifier = Modifier
                    .padding(4.dp)
                    .size(48.dp),
            )
        },
        iconPlaceholder = false,
    ) {
        Checkbox(
            checked = app.isSelected || !app.isChangeable,
            enabled = app.isChangeable,
            onCheckedChange = null
        )
    }
}

@Composable
private fun DynamicManagerManualDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int, String) -> Unit,
) {
    val sizeState = rememberTextFieldState()
    val hashState = rememberTextFieldState()
    val size = sizeState.text.toString()
    val hash = hashState.text.toString()
    val sizeValue = size.toIntOrNull()
    val hashValid = hash.length == 64
    val isValid = sizeValue != null && sizeValue > 0 && hashValid

    LaunchedEffect(sizeState.text) {
        val filtered = sizeState.text.toString().filter(Char::isDigit)
        if (filtered != sizeState.text.toString()) {
            sizeState.edit {
                replace(0, length, filtered)
            }
        }
    }

    LaunchedEffect(hashState.text) {
        val trimmed = hashState.text.toString().trim()
        if (trimmed != hashState.text.toString()) {
            hashState.edit {
                replace(0, length, trimmed)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dynamic_manager_manual_config)) },
        text = {
            SegmentedColumn {
                item {
                    SettingsTextFieldWidget(
                        modifier = Modifier.fillMaxWidth(),
                        renderBackgroundBlur = false,
                        state = sizeState,
                        title = stringResource(R.string.signature_size),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        lineLimits = TextFieldLineLimits.SingleLine,
                    )
                }

                item {
                    SettingsTextFieldWidget(
                        modifier = Modifier.fillMaxWidth(),
                        renderBackgroundBlur = false,
                        state = hashState,
                        title = stringResource(R.string.signature_hash),
                        error = if (hash.isNotEmpty() && !hashValid) {
                            stringResource(R.string.hash_must_be_64_chars)
                        } else {
                            ""
                        },
                        lineLimits = TextFieldLineLimits.SingleLine,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = {
                    onConfirm(sizeValue ?: return@TextButton, hash)
                }
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}
