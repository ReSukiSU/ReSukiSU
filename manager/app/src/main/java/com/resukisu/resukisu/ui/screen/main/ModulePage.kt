package com.resukisu.resukisu.ui.screen.main

import android.annotation.SuppressLint
import android.app.Activity.CLIPBOARD_SERVICE
import android.app.Activity.RESULT_OK
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.Undo
import androidx.compose.material.icons.automirrored.twotone.Wysiwyg
import androidx.compose.material.icons.twotone.Check
import androidx.compose.material.icons.twotone.ChevronRight
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material.icons.twotone.Cloud
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Download
import androidx.compose.material.icons.twotone.Extension
import androidx.compose.material.icons.twotone.MoreVert
import androidx.compose.material.icons.twotone.Photo
import androidx.compose.material.icons.twotone.PlayArrow
import androidx.compose.material.icons.twotone.Refresh
import androidx.compose.material.icons.twotone.Restore
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.FixedScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.capsule.ContinuousRoundedRectangle
import com.resukisu.resukisu.R
import com.resukisu.resukisu.domain.model.InstalledModule
import com.resukisu.resukisu.domain.model.MetaModuleStatus
import com.resukisu.resukisu.domain.usecase.EnqueueDownloadUseCase
import com.resukisu.resukisu.domain.usecase.ExtractModuleNameUseCase
import com.resukisu.resukisu.domain.usecase.FetchRemoteTextUseCase
import com.resukisu.resukisu.domain.usecase.IsModuleUriAccessibleUseCase
import com.resukisu.resukisu.domain.usecase.ObserveDownloadUseCase
import com.resukisu.resukisu.domain.usecase.TakeModuleUriPermissionUseCase
import com.resukisu.resukisu.ui.component.ConfirmResult
import com.resukisu.resukisu.ui.component.InstallConfirmationDialog
import com.resukisu.resukisu.ui.component.SearchAppBar
import com.resukisu.resukisu.ui.component.SwipeableSnackbarHost
import com.resukisu.resukisu.ui.component.WarningCard
import com.resukisu.resukisu.ui.component.ZipFileDetector
import com.resukisu.resukisu.ui.component.ZipFileInfo
import com.resukisu.resukisu.ui.component.ZipType
import com.resukisu.resukisu.ui.component.rememberConfirmDialog
import com.resukisu.resukisu.ui.component.rememberLoadingDialog
import com.resukisu.resukisu.ui.component.rememberSearchAppBarScrollBehavior
import com.resukisu.resukisu.ui.component.settings.SegmentedColumn
import com.resukisu.resukisu.ui.component.settings.SettingsBaseWidget
import com.resukisu.resukisu.ui.component.settings.SettingsJumpPageWidget
import com.resukisu.resukisu.ui.component.settings.SettingsTextFieldWidget
import com.resukisu.resukisu.ui.navigation.LocalNavigator
import com.resukisu.resukisu.ui.navigation.Route
import com.resukisu.resukisu.ui.screen.LabelText
import com.resukisu.resukisu.ui.theme.CardConfig
import com.resukisu.resukisu.ui.theme.ThemeConfig
import com.resukisu.resukisu.ui.theme.blurSource
import com.resukisu.resukisu.ui.theme.renderBackgroundBlur
import com.resukisu.resukisu.ui.util.LocalPermissionRequestInterface
import com.resukisu.resukisu.ui.util.LocalSnackbarHost
import com.resukisu.resukisu.ui.util.downloader.download
import com.resukisu.resukisu.ui.util.module.Shortcut
import com.resukisu.resukisu.ui.util.showReplacingSnackbar
import com.resukisu.resukisu.ui.viewmodel.HomeViewModel
import com.resukisu.resukisu.ui.viewmodel.ModuleUiAction
import com.resukisu.resukisu.ui.viewmodel.ModuleUiEvent
import com.resukisu.resukisu.ui.viewmodel.ModuleUiState
import com.resukisu.resukisu.ui.viewmodel.ModuleViewModel
import com.resukisu.resukisu.ui.webui.WebUIActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel


private enum class ShortcutType {
    Action,
    WebUI
}

/**
 * @author ShirkNeko
 * @date 2025/9/29.
 */
