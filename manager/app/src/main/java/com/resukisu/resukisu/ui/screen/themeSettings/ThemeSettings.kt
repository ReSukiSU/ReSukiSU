package com.resukisu.resukisu.ui.screen.themeSettings

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DesignServices
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.resukisu.resukisu.R
import com.resukisu.resukisu.ksuApp
import com.resukisu.resukisu.ui.component.ConfirmResult
import com.resukisu.resukisu.ui.component.KeyPointSlider
import com.resukisu.resukisu.ui.component.rememberConfirmDialog
import com.resukisu.resukisu.ui.component.settings.AppBackButton
import com.resukisu.resukisu.ui.component.settings.SegmentedColumn
import com.resukisu.resukisu.ui.component.settings.SegmentedColumnScope
import com.resukisu.resukisu.ui.component.settings.SettingsBaseWidget
import com.resukisu.resukisu.ui.component.settings.SettingsChooseDialog
import com.resukisu.resukisu.ui.component.settings.SettingsChooseWidget
import com.resukisu.resukisu.ui.component.settings.SettingsJumpPageWidget
import com.resukisu.resukisu.ui.component.settings.SettingsSwitchWidget
import com.resukisu.resukisu.ui.navigation.LocalNavigator
import com.resukisu.resukisu.ui.screen.themeSettings.component.LanguageSelectionDialog
import com.resukisu.resukisu.ui.screen.themeSettings.component.ThemeSettingsDialogs
import com.resukisu.resukisu.ui.screen.themeSettings.crop.BackgroundCropActivity
import com.resukisu.resukisu.ui.screen.themeSettings.util.restartActivity
import com.resukisu.resukisu.ui.theme.BackgroundManager
import com.resukisu.resukisu.ui.theme.CardConfig
import com.resukisu.resukisu.ui.theme.ThemeConfig
import com.resukisu.resukisu.ui.theme.blurEffect
import com.resukisu.resukisu.ui.theme.blurSource
import com.resukisu.resukisu.ui.theme.renderBackgroundBlur
import com.resukisu.resukisu.ui.viewmodel.HomeUiState
import com.resukisu.resukisu.ui.viewmodel.HomeViewModel
import com.resukisu.resukisu.ui.viewmodel.ModuleUiState
import com.resukisu.resukisu.ui.viewmodel.ModuleViewModel
import com.resukisu.resukisu.ui.viewmodel.PredictiveBackAnimation
import com.resukisu.resukisu.ui.viewmodel.PredictiveBackExitDirection
import com.resukisu.resukisu.ui.viewmodel.SettingsUiState
import com.resukisu.resukisu.ui.viewmodel.SettingsViewModel
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

