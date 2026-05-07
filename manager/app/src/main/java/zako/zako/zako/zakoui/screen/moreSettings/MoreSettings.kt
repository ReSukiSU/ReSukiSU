package zako.zako.zako.zakoui.screen.moreSettings

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.resukisu.resukisu.R
import com.resukisu.resukisu.ui.MainActivity
import com.resukisu.resukisu.ui.component.ConfirmResult
import com.resukisu.resukisu.ui.component.rememberConfirmDialog
import com.resukisu.resukisu.ui.component.rememberCustomDialog
import com.resukisu.resukisu.ui.component.settings.AppBackButton
import com.resukisu.resukisu.ui.component.settings.SettingsBaseWidget
import com.resukisu.resukisu.ui.component.settings.SettingsDropdownWidget
import com.resukisu.resukisu.ui.component.settings.SettingsJumpPageWidget
import com.resukisu.resukisu.ui.component.settings.SettingsSwitchWidget
import com.resukisu.resukisu.ui.component.settings.SplicedColumnGroup
import com.resukisu.resukisu.ui.component.settings.SplicedGroupScope
import com.resukisu.resukisu.ui.navigation.LocalNavigator
import com.resukisu.resukisu.ui.theme.BackgroundManager
import com.resukisu.resukisu.ui.theme.CardConfig
import com.resukisu.resukisu.ui.theme.ThemeColors
import com.resukisu.resukisu.ui.theme.ThemeConfig
import com.resukisu.resukisu.ui.theme.blurEffect
import com.resukisu.resukisu.ui.theme.blurSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import zako.zako.zako.zakoui.screen.moreSettings.component.ColorCircle
import zako.zako.zako.zakoui.screen.moreSettings.component.LanguageSelectionDialog
import zako.zako.zako.zakoui.screen.moreSettings.component.MoreSettingsDialogs
import zako.zako.zako.zakoui.screen.moreSettings.state.MoreSettingsState
import zako.zako.zako.zakoui.screen.moreSettings.util.LocaleHelper
import java.io.File
import kotlin.math.roundToInt

