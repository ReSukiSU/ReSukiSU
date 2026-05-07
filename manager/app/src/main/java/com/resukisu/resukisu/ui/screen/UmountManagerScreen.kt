package com.resukisu.resukisu.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.resukisu.resukisu.R
import com.resukisu.resukisu.ui.component.ConfirmResult
import com.resukisu.resukisu.ui.component.SwipeableSnackbarHost
import com.resukisu.resukisu.ui.component.WarningCard
import com.resukisu.resukisu.ui.component.rememberConfirmDialog
import com.resukisu.resukisu.ui.component.settings.AppBackButton
import com.resukisu.resukisu.ui.component.settings.SettingsBaseWidget
import com.resukisu.resukisu.ui.component.settings.splicedLazyColumnGroup
import com.resukisu.resukisu.ui.navigation.LocalNavigator
import com.resukisu.resukisu.ui.theme.CardConfig
import com.resukisu.resukisu.ui.theme.ThemeConfig
import com.resukisu.resukisu.ui.theme.blurEffect
import com.resukisu.resukisu.ui.theme.blurSource
import com.resukisu.resukisu.ui.util.LocalSnackbarHost
import com.resukisu.resukisu.ui.viewmodel.UmountManagerScreenViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UmountManagerScreen() {
    val viewModel = viewModel<UmountManagerScreenViewModel>()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val snackBarHost = LocalSnackbarHost.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val confirmDialog = rememberConfirmDialog()

    var isLoading by remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()
    var showAddDialog by remember { mutableStateOf(false) }

    val confirmDelete = stringResource(R.string.confirm_delete)

    LaunchedEffect(Unit) {
        scrollBehavior.state.heightOffset =
            scrollBehavior.state.heightOffsetLimit
        viewModel.refreshData(context)
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                modifier = Modifier
                    .blurEffect(),
                title = { Text(stringResource(R.string.umount_path_manager)) },
                navigationIcon = {
                    val navigator = LocalNavigator.current
                    AppBackButton(
                        onClick = {
                            navigator.pop()
                        }
                    )
                },
                windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp)),
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor =
                        if (ThemeConfig.isEnableBlur)
                            Color.Transparent
                        else
                            MaterialTheme.colorScheme.surfaceContainer.copy(CardConfig.cardAlpha),
                    scrolledContainerColor =
                        if (ThemeConfig.isEnableBlur)
                            Color.Transparent
                        else
                            MaterialTheme.colorScheme.surfaceContainer.copy(CardConfig.cardAlpha),
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true }
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
            }
        },
        snackbarHost = { SwipeableSnackbarHost(hostState = snackBarHost) },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) { paddingValues ->
        if (isLoading) { // 初次加载时动画
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentAlignment = Alignment.Center,
            ) {
                LoadingIndicator()
            }
        }
        else {
            PullToRefreshBox(
                state = pullToRefreshState,
                isRefreshing = viewModel.isRefreshing,
                onRefresh = {
                    viewModel.markUmountPathDirty()
                    viewModel.refreshData(context)
                },
                indicator = {
                    PullToRefreshDefaults.LoadingIndicator(
                        state = pullToRefreshState,
                        isRefreshing = viewModel.isRefreshing,
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
                        bottom = paddingValues.calculateBottomPadding() + 72.dp + 5.dp + 5.dp // FAB
                    )
                ) {
                    item {
                        WarningCard(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            message = stringResource(R.string.changes_take_effect_immediately),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    if (viewModel.umountPaths.isEmpty()) {
                        item {
                            WarningCard(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                message = stringResource(R.string.no_any_umount_path),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    splicedLazyColumnGroup(
                        viewModel.umountPaths,
                        key = { _, it -> it.path }) { _, entry ->
                        SettingsBaseWidget(
                            icon = Icons.Filled.Folder,
                            title = entry.path,
                            descriptionColumnContent = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 5.dp)
                                ) {
                                    LabelText(
                                        label = if (entry.persistent) {
                                            stringResource(R.string.persistent)
                                        } else {
                                            stringResource(R.string.temporary)
                                        }
                                    )
                                    LabelText(
                                        label = entry.flagName,
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    )
                                }
                            }
                        ) {
                            val confirmDeleteSummary = stringResource(
                                R.string.confirm_delete_umount_path,
                                entry.path
                            )
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        val confirmResult = confirmDialog.awaitConfirm(
                                            title = confirmDelete,
                                            content = confirmDeleteSummary
                                        )
                                        if (confirmResult != ConfirmResult.Confirmed)
                                            return@launch
                                        withContext(Dispatchers.IO) {
                                            viewModel.removePath(entry, snackBarHost, context)
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showAddDialog) {
            AddUmountPathDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { path, flags ->
                    showAddDialog = false

                    viewModel.umountPaths.filter { it.path == path }.forEach {
                        viewModel.removePath(
                            entry = it,
                            snackBarHost = null,
                            context = null,
                        )
                    }

                    viewModel.addPath(
                        path = path,
                        flags = flags,
                        snackBarHost = snackBarHost,
                        context = context
                    )
                }
            )
        }
    }
}

@Composable
fun AddUmountPathDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Int) -> Unit
) {
    var path by remember { mutableStateOf("") }
    var flags by remember { mutableStateOf("0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_umount_path)) },
        text = {
            Column {
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    label = { Text(stringResource(R.string.mount_path)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = flags,
                    onValueChange = { flags = it },
                    label = { Text(stringResource(R.string.umount_flags)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = { Text(stringResource(R.string.umount_flags_hint)) }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val flagsInt = flags.toIntOrNull() ?: 0
                    onConfirm(path, flagsInt)
                },
                enabled = path.isNotBlank()
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
