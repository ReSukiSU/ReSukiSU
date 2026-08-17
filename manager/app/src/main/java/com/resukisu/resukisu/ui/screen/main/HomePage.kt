package com.resukisu.resukisu.ui.screen.main

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.system.Os
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
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
import androidx.compose.material.icons.twotone.Block
import androidx.compose.material.icons.twotone.Error
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material.icons.twotone.PowerSettingsNew
import androidx.compose.material.icons.twotone.TaskAlt
import androidx.compose.material.icons.twotone.Tune
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.resukisu.resukisu.BuildConfig
import com.resukisu.resukisu.Natives.KernelPatchImplementation
import com.resukisu.resukisu.R
import com.resukisu.resukisu.domain.model.HomeSystemInfo
import com.resukisu.resukisu.domain.model.KernelStatus
import com.resukisu.resukisu.domain.model.ManagerUpdateChannel
import com.resukisu.resukisu.domain.model.ManagerUpdateInfo
import com.resukisu.resukisu.domain.usecase.EnqueueManagerUpdateUseCase
import com.resukisu.resukisu.magica.MagicaService
import com.resukisu.resukisu.ui.component.KsuIsValid
import com.resukisu.resukisu.ui.component.SwipeableSnackbarHost
import com.resukisu.resukisu.ui.component.WarningCard
import com.resukisu.resukisu.ui.component.rememberConfirmDialog
import com.resukisu.resukisu.ui.component.rememberLoadingDialog
import com.resukisu.resukisu.ui.component.settings.SegmentedColumn
import com.resukisu.resukisu.ui.component.settings.SettingsBaseWidget
import com.resukisu.resukisu.ui.navigation.LocalNavigator
import com.resukisu.resukisu.ui.navigation.Route
import com.resukisu.resukisu.ui.screen.LabelText
import com.resukisu.resukisu.ui.theme.CardConfig
import com.resukisu.resukisu.ui.theme.ThemeConfig
import com.resukisu.resukisu.ui.theme.blurEffect
import com.resukisu.resukisu.ui.theme.blurSource
import com.resukisu.resukisu.ui.util.LocalPermissionRequestInterface
import com.resukisu.resukisu.ui.util.LocalSnackbarHost
import com.resukisu.resukisu.ui.util.downloader.downloadManagerUpdate
import com.resukisu.resukisu.ui.viewmodel.HomeUiAction
import com.resukisu.resukisu.ui.viewmodel.HomeUiEvent
import com.resukisu.resukisu.ui.viewmodel.HomeUiState
import com.resukisu.resukisu.ui.viewmodel.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Duration.Companion.milliseconds