// TODO Rename this screen to ThemeSettingsScreen, and drop SELinux config, rewrite dynamic manager
@SuppressLint(
    "LocalContextConfigurationRead", "LocalContextResourcesRead", "ObsoleteSdkInt",
    "RestrictedApi"
)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MoreSettingsScreen() {
    // 顶部滚动行为
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val systemIsDark = isSystemInDarkTheme()

    // 创建设置状态管理器
    val settingsState = remember { MoreSettingsState(context, prefs, systemIsDark) }
    val activity = LocalActivity.current as MainActivity
    val settingsHandlers = remember { MoreSettingsHandlers(activity, prefs, settingsState) }

    // TODO Add In app crop as fallback
    // 图片选择器
    val cropImageLauncher = rememberLauncherForActivityResult(
        object : ActivityResultContract<Uri, Uri?>() {
            override fun createIntent(context: Context, input: Uri): Intent {
                val tempFile = File(context.cacheDir, "background_crop_cache").apply {
                    parentFile?.mkdirs()
                    delete()
                    createNewFile()
                    deleteOnExit()
                }

                context.contentResolver.openInputStream(input)?.use { inputStream ->
                    tempFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                val tempUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    tempFile
                )

                return Intent("com.android.camera.action.CROP").apply {
                    setDataAndType(tempUri, "image/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    putExtra("crop", "true")

                    val displayMetrics = context.resources.displayMetrics
                    val screenWidth = displayMetrics.widthPixels
                    val screenHeight = displayMetrics.heightPixels

                    putExtra("aspectX", screenWidth)
                    putExtra("aspectY", screenHeight)
                    putExtra("outputX", screenWidth)
                    putExtra("outputY", screenHeight)

                    putExtra("return-data", false)

                    putExtra(MediaStore.EXTRA_OUTPUT, tempUri)
                }
            }

            override fun parseResult(
                resultCode: Int,
                intent: Intent?
            ): Uri? {
                return intent?.data
            }
        }
    ) { uri: Uri? ->
        uri?.let {
            settingsHandlers.handleCustomBackground(it)
        }
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            cropImageLauncher.launch(uri)
        }
    }

    // 初始化设置
    LaunchedEffect(Unit) {
        settingsHandlers.initializeSettings()
    }

    // 各种设置对话框
    MoreSettingsDialogs(
        state = settingsState,
        handlers = settingsHandlers
    )

    val navigator = LocalNavigator.current

    LaunchedEffect(Unit) {
        scrollBehavior.state.heightOffset = scrollBehavior.state.heightOffsetLimit
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                modifier = Modifier
                    .blurEffect(),
                title = {
                    Text(
                        text = stringResource(R.string.more_settings)
                    )
                },
                navigationIcon = {
                    AppBackButton(
                        onClick = {
                            navigator.pop()
                        }
                    )
                },
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
                ),
                windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp)),
                scrollBehavior = scrollBehavior
            )
        },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .blurSource()
        ) {
            item {
                Spacer(modifier = Modifier.height(paddingValues.calculateTopPadding()))
            }

            item {
                // 外观设置
                AppearanceSettings(
                    state = settingsState,
                    handlers = settingsHandlers,
                    pickImageLauncher = pickImageLauncher,
                    coroutineScope = coroutineScope
                )
            }

            item {
                // Predictive Back Settings
                val transition = LocalNavAnimatedContentScope.current.transition
                val uiState by activity.settingsStateFlow.collectAsStateWithLifecycle()

                val predictiveBackAnimationDialog = rememberCustomDialog { dismiss ->
                    PredictiveBackAnimationDialog(
                        currentAnimation = uiState.predictiveBackAnimation,
                        onDismiss = dismiss,
                        onSelect = { animation ->
                            // Hey Google
                            // Why you keep playing the animation even we are already play completed?

                            // This is very dirty, We are using RestrictedApi, but we don't have other choice
                            transition.setPlaytimeAfterInitialAndTargetStateEstablished(
                                transition.targetState,
                                transition.targetState,
                                transition.playTimeNanos
                            )

                            activity.settingsStateFlow.value =
                                activity.settingsStateFlow.value.copy(
                                    predictiveBackAnimation = animation
                                )

                            prefs.edit(commit = true) {
                                putString("predictive_back_animation", animation.value)
                            }

                            dismiss()
                        }
                    )
                }

                val predictiveBackExitDirectionDialog = rememberCustomDialog { dismiss ->
                    PredictiveBackExitDirectionDialog(
                        currentDirection = uiState.predictiveBackExitDirection,
                        onDismiss = dismiss,
                        onSelect = { direction ->
                            activity.settingsStateFlow.value =
                                activity.settingsStateFlow.value.copy(
                                    predictiveBackExitDirection = direction
                                )

                            prefs.edit(commit = true) {
                                putString("predictive_back_exit_direction", direction.value)
                            }

                            dismiss()
                        }
                    )
                }

                SplicedColumnGroup(
                    title = stringResource(R.string.predictive_back_settings)
                ) {
                    item { PredictiveBackAnimationWidget(uiState) { predictiveBackAnimationDialog.show() } }
                    item(
                        visible = uiState.predictiveBackAnimation == MainActivity.PredictiveBackAnimation.Scale ||
                                uiState.predictiveBackAnimation == MainActivity.PredictiveBackAnimation.AOSP
                    ) {
                        PredictiveBackAnimationDirectionWidget(uiState) { predictiveBackExitDirectionDialog.show() }
                    }
                }
            }

            item {
                // 自定义设置
                CustomizationSettings(
                    state = settingsState,
                    handlers = settingsHandlers
                )
            }

            item {
                // 系统导航栏padding计算
                Spacer(modifier = Modifier.height(paddingValues.calculateBottomPadding()))
            }
        }
    }
}