@SuppressLint(
    "LocalContextConfigurationRead", "LocalContextResourcesRead", "ObsoleteSdkInt",
    "RestrictedApi"
)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ThemeSettingsScreen() {
    // 顶部滚动行为
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val systemIsDark = isSystemInDarkTheme()

    // 创建设置状态管理器
    val settingsViewModel = viewModel<SettingsViewModel>(viewModelStoreOwner = ksuApp)
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()

    val homeViewModel = viewModel<HomeViewModel>(viewModelStoreOwner = ksuApp)
    val homeUiState by homeViewModel.uiState.collectAsStateWithLifecycle()

    val moduleViewModel = viewModel<ModuleViewModel>(viewModelStoreOwner = ksuApp)
    val moduleUiState by moduleViewModel.uiState.collectAsStateWithLifecycle()

    // Image selection and cropping
    var pendingBackgroundImageUri by remember { mutableStateOf<Uri?>(null) }
    var pendingExternalCropOutputUri by remember { mutableStateOf<Uri?>(null) }
    var showCropMethodDialog by remember { mutableStateOf(false) }

    val externalCropUnavailable = stringResource(R.string.background_external_crop_unavailable)
    val backgroundCropFailed = stringResource(R.string.background_crop_failed)

    val uCropLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { data ->
                UCrop.getOutput(data)?.let {
                    settingsViewModel.handleCustomBackground(context, it)
                }
            }
        } else if (result.resultCode == UCrop.RESULT_ERROR) {
            Toast.makeText(
                context,
                backgroundCropFailed,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val externalCropLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val outputUri = pendingExternalCropOutputUri ?: result.data?.data
            outputUri?.let {
                settingsViewModel.handleCustomBackground(context, it)
            }
        }
        pendingExternalCropOutputUri = null
    }

    val launchInAppCrop = { sourceUri: Uri ->
        val outputUri = createBackgroundCropOutputUri(context, "background_crop_ucrop")
        val (screenWidth, screenHeight) = context.backgroundCropSize()
        val intent = Intent(context, BackgroundCropActivity::class.java).apply {
            putExtra(UCrop.EXTRA_INPUT_URI, sourceUri)
            putExtra(UCrop.EXTRA_OUTPUT_URI, outputUri)
            putExtra(UCrop.EXTRA_ASPECT_RATIO_X, screenWidth.toFloat())
            putExtra(UCrop.EXTRA_ASPECT_RATIO_Y, screenHeight.toFloat())
            putExtra(UCrop.EXTRA_MAX_SIZE_X, screenWidth)
            putExtra(UCrop.EXTRA_MAX_SIZE_Y, screenHeight)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }

        uCropLauncher.launch(intent)
    }

    val launchExternalCrop = { sourceUri: Uri ->
        val uri = createBackgroundCropOutputUri(context, "background_crop_external")
        context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }

        val (screenWidth, screenHeight) = context.backgroundCropSize()
        val intent = Intent("com.android.camera.action.CROP").apply {
            setDataAndType(uri, "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            putExtra("crop", "true")
            putExtra("aspectX", screenWidth)
            putExtra("aspectY", screenHeight)
            putExtra("outputX", screenWidth)
            putExtra("outputY", screenHeight)
            putExtra("return-data", false)
            putExtra(MediaStore.EXTRA_OUTPUT, uri)
        }

        try {
            pendingExternalCropOutputUri = uri
            externalCropLauncher.launch(intent)
        } catch (t: Throwable) {
            Log.e("ThemeSettings", "External Crop failed, fallback to in-app crop", t)
            pendingExternalCropOutputUri = null
            Toast.makeText(
                context,
                externalCropUnavailable,
                Toast.LENGTH_SHORT
            ).show()
            launchInAppCrop(sourceUri)
        }
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            pendingBackgroundImageUri = it
            showCropMethodDialog = true
        }
    }

    SettingsChooseDialog(
        show = showCropMethodDialog,
        title = stringResource(R.string.background_crop_method_title),
        items = listOf(
            stringResource(R.string.background_crop_method_in_app),
            stringResource(R.string.background_crop_method_external)
        ),
        selectedIndex = 0,
        onDismiss = {
            showCropMethodDialog = false
            pendingBackgroundImageUri = null
        },
        onSelectedIndexChange = { index ->
            pendingBackgroundImageUri?.let { sourceUri ->
                when (index) {
                    0 -> launchInAppCrop(sourceUri)
                    1 -> launchExternalCrop(sourceUri)
                }
            }
        }
    )

    // 初始化设置
    LaunchedEffect(Unit) {
        settingsViewModel.initialize(context, systemIsDark)
    }

    // 各种设置对话框
    ThemeSettingsDialogs(
        state = settingsState,
        viewModel = settingsViewModel
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
                        text = stringResource(R.string.theme_settings)
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
                    viewModel = settingsViewModel,
                    pickImageLauncher = pickImageLauncher,
                    coroutineScope = coroutineScope
                )
            }

            item {
                // Predictive Back Settings
                val transition = LocalNavAnimatedContentScope.current.transition

                SegmentedColumn(
                    title = stringResource(R.string.predictive_back_settings)
                ) {
                    item {
                        PredictiveBackAnimationWidget(settingsState) { animation ->
                            // Hey Google
                            // Why you keep playing the animation even we are already play completed?

                            // This is very dirty, We are using RestrictedApi, but we don't have other choice
                            transition.setPlaytimeAfterInitialAndTargetStateEstablished(
                                transition.targetState,
                                transition.targetState,
                                transition.playTimeNanos
                            )

                            settingsViewModel.setPredictiveBackAnimation(context, animation)
                        }
                    }
                    item(
                        visible = settingsState.predictiveBackAnimation == PredictiveBackAnimation.Scale ||
                                settingsState.predictiveBackAnimation == PredictiveBackAnimation.AOSP
                    ) {
                        PredictiveBackAnimationDirectionWidget(settingsState) { direction ->
                            settingsViewModel.setPredictiveBackExitDirection(context, direction)
                        }
                    }
                }
            }

            item {
                // 自定义设置
                CustomizationSettings(
                    homeUiState = homeUiState,
                    homeViewModel = homeViewModel,
                    moduleUiState = moduleUiState,
                    moduleViewModel = moduleViewModel,
                    settingsUiState = settingsState,
                    settingsViewModel = settingsViewModel
                )
            }

            item {
                // 系统导航栏padding计算
                Spacer(modifier = Modifier.height(paddingValues.calculateBottomPadding()))
            }
        }
    }
}

