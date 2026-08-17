package com.resukisu.resukisu.ui.screen.main

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.Article
import androidx.compose.material.icons.twotone.Archive
import androidx.compose.material.icons.twotone.ChevronRight
import androidx.compose.material.icons.twotone.MoreVert
import androidx.compose.material.icons.twotone.SearchOff
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.resukisu.resukisu.R
import com.resukisu.resukisu.domain.model.AllowlistOperationResult
import com.resukisu.resukisu.domain.model.InstalledAppGroup
import com.resukisu.resukisu.ui.component.ConfirmResult
import com.resukisu.resukisu.ui.component.PackageIcon
import com.resukisu.resukisu.ui.component.SearchAppBar
import com.resukisu.resukisu.ui.component.SwipeableSnackbarHost
import com.resukisu.resukisu.ui.component.rememberConfirmDialog
import com.resukisu.resukisu.ui.component.rememberSearchAppBarScrollBehavior
import com.resukisu.resukisu.ui.component.settings.SettingsBaseWidget
import com.resukisu.resukisu.ui.component.settings.lazySegmentColumn
import com.resukisu.resukisu.ui.navigation.LocalNavigator
import com.resukisu.resukisu.ui.navigation.Route
import com.resukisu.resukisu.ui.screen.LabelText
import com.resukisu.resukisu.ui.theme.blurSource
import com.resukisu.resukisu.ui.util.LocalSnackbarHost
import com.resukisu.resukisu.ui.util.showReplacingSnackbar
import com.resukisu.resukisu.ui.viewmodel.SortType
import com.resukisu.resukisu.ui.viewmodel.SuperUserUiAction
import com.resukisu.resukisu.ui.viewmodel.SuperUserUiEvent
import com.resukisu.resukisu.ui.viewmodel.SuperUserUiState
import com.resukisu.resukisu.ui.viewmodel.SuperUserViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class SuperUserMenuItem(
    val checked: Boolean = false,
    val titleRes: Int,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SuperUserPage(bottomPadding: Dp) {
    val context = LocalContext.current
    val viewModel = koinViewModel<SuperUserViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = rememberSearchAppBarScrollBehavior(
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)
    )
    val listState = rememberLazyListState()
    val snackBarHostState = LocalSnackbarHost.current

    var showDropdown by remember { mutableStateOf(false) }
    val restoreConfirmDialog = rememberConfirmDialog()
    val restoreConfirmTitle = stringResource(R.string.allowlist_restore_confirm_title)
    val restoreConfirmMessage = stringResource(R.string.allowlist_restore_confirm_message)
    val confirmText = stringResource(R.string.confirm)
    val cancelText = stringResource(R.string.cancel)

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is SuperUserUiEvent.Error -> if (event.message.isNotBlank()) {
                    snackBarHostState.showReplacingSnackbar(event.message)
                }

                is SuperUserUiEvent.AllowlistOperationFinished -> {
                    val successMessage = if (event.restore) {
                        R.string.allowlist_restore_success
                    } else {
                        R.string.allowlist_backup_success
                    }
                    val failureMessage = if (event.restore) {
                        R.string.allowlist_restore_failed
                    } else {
                        R.string.allowlist_backup_failed
                    }
                    snackBarHostState.showReplacingSnackbar(
                        message = context.allowlistOperationMessage(
                            result = event.result,
                            successMessage = successMessage,
                            failureMessage = failureMessage,
                        ),
                        duration = SnackbarDuration.Long,
                    )
                }
            }
        }
    }

    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            viewModel.dispatch(SuperUserUiAction.BackupAllowlist(uri.toString()))
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val confirmed = restoreConfirmDialog.awaitConfirm(
                    title = restoreConfirmTitle,
                    content = restoreConfirmMessage,
                    confirm = confirmText,
                    dismiss = cancelText,
                )
                if (confirmed != ConfirmResult.Confirmed) return@launch

                viewModel.dispatch(SuperUserUiAction.RestoreAllowlist(uri.toString()))
            }
        }
    }

    val navigator = LocalNavigator.current

    LaunchedEffect(Unit) {
        viewModel.dispatch(SuperUserUiAction.Search(""))
    }

    Scaffold(
        modifier = Modifier
            .testTag(SUPER_USER_SCREEN_TEST_TAG)
            .semantics { testTagsAsResourceId = true },
        topBar = {
            SearchAppBar(
                title = stringResource(R.string.superuser),
                searchText = uiState.search,
                onSearchTextChange = { viewModel.dispatch(SuperUserUiAction.Search(it)) },
                dropdownContent = {
                    IconButton(onClick = { showDropdown = true }) {
                        Icon(
                            imageVector = Icons.TwoTone.MoreVert,
                            contentDescription = stringResource(id = R.string.settings),
                        )

                        SuperUserDropdown(
                            expanded = showDropdown,
                            onDismissRequest = { showDropdown = false },
                            viewModel = viewModel,
                            uiState = uiState,
                            onBackupAllowlist = {
                                backupLauncher.launch(createAllowlistBackupFileName())
                            },
                            onRestoreAllowlist = {
                                restoreLauncher.launch(arrayOf("application/octet-stream"))
                            },
                        )
                    }
                },
                navigationContent = {
                    IconButton(onClick = {
                        navigator.push(Route.Sulog)
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.TwoTone.Article,
                            contentDescription = stringResource(R.string.sulog)
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                searchBarPlaceHolderText = stringResource(R.string.search_apps),
            )
        },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        snackbarHost = {
            SwipeableSnackbarHost(
                modifier = Modifier.padding(bottom = bottomPadding),
                hostState = snackBarHostState
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        SuperUserContent(
            innerPadding = innerPadding,
            viewModel = viewModel,
            uiState = uiState,
            listState = listState,
            scrollBehavior = scrollBehavior,
            bottomPadding = bottomPadding,
        )
    }
}

private fun Context.allowlistOperationMessage(
    result: AllowlistOperationResult,
    successMessage: Int,
    failureMessage: Int,
): String {
    return when (result) {
        AllowlistOperationResult.Success ->
            getString(successMessage)

        AllowlistOperationResult.InvalidFile ->
            getString(failureMessage, getString(R.string.unknown_file))

        AllowlistOperationResult.UnsupportedVersion ->
            getString(failureMessage, getString(R.string.home_unsupported))

        is AllowlistOperationResult.ProfileUpdateFailed ->
            getString(
                failureMessage,
                getString(R.string.failed_to_update_app_profile, result.uid.toString()),
            )

        is AllowlistOperationResult.Failed ->
            getString(
                failureMessage,
                result.cause?.localizedMessage ?: getString(R.string.unknown),
            )
    }
}

private fun createAllowlistBackupFileName(): String {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    return "ksu_allowlist_backup_$timestamp.dat"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SuperUserContent(
    innerPadding: PaddingValues,
    viewModel: SuperUserViewModel,
    uiState: SuperUserUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    scrollBehavior: TopAppBarScrollBehavior,
    bottomPadding: Dp,
) {
    val navigator = LocalNavigator.current
    val pullRefreshState = rememberPullToRefreshState()

    if (uiState.appGroupList.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blurSource(),
            contentAlignment = Alignment.Center
        ) {
            if (uiState.isRefreshing && uiState.search.isEmpty()) {
                LoadingIndicator()
            } else {
                val isSearchEmpty = uiState.search.isNotEmpty()
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = if (isSearchEmpty) Icons.TwoTone.SearchOff else Icons.TwoTone.Archive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(96.dp)
                            .padding(bottom = 16.dp)
                    )
                    Text(
                        text = if (isSearchEmpty) {
                            stringResource(R.string.no_apps_found)
                        } else {
                            stringResource(R.string.no_apps_in_category)
                        },
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
        return
    }

    PullToRefreshBox(
        state = pullRefreshState,
        onRefresh = { viewModel.dispatch(SuperUserUiAction.Refresh) },
        isRefreshing = uiState.isRefreshing,
        modifier = Modifier
            .fillMaxSize()
            .blurSource(),
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                modifier = Modifier
                    .padding(top = innerPadding.calculateTopPadding())
                    .align(Alignment.TopCenter),
                state = pullRefreshState,
                isRefreshing = uiState.isRefreshing,
            )
        },
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .testTag(SUPER_USER_LIST_TEST_TAG)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        ) {
            item {
                Spacer(modifier = Modifier.height(innerPadding.calculateTopPadding()))
            }
            lazySegmentColumn(
                items = uiState.appGroupList,
                key = { _, appGroup -> "${appGroup.uid}-${appGroup.mainApp.packageName}" },
                contentType = { _, _ -> "AppGroupItem" }
            ) { _, appGroup ->
                AppGroupItem(
                    appGroup = appGroup
                ) {
                    navigator.push(Route.AppProfile(appGroup.uid, appGroup.mainApp.packageName))
                }
            }

            item {
                Spacer(modifier = Modifier.height(bottomPadding + innerPadding.calculateBottomPadding() + 15.dp))
            }
        }
    }
}

private const val SUPER_USER_LIST_TEST_TAG = "super_user_app_list"
private const val SUPER_USER_SCREEN_TEST_TAG = "super_user_screen"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SuperUserDropdown(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    viewModel: SuperUserViewModel,
    uiState: SuperUserUiState,
    onBackupAllowlist: () -> Unit,
    onRestoreAllowlist: () -> Unit,
) {
    val menuItems = remember(
        uiState.showSystemApps,
        onBackupAllowlist,
        onRestoreAllowlist,
    ) {
        listOf(
            SuperUserMenuItem(
                checked = uiState.showSystemApps,
                titleRes = R.string.show_system_apps,
                onClick = {
                    viewModel.dispatch(SuperUserUiAction.SetShowSystemApps(!uiState.showSystemApps))
                }
            ),
            SuperUserMenuItem(
                titleRes = R.string.backup_allowlist,
                onClick = onBackupAllowlist,
            ),
            SuperUserMenuItem(
                titleRes = R.string.restore_allowlist,
                onClick = onRestoreAllowlist,
            )
        )
    }

    DropdownMenuPopup(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
    ) {
        DropdownMenuGroup(
            shapes = MenuDefaults.groupShapes(),
        ) {
            SortType.entries.forEachIndexed { index, sortType ->
                DropdownMenuItem(
                    selected = uiState.currentSortType == sortType,
                    text = { Text(stringResource(sortType.displayNameRes)) },
                    onClick = {
                        viewModel.dispatch(SuperUserUiAction.SetSort(sortType))
                    },
                    shapes = MenuDefaults.itemShape(
                        index = index,
                        count = SortType.entries.size,
                    ),
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        DropdownMenuGroup(
            shapes = MenuDefaults.groupShapes(),
        ) {
            menuItems.forEachIndexed { index, menuItem ->
                DropdownMenuItem(
                    selected = menuItem.checked,
                    text = { Text(stringResource(menuItem.titleRes)) },
                    onClick = {
                        onDismissRequest()
                        menuItem.onClick()
                    },
                    shapes = MenuDefaults.itemShape(
                        index = index,
                        count = menuItems.size,
                    ),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun AppGroupItem(
    appGroup: InstalledAppGroup,
    onClick: () -> Unit,
) {
    val mainApp = appGroup.mainApp
    SettingsBaseWidget(
        onClick = {
            onClick()
        },
        title = mainApp.label,
        description = if (appGroup.apps.size > 1) {
            stringResource(R.string.group_contains_apps, appGroup.apps.size)
        } else {
            mainApp.packageName
        },
        descriptionColumnContent = {
            Spacer(modifier = Modifier.height(5.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (appGroup.allowSu) {
                    LabelText(label = "ROOT")
                } else {
                    if (appGroup.shouldUmount) {
                        LabelText(
                            label = "UMOUNT",
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        )
                    }
                }
                if (appGroup.hasCustomProfile) {
                    LabelText(
                        label = "CUSTOM",
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    )
                } else if (!appGroup.allowSu) {
                    LabelText(
                        label = "DEFAULT",
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                }
                if (appGroup.apps.size > 1) {
                    appGroup.userName?.let {
                        LabelText(
                            label = it,
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        )
                    }
                }
                if (appGroup.isRecentlyInstalled) {
                    LabelText(
                        label = stringResource(R.string.recently_installed),
                        containerColor = MaterialTheme.colorScheme.surfaceBright
                    )
                }
            }
        },
        leadingContent = {
            PackageIcon(
                packageName = mainApp.packageName,
                contentDescription = mainApp.label,
                modifier = Modifier
                    .padding(4.dp)
                    .size(48.dp),
            )
        },
        iconPlaceholder = false,
    ) {
        Icon(
            imageVector = Icons.TwoTone.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
    }
}