@Composable
fun PredictiveBackAnimationWidget(
    uiState: MainActivity.SettingsState,
    onClick: () -> Unit
) {
    SettingsBaseWidget(
        icon = Icons.Rounded.Animation,
        title = stringResource(R.string.predictive_back_animation),
        description = when (uiState.predictiveBackAnimation) {
            MainActivity.PredictiveBackAnimation.None -> stringResource(R.string.predictive_back_animation_none)
            MainActivity.PredictiveBackAnimation.AOSP -> stringResource(R.string.predictive_back_animation_aosp)
            MainActivity.PredictiveBackAnimation.MIUIX -> stringResource(R.string.predictive_back_animation_miuix)
            MainActivity.PredictiveBackAnimation.Scale -> stringResource(R.string.predictive_back_animation_scale)
            MainActivity.PredictiveBackAnimation.KernelSUClassic -> stringResource(R.string.predictive_back_animation_ksu_classic)
        },
        onClick = {
            onClick()
        }
    ) {}
}

@Composable
fun PredictiveBackAnimationDirectionWidget(
    uiState: MainActivity.SettingsState,
    onClick: () -> Unit
) {
    SettingsBaseWidget(
        icon = Icons.Rounded.SwapHoriz,
        title = stringResource(R.string.predictive_back_exit_direction),
        description = when (uiState.predictiveBackExitDirection) {
            MainActivity.PredictiveBackExitDirection.FOLLOW_GESTURE -> stringResource(R.string.predictive_back_exit_direction_follow_gesture)
            MainActivity.PredictiveBackExitDirection.ALWAYS_RIGHT -> stringResource(R.string.predictive_back_exit_direction_always_right)
            MainActivity.PredictiveBackExitDirection.ALWAYS_LEFT -> stringResource(R.string.predictive_back_exit_direction_always_left)
        },
        onClick = {
            onClick()
        }
    ) {}
}