@SuppressLint("ResourceType", "AutoboxingStateCreation")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ModulePage(bottomPadding: Dp) {
    val isModuleUriAccessible = koinInject<IsModuleUriAccessibleUseCase>()
    val takeModuleUriPermission = koinInject<TakeModuleUriPermissionUseCase>()
    val extractModuleName = koinInject<ExtractModuleNameUseCase>()
    val zipFileDetector = koinInject<ZipFileDetector>()
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val viewModel = koinViewModel<ModuleViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val homeState by koinViewModel<HomeViewModel>().state.collectAsStateWithLifecycle()
    val snackBarHost = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()
    var lastClickTime by remember { mutableStateOf(0L) }

    var showDropdown by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    var showConfirmationDialog by remember { mutableStateOf(false) }
    var pendingZipFiles by remember { mutableStateOf<List<ZipFileInfo>>(emptyList()) }
    InstallConfirmationDialog(
        show = showConfirmationDialog,
        zipFiles = pendingZipFiles,
        onConfirm = { info ->
            showConfirmationDialog = false
            navigator.push(
                Route.Flash.modules(info.filter { it.type == ZipType.MODULE }
                    .map { it.uri.toString() })
            )
            viewModel.dispatch(ModuleUiAction.MarkNeedRefresh)
        },
        onDismiss = {
            showConfirmationDialog = false
            pendingZipFiles = emptyList()
        }
    )

    val selectZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode != RESULT_OK) {
            return@rememberLauncherForActivityResult
        }
        val data = it.data ?: return@rememberLauncherForActivityResult

        scope.launch {
            val zipFiles = mutableListOf<ZipFileInfo>()
            val clipData = data.clipData
            if (clipData != null) {
                val selectedModules = mutableListOf<Uri>()
                val selectedModuleNames = mutableMapOf<Uri, String>()

                fun processUri(uri: Uri) {
                    try {
                        val uriString = uri.toString()
                        if (!isModuleUriAccessible(uriString)) {
                            return
                        }
                        takeModuleUriPermission(uriString)
                        val moduleName = extractModuleName(uriString)
                        selectedModules.add(uri)
                        selectedModuleNames[uri] = moduleName
                    } catch (e: Exception) {
                        Log.e("ModuleScreen", "Error while processing URI: $uri, Error: ${e.message}")
                    }
                }

                for (i in 0 until clipData.itemCount) {
                    val uri = clipData.getItemAt(i).uri
                    processUri(uri)
                }

                if (selectedModules.isEmpty()) {
                    snackBarHost.showReplacingSnackbar("Unable to access selected module files")
                    return@launch
                }
                selectedModules.forEach { it ->
                    zipFiles.add(zipFileDetector.parseModuleInfo(context, it))
                }
                pendingZipFiles = zipFiles

                showConfirmationDialog = true
            } else {
                val uri = data.data ?: return@launch
                // 单个安装模块
                try {
                    val uriString = uri.toString()
                    if (!isModuleUriAccessible(uriString)) {
                        snackBarHost.showReplacingSnackbar("Unable to access selected module files")
                        return@launch
                    }

                    takeModuleUriPermission(uriString)

                    zipFiles.add(zipFileDetector.parseModuleInfo(context, uri))
                    pendingZipFiles = zipFiles

                    showConfirmationDialog = true
                } catch (e: Exception) {
                    Log.e("ModuleScreen", "Error processing a single URI: $uri, Error: ${e.message}")
                    snackBarHost.showReplacingSnackbar("Error processing module file: ${e.message}")
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.dispatch(ModuleUiAction.Search(""))
        if (uiState.moduleList.isEmpty() || uiState.isNeedRefresh) {
            viewModel.dispatch(ModuleUiAction.Refresh())
        }
    }

    val isSafeMode = homeState.systemStatus.isSafeMode
    val hideInstallButton = isSafeMode || uiState.hasMagisk

    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = rememberSearchAppBarScrollBehavior(
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)
    )

    Scaffold(
        topBar = {
            SearchAppBar(
                title = stringResource(R.string.module),
                searchText = uiState.search,
                onSearchTextChange = { query ->
                    viewModel.dispatch(ModuleUiAction.Search(query))
                },
                dropdownContent = {
                    IconButton(
                        onClick = { showDropdown = true },
                    ) {
                        Icon(
                            imageVector = Icons.TwoTone.MoreVert,
                            contentDescription = stringResource(id = R.string.settings),
                        )

                        ModuleDropdown(
                            expanded = showDropdown,
                            onDismissRequest = { showDropdown = false },
                            viewModel = viewModel,
                            uiState = uiState,
                        )
                    }
                },
                navigationContent = {
                    IconButton(
                        onClick = {
                            navigator.push(Route.ModuleRepo)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.TwoTone.Cloud,
                            contentDescription = stringResource(id = R.string.module_repo),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                searchBarPlaceHolderText = stringResource(R.string.search_modules),
            )
        },
        floatingActionButton = {
            if (hideInstallButton) return@Scaffold

            FloatingActionButton(
                modifier = Modifier.padding(bottom = bottomPadding + 5.dp),
                contentColor = MaterialTheme.colorScheme.onPrimary,
                containerColor = MaterialTheme.colorScheme.primary,
                onClick = {
                    selectZipLauncher.launch(
                        Intent(Intent.ACTION_GET_CONTENT).apply {
                            type = "application/zip"
                            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                        }
                    )
                },
                content = {
                    Icon(
                        painter = painterResource(id = R.drawable.package_import),
                        contentDescription = null
                    )
                }
            )
        },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
        ),
        snackbarHost = {
            SwipeableSnackbarHost(
                hostState = snackBarHost
            )
        }
    ) { innerPadding ->
        when {
            uiState.hasMagisk -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.TwoTone.Warning,
                            contentDescription = null,
                            modifier = Modifier
                                .size(64.dp)
                                .padding(bottom = 16.dp)
                        )
                        Text(
                            stringResource(R.string.module_magisk_conflict),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
            uiState.moduleList.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.TwoTone.Extension,
                            contentDescription = null,
                            modifier = Modifier
                                .size(96.dp)
                                .padding(bottom = 16.dp)
                        )
                        Text(
                            text =
                                if (uiState.search.isNotEmpty())
                                    stringResource(R.string.search_no_any_match)
                                else
                                    stringResource(R.string.module_empty),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
            else -> {
                ModuleList(
                    viewModel = viewModel,
                    uiState = uiState,
                    listState = listState,
                    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                    onUpdateModule = {
                        navigator.push(Route.Flash.moduleUpdate(it.toString()))
                    },
                    onClickModule = { id, name, hasWebUi ->
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastClickTime < 600) {
                            Log.d("ModuleScreen", "Click too fast, ignoring")
                            return@ModuleList
                        }
                        lastClickTime = currentTime

                        if (hasWebUi) {
                            try {
                                context.startActivity(
                                    Intent(context, WebUIActivity::class.java)
                                    .setData("kernelsu://webui/$id".toUri())
                                    .putExtra("id", id)
                                        .putExtra("name", name)
                                )
                            } catch (e: Exception) {
                                Log.e("ModuleScreen", "Error launching WebUI: ${e.message}", e)
                                scope.launch {
                                    snackBarHost.showReplacingSnackbar("Error launching WebUI: ${e.message}")
                                }
                            }
                            return@ModuleList
                        }
                    },
                    context = context,
                    snackBarHost = snackBarHost,
                    bottomPadding = bottomPadding + innerPadding.calculateBottomPadding(),
                    topPadding = innerPadding.calculateTopPadding(),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ModuleDropdown(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    viewModel: ModuleViewModel,
    uiState: ModuleUiState,
) {
    DropdownMenuPopup(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
    ) {
        DropdownMenuGroup(
            shapes = MenuDefaults.groupShapes(),
        ) {
            DropdownMenuItem(
                checked = uiState.sortActionFirst,
                onCheckedChange = { checked ->
                    viewModel.dispatch(
                        ModuleUiAction.Sort(uiState.sortEnabledFirst, checked)
                    )
                },
                text = { Text(stringResource(R.string.module_sort_action_first)) },
                shapes = MenuDefaults.itemShape(
                    index = 0,
                    count = 2,
                ),
            )
            DropdownMenuItem(
                checked = uiState.sortEnabledFirst,
                onCheckedChange = { checked ->
                    viewModel.dispatch(
                        ModuleUiAction.Sort(checked, uiState.sortActionFirst)
                    )
                },
                text = { Text(stringResource(R.string.module_sort_enabled_first)) },
                shapes = MenuDefaults.itemShape(
                    index = 1,
                    count = 2,
                ),
            )
        }
    }
}

private fun getMetaModuleWarningText(
    hasModuleRequireMount: Boolean,
    showWarning: Boolean,
    context: Context,
    status: MetaModuleStatus,
) : String? {
    if (!showWarning) return null
    if (!hasModuleRequireMount) return null

    return when (status) {
        MetaModuleStatus.MISSING -> context.getString(R.string.no_meta_module_installed)
        MetaModuleStatus.REMOVED -> context.getString(R.string.meta_module_removed)
        MetaModuleStatus.DISABLED -> context.getString(R.string.meta_module_disabled)
        MetaModuleStatus.ACTIVE -> null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MetaModuleWarningCard(
    text: String,
    visible: Boolean,
    onClose: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        WarningCard(
            shape = CardDefaults.elevatedShape,
            message = text,
            onClose = onClose,
        )

        Spacer(Modifier.height(8.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ModuleList(
    viewModel: ModuleViewModel,
    uiState: ModuleUiState,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    boxModifier: Modifier = Modifier,
    onUpdateModule: (Uri) -> Unit,
    onClickModule: (id: String, name: String, hasWebUi: Boolean) -> Unit,
    context: Context,
    snackBarHost: SnackbarHostState,
    bottomPadding : Dp,
    topPadding : Dp,
) {
    val shortcut = koinInject<Shortcut>()
    var showMetaModuleWarning by rememberSaveable { mutableStateOf(true) }
    val fetchRemoteText = koinInject<FetchRemoteTextUseCase>()
    val enqueueDownload = koinInject<EnqueueDownloadUseCase>()
    val observeDownload = koinInject<ObserveDownloadUseCase>()
    val permissionRequestInterface = LocalPermissionRequestInterface.current
    val scope = rememberCoroutineScope()
    val pullRefreshState = rememberPullToRefreshState()
    val failedEnable = stringResource(R.string.module_failed_to_enable)
    val failedDisable = stringResource(R.string.module_failed_to_disable)
    val failedUninstall = stringResource(R.string.module_uninstall_failed)
    val successUninstall = stringResource(R.string.module_uninstall_success)
    val reboot = stringResource(R.string.reboot)
    val rebootToApply = stringResource(R.string.reboot_to_apply)
    val moduleStr = stringResource(R.string.module)
    val uninstall = stringResource(R.string.uninstall)
    val cancel = stringResource(android.R.string.cancel)
    val moduleUninstallConfirm = stringResource(R.string.module_uninstall_confirm)
    val metaModuleUninstallConfirm = stringResource(R.string.metamodule_uninstall_confirm)
    val updateText = stringResource(R.string.module_update)
    val changelogText = stringResource(R.string.module_changelog)
    val downloadingText = stringResource(R.string.module_downloading)
    val startDownloadingText = stringResource(R.string.module_start_downloading)
    val fetchChangeLogFailed = stringResource(R.string.module_changelog_failed)

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is ModuleUiEvent.EnabledChanged -> {
                    val moduleName = uiState.moduleList
                        .find { it.dirId == event.moduleId }
                        ?.name ?: event.moduleId
                    if (event.successful) {
                        val result = snackBarHost.showReplacingSnackbar(
                            message = rebootToApply,
                            actionLabel = reboot,
                            duration = SnackbarDuration.Long,
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            viewModel.dispatch(ModuleUiAction.Reboot)
                        }
                    } else {
                        val message = if (event.enabled) failedEnable else failedDisable
                        snackBarHost.showReplacingSnackbar(message.format(moduleName))
                    }
                }

                is ModuleUiEvent.RemovedChanged -> {
                    val moduleName = uiState.moduleList
                        .find { it.dirId == event.moduleId }
                        ?.name ?: event.moduleId
                    if (event.successful) {
                        viewModel.dispatch(ModuleUiAction.MarkNeedRefresh)
                        viewModel.dispatch(ModuleUiAction.Refresh())
                    }
                    if (event.removed) {
                        val message = if (event.successful) successUninstall else failedUninstall
                        val result = snackBarHost.showReplacingSnackbar(
                            message = message.format(moduleName),
                            actionLabel = reboot.takeIf { event.successful },
                            duration = SnackbarDuration.Long,
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            viewModel.dispatch(ModuleUiAction.Reboot)
                        }
                    }
                }

                is ModuleUiEvent.Error -> if (event.message.isNotBlank()) {
                    snackBarHost.showReplacingSnackbar(event.message)
                }

                ModuleUiEvent.RefreshCompleted -> Unit
            }
        }
    }

    val loadingDialog = rememberLoadingDialog()
    val confirmDialog = rememberConfirmDialog()

    var shortcutModuleId by rememberSaveable { mutableStateOf<String?>(null) }
    val textFieldState = rememberTextFieldState()
    var shortcutIconUri by rememberSaveable { mutableStateOf<String?>(null) }
    var defaultShortcutIconUri by rememberSaveable { mutableStateOf<String?>(null) }
    var defaultActionShortcutIconUri by rememberSaveable { mutableStateOf<String?>(null) }
    var defaultWebUiShortcutIconUri by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedShortcutType by rememberSaveable { mutableStateOf<ShortcutType?>(null) }
    val showShortcutDialog = remember { mutableStateOf(false) }
    val showShortcutTypeRow = remember { mutableStateOf(false) }

    fun openShortcutDialogForType(type: ShortcutType) {
        selectedShortcutType = type
        val defaultIcon = when (type) {
            ShortcutType.Action -> defaultActionShortcutIconUri ?: defaultWebUiShortcutIconUri
            ShortcutType.WebUI -> defaultWebUiShortcutIconUri ?: defaultActionShortcutIconUri
        }
        defaultShortcutIconUri = defaultIcon
        shortcutIconUri = defaultIcon
        showShortcutDialog.value = true
    }

    fun hasModuleShortcut(context: Context, moduleId: String, type: ShortcutType): Boolean {
        return when (type) {
            ShortcutType.Action -> shortcut.hasModuleActionShortcut(context, moduleId)
            ShortcutType.WebUI -> shortcut.hasModuleWebUiShortcut(context, moduleId)
        }
    }

    fun deleteModuleShortcut(context: Context, moduleId: String, type: ShortcutType) {
        when (type) {
            ShortcutType.Action -> shortcut.deleteModuleActionShortcut(context, moduleId)
            ShortcutType.WebUI -> shortcut.deleteModuleWebUiShortcut(context, moduleId)
        }
    }

    fun createModuleShortcut(
        context: Context,
        moduleId: String,
        name: String,
        iconUri: String?,
        type: ShortcutType
    ) {
        when (type) {
            ShortcutType.Action -> {
                shortcut.createModuleActionShortcut(
                    context = context,
                    moduleId = moduleId,
                    name = name,
                    iconUri = iconUri
                )
            }

            ShortcutType.WebUI -> {
                shortcut.createModuleWebUiShortcut(
                    context = context,
                    moduleId = moduleId,
                    name = name,
                    iconUri = iconUri
                )
            }
        }
    }

    val pickShortcutIconLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        shortcutIconUri = uri?.toString()
    }

    val shortcutPreviewIcon = remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(shortcutIconUri) {
        val uriStr = shortcutIconUri
        if (uriStr.isNullOrBlank()) {
            shortcutPreviewIcon.value = null
            return@LaunchedEffect
        }
        val bitmap = withContext(Dispatchers.IO) {
            shortcut.loadShortcutBitmap(context, uriStr)
        }
        shortcutPreviewIcon.value = bitmap?.asImageBitmap()
    }

    var hasExistingShortcut by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(shortcutModuleId, selectedShortcutType, showShortcutDialog.value) {
        val moduleId = shortcutModuleId
        val type = selectedShortcutType
        if (!showShortcutDialog.value || moduleId.isNullOrBlank() || type == null) {
            hasExistingShortcut = false
            return@LaunchedEffect
        }
        val exists = withContext(Dispatchers.IO) {
            hasModuleShortcut(context, moduleId, type)
        }
        hasExistingShortcut = exists
    }

    suspend fun onModuleUpdate(
        module: InstalledModule,
        changelogUrl: String,
        downloadUrl: String,
        fileName: String
    ) {
        val changelogResult = loadingDialog.withLoading {
            fetchRemoteText(changelogUrl)
        }

        val showToast: suspend (String) -> Unit = { msg ->
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    msg,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        val changelog = changelogResult.getOrElse {
            showToast(fetchChangeLogFailed.format(it.message))
            return
        }

        val confirmResult = confirmDialog.awaitConfirm(
            changelogText,
            content = changelog,
            markdown = true,
            confirm = updateText,
        )

        if (confirmResult != ConfirmResult.Confirmed) {
            return
        }

        showToast(startDownloadingText.format(module.name))

        val downloading = downloadingText.format(module.name)
        withContext(Dispatchers.IO) {
            download(
                context,
                permissionRequestInterface,
                downloadUrl,
                fileName,
                enqueueDownload,
                observeDownload,
                onDownloaded = { uri ->
                    onUpdateModule(uri)
                },
                onDownloading = {
                    launch(Dispatchers.Main) {
                        Toast.makeText(context, downloading, Toast.LENGTH_SHORT).show()
                    }
                },
            )
        }
    }

    suspend fun onModuleUninstallClicked(module: InstalledModule) {
        val isUninstall = !module.remove
        if (isUninstall) {
            val formatter = if (module.metamodule) metaModuleUninstallConfirm else moduleUninstallConfirm
            val confirmResult = confirmDialog.awaitConfirm(
                moduleStr,
                content = formatter.format(module.name),
                confirm = uninstall,
                dismiss = cancel
            )
            if (confirmResult != ConfirmResult.Confirmed) {
                return
            }
        }

        if (isUninstall) {
            withContext(Dispatchers.IO) {
                shortcut.deleteModuleActionShortcut(context, module.id)
                shortcut.deleteModuleWebUiShortcut(context, module.id)
            }
        }
        viewModel.dispatch(ModuleUiAction.SetRemoved(module.dirId, isUninstall))
    }

    fun onModuleAddShortcut(module: InstalledModule) {
        shortcutModuleId = module.id
        textFieldState.edit {
            replace(0, length, module.name)
        }
        shortcutIconUri = null
        defaultShortcutIconUri = null
        defaultActionShortcutIconUri = module.actionIconPath
            ?.takeIf { it.isNotBlank() }
            ?.let { "su:$it" }
        defaultWebUiShortcutIconUri = module.webUiIconPath
            ?.takeIf { it.isNotBlank() }
            ?.let { "su:$it" }
        if (module.hasActionScript && module.hasWebUi) {
            selectedShortcutType = null
            showShortcutTypeRow.value = true
            openShortcutDialogForType(ShortcutType.Action)
        } else if (module.hasActionScript) {
            openShortcutDialogForType(ShortcutType.Action)
        } else if (module.hasWebUi) {
            openShortcutDialogForType(ShortcutType.WebUI)
        }
    }

    PullToRefreshBox(
        state = pullRefreshState,
        onRefresh = {
            viewModel.dispatch(ModuleUiAction.Refresh(manual = true))
        },
        modifier = boxModifier
            .fillMaxSize()
            .blurSource(),
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                modifier = Modifier
                    .padding(top = topPadding)
                    .align(Alignment.TopCenter),
                state = pullRefreshState,
                isRefreshing = uiState.isRefreshing,
            )
        },
        isRefreshing = uiState.isRefreshing
    ) {
        val metaModuleWarningText by produceState<String?>(
            initialValue = null,
            uiState.hasModuleRequireMount,
            showMetaModuleWarning,
            uiState.metaModuleStatus,
        ) {
            value = withContext(Dispatchers.IO) {
                getMetaModuleWarningText(
                    uiState.hasModuleRequireMount,
                    showMetaModuleWarning,
                    context,
                    uiState.metaModuleStatus,
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = modifier,
            contentPadding = remember {
                PaddingValues(
                    start = 16.dp,
                    top = 0.dp,
                    end = 16.dp,
                    bottom = 72.dp + 5.dp + 5.dp // FAB + bottom padding of FAB
                )
            },
        ) {
            item {
                Spacer(modifier = Modifier.height(topPadding))
            }

            if (metaModuleWarningText != null) {
                item(
                    key = "warning"
                ) {
                    MetaModuleWarningCard(
                        text = metaModuleWarningText!!,
                        visible = showMetaModuleWarning,
                        onClose = { showMetaModuleWarning = false },
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            items(
                items = uiState.moduleList,
                key = { "module-$it.id" }
            ) { module ->
                ModuleItem(
                    viewModel = viewModel,
                    module = module,
                    moduleSizes = uiState.moduleSizes,
                    updateUrl = module.moduleUpdate?.zipUrl.orEmpty(),
                    onUninstallClicked = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                onModuleUninstallClicked(module)
                            }
                        }
                    },
                    onCheckChanged = { enabled ->
                        viewModel.dispatch(ModuleUiAction.SetEnabled(module.dirId, enabled))
                        true
                    },
                    onUpdate = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                onModuleUpdate(
                                    module,
                                    module.moduleUpdate!!.changelog,
                                    module.moduleUpdate.zipUrl,
                                    "${module.name}-${module.moduleUpdate.version}.zip"
                                )
                            }
                        }
                    },
                    onClick = {
                        onClickModule(it.dirId, it.name, it.hasWebUi)
                    },
                    onModuleAddShortcut = {
                        onModuleAddShortcut(it)
                    },
                    showMoreModuleInfo = uiState.showMoreModuleInfo,
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                Spacer(modifier = Modifier.height(bottomPadding))
            }
        }
    }

    if (showShortcutDialog.value) {
        ModalBottomSheet(
            sheetState = rememberBottomSheetState(
                initialValue = SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
            ),
            onDismissRequest = {
                showShortcutDialog.value = false
                showShortcutTypeRow.value = false
            }
        ) {
            var error by remember { mutableStateOf("") }
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.module_shortcut_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                )
                if (showShortcutTypeRow.value) {
                    PrimaryTabRow(
                        selectedTabIndex = selectedShortcutType?.ordinal ?: 0,
                        containerColor = Color.Transparent,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Tab(
                            selected = selectedShortcutType == ShortcutType.Action,
                            onClick = {
                                selectedShortcutType = ShortcutType.Action
                                shortcutIconUri = defaultActionShortcutIconUri
                                defaultShortcutIconUri = defaultActionShortcutIconUri
                            },
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            text = { Text("Action") }
                        )

                        Tab(
                            selected = selectedShortcutType == ShortcutType.WebUI,
                            onClick = {
                                selectedShortcutType = ShortcutType.WebUI
                                shortcutIconUri = defaultWebUiShortcutIconUri
                                defaultShortcutIconUri = defaultWebUiShortcutIconUri
                            },
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            text = { Text("WebUI") }
                        )
                    }
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .padding(vertical = 16.dp)
                        .size(100.dp)
                        .clip(ContinuousRoundedRectangle(25.dp))
                ) {
                    val preview = shortcutPreviewIcon.value
                    if (preview != null) {
                        Image(
                            bitmap = preview,
                            modifier = Modifier.size(100.dp),
                            contentDescription = null,
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .background(Color.White)
                        )
                        Image(
                            painter = painterResource(id = R.drawable.ic_launcher_foreground),
                            contentDescription = null,
                            contentScale = FixedScale(1.5f)
                        )
                    }
                }
                SegmentedColumn {
                    if (shortcutIconUri == defaultShortcutIconUri) {
                        item {
                            SettingsBaseWidget(
                                icon = Icons.TwoTone.Photo,
                                isOnBackground = false,
                                title = stringResource(id = R.string.module_shortcut_icon_pick),
                                onClick = {
                                    pickShortcutIconLauncher.launch("image/*")
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.TwoTone.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    } else {
                        item {
                            SettingsBaseWidget(
                                icon = Icons.TwoTone.Restore,
                                isOnBackground = false,
                                title = stringResource(id = R.string.restore),
                                onClick = {
                                    shortcutIconUri = defaultShortcutIconUri
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.TwoTone.Undo,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    item {
                        val shouldNotEmpty =
                            stringResource(R.string.module_shortcut_should_not_empty)
                        SettingsTextFieldWidget(
                            state = textFieldState,
                            title = stringResource(id = R.string.module_shortcut_name_label),
                            error = error,
                            renderBackgroundBlur = false,
                        )

                        LaunchedEffect(textFieldState.text) {
                            error = if (textFieldState.text.isBlank()) {
                                shouldNotEmpty
                            } else ""
                        }
                    }

                    if (hasExistingShortcut) {
                        item {
                            SettingsJumpPageWidget(
                                icon = Icons.TwoTone.Delete,
                                renderBackgroundBlur = false,
                                title = stringResource(id = R.string.module_shortcut_delete),
                                onClick = {
                                    val moduleId = shortcutModuleId
                                    val type = selectedShortcutType
                                    if (!moduleId.isNullOrBlank() && type != null) {
                                        deleteModuleShortcut(context, moduleId, type)
                                    }
                                    showShortcutDialog.value = false
                                },
                            )
                        }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { showShortcutDialog.value = false },
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 16.dp)
                    ) {
                        Text(
                            text = stringResource(id = android.R.string.cancel),
                        )
                    }

                    Button(
                        onClick = {
                            val moduleId = shortcutModuleId
                            val type = selectedShortcutType
                            if (!moduleId.isNullOrBlank() && textFieldState.text.isNotBlank() && type != null) {
                                createModuleShortcut(
                                    context = context,
                                    moduleId = moduleId,
                                    name = textFieldState.text.toString(),
                                    iconUri = shortcutIconUri,
                                    type = type
                                )
                            }
                            showShortcutDialog.value = false
                        },
                        enabled = error.isBlank(),
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 16.dp)
                    ) {
                        Text(
                            text = if (hasExistingShortcut) {
                                stringResource(id = R.string.module_update)
                            } else {
                                stringResource(id = android.R.string.ok)
                            },
                        )
                    }
                }
            }
        }
    }
}

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun ModuleItem(
    viewModel: ModuleViewModel,
    module: InstalledModule,
    moduleSizes: Map<String, String>,
    updateUrl: String,
    onUninstallClicked: (InstalledModule) -> Unit,
    onCheckChanged: suspend (Boolean) -> Boolean,
    onUpdate: (InstalledModule) -> Unit,
    onClick: (InstalledModule) -> Unit,
    onModuleAddShortcut: (InstalledModule) -> Unit,
    showMoreModuleInfo: Boolean,
) {
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
    val hapticFeedback = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var isEnabled by remember(module.dirId) { mutableStateOf(module.enabled) }
    var isChangingEnabled by remember(module.dirId) { mutableStateOf(false) }

    LaunchedEffect(module.enabled) {
        isEnabled = module.enabled
    }

    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .renderBackgroundBlur(),
        color =
            if (themeConfig.isEnableBlurExp)
                Color.Transparent
            else
                MaterialTheme.colorScheme.surfaceBright.copy(cardConfig.cardAlpha),
        shape = RoundedCornerShape(16.dp)
    ) {
        val textDecoration = if (!module.remove) null else TextDecoration.LineThrough
        val interactionSource = remember { MutableInteractionSource() }

        LaunchedEffect(module.dirId) {
            viewModel.dispatch(ModuleUiAction.LoadSize(module.dirId))
        }

        val sizeStr = moduleSizes[module.dirId]

        Column(
            modifier = Modifier
                .run {
                    if (module.hasActionScript || module.hasWebUi) {
                        combinedClickable(
                            onLongClick = {
                                onModuleAddShortcut(module)
                            },
                            onClick = {
                                if (module.hasWebUi) {
                                    onClick(module)
                                }
                            }
                        )
                    } else {
                        this
                    }
                }
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val moduleVersion = stringResource(id = R.string.module_version)
                val moduleAuthor = stringResource(id = R.string.module_author)

                Column(
                    modifier = Modifier.fillMaxWidth(0.8f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = module.name,
                            fontSize = MaterialTheme.typography.titleMedium.fontSize,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                            fontFamily = MaterialTheme.typography.titleMedium.fontFamily,
                            textDecoration = textDecoration,
                            modifier = Modifier.weight(1f, false)
                        )
                    }

                    Text(
                        text = "$moduleVersion: ${module.version}",
                        fontSize = MaterialTheme.typography.bodySmall.fontSize,
                        lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                        fontFamily = MaterialTheme.typography.bodySmall.fontFamily,
                        textDecoration = textDecoration,
                    )

                    Text(
                        text = "$moduleAuthor: ${module.author}",
                        fontSize = MaterialTheme.typography.bodySmall.fontSize,
                        lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                        fontFamily = MaterialTheme.typography.bodySmall.fontFamily,
                        textDecoration = textDecoration,
                    )

                    // 显示更多模块信息时添加updateJson
                    if (showMoreModuleInfo && module.updateJson.isNotEmpty()) {
                        val updateJsonLabel = stringResource(R.string.module_update_json)
                        Text(
                            text = "$updateJsonLabel: ${module.updateJson}",
                            fontSize = MaterialTheme.typography.bodySmall.fontSize,
                            lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                            fontFamily = MaterialTheme.typography.bodySmall.fontFamily,
                            textDecoration = textDecoration,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { },
                                    onLongClick = {
                                        val clipData = ClipData.newPlainText(
                                            "Update JSON URL",
                                            module.updateJson
                                        )
                                        clipboardManager.setPrimaryClip(clipData)
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)

                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.module_update_json_copied),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                ),
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Switch(
                        enabled = !module.update && !isChangingEnabled,
                        checked = isEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                isChangingEnabled = true
                                try {
                                    if (onCheckChanged(enabled)) {
                                        isEnabled = enabled
                                    }
                                } finally {
                                    isChangingEnabled = false
                                }
                            }
                        },
                        interactionSource = if (!module.hasWebUi) interactionSource else null,
                        thumbContent = {
                            if (isEnabled) {
                                Icon(
                                    imageVector = Icons.TwoTone.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(SwitchDefaults.IconSize),
                                )
                            } else
                            {
                                Icon(
                                    imageVector = Icons.TwoTone.Close,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.surfaceBright,
                                    modifier = Modifier.size(SwitchDefaults.IconSize),
                                )
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = module.description,
                fontSize = MaterialTheme.typography.bodySmall.fontSize,
                fontFamily = MaterialTheme.typography.bodySmall.fontFamily,
                lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                fontWeight = MaterialTheme.typography.bodySmall.fontWeight,
                overflow = TextOverflow.Ellipsis,
                maxLines = 4,
                textDecoration = textDecoration,
            )

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                LabelText(
                    label = module.dirId,
                    containerColor = MaterialTheme.colorScheme.primary,
                )
                if (module.metamodule) {
                    LabelText(
                        label = "META",
                        containerColor = MaterialTheme.colorScheme.tertiary,
                    )
                }
                LabelText(
                    label = sizeStr ?: "0 KB",
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider(thickness = Dp.Hairline)

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (module.hasActionScript) {
                    FilledTonalButton(
                        modifier = Modifier.defaultMinSize(minWidth = 52.dp, minHeight = 32.dp),
                        enabled = !module.remove && isEnabled,
                        onClick = {
                            navigator.push(Route.ExecuteModuleAction(module.dirId))
                            viewModel.dispatch(ModuleUiAction.MarkNeedRefresh)
                        },
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            top = 7.dp,
                            end = 12.dp,
                            bottom = 7.dp,
                        ),
                    ) {
                        Icon(
                            modifier = Modifier.size(20.dp),
                            imageVector = Icons.TwoTone.PlayArrow,
                            contentDescription = null
                        )
                    }
                }

                if (module.hasWebUi) {
                    FilledTonalButton(
                        modifier = Modifier.defaultMinSize(minWidth = 52.dp, minHeight = 32.dp),
                        enabled = !module.remove && isEnabled,
                        onClick = { onClick(module) },
                        interactionSource = interactionSource,
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            top = 7.dp,
                            end = 12.dp,
                            bottom = 7.dp,
                        ),
                    ) {
                        Icon(
                            modifier = Modifier.size(20.dp),
                            imageVector = Icons.AutoMirrored.TwoTone.Wysiwyg,
                            contentDescription = null
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f, true))

                if (updateUrl.isNotEmpty()) {
                    Button(
                        modifier = Modifier.defaultMinSize(minWidth = 52.dp, minHeight = 32.dp),
                        enabled = !module.remove,
                        onClick = { onUpdate(module) },
                        shape = ButtonDefaults.textShape,
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            top = 7.dp,
                            end = 12.dp,
                            bottom = 7.dp,
                        ),
                    ) {
                        Icon(
                            modifier = Modifier.size(20.dp),
                            imageVector = Icons.TwoTone.Download,
                            contentDescription = null
                        )
                    }
                }

                FilledTonalButton(
                    modifier = Modifier.defaultMinSize(minWidth = 52.dp, minHeight = 32.dp),
                    onClick = { onUninstallClicked(module) },
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        top = 9.dp,
                        end = 12.dp,
                        bottom = 7.dp,
                    ),
                ) {
                    if (!module.remove) {
                        Icon(
                            modifier = Modifier.size(20.dp),
                            imageVector = Icons.TwoTone.Delete,
                            contentDescription = null,
                        )
                    } else {
                        Icon(
                            modifier = Modifier
                                .size(20.dp)
                                .rotate(180f),
                            imageVector = Icons.TwoTone.Refresh,
                            contentDescription = null
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun ModuleItemPreview() {
    val module = InstalledModule(
        id = "id",
        name = "name",
        version = "version",
        versionCode = 1,
        author = "author",
        description = "I am a test module and i do nothing but show a very long description",
        enabled = true,
        update = true,
        remove = false,
        updateJson = "",
        hasWebUi = true,
        hasActionScript = true,
        metamodule = true,
        actionIconPath = null,
        webUiIconPath = null,
        dirId = "dirId",
        moduleUpdate = null
    )
    ModuleItem(
        koinViewModel<ModuleViewModel>(),
        module,
        emptyMap(),
        "",
        {},
        { true },
        {},
        {},
        {},
        false,
    )
}