private fun PaletteStyle.displayName(): String = when (this) {
    PaletteStyle.TonalSpot -> "Tonal Spot"
    PaletteStyle.Neutral -> "Neutral"
    PaletteStyle.Vibrant -> "Vibrant"
    PaletteStyle.Expressive -> "Expressive"
    PaletteStyle.Rainbow -> "Rainbow"
    PaletteStyle.FruitSalad -> "Fruit Salad"
    PaletteStyle.Monochrome -> "Monochrome"
    PaletteStyle.Fidelity -> "Fidelity"
    PaletteStyle.Content -> "Content"
}

private fun ColorSpec.SpecVersion.displayName(): String = when (this) {
    ColorSpec.SpecVersion.SPEC_2021 -> "Spec 2021"
    ColorSpec.SpecVersion.SPEC_2025 -> "Spec 2025"
}

private fun Context.backgroundCropSize(): Pair<Int, Int> {
    val displayMetrics = resources.displayMetrics
    return displayMetrics.widthPixels to displayMetrics.heightPixels
}

private fun createBackgroundCropOutputUri(context: Context, prefix: String): Uri {
    val outputFile = File(context.cacheDir, "${prefix}_${System.currentTimeMillis()}.jpg").apply {
        parentFile?.mkdirs()
        delete()
        createNewFile()
        deleteOnExit()
    }

    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        outputFile
    )
}

@Composable
fun PredictiveBackAnimationWidget(
    uiState: SettingsUiState,
    onSelect: (PredictiveBackAnimation) -> Unit
) {
    SettingsChooseWidget(
        icon = Icons.Rounded.Animation,
        title = stringResource(R.string.predictive_back_animation),
        items = listOf(
            stringResource(R.string.predictive_back_animation_none),
            stringResource(R.string.predictive_back_animation_aosp),
            stringResource(R.string.predictive_back_animation_miuix),
            stringResource(R.string.predictive_back_animation_scale),
            stringResource(R.string.predictive_back_animation_ksu_classic)
        ),
        selectedIndex = uiState.predictiveBackAnimation.ordinal,
        onSelectedIndexChange = { index ->
            PredictiveBackAnimation.entries.getOrNull(index)?.let(onSelect)
        }
    )
}