@Composable
fun PredictiveBackAnimationDialog(
    currentAnimation: MainActivity.PredictiveBackAnimation,
    onDismiss: () -> Unit,
    onSelect: (MainActivity.PredictiveBackAnimation) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.predictive_back_animation_desc)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                MainActivity.PredictiveBackAnimation.entries.forEach { animation ->
                    val animationText = when (animation) {
                        MainActivity.PredictiveBackAnimation.None -> stringResource(R.string.predictive_back_animation_none)
                        MainActivity.PredictiveBackAnimation.AOSP -> stringResource(R.string.predictive_back_animation_aosp)
                        MainActivity.PredictiveBackAnimation.MIUIX -> stringResource(R.string.predictive_back_animation_miuix)
                        MainActivity.PredictiveBackAnimation.Scale -> stringResource(R.string.predictive_back_animation_scale)
                        MainActivity.PredictiveBackAnimation.KernelSUClassic -> stringResource(R.string.predictive_back_animation_ksu_classic)
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(animation) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (animation == currentAnimation),
                            onClick = { onSelect(animation) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(animationText)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

@Composable
fun PredictiveBackExitDirectionDialog(
    currentDirection: MainActivity.PredictiveBackExitDirection,
    onDismiss: () -> Unit,
    onSelect: (MainActivity.PredictiveBackExitDirection) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.predictive_back_exit_direction_desc)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                MainActivity.PredictiveBackExitDirection.entries.forEach { direction ->
                    val directionText = when (direction) {
                        MainActivity.PredictiveBackExitDirection.FOLLOW_GESTURE -> stringResource(R.string.predictive_back_exit_direction_follow_gesture)
                        MainActivity.PredictiveBackExitDirection.ALWAYS_RIGHT -> stringResource(R.string.predictive_back_exit_direction_always_right)
                        MainActivity.PredictiveBackExitDirection.ALWAYS_LEFT -> stringResource(R.string.predictive_back_exit_direction_always_left)
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(direction) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (direction == currentDirection),
                            onClick = { onSelect(direction) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(directionText)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

@Composable
private fun AppearanceSettings(
    state: MoreSettingsState,
    handlers: MoreSettingsHandlers,
    pickImageLauncher: ManagedActivityResultLauncher<String, Uri?>,
    coroutineScope: CoroutineScope
) {
    SplicedColumnGroup(title = stringResource(R.string.appearance_settings)) {
        item {
            // 语言设置
            LanguageSetting(state = state)
        }

        item {
            // 主题模式
            SettingsDropdownWidget(
                icon = Icons.Default.DarkMode,
                title = stringResource(R.string.theme_mode),
                items = state.themeOptions,
                selectedIndex = state.themeMode,
                onSelectedIndexChange = { index ->
                    handlers.handleThemeModeChange(index)
                }
            )
        }

        // TODO MonetCompat with Android S-, Choose System Seed Color or Custom background color
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            item {
                // 动态颜色开关
                SettingsSwitchWidget(
                    icon = Icons.Filled.ColorLens,
                    title = stringResource(R.string.dynamic_color_title),
                    description = stringResource(R.string.dynamic_color_summary),
                    checked = state.useDynamicColor,
                    onCheckedChange = handlers::handleDynamicColorChange
                )
            }
        }

        item(
            visible = !state.useDynamicColor || Build.VERSION.SDK_INT < Build.VERSION_CODES.S
        ) {
            // TODO ColorPicker seedColor
            // 主题色选择
            ThemeColorSelection(state = state)
        }

        item {
            // DPI 设置
            DpiSettings(state = state, handlers = handlers, coroutineScope = coroutineScope)
        }

        item {
            // 自定义背景设置
            CustomBackgroundSettings(
                state = state,
                handlers = handlers,
                pickImageLauncher = pickImageLauncher,
                coroutineScope = coroutineScope
            )
        }

        // TODO Add HazeConfig and unify hazeState management
    }
}

@Composable
private fun CustomizationSettings(
    state: MoreSettingsState,
    handlers: MoreSettingsHandlers
) {
    SplicedColumnGroup(title = stringResource(R.string.custom_settings)) {
        item {
            // 图标切换
            SettingsSwitchWidget(
                icon = Icons.Default.Android,
                title = stringResource(R.string.icon_switch_title),
                description = stringResource(R.string.icon_switch_summary),
                checked = state.useAltIcon,
                onCheckedChange = handlers::handleIconChange
            )
        }

        item {
            // 显示更多模块信息
            SettingsSwitchWidget(
                icon = Icons.Filled.Info,
                title = stringResource(R.string.show_more_module_info),
                description = stringResource(R.string.show_more_module_info_summary),
                checked = state.showMoreModuleInfo,
                onCheckedChange = handlers::handleShowMoreModuleInfoChange
            )
        }

        item {
            // 简洁模式开关
            SettingsSwitchWidget(
                icon = Icons.Filled.Brush,
                title = stringResource(R.string.simple_mode),
                description = stringResource(R.string.simple_mode_summary),
                checked = state.isSimpleMode,
                onCheckedChange = handlers::handleSimpleModeChange
            )
        }

        hideOptionsSettings(state = state, handlers = handlers)
    }
}

private fun SplicedGroupScope.hideOptionsSettings(
    state: MoreSettingsState,
    handlers: MoreSettingsHandlers
) {
    item {
        // 隐藏内核版本号
        SettingsSwitchWidget(
            icon = Icons.Filled.VisibilityOff,
            title = stringResource(R.string.hide_kernel_kernelsu_version),
            description = stringResource(R.string.hide_kernel_kernelsu_version_summary),
            checked = state.isHideVersion,
            onCheckedChange = handlers::handleHideVersionChange
        )
    }

    item {
        // 隐藏模块数量等信息
        SettingsSwitchWidget(
            icon = Icons.Filled.VisibilityOff,
            title = stringResource(R.string.hide_other_info),
            description = stringResource(R.string.hide_other_info_summary),
            checked = state.isHideOtherInfo,
            onCheckedChange = handlers::handleHideOtherInfoChange
        )
    }

    item {
        // SuSFS 状态信息
        SettingsSwitchWidget(
            icon = Icons.Filled.VisibilityOff,
            title = stringResource(R.string.hide_susfs_status),
            description = stringResource(R.string.hide_susfs_status_summary),
            checked = state.isHideSusfsStatus,
            onCheckedChange = handlers::handleHideSusfsStatusChange
        )
    }

    item {
        // Zygisk 实现状态信息
        SettingsSwitchWidget(
            icon = Icons.Filled.VisibilityOff,
            title = stringResource(R.string.hide_zygisk_implement),
            description = stringResource(R.string.hide_zygisk_implement_summary),
            checked = state.isHideZygiskImplement,
            onCheckedChange = handlers::handleHideZygiskImplementChange
        )
    }

    item {
        // 元模块实现状态信息
        SettingsSwitchWidget(
            icon = Icons.Filled.VisibilityOff,
            title = stringResource(R.string.hide_meta_module_implement),
            description = stringResource(R.string.hide_meta_module_implement_summary),
            checked = state.isHideMetaModuleImplement,
            onCheckedChange = handlers::handleHideMetaModuleImplementChange
        )
    }

    // KPM 状态信息隐藏
    item {
        SettingsSwitchWidget(
            icon = Icons.Filled.VisibilityOff,
            title = stringResource(R.string.show_kpm_info),
            description = stringResource(R.string.show_kpm_info_summary),
            checked = state.isShowKpmInfo,
            onCheckedChange = handlers::handleShowKpmInfoChange
        )
    }

    item {
        // 隐藏链接信息
        SettingsSwitchWidget(
            icon = Icons.Filled.VisibilityOff,
            title = stringResource(R.string.hide_link_card),
            description = stringResource(R.string.hide_link_card_summary),
            checked = state.isHideLinkCard,
            onCheckedChange = handlers::handleHideLinkCardChange
        )
    }

    item {
        // 隐藏标签行
        SettingsSwitchWidget(
            icon = Icons.Filled.VisibilityOff,
            title = stringResource(R.string.hide_tag_card),
            description = stringResource(R.string.hide_tag_card_summary),
            checked = state.isHideTagRow,
            onCheckedChange = handlers::handleHideTagRowChange
        )
    }
}

@Composable
private fun ThemeColorSelection(state: MoreSettingsState) {
    SettingsBaseWidget(
        icon = Icons.Default.Palette,
        title = stringResource(R.string.theme_color),
        description = when (ThemeConfig.currentTheme) {
            is ThemeColors.Green -> stringResource(R.string.color_green)
            is ThemeColors.Purple -> stringResource(R.string.color_purple)
            is ThemeColors.Orange -> stringResource(R.string.color_orange)
            is ThemeColors.Pink -> stringResource(R.string.color_pink)
            is ThemeColors.Gray -> stringResource(R.string.color_gray)
            is ThemeColors.Yellow -> stringResource(R.string.color_yellow)
            else -> stringResource(R.string.color_default)
        },
        onClick = { state.showThemeColorDialog = true },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 8.dp)
        ) {
            val theme = ThemeConfig.currentTheme
            val isDark = isSystemInDarkTheme()

            ColorCircle(
                color = if (isDark) theme.primaryDark else theme.primaryLight,
                isSelected = false,
                modifier = Modifier.padding(horizontal = 2.dp)
            )
            ColorCircle(
                color = if (isDark) theme.secondaryDark else theme.secondaryLight,
                isSelected = false,
                modifier = Modifier.padding(horizontal = 2.dp)
            )
            ColorCircle(
                color = if (isDark) theme.tertiaryDark else theme.tertiaryLight,
                isSelected = false,
                modifier = Modifier.padding(horizontal = 2.dp)
            )
        }
    }
}

@Composable
private fun DpiSettings(
    state: MoreSettingsState,
    handlers: MoreSettingsHandlers,
    coroutineScope: CoroutineScope
) {
    SettingsBaseWidget(
        icon = Icons.Default.FormatSize,
        title = stringResource(R.string.app_dpi_title),
        description = stringResource(R.string.app_dpi_summary),
        onClick = {},
    ) {
        Text(
            text = handlers.getDpiFriendlyName(state.tempDpi),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }

    // DPI 滑动条和控制
    DpiSliderControls(state = state, handlers = handlers, coroutineScope = coroutineScope)
}

@Composable
private fun DpiSliderControls(
    state: MoreSettingsState,
    handlers: MoreSettingsHandlers,
    coroutineScope: CoroutineScope
) {
    val confirmDialog = rememberConfirmDialog()
    val dpiConfirmTitle = stringResource(R.string.dpi_confirm_title)
    val dpiConfirmMessage =
        stringResource(R.string.dpi_confirm_message, state.currentDpi, state.tempDpi)
    val confirmText = stringResource(R.string.confirm)
    val cancelText = stringResource(R.string.cancel)

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        val sliderValue by animateFloatAsState(
            targetValue = state.tempDpi.toFloat(),
            label = "DPI Slider Animation"
        )

        Slider(
            value = sliderValue,
            onValueChange = { newValue ->
                state.tempDpi = newValue.toInt()
                state.isDpiCustom = !state.dpiPresets.containsValue(state.tempDpi)
            },
            valueRange = 160f..600f,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )

        // DPI 预设按钮行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            state.dpiPresets.forEach { (name, dpi) ->
                val isSelected = state.tempDpi == dpi
                val buttonColor = if (isSelected)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(buttonColor)
                        .clickable {
                            state.tempDpi = dpi
                            state.isDpiCustom = false
                        }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelected)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Text(
            text = if (state.isDpiCustom)
                "${stringResource(R.string.dpi_size_custom)}: ${state.tempDpi}"
            else
                "${handlers.getDpiFriendlyName(state.tempDpi)}: ${state.tempDpi}",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp)
        )

        Button(
            onClick = {
                coroutineScope.launch {
                    val confirmResult = confirmDialog.awaitConfirm(
                        title = dpiConfirmTitle,
                        content = dpiConfirmMessage,
                        confirm = confirmText,
                        dismiss = cancelText
                    )

                    if (confirmResult != ConfirmResult.Confirmed) return@launch

                    handlers.handleDpiApply()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            enabled = state.tempDpi != state.currentDpi
        ) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.dpi_apply_settings))
        }
    }
}