/**
 * @author ShirkNeko
 * @date 2025/9/29.
 */

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomePage(
    bottomPadding: Dp,
) {
    val context = LocalContext.current
    val viewModel = koinViewModel<HomeViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.dispatch(HomeUiAction.AwaitInitialData)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeUiEvent.Error -> if (event.message.isNotBlank()) {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    if (!uiState.isInitialDataLoaded) return

    val pullRefreshState = rememberPullToRefreshState()
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)
    val scrollState = rememberScrollState()
    val navigator = LocalNavigator.current
    val loadingDialog = rememberLoadingDialog()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopBar(
                uiState = uiState,
                onReboot = { viewModel.dispatch(HomeUiAction.Reboot(it)) },
                scrollBehavior = scrollBehavior,
            )
        },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
        ),
        snackbarHost = {
            SwipeableSnackbarHost(
                modifier = Modifier.padding(bottom = bottomPadding),
                hostState = LocalSnackbarHost.current
            )
        }
    ) { innerPadding ->
        PullToRefreshBox(
            state = pullRefreshState,
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.dispatch(HomeUiAction.Refresh()) },
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .verticalScroll(scrollState)
                    .padding(
                        top = innerPadding.calculateTopPadding() + 2.dp,
                        start = 16.dp,
                        end = 16.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // 状态卡片
                if (uiState.isCoreDataLoaded) {
                    if (uiState.systemStatus.requireNewKernel) {
                        if ((uiState.systemStatus.ksuVersion ?: 0) > BuildConfig.VERSION_CODE) {
                            WarningCard(
                                message = stringResource(
                                    id = R.string.require_manager_version,
                                    BuildConfig.VERSION_CODE,
                                    uiState.systemStatus.ksuVersion ?: 0
                                ),
                                icon = {
                                    Icon(
                                        imageVector = Icons.TwoTone.Error,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            )
                        } else {
                            WarningCard(
                                message = stringResource(
                                    id = R.string.require_kernel_version,
                                    uiState.systemStatus.ksuVersion ?: 0,
                                    BuildConfig.VERSION_CODE
                                ),
                                icon = {
                                    Icon(
                                        imageVector = Icons.TwoTone.Error,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // 警告信息
                    if (BuildConfig.DEBUG) {
                        WarningCard(
                            message = stringResource(R.string.debug_version_notice),
                            icon = {
                                Icon(
                                    imageVector = Icons.TwoTone.Error,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    if (!uiState.systemStatus.isOfficialSignature) {
                        WarningCard(
                            message = stringResource(
                                R.string.unofficial_version_notice,
                                stringResource(R.string.app_name)
                            ),
                            icon = {
                                Icon(
                                    imageVector = Icons.TwoTone.Error,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    if (BuildConfig.IS_PR_BUILD || uiState.systemStatus.isPrBuild) {
                        WarningCard(
                            message = stringResource(
                                id = R.string.home_pr_build_warning
                            ),
                            icon = {
                                Icon(
                                    imageVector = Icons.TwoTone.Error,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    if (uiState.systemStatus.kernelPatchImplementation == KernelPatchImplementation.OFFICIAL) {
                        WarningCard(
                            message = stringResource(
                                R.string.conflict_with_apatch,
                            ),
                            icon = {
                                Icon(
                                    imageVector = Icons.TwoTone.Error,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    if (uiState.systemStatus.ksuVersion != null && !uiState.systemStatus.isRootAvailable) {
                        WarningCard(
                            message = stringResource(id = R.string.grant_root_failed),
                            icon = {
                                Icon(
                                    imageVector = Icons.TwoTone.Error,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    StatusCard(
                        uiState = uiState,
                        onClickInstall = {
                            navigator.push(Route.Install(preselectedKernelUri = null))
                        },
                        onClickJailbreak = {
                            loadingDialog.showLoading()
                            context.startService(Intent(context, MagicaService::class.java))
                            // Manager will be force-stopped and restarted by late-load on success.
                            // If that doesn't happen within timeout, jailbreak likely failed.
                            scope.launch(Dispatchers.IO) {
                                delay(30_000.milliseconds)
                                withContext(Dispatchers.Main) {
                                    loadingDialog.hide()
                                    Toast.makeText(
                                        context,
                                        R.string.jailbreak_timeout,
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))

                ManagerUpdateCard(uiState.stableManagerUpdate)
                Spacer(modifier = Modifier.height(10.dp))
                ManagerUpdateCard(uiState.betaManagerUpdate)
                Spacer(modifier = Modifier.height(10.dp))
                if (uiState.isBetaManagerUpdateCheckFailed) {
                    WarningCard(
                        message = stringResource(R.string.beta_update_check_failed),
                        icon = {
                            Icon(
                                imageVector = Icons.TwoTone.Error,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                if (uiState.isExtendedDataLoaded) {
                    InfoCard(
                        systemStatus = uiState.systemStatus,
                        systemInfo = uiState.systemInfo,
                        isSimpleMode = uiState.isSimpleMode,
                    )
                }

                // 链接卡片
                if (!uiState.isSimpleMode) {
                    DonateCard()
                    LearnMoreCard()
                }

                Spacer(Modifier.height(bottomPadding))
            }
        }
    }
}

@Composable
private fun ManagerUpdateCard(update: ManagerUpdateInfo?) {
    val visibilityState = remember { MutableTransitionState(false) }
    var displayedUpdate by remember { mutableStateOf<ManagerUpdateInfo?>(null) }

    LaunchedEffect(update) {
        if (update != null) displayedUpdate = update
        visibilityState.targetState = update != null
    }

    AnimatedVisibility(
        visibleState = visibilityState,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        displayedUpdate?.let { updateInfo ->
            ManagerUpdateCardContent(updateInfo)
        }
    }
}

@Composable
private fun ManagerUpdateCardContent(updateInfo: ManagerUpdateInfo) {
    val context = LocalContext.current
    val permissionRequestInterface = LocalPermissionRequestInterface.current
    val enqueueManagerUpdate = koinInject<EnqueueManagerUpdateUseCase>()
    val channelTitle = stringResource(
        if (updateInfo.channel == ManagerUpdateChannel.STABLE) {
            R.string.manager_update_stable
        } else {
            R.string.manager_update_beta
        }
    )
    val message = if (updateInfo.channel == ManagerUpdateChannel.STABLE) {
        stringResource(R.string.new_version_available, updateInfo.versionCode)
    } else {
        stringResource(R.string.beta_version_available, updateInfo.versionCode)
    }
    val updateText = stringResource(R.string.module_update)
    val details = stringResource(
        R.string.manager_update_details,
        updateInfo.versionName,
        updateInfo.versionCode,
        updateInfo.abi,
    )
    val dialogContent = if (updateInfo.changelog.isBlank()) {
        details
    } else {
        "$details\n\n${updateInfo.changelog}"
    }
    val updateDialog = rememberConfirmDialog(
        onConfirm = {
            downloadManagerUpdate(
                context,
                permissionRequestInterface,
                updateInfo,
                enqueueManagerUpdate,
            )
        }
    )

    WarningCard(
        message = message,
        color = MaterialTheme.colorScheme.outlineVariant,
        icon = {
            Icon(
                imageVector = Icons.TwoTone.Info,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        },
        onClick = {
            updateDialog.showConfirm(
                title = channelTitle,
                content = dialogContent,
                markdown = updateInfo.changelog.isNotBlank(),
                confirm = updateText,
            )
        }
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RebootDropdownItems(
    items: Map<Int, String>,
    onReboot: (String) -> Unit,
) {
    items.onEachIndexed { index, (id, reason) ->
        DropdownMenuItem(
            selected = false,
            text = { Text(stringResource(id)) },
            onClick = { onReboot(reason) },
            shapes = MenuDefaults.itemShape(
                index = index,
                count = items.size
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TopBar(
    uiState: HomeUiState,
    onReboot: (String) -> Unit,
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
    val navigator = LocalNavigator.current

    LargeFlexibleTopAppBar(
        modifier = Modifier.blurEffect(),
        title = {
            Text(
                text = stringResource(R.string.app_name)
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor =
                if (themeConfig.isEnableBlur)
                    Color.Transparent
                else
                    MaterialTheme.colorScheme.surfaceContainer.copy(cardConfig.cardAlpha),
            scrolledContainerColor =
                if (themeConfig.isEnableBlur)
                    Color.Transparent
                else
                    MaterialTheme.colorScheme.surfaceContainer.copy(cardConfig.cardAlpha),
        ),
        actions = {
            if (uiState.isCoreDataLoaded) {
                // SuSFS 配置按钮
                if (uiState.systemInfo.susfsVersionSupported) {
                    IconButton(onClick = {
                        navigator.push(Route.SuSFSConfig)
                    }) {
                        Icon(
                            imageVector = Icons.TwoTone.Tune,
                            contentDescription = stringResource(R.string.susfs_config_setting_title)
                        )
                    }
                }

                // 重启按钮
                var showDropdown by remember { mutableStateOf(false) }
                KsuIsValid(uiState.systemStatus) {
                    IconButton(onClick = {
                        showDropdown = true
                    }) {
                        Icon(
                            imageVector = Icons.TwoTone.PowerSettingsNew,
                            contentDescription = stringResource(id = R.string.reboot)
                        )

                        DropdownMenuPopup(expanded = showDropdown, onDismissRequest = {
                            showDropdown = false
                        }) {
                            DropdownMenuGroup(
                                shapes = MenuDefaults.groupShapes()
                            ) {
                                val pm =
                                    LocalContext.current.getSystemService(Context.POWER_SERVICE) as PowerManager?
                                var methods = mapOf(
                                    R.string.reboot to "",
                                    R.string.reboot_soft to "soft_reboot",
                                    R.string.reboot_recovery to "recovery",
                                    R.string.reboot_bootloader to "bootloader",
                                    R.string.reboot_download to "download",
                                    R.string.reboot_edl to "edl"
                                )

                                @Suppress("DEPRECATION")
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && pm?.isRebootingUserspaceSupported == true) {
                                    methods = methods + (R.string.reboot_userspace to "userspace")
                                }

                                RebootDropdownItems(methods, onReboot)
                            }
                        }
                    }
                }
            }
        },
        windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp)),
        scrollBehavior = scrollBehavior
    )
}

@Composable
private fun StatusCard(
    uiState: HomeUiState,
    onClickInstall: () -> Unit = {},
    onClickJailbreak: () -> Unit = {}
) {
    val systemStatus = uiState.systemStatus
    val onClick = { _: Offset ->
        if (systemStatus.isRootAvailable || systemStatus.kernelVersion.isGKI()) {
            onClickInstall()
        }
    }

    when {
        systemStatus.ksuVersion != null -> {
            val workingModeText = when {
                systemStatus.isSafeMode -> stringResource(id = R.string.safe_mode)
                else -> stringResource(id = R.string.home_working)
            }

            val workingModeSurfaceText = when {
                systemStatus.lkmMode == true -> "LKM"
                else -> "Built-in"
            }

            SettingsBaseWidget(
                icon = Icons.TwoTone.TaskAlt,
                iconSize = 18.dp,
                title = workingModeText,
                description = stringResource(
                    R.string.home_short_info,
                    uiState.systemInfo.superuserCount,
                    uiState.systemInfo.moduleCount
                ),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                foreContent = {
                    Spacer(Modifier.width(8.dp))

                    // 工作模式标签
                    LabelText(
                        label = workingModeSurfaceText,
                        containerColor = MaterialTheme.colorScheme.primary
                    )

                    if (systemStatus.isLateLoadMode) {
                        Spacer(Modifier.width(6.dp))
                        LabelText(
                            label = stringResource(id = R.string.jailbreak_mode),
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    }

                    // 架构标签
                    if (Os.uname().machine != "aarch64") {
                        Spacer(Modifier.width(6.dp))
                        LabelText(
                            label = Os.uname().machine,
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                onClick = onClick
            )
        }

        systemStatus.kernelVersion.isGKI() -> {
            SettingsBaseWidget(
                icon = Icons.TwoTone.Warning,
                iconSize = 18.dp,
                isError = true,
                containerColor = MaterialTheme.colorScheme.errorContainer,
                title = stringResource(R.string.home_not_installed),
                description = stringResource(R.string.home_click_to_install),
                onClick = onClick,
                trailingContent = if (systemStatus.isSELinuxPermissive) {
                    {
                        Button(
                            onClick = onClickJailbreak,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )

                        ) {
                            Text(stringResource(R.string.home_jailbreak))
                        }
                    }
                } else null
            )
        }

        else -> {
            SettingsBaseWidget(
                icon = Icons.TwoTone.Block,
                iconSize = 18.dp,
                containerColor = MaterialTheme.colorScheme.errorContainer,
                isError = true,
                title = stringResource(R.string.home_unsupported),
                description = stringResource(R.string.home_unsupported_reason),
            )
        }
    }
}

@Composable
fun LearnMoreCard() {
    val uriHandler = LocalUriHandler.current
    val url = stringResource(R.string.home_learn_kernelsu_url)

    SegmentedColumn(
        modifier = Modifier.fillMaxWidth(),
        title = stringResource(R.string.learn_more),
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)
    ) {
        item {
            SettingsBaseWidget(
                iconPlaceholder = false,
                title = stringResource(R.string.home_learn_kernelsu),
                description = stringResource(R.string.home_click_to_learn_kernelsu),
                onClick = {
                    uriHandler.openUri(url)
                }
            )
        }
    }
}

@Composable
fun DonateCard() {
    val uriHandler = LocalUriHandler.current
    SegmentedColumn(
        modifier = Modifier.fillMaxWidth(),
        title = stringResource(R.string.home_support_title),
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)
    ) {
        item {
            SettingsBaseWidget(
                iconPlaceholder = false,
                title = stringResource(R.string.home_support_title),
                description = stringResource(R.string.home_support_content),
                onClick = {
                    uriHandler.openUri("https://patreon.com/weishu")
                },
            )
        }
    }
}

@Composable
private fun InfoCard(
    systemStatus: KernelStatus,
    systemInfo: HomeSystemInfo,
    isSimpleMode: Boolean,
) {
    val managersList = systemInfo.managersList

    SegmentedColumn(
        title = stringResource(R.string.home_version_info),
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)
    ) {
        item {
            SettingsBaseWidget(
                iconPlaceholder = false,
                title = stringResource(R.string.home_device_model),
                description = systemInfo.deviceModel,
            )
        }

        item {
            SettingsBaseWidget(
                iconPlaceholder = false,
                title = stringResource(R.string.home_kernel),
                description = systemInfo.kernelRelease,
            )
        }

        item(
            visible = !isSimpleMode
        ) {
            SettingsBaseWidget(
                iconPlaceholder = false,
                title = stringResource(R.string.home_android_version),
                description = systemInfo.androidVersion,
            )
        }


        item(
            visible = systemStatus.isValid
        ) {
            SettingsBaseWidget(
                iconPlaceholder = false,
                title = stringResource(R.string.home_kernel_version),
                description = systemStatus.ksuFullVersion.orEmpty(),
            )
        }

        item {
            SettingsBaseWidget(
                iconPlaceholder = false,
                title = stringResource(R.string.home_manager_version),
                description = "${systemInfo.managerVersion.first} (${systemInfo.managerVersion.second}/${systemInfo.managerVersion.third})",
            )
        }

        item(
            visible = !isSimpleMode && systemInfo.susfsEnabled && systemInfo.susfsVersion.isNotEmpty()
        ) {
            SettingsBaseWidget(
                iconPlaceholder = false,
                title = stringResource(R.string.home_susfs_version),
                description = systemInfo.susfsVersion,
            )
        }
    }

    SegmentedColumn(
        title = stringResource(R.string.home_status_info),
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)
    ) {
        item {
            SettingsBaseWidget(
                iconPlaceholder = false,
                title = stringResource(R.string.home_selinux_status),
                description = systemInfo.selinuxStatus,
            )
        }

        item {
            val seccompDisplay = when (systemInfo.seccompStatus) {
                -1 -> stringResource(R.string.seccomp_status_not_supported)
                0 -> stringResource(R.string.seccomp_status_disabled)
                1 -> stringResource(R.string.seccomp_status_strict)
                2 -> stringResource(R.string.seccomp_status_filter)
                else -> stringResource(R.string.seccomp_status_unknown)
            }

            SettingsBaseWidget(
                iconPlaceholder = false,
                title = stringResource(R.string.home_seccomp_status),
                description = seccompDisplay,
            )
        }

        item(
            visible = !isSimpleMode && managersList != null
        ) {
            val signatureMap =
                managersList?.managers.orEmpty().groupBy { it.signatureIndex }
            val managersText = buildString {
                signatureMap.toSortedMap().forEach { (signatureIndex, managers) ->
                    append(managers.joinToString(", ") { "UID: ${it.uid}" })
                    append(" ")
                    append(
                        when (signatureIndex) {
                            0 -> "(${stringResource(R.string.app_name)})"
                            255 -> "(${stringResource(R.string.dynamic_managerature)})"
                            else -> if (signatureIndex >= 1) "(${
                                stringResource(
                                    R.string.signature_index,
                                    signatureIndex
                                )
                            })" else "(${stringResource(R.string.unknown_signature)})"
                        }
                    )
                    append(" | ")
                }
            }.trimEnd(' ', '|')

            SettingsBaseWidget(
                iconPlaceholder = false,
                title = stringResource(R.string.multi_manager_list),
                description = managersText.ifEmpty { stringResource(R.string.no_active_manager) },
            )
        }

        item(
            visible = !isSimpleMode && systemStatus.isValid
        ) {
            SettingsBaseWidget(
                iconPlaceholder = false,
                title = stringResource(R.string.home_hook_type),
                description = systemStatus.hookType,
            )
        }

        item(
            visible = !isSimpleMode && systemInfo.zygiskImplement.isNotEmpty() && systemInfo.zygiskImplement != "None"
        ) {
            SettingsBaseWidget(
                iconPlaceholder = false,
                title = stringResource(R.string.home_zygisk_implement),
                description = systemInfo.zygiskImplement,
            )
        }

        item(
            visible = !isSimpleMode && systemInfo.metaModuleImplement.isNotEmpty() && systemInfo.metaModuleImplement != "None"
        ) {
            SettingsBaseWidget(
                iconPlaceholder = false,
                title = stringResource(R.string.home_meta_module_implement),
                description = systemInfo.metaModuleImplement,
            )
        }
    }
}
