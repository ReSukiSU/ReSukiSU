package com.resukisu.resukisu.ui.screen.main

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.twotone.Archive
import androidx.compose.material.icons.twotone.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.resukisu.resukisu.Natives
import com.resukisu.resukisu.R
import com.resukisu.resukisu.ksuApp
import com.resukisu.resukisu.ui.component.SearchAppBar
import com.resukisu.resukisu.ui.component.SwipeableSnackbarHost
import com.resukisu.resukisu.ui.component.settings.SettingsBaseWidget
import com.resukisu.resukisu.ui.component.settings.lazySegmentColumn
import com.resukisu.resukisu.ui.navigation.LocalNavigator
import com.resukisu.resukisu.ui.navigation.Route
import com.resukisu.resukisu.ui.screen.LabelText
import com.resukisu.resukisu.ui.theme.blurSource
import com.resukisu.resukisu.ui.util.LocalSnackbarHost
import com.resukisu.resukisu.ui.util.module.ModuleModify
import com.resukisu.resukisu.ui.viewmodel.AppCategory
import com.resukisu.resukisu.ui.viewmodel.SortType
import com.resukisu.resukisu.ui.viewmodel.SuperUserUiState
import com.resukisu.resukisu.ui.viewmodel.SuperUserViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

data class BottomSheetMenuItem(
    val icon: ImageVector,
    val titleRes: Int,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuperUserPage(bottomPadding: Dp) {
    val context = LocalContext.current
    val viewModel = viewModel<SuperUserViewModel>(
        viewModelStoreOwner = ksuApp
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)
    val listState = rememberLazyListState()
    val snackBarHostState = LocalSnackbarHost.current

    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showBottomSheet by remember { mutableStateOf(false) }

    val backupLauncher = ModuleModify.rememberAllowlistBackupLauncher(context, snackBarHostState)
    val restoreLauncher = ModuleModify.rememberAllowlistRestoreLauncher(context, snackBarHostState)

    val navigator = LocalNavigator.current

    LaunchedEffect(Unit) {
        viewModel.updateSearch("")
    }

    val appCounts = remember(uiState.appGroupList, uiState.showSystemApps) {
        mapOf(
            AppCategory.ALL to uiState.appGroupList.size,
            AppCategory.ROOT to uiState.appGroupList.count { it.allowSu },
            AppCategory.CUSTOM to uiState.appGroupList.count { !it.allowSu && it.hasCustomProfile },
            AppCategory.DEFAULT to uiState.appGroupList.count { !it.allowSu && !it.hasCustomProfile }
        )
    }

    Scaffold(
        topBar = {
            SearchAppBar(
                title = stringResource(R.string.superuser),
                searchText = uiState.search,
                onSearchTextChange = viewModel::updateSearch,
                dropdownContent = {
                    IconButton(onClick = { showBottomSheet = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(id = R.string.settings),
                        )
                    }
                },
                navigationContent = {
                    IconButton(onClick = {
                        navigator.push(Route.Sulog)
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Article,
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
            SwipeableSnackbarHost(hostState = snackBarHostState)
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        SuperUserContent(
            innerPadding = innerPadding,
            viewModel = viewModel,
            uiState = uiState,
            listState = listState,
            scrollBehavior = scrollBehavior,
            scope = scope,
            bottomPadding = bottomPadding,
        )

        if (showBottomSheet) {
            SuperUserBottomSheet(
                bottomSheetState = bottomSheetState,
                onDismiss = { showBottomSheet = false },
                viewModel = viewModel,
                uiState = uiState,
                appCounts = appCounts,
                backupLauncher = backupLauncher,
                restoreLauncher = restoreLauncher
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SuperUserContent(
    innerPadding: PaddingValues,
    viewModel: SuperUserViewModel,
    uiState: SuperUserUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    scrollBehavior: TopAppBarScrollBehavior,
    scope: CoroutineScope,
    bottomPadding: Dp,
) {
    val navigator = LocalNavigator.current
    val pullRefreshState = rememberPullToRefreshState()

    if (uiState.appGroupList.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if ((uiState.isRefreshing || uiState.appGroupList.isEmpty()) && uiState.search.isEmpty()) {
                    LoadingIndicator()
                }
                else {
                    val selectedCategory = uiState.selectedCategory
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
                            text = if (isSearchEmpty || selectedCategory == AppCategory.ALL) {
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
        }
        return
    }

    PullToRefreshBox(
        state = pullRefreshState,
        onRefresh = { scope.launch { viewModel.fetchAppList() } },
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
                    navigator.push(Route.AppProfile(appGroup))
                }
            }

            item {
                Spacer(modifier = Modifier.height(bottomPadding + innerPadding.calculateBottomPadding() + 15.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SuperUserBottomSheet(
    bottomSheetState: SheetState,
    onDismiss: () -> Unit,
    viewModel: SuperUserViewModel,
    uiState: SuperUserUiState,
    appCounts: Map<AppCategory, Int>,
    backupLauncher: androidx.activity.result.ActivityResultLauncher<android.content.Intent>,
    restoreLauncher: androidx.activity.result.ActivityResultLauncher<android.content.Intent>
) {
    val bottomSheetMenuItems = remember(uiState.showSystemApps) {
        listOf(
            BottomSheetMenuItem(
                icon = Icons.Filled.Refresh,
                titleRes = R.string.refresh,
                onClick = {
                    viewModel.viewModelScope.launch { viewModel.fetchAppList() }
                }
            ),
            BottomSheetMenuItem(
                icon = if (uiState.showSystemApps) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                titleRes = if (uiState.showSystemApps) R.string.hide_system_apps else R.string.show_system_apps,
                onClick = {
                    viewModel.updateShowSystemApps(!uiState.showSystemApps)
                }
            ),
            BottomSheetMenuItem(
                icon = Icons.Filled.Save,
                titleRes = R.string.backup_allowlist,
                onClick = {
                    backupLauncher.launch(ModuleModify.createAllowlistBackupIntent())
                }
            ),
            BottomSheetMenuItem(
                icon = Icons.Filled.RestoreFromTrash,
                titleRes = R.string.restore_allowlist,
                onClick = {
                    restoreLauncher.launch(ModuleModify.createAllowlistRestoreIntent())
                }
            )
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = bottomSheetState,
        dragHandle = {
            Surface(
                modifier = Modifier.padding(vertical = 11.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(Modifier.size(width = 32.dp, height = 4.dp))
            }
        }
    ) {
        BottomSheetContent(
            menuItems = bottomSheetMenuItems,
            currentSortType = uiState.currentSortType,
            onSortTypeChanged = { newSortType ->
                viewModel.updateCurrentSortType(newSortType)
            },
            selectedCategory = uiState.selectedCategory,
            onCategorySelected = { newCategory ->
                viewModel.updateSelectedCategory(newCategory)
            },
            appCounts = appCounts
        )
    }
}

@Composable
private fun BottomSheetContent(
    menuItems: List<BottomSheetMenuItem>,
    currentSortType: SortType,
    onSortTypeChanged: (SortType) -> Unit,
    selectedCategory: AppCategory,
    onCategorySelected: (AppCategory) -> Unit,
    appCounts: Map<AppCategory, Int>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp)
    ) {
        Text(
            text = stringResource(R.string.menu_options),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(menuItems) { menuItem ->
                BottomSheetMenuItemView(menuItem = menuItem)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp))

        Text(
            text = stringResource(R.string.sort_options),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(SortType.entries.toTypedArray()) { sortType ->
                FilterChip(
                    onClick = { onSortTypeChanged(sortType) },
                    label = { Text(stringResource(sortType.displayNameRes)) },
                    selected = currentSortType == sortType
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp))

        Text(
            text = stringResource(R.string.app_categories),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(AppCategory.entries.toTypedArray()) { category ->
                CategoryChip(
                    category = category,
                    isSelected = selectedCategory == category,
                    onClick = { onCategorySelected(category) },
                    appCount = appCounts[category] ?: 0
                )
            }
        }
    }
}

@Composable
private fun CategoryChip(
    category: AppCategory,
    isSelected: Boolean,
    onClick: () -> Unit,
    appCount: Int,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "categoryChipScale"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        tonalElevation = if (isSelected) 4.dp else 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(category.displayNameRes),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = "$appCount apps",
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
private fun BottomSheetMenuItemView(menuItem: BottomSheetMenuItem) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "menuItemScale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { menuItem.onClick() }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = menuItem.icon,
                    contentDescription = stringResource(menuItem.titleRes),
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(menuItem.titleRes),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun AppGroupItem(
    appGroup: SuperUserViewModel.AppGroup,
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
                    if (Natives.uidShouldUmount(appGroup.uid)) {
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
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        },
        leadingContent = {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(mainApp.packageInfo)
                    .crossfade(true)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .build(),
                contentDescription = mainApp.label,
                modifier = Modifier
                    .padding(4.dp)
                    .size(48.dp)
            )
        },
        iconPlaceholder = false,
    ) {
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
    }
}