@Composable
private fun CustomBackgroundSettings(
    state: MoreSettingsState,
    handlers: MoreSettingsHandlers,
    pickImageLauncher: ManagedActivityResultLauncher<String, Uri?>,
    coroutineScope: CoroutineScope
) {
    // TODO Portrait/Landscape wallpaper split
    // 自定义背景开关
    SettingsSwitchWidget(
        icon = Icons.Filled.Wallpaper,
        title = stringResource(id = R.string.settings_custom_background),
        description = stringResource(id = R.string.settings_custom_background_summary),
        checked = state.isCustomBackgroundEnabled,
        onCheckedChange = { isChecked ->
            if (isChecked) {
                pickImageLauncher.launch("image/*")
            } else {
                handlers.handleRemoveCustomBackground()
            }
        }
    )

    // 透明度和亮度调节
    AnimatedVisibility(
        visible = ThemeConfig.customBackgroundUri != null,
        enter = fadeIn() + slideInVertically(),
        exit = fadeOut() + slideOutVertically()
    ) {
        BackgroundAdjustmentControls(
            state = state,
            handlers = handlers,
            coroutineScope = coroutineScope
        )
    }
}

@Composable
private fun BackgroundAdjustmentControls(
    state: MoreSettingsState,
    handlers: MoreSettingsHandlers,
    coroutineScope: CoroutineScope
) {
    val context = LocalContext.current

    Column {
        Column(modifier = Modifier
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp)) {
            AlphaSlider(state = state, handlers = handlers, coroutineScope = coroutineScope)
            DimSlider(state = state, handlers = handlers, coroutineScope = coroutineScope)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SettingsSwitchWidget(
                icon = Icons.Filled.BlurOn,
                title = stringResource(id = R.string.settings_config_enable_blur),
                description = stringResource(id = R.string.settings_config_enable_blur_summary),
                checked = ThemeConfig.isEnableBlur,
                onCheckedChange = { isChecked ->
                    BackgroundManager.saveEnableBlur(context, isChecked)
                }
            )
            AnimatedVisibility(
                visible = ThemeConfig.isEnableBlur,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                SettingsSwitchWidget(
                    icon = Icons.Filled.Draw,
                    title = stringResource(id = R.string.settings_exp_draw_background_to_blur),
                    description = stringResource(id = R.string.settings_exp_draw_background_to_blur_description),
                    isError = true,
                    checked = ThemeConfig.isEnableBlurExp,
                    onCheckedChange = { isChecked ->
                        BackgroundManager.saveEnableBlurExp(context, isChecked)
                    }
                )
            }

            AnimatedVisibility(
                visible = state.useDynamicColor,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                SettingsSwitchWidget(
                    icon = Icons.Filled.FormatColorFill,
                    title = stringResource(id = R.string.settings_config_use_custom_background_seed_color),
                    description = stringResource(id = R.string.settings_config_use_custom_background_seed_color_summary),
                    checked = ThemeConfig.isUseBackgroundSeedColor,
                    onCheckedChange = { isChecked ->
                        BackgroundManager.saveUseBackgroundSeedColor(context, isChecked)
                    }
                )
            }
        }
        SettingsSwitchWidget(
            icon = Icons.Filled.Contrast,
            title = stringResource(id = R.string.settings_custom_enable_high_contrast),
            description = stringResource(id = R.string.settings_custom_enable_high_contrast_summary),
            checked = ThemeConfig.isHighContrastMode,
            onCheckedChange = { isChecked ->
                BackgroundManager.saveEnableHighContrastMode(context, isChecked)
            }
        )
    }
}