@Composable
fun PredictiveBackAnimationDirectionWidget(
    uiState: SettingsUiState,
    onSelect: (PredictiveBackExitDirection) -> Unit
) {
    SettingsChooseWidget(
        icon = Icons.Rounded.SwapHoriz,
        title = stringResource(R.string.predictive_back_exit_direction),
        items = listOf(
            stringResource(R.string.predictive_back_exit_direction_follow_gesture),
            stringResource(R.string.predictive_back_exit_direction_always_right),
            stringResource(R.string.predictive_back_exit_direction_always_left)
        ),
        selectedIndex = uiState.predictiveBackExitDirection.ordinal,
        onSelectedIndexChange = { index ->
            PredictiveBackExitDirection.entries.getOrNull(index)?.let(onSelect)
        }
    )
}

@Composable
private fun AppearanceSettings(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    pickImageLauncher: ManagedActivityResultLauncher<String, Uri?>,
    coroutineScope: CoroutineScope
) {
    val context = LocalContext.current
    SegmentedColumn(title = stringResource(R.string.appearance_settings)) {
        item {
            // 语言设置
            LanguageSetting(state = state, viewModel = viewModel)
        }

        item {
            // 主题模式
            SettingsChooseWidget(
                icon = Icons.Default.DarkMode,
                title = stringResource(R.string.theme_mode),
                items = state.themeOptions,
                selectedIndex = state.themeMode,
                onSelectedIndexChange = { index ->
                    viewModel.handleThemeModeChange(context, index)
                }
            )
        }

        item {
            // 动态颜色开关
            SettingsSwitchWidget(
                icon = Icons.Filled.ColorLens,
                title = stringResource(R.string.dynamic_color_title),
                description = stringResource(R.string.dynamic_color_summary),
                checked = state.useDynamicColor,
                onCheckedChange = { viewModel.handleDynamicColorChange(context, it) }
            )
        }

        item(
            visible = !state.useDynamicColor,
            topPadding = 1.dp,
        ) {
            // 主题色选择
            ThemeColorSelection(viewModel = viewModel)
        }

        item {
            SettingsChooseWidget(
                icon = Icons.Filled.Style,
                title = stringResource(R.string.dynamic_palette_style),
                items = PaletteStyle.entries.map { it.displayName() },
                selectedIndex = PaletteStyle.entries.indexOf(state.dynamicPaletteStyle),
                onSelectedIndexChange = { index ->
                    viewModel.handleDynamicPaletteStyleChange(
                        context,
                        PaletteStyle.entries.getOrElse(index) { PaletteStyle.TonalSpot }
                    )
                }
            )
        }

        item {
            SettingsChooseWidget(
                icon = Icons.Filled.DesignServices,
                title = stringResource(R.string.dynamic_color_spec),
                items = ColorSpec.SpecVersion.entries.map { it.displayName() },
                selectedIndex = ColorSpec.SpecVersion.entries.indexOf(state.dynamicColorSpec),
                onSelectedIndexChange = { index ->
                    viewModel.handleDynamicColorSpecChange(
                        context,
                        ColorSpec.SpecVersion.entries.getOrElse(index) {
                            ColorSpec.SpecVersion.SPEC_2021
                        }
                    )
                }
            )
        }

        item {
            SettingsBaseWidget(
                icon = Icons.Default.FormatSize,
                title = stringResource(R.string.app_dpi_title),
                description = stringResource(R.string.app_dpi_summary),
                onClick = {},
            ) {
                Text(
                    text = viewModel.getDpiFriendlyName(context, state.tempDpi),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        item(
            topPadding = 1.dp,
        ) { shape ->
            Surface(
                modifier = Modifier
                    .clip(shape)
                    .renderBackgroundBlur(),
                color = if (ThemeConfig.isEnableBlurExp) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHighest.copy(
                    alpha = CardConfig.cardAlpha
                ),
                shape = shape
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    DpiSliderControls(
                        state = state,
                        viewModel = viewModel,
                        coroutineScope = coroutineScope
                    )
                }
            }
        }

        expandableItem(
            expanded = ThemeConfig.customBackgroundUri != null,
            topContent = {
                CustomBackgroundSettings(
                    state = state,
                    viewModel = viewModel,
                    pickImageLauncher = pickImageLauncher,
                )
            },
            bottomContent = {
                backgroundAdjustmentControls(state, viewModel, coroutineScope)
            }
        )
    }
}

@Composable
private fun CustomizationSettings(
    homeUiState: HomeUiState,
    moduleUiState: ModuleUiState,
    settingsUiState: SettingsUiState,
    settingsViewModel: SettingsViewModel,
    homeViewModel: HomeViewModel,
    moduleViewModel: ModuleViewModel,
) {
    val context = LocalContext.current
    SegmentedColumn(title = stringResource(R.string.custom_settings)) {
        item {
            // 图标切换
            SettingsSwitchWidget(
                icon = Icons.Default.Android,
                title = stringResource(R.string.icon_switch_title),
                description = stringResource(R.string.icon_switch_summary),
                checked = settingsUiState.useAltIcon,
                onCheckedChange = { settingsViewModel.handleIconChange(context, it) }
            )
        }

        item {
            // 显示更多模块信息
            SettingsSwitchWidget(
                icon = Icons.Filled.Info,
                title = stringResource(R.string.show_more_module_info),
                description = stringResource(R.string.show_more_module_info_summary),
                checked = moduleUiState.showMoreModuleInfo,
                onCheckedChange = { moduleViewModel.handleShowMoreModuleInfoChange(context, it) }
            )
        }

        item {
            // 简洁模式开关
            SettingsSwitchWidget(
                icon = Icons.Filled.Brush,
                title = stringResource(R.string.simple_mode),
                description = stringResource(R.string.simple_mode_summary),
                checked = homeUiState.isSimpleMode,
                onCheckedChange = { homeViewModel.handleSimpleModeChange(context, it) }
            )
        }

        hideOptionsSettings(homeUiState, moduleUiState, homeViewModel, moduleViewModel)
    }
}

private fun SegmentedColumnScope.hideOptionsSettings(
    homeUiState: HomeUiState,
    moduleUiState: ModuleUiState,
    homeViewModel: HomeViewModel,
    moduleViewModel: ModuleViewModel,
) {
    item {
        // 隐藏内核版本信息
        SettingsSwitchWidget(
            icon = Icons.Filled.VisibilityOff,
            title = stringResource(R.string.hide_kernel_kernelsu_version),
            description = stringResource(R.string.hide_kernel_kernelsu_version_summary),
            checked = homeUiState.isHideVersion,
            onCheckedChange = homeViewModel::handleHideVersionChange
        )
    }

    item {
        // 隐藏模块数量等信息
        SettingsSwitchWidget(
            icon = Icons.Filled.VisibilityOff,
            title = stringResource(R.string.hide_other_info),
            description = stringResource(R.string.hide_other_info_summary),
            checked = homeUiState.isHideOtherInfo,
            onCheckedChange = homeViewModel::handleHideOtherInfoChange
        )
    }

    item {
        // SuSFS 状态信息
        SettingsSwitchWidget(
            icon = Icons.Filled.VisibilityOff,
            title = stringResource(R.string.hide_susfs_status),
            description = stringResource(R.string.hide_susfs_status_summary),
            checked = homeUiState.isHideSusfsStatus,
            onCheckedChange = homeViewModel::handleHideSusfsStatusChange
        )
    }

    item {
        // Zygisk 实现状态信息
        SettingsSwitchWidget(
            icon = Icons.Filled.VisibilityOff,
            title = stringResource(R.string.hide_zygisk_implement),
            description = stringResource(R.string.hide_zygisk_implement_summary),
            checked = homeUiState.isHideZygiskImplement,
            onCheckedChange = homeViewModel::handleHideZygiskImplementChange
        )
    }

    item {
        // 元模块实现状态信息
        SettingsSwitchWidget(
            icon = Icons.Filled.VisibilityOff,
            title = stringResource(R.string.hide_meta_module_implement),
            description = stringResource(R.string.hide_meta_module_implement_summary),
            checked = homeUiState.isHideMetaModuleImplement,
            onCheckedChange = homeViewModel::handleHideMetaModuleImplementChange
        )
    }

    item {
        // 隐藏链接信息
        SettingsSwitchWidget(
            icon = Icons.Filled.VisibilityOff,
            title = stringResource(R.string.hide_link_card),
            description = stringResource(R.string.hide_link_card_summary),
            checked = homeUiState.isHideLinkCard,
            onCheckedChange = homeViewModel::handleHideLinkCardChange
        )
    }

    item {
        // 隐藏标签行
        SettingsSwitchWidget(
            icon = Icons.Filled.VisibilityOff,
            title = stringResource(R.string.hide_tag_card),
            description = stringResource(R.string.hide_tag_card_summary),
            checked = moduleUiState.isHideTagRow,
            onCheckedChange = moduleViewModel::handleHideTagRowChange
        )
    }
}

@Composable
private fun ThemeColorSelection(viewModel: SettingsViewModel) {
    SettingsBaseWidget(
        icon = Icons.Default.Palette,
        title = stringResource(R.string.theme_color),
        description = ThemeConfig.seedColor.toSeedColorHex(),
        onClick = { viewModel.setThemeColorDialogVisible(true) },
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(Color(ThemeConfig.seedColor))
        )
    }
}

private fun Int.toSeedColorHex(): String = "#%06X".format(this and 0x00FFFFFF)

@Composable
private fun DpiSliderControls(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    coroutineScope: CoroutineScope
) {
    val context = LocalContext.current
    val confirmDialog = rememberConfirmDialog()
    val dpiConfirmTitle = stringResource(R.string.dpi_confirm_title)
    val dpiConfirmMessage =
        stringResource(R.string.dpi_confirm_message, state.currentDpi, state.tempDpi)
    val confirmText = stringResource(R.string.confirm)
    val cancelText = stringResource(R.string.cancel)

    val sliderValue by animateFloatAsState(
        targetValue = state.tempDpi.toFloat(),
        label = "DPI Slider Animation"
    )

    KeyPointSlider(
        value = sliderValue,
        onValueChange = { newValue ->
            viewModel.updateTempDpi(newValue.toInt())
        },
        modifier = Modifier.fillMaxWidth(),
        valueRange = 160f..600f,
        keyPoints = state.dpiPresets.map { (_, dpi) -> dpi.toFloat() },
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
                        viewModel.updateTempDpi(dpi)
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
            "${viewModel.getDpiFriendlyName(context, state.tempDpi)}: ${state.tempDpi}",
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

                viewModel.handleDpiApply(context)
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

@Composable
private fun CustomBackgroundSettings(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    pickImageLauncher: ManagedActivityResultLauncher<String, Uri?>,
) {
    val context = LocalContext.current
    // TODO Portrait/Landscape wallpaper split

    SettingsSwitchWidget(
        icon = Icons.Filled.Wallpaper,
        title = stringResource(id = R.string.settings_custom_background),
        description = stringResource(id = R.string.settings_custom_background_summary),
        checked = state.isCustomBackgroundEnabled,
        onCheckedChange = { isChecked ->
            if (isChecked) {
                pickImageLauncher.launch("image/*")
            } else {
                viewModel.handleRemoveCustomBackground(context)
            }
        },
    )
}

private fun SegmentedColumnScope.backgroundAdjustmentControls(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    coroutineScope: CoroutineScope,
) {
    item(
        topPadding = 1.dp
    ) {
        AlphaSlider(
            state = state,
            viewModel = viewModel,
            coroutineScope = coroutineScope
        )
    }

    item(
        topPadding = 1.dp
    ) {
        DimSlider(
            state = state,
            viewModel = viewModel,
            coroutineScope = coroutineScope
        )
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        expandableItem(
            expanded = ThemeConfig.isEnableBlur,
            topPadding = 1.dp,
            topContent = {
                val context = LocalContext.current

                SettingsSwitchWidget(
                    icon = Icons.Filled.BlurOn,
                    title = stringResource(id = R.string.settings_config_enable_blur),
                    description = stringResource(id = R.string.settings_config_enable_blur_summary),
                    checked = ThemeConfig.isEnableBlur,
                    onCheckedChange = { isChecked ->
                        BackgroundManager.saveEnableBlur(context, isChecked)
                        if (!isChecked)
                            BackgroundManager.saveEnableBlurExp(context, false)
                    }
                )
            },
            bottomContent = {
                item(
                    topPadding = 1.dp,
                ) {
                    val context = LocalContext.current

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
            }
        )

        item(
            visible = state.useDynamicColor,
            topPadding = 1.dp,
        ) {
            val context = LocalContext.current

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

        item(
            topPadding = 1.dp,
        ) {
            val context = LocalContext.current

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
}

@Composable
private fun AlphaSlider(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    coroutineScope: CoroutineScope
) {
    val context = LocalContext.current
    SettingsBaseWidget(
        icon = Icons.Filled.Opacity,
        title = stringResource(R.string.settings_card_alpha),
        descriptionColumnContent = {
            val alphaSliderValue by animateFloatAsState(
                targetValue = state.cardAlpha,
                label = "Alpha Slider Animation"
            )

            KeyPointSlider(
                value = alphaSliderValue,
                onValueChange = { newValue ->
                    viewModel.handleCardAlphaChange(context, newValue)
                },
                onValueChangeFinished = {
                    coroutineScope.launch(Dispatchers.IO) {
                        viewModel.saveCardConfig(context)
                    }
                },
                valueRange = 0f..1f,
                keyPoints = listOf(0.25f, 0.5f, 0.75f),
            )
        }
    ) {
        Box(contentAlignment = Alignment.CenterEnd) {
            Text( // Some stupid way to solve measure problem
                text = "100%",
                style = MaterialTheme.typography.labelMediumEmphasized,
                modifier = Modifier.alpha(0f)
            )
            Text(
                text = "${(state.cardAlpha * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelMediumEmphasized
            )
        }
    }
}

@Composable
private fun DimSlider(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    coroutineScope: CoroutineScope
) {
    val context = LocalContext.current
    SettingsBaseWidget(
        icon = Icons.Filled.LightMode,
        title = stringResource(R.string.settings_background_dim),
        descriptionColumnContent = {
            val dimSliderValue by animateFloatAsState(
                targetValue = state.backgroundDim,
                label = "Dim Slider Animation"
            )

            KeyPointSlider(
                value = dimSliderValue,
                onValueChange = { newValue ->
                    viewModel.handleBackgroundDimChange(context, newValue)
                },
                onValueChangeFinished = {
                    coroutineScope.launch(Dispatchers.IO) {
                        viewModel.saveCardConfig(context)
                    }
                },
                valueRange = 0f..1f,
                keyPoints = listOf(0.25f, 0.5f, 0.75f),
            )
        }
    ) {
        Box(contentAlignment = Alignment.CenterEnd) {
            Text( // Some stupid way to solve measure problem
                text = "100%",
                style = MaterialTheme.typography.labelMediumEmphasized,
                modifier = Modifier.alpha(0f)
            )

            Text(
                text = "${(state.backgroundDim * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelMediumEmphasized,
            )
        }
    }
}

@Composable
private fun LanguageSetting(state: SettingsUiState, viewModel: SettingsViewModel) {
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
        onClick = { viewModel.setLanguageDialogVisible(true) }
    )

    // Language Selection Dialog
    if (state.showLanguageDialog) {
        LanguageSelectionDialog(
            onLanguageSelected = {
                // Update local state immediately
                viewModel.refreshCurrentLocale(context)
                // Apply locale change immediately for Android < 13
                restartActivity(context)
            },
            onDismiss = { viewModel.setLanguageDialogVisible(false) }
        )
    }
}