@Composable
private fun AlphaSlider(
    state: MoreSettingsState,
    handlers: MoreSettingsHandlers,
    coroutineScope: CoroutineScope
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 4.dp)
    ) {
        Icon(
            Icons.Filled.Opacity,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.settings_card_alpha),
            style = MaterialTheme.typography.titleSmall
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "${(state.cardAlpha * 100).roundToInt()}%",
            style = MaterialTheme.typography.labelMedium,
        )
    }

    val alphaSliderValue by animateFloatAsState(
        targetValue = state.cardAlpha,
        label = "Alpha Slider Animation"
    )

    Slider(
        value = alphaSliderValue,
        onValueChange = { newValue ->
            handlers.handleCardAlphaChange(newValue)
        },
        onValueChangeFinished = {
            coroutineScope.launch(Dispatchers.IO) {
                CardConfig.save(handlers.activity)
            }
        },
        valueRange = 0f..1f,
        colors = SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    )
}

@Composable
private fun DimSlider(
    state: MoreSettingsState,
    handlers: MoreSettingsHandlers,
    coroutineScope: CoroutineScope
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
    ) {
        Icon(
            Icons.Filled.LightMode,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.settings_background_dim),
            style = MaterialTheme.typography.titleSmall
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "${(state.backgroundDim * 100).roundToInt()}%",
            style = MaterialTheme.typography.labelMedium,
        )
    }

    val dimSliderValue by animateFloatAsState(
        targetValue = state.backgroundDim,
        label = "Dim Slider Animation"
    )

    Slider(
        value = dimSliderValue,
        onValueChange = { newValue ->
            handlers.handleBackgroundDimChange(newValue)
        },
        onValueChangeFinished = {
            coroutineScope.launch(Dispatchers.IO) {
                CardConfig.save(handlers.activity)
            }
        },
        valueRange = 0f..1f,
        colors = SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    )
}

@Composable
private fun LanguageSetting(state: MoreSettingsState) {
    val context = LocalContext.current
    val language = stringResource(id = R.string.settings_language)
    val languageSystemDefault = stringResource(R.string.language_system_default)

    // Compute display name based on current app locale
    val currentLanguageDisplay = remember(state.currentAppLocale) {
        val locale = state.currentAppLocale
        if (locale != null) {
            locale.getDisplayName(locale)
        } else {
            languageSystemDefault
        }
    }

    SettingsJumpPageWidget(
        icon = Icons.Filled.Translate,
        title = language,
        description = currentLanguageDisplay,
        onClick = { state.showLanguageDialog = true }
    )

    // Language Selection Dialog
    if (state.showLanguageDialog) {
        LanguageSelectionDialog(
            onLanguageSelected = { newLocale ->
                // Update local state immediately
                state.currentAppLocale = LocaleHelper.getCurrentAppLocale(context)
                // Apply locale change immediately for Android < 13
                LocaleHelper.restartActivity(context)
            },
            onDismiss = { state.showLanguageDialog = false }
        )
    }
}