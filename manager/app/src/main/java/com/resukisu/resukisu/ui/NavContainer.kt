package com.resukisu.resukisu.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.resukisu.resukisu.ui.activity.PermissionRequestInterface
import com.resukisu.resukisu.ui.animation.predictiveback.installerNavTransition
import com.resukisu.resukisu.ui.component.InstallConfirmationDialog
import com.resukisu.resukisu.ui.component.ZipFileDetector
import com.resukisu.resukisu.ui.component.ZipFileInfo
import com.resukisu.resukisu.ui.component.ZipType
import com.resukisu.resukisu.ui.navigation.HandleDeepLink
import com.resukisu.resukisu.ui.navigation.LocalNavigator
import com.resukisu.resukisu.ui.navigation.Navigator
import com.resukisu.resukisu.ui.navigation.Route
import com.resukisu.resukisu.ui.overscroll.StretchOverscrollCompensationState
import com.resukisu.resukisu.ui.overscroll.rememberCustomOverscrollFactory
import com.resukisu.resukisu.ui.screen.AppProfileScreen
import com.resukisu.resukisu.ui.screen.AppProfileTemplateScreen
import com.resukisu.resukisu.ui.screen.DynamicManagerScreen
import com.resukisu.resukisu.ui.screen.ExecuteModuleActionScreen
import com.resukisu.resukisu.ui.screen.FlashIt
import com.resukisu.resukisu.ui.screen.FlashScreen
import com.resukisu.resukisu.ui.screen.InstallScreen
import com.resukisu.resukisu.ui.screen.SulogScreen
import com.resukisu.resukisu.ui.screen.TemplateEditorScreen
import com.resukisu.resukisu.ui.screen.UmountManagerScreen
import com.resukisu.resukisu.ui.screen.about.AboutScreen
import com.resukisu.resukisu.ui.screen.about.OpenSourceLicenseScreen
import com.resukisu.resukisu.ui.screen.kernelFlash.KernelFlashScreen
import com.resukisu.resukisu.ui.screen.main.MainScreen
import com.resukisu.resukisu.ui.screen.moduleRepo.ModuleRepoScreen
import com.resukisu.resukisu.ui.screen.moduleRepo.OnlineModuleDetailScreen
import com.resukisu.resukisu.ui.screen.susfs.SuSFSConfigScreen
import com.resukisu.resukisu.ui.screen.themeSettings.ThemeSettingsScreen
import com.resukisu.resukisu.ui.theme.BackgroundRenderState
import com.resukisu.resukisu.ui.theme.LocalBackgroundRenderState
import com.resukisu.resukisu.ui.theme.ThemeConfig
import com.resukisu.resukisu.ui.util.LocalBackgroundBlurAnchor
import com.resukisu.resukisu.ui.util.LocalBlurState
import com.resukisu.resukisu.ui.util.LocalPermissionRequestInterface
import com.resukisu.resukisu.ui.util.LocalSnackbarHost
import com.resukisu.resukisu.ui.util.LocalStretchOverscrollCompensationState
import com.resukisu.resukisu.ui.util.rememberDeviceCornerRadius
import com.resukisu.resukisu.ui.viewmodel.MainIntentViewModel
import com.resukisu.resukisu.ui.viewmodel.PredictiveBackAnimation
import com.resukisu.resukisu.ui.viewmodel.SettingsViewModel
import com.resukisu.resukisu.ui.webui.WebUIActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.nav.core.NavCornerClipMode
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported
import kotlin.coroutines.resume


@Composable
fun NavContainer(
    zipUri: List<Uri>?,
    intentState: MutableStateFlow<Int>,
    settingsViewModel: SettingsViewModel,
    showConfirmationDialog: MutableState<Boolean>,
    pendingZipFiles: MutableState<List<ZipFileInfo>>,
) {
    val themeConfig: ThemeConfig = koinInject()
    val backgroundRenderState = LocalBackgroundRenderState.current
    val zipFileDetector = koinInject<ZipFileDetector>()
    val activity = LocalActivity.current as MainActivity
    val context = LocalContext.current
    val mainIntentViewModel = koinViewModel<MainIntentViewModel>()
    val mainIntentState by mainIntentViewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(zipUri) {
        if (zipUri.isNullOrEmpty()) return@LaunchedEffect

        activity.lifecycleScope.launch(Dispatchers.IO) {
            val zipFileInfos = zipUri.map { uri ->
                zipFileDetector.parseZipFile(context, uri)
            }.filter { it.type != ZipType.UNKNOWN }

            withContext(Dispatchers.Main) {
                if (zipFileInfos.isNotEmpty()) {
                    pendingZipFiles.value = zipFileInfos
                    showConfirmationDialog.value = true
                } else {
                    activity.finish()
                }
            }
        }
    }

    val settings by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val systemDensity = LocalDensity.current
    val stretchOverscrollCompensationState = remember {
        StretchOverscrollCompensationState()
    }
    val overscrollFactory = rememberCustomOverscrollFactory(
        compensationState = stretchOverscrollCompensationState,
    )

    val density = remember(systemDensity, settings.dpi) {
        if (settings.dpi <= 0f) {
            systemDensity
        } else {
            val targetDensity = settings.dpi / 160f
            Density(density = targetDensity, fontScale = systemDensity.fontScale)
        }
    }

    val backStack = rememberNavBackStack<Route>(Route.Main)
    val navigator = remember(backStack) { Navigator(backStack) }
    val onBack = remember(navigator) {
        {
            when (val top = navigator.current()) {
                is Route.TemplateEditor -> {
                    if (!top.readOnly) {
                        navigator.setResult("template_edit", true)
                    } else {
                        navigator.pop()
                    }
                }

                else -> navigator.pop()
            }
        }
    }
    val useBlur = themeConfig.isEnableBlur

    lateinit var permissionRequestHandler: ManagedActivityResultLauncher<Array<String>, Map<String, @JvmSuppressWildcards Boolean>>

    val permissionRequestInterface = object : PermissionRequestInterface {
        private val mutex = Mutex()
        private var currentCallback: ((Map<String, @JvmSuppressWildcards Boolean>) -> Unit)? =
            null

        override fun requestPermission(
            permission: String,
            callback: (Boolean) -> Unit,
            requestDescription: String
        ) {
            if (activity.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
                callback(true)
                return
            }

            activity.lifecycleScope.launch {
                mutex.withLock {
                    suspendCancellableCoroutine { continuation ->
                        currentCallback = { result ->
                            callback(result.any { it.value })
                            continuation.resume(Unit)
                        }

                        if (requestDescription.isNotBlank() && ActivityCompat.shouldShowRequestPermissionRationale(
                                activity,
                                permission
                            )
                        ) {
                            Toast.makeText(
                                context,
                                requestDescription,
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                        permissionRequestHandler.launch(arrayOf(permission))
                    }
                }
            }
        }

        override fun requestPermissions(
            permissions: Array<String>,
            callback: (Map<String, @JvmSuppressWildcards Boolean>) -> Unit,
            requestDescription: Map<String, String>
        ) {
            val permissionsToRequest = permissions.filter {
                activity.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
            }.toTypedArray()

            if (permissionsToRequest.isEmpty()) {
                callback(permissions.associateWith { true })
                return
            }

            activity.lifecycleScope.launch {
                mutex.withLock {
                    suspendCancellableCoroutine { continuation ->
                        currentCallback = { result ->
                            val finalResult = permissions.associateWith { perm ->
                                result[perm] ?: true
                            }
                            callback(finalResult)
                            continuation.resume(Unit)
                        }

                        permissionsToRequest.forEach { perm ->
                            if (ActivityCompat.shouldShowRequestPermissionRationale(
                                    activity,
                                    perm
                                )
                            ) {
                                val msg = requestDescription[perm]
                                if (!msg.isNullOrBlank()) {
                                    Toast.makeText(
                                        activity,
                                        msg,
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }

                        permissionRequestHandler.launch(permissionsToRequest)
                    }
                }
            }
        }

        fun onPermissionRequestCallback(result: Map<String, @JvmSuppressWildcards Boolean>) =
            currentCallback?.invoke(result)
    }

    permissionRequestHandler = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = permissionRequestInterface::onPermissionRequestCallback
    )

    CompositionLocalProvider(
        LocalOverscrollFactory provides overscrollFactory,
        LocalStretchOverscrollCompensationState provides stretchOverscrollCompensationState,
        LocalPermissionRequestInterface provides permissionRequestInterface,
        LocalNavigator provides navigator,
        LocalDensity provides density
    ) {
        HandleDeepLink(
            intentState = intentState.collectAsState()
        )

        ShortcutIntentHandler(
            intentState = intentState
        )

        InstallConfirmationDialog(
            show = showConfirmationDialog.value,
            zipFiles = pendingZipFiles.value,
            onConfirm = { confirmedFiles ->
                showConfirmationDialog.value = false
                activity.lifecycleScope.launch(Dispatchers.IO) {
                    val moduleUris =
                        confirmedFiles.filter { it.type == ZipType.MODULE }
                            .map { it.uri }
                    val kernelUris =
                        confirmedFiles.filter { it.type == ZipType.KERNEL }
                            .map { it.uri }

                    when {
                        kernelUris.isNotEmpty() && moduleUris.isEmpty() -> {
                            if (kernelUris.size == 1 && mainIntentState.rootAvailable) {
                                withContext(Dispatchers.Main) {
                                    navigator.push(
                                        Route.Install(
                                            preselectedKernelUri = kernelUris.first()
                                                .toString()
                                        )
                                    )
                                }
                            }
                        }

                        moduleUris.isNotEmpty() -> {
                            withContext(Dispatchers.Main) {
                                navigator.push(
                                    Route.Flash.modules(moduleUris.map(Uri::toString))
                                )
                            }
                        }
                    }
                }
            },
            onDismiss = {
                showConfirmationDialog.value = false
                pendingZipFiles.value = emptyList()
                activity.finish()
            }
        )

        val navCornerRadius = rememberDeviceCornerRadius(defaultRadius = 0.dp)
        val roundAllCorners =
            settings.predictiveBackAnimation == PredictiveBackAnimation.AOSP ||
                settings.predictiveBackAnimation == PredictiveBackAnimation.Scale ||
                settings.predictiveBackAnimation == PredictiveBackAnimation.KernelSUClassic
        val backdropColor = MaterialTheme.colorScheme.surfaceContainer
        val effects = remember(navCornerRadius, roundAllCorners, backdropColor) {
            NavDisplayEffects(
                enableCornerClip = true,
                cornerClipRadius = if (roundAllCorners && navCornerRadius <= 0.dp) 32.dp else navCornerRadius,
                cornerClipMode = if (roundAllCorners) NavCornerClipMode.All else NavCornerClipMode.Leading,
                dimAmount = 0.5f,
                backdropColor = backdropColor,
                blockInputDuringTransition = false,
            )
        }
        val transition = remember(
            settings.predictiveBackAnimation,
            settings.predictiveBackExitDirection
        ) {
            installerNavTransition(
                animation = settings.predictiveBackAnimation,
                exitDirection = settings.predictiveBackExitDirection,
            )
        }
        val swipeBackDirection = when (LocalLayoutDirection.current) {
            LayoutDirection.Rtl -> NavSwipeDirection.RightToLeft
            LayoutDirection.Ltr -> NavSwipeDirection.LeftToRight
        }
        val interceptPredictiveBack =
            settings.predictiveBackAnimation == PredictiveBackAnimation.None && backStack.size > 1

        NavDisplay(
            backStack = backStack,
            onBack = onBack,
            transition = transition,
            effects = effects,
        ) {
            entry<Route.About>(swipeDismiss = swipeBackDirection) {
                ManagerNavEntry(
                    interceptPredictiveBack = interceptPredictiveBack,
                    onBack = onBack,
                    themeConfig = themeConfig,
                    backgroundRenderState = backgroundRenderState,
                    useBlur = useBlur,
                ) {
                    AboutScreen()
                }
            }
            entry<Route.OpenSourceLicense>(swipeDismiss = swipeBackDirection) {
                ManagerNavEntry(
                    interceptPredictiveBack = interceptPredictiveBack,
                    onBack = onBack,
                    themeConfig = themeConfig,
                    backgroundRenderState = backgroundRenderState,
                    useBlur = useBlur,
                ) {
                    OpenSourceLicenseScreen()
                }
            }
            entry<Route.Sulog>(swipeDismiss = swipeBackDirection) {
                ManagerNavEntry(
                    interceptPredictiveBack = interceptPredictiveBack,
                    onBack = onBack,
                    themeConfig = themeConfig,
                    backgroundRenderState = backgroundRenderState,
                    useBlur = useBlur,
                ) {
                    SulogScreen()
                }
            }
            entry<Route.Main>(swipeDismiss = NavSwipeDirection.None) {
                ManagerNavEntry(
                    interceptPredictiveBack = interceptPredictiveBack,
                    onBack = onBack,
                    themeConfig = themeConfig,
                    backgroundRenderState = backgroundRenderState,
                    useBlur = useBlur,
                ) {
                    MainScreen()
                }
            }
            entry<Route.AppProfileTemplate>(swipeDismiss = swipeBackDirection) {
                ManagerNavEntry(
                    interceptPredictiveBack = interceptPredictiveBack,
                    onBack = onBack,
                    themeConfig = themeConfig,
                    backgroundRenderState = backgroundRenderState,
                    useBlur = useBlur,
                ) {
                    AppProfileTemplateScreen()
                }
            }
            entry<Route.TemplateEditor>(swipeDismiss = NavSwipeDirection.None) { key ->
                ManagerNavEntry(
                    interceptPredictiveBack = interceptPredictiveBack,
                    onBack = onBack,
                    themeConfig = themeConfig,
                    backgroundRenderState = backgroundRenderState,
                    useBlur = useBlur,
                ) {
                    TemplateEditorScreen(
                        templateId = key.templateId,
                        readOnly = key.readOnly,
                        isCreation = key.isCreation,
                    )
                }
            }
            entry<Route.AppProfile>(swipeDismiss = swipeBackDirection) { key ->
                ManagerNavEntry(
                    interceptPredictiveBack = interceptPredictiveBack,
                    onBack = onBack,
                    themeConfig = themeConfig,
                    backgroundRenderState = backgroundRenderState,
                    useBlur = useBlur,
                ) {
                    AppProfileScreen(key.uid, key.packageName)
                }
            }
            entry<Route.ModuleRepo>(swipeDismiss = swipeBackDirection) {
                ManagerNavEntry(
                    interceptPredictiveBack = interceptPredictiveBack,
                    onBack = onBack,
                    themeConfig = themeConfig,
                    backgroundRenderState = backgroundRenderState,
                    useBlur = useBlur,
                ) {
                    ModuleRepoScreen()
                }
            }
            entry<Route.ModuleRepoDetail>(swipeDismiss = swipeBackDirection) { key ->
                ManagerNavEntry(
                    interceptPredictiveBack = interceptPredictiveBack,
                    onBack = onBack,
                    themeConfig = themeConfig,
                    backgroundRenderState = backgroundRenderState,
                    useBlur = useBlur,
                ) {
                    OnlineModuleDetailScreen(key.moduleId)
                }
            }
            entry<Route.Install>(swipeDismiss = NavSwipeDirection.None) { key ->
                ManagerNavEntry(
                    interceptPredictiveBack = interceptPredictiveBack,
                    onBack = onBack,
                    themeConfig = themeConfig,
                    backgroundRenderState = backgroundRenderState,
                    useBlur = useBlur,
                ) {
                    InstallScreen(key.preselectedKernelUri)
                }
            }
            entry<Route.Flash>(swipeDismiss = swipeBackDirection) { key ->
                ManagerNavEntry(
                    interceptPredictiveBack = interceptPredictiveBack,
                    onBack = onBack,
                    themeConfig = themeConfig,
                    backgroundRenderState = backgroundRenderState,
                    useBlur = useBlur,
                ) {
                    FlashScreen(key.toFlashIt())
                }
            }
            entry<Route.ExecuteModuleAction>(swipeDismiss = swipeBackDirection) { key ->
                ManagerNavEntry(
                    interceptPredictiveBack = interceptPredictiveBack,
                    onBack = onBack,
                    themeConfig = themeConfig,
                    backgroundRenderState = backgroundRenderState,
                    useBlur = useBlur,
                ) {
                    ExecuteModuleActionScreen(key.moduleId)
                }
            }
            entry<Route.Home>(swipeDismiss = NavSwipeDirection.None) {
                ManagerNavEntry(
                    interceptPredictiveBack = interceptPredictiveBack,
                    onBack = onBack,
                    themeConfig = themeConfig,
                    backgroundRenderState = backgroundRenderState,
                    useBlur = useBlur,
                ) {
                    MainScreen()
                }
            }
            entry<Route.SuperUser>(swipeDismiss = NavSwipeDirection.None) {
                ManagerNavEntry(
                    interceptPredictiveBack = interceptPredictiveBack,
                    onBack = onBack,
                    themeConfig = themeConfig,
                    backgroundRenderState = backgroundRenderState,
                    useBlur = useBlur,
                ) {
                    MainScreen()
                }
            }
            entry<Route.Module>(swipeDismiss = NavSwipeDirection.None) {
                ManagerNavEntry(
                    interceptPredictiveBack = interceptPredictiveBack,
                    onBack = onBack,
                    themeConfig = themeConfig,
                    backgroundRenderState = backgroundRenderState,
                    useBlur = useBlur,
                ) {
                    MainScreen()
                }
            }
            entry<Route.Settings>(swipeDismiss = NavSwipeDirection.None) {
                ManagerNavEntry(
                    interceptPredictiveBack = interceptPredictiveBack,
                    onBack = onBack,
                    themeConfig = themeConfig,
                    backgroundRenderState = backgroundRenderState,
                    useBlur = useBlur,
                ) {
                    MainScreen()
                }
            }
            entry<Route.ThemeSettings>(swipeDismiss = swipeBackDirection) {
                ManagerNavEntry(
                    interceptPredictiveBack = interceptPredictiveBack,
                    onBack = onBack,
                    themeConfig = themeConfig,
                    backgroundRenderState = backgroundRenderState,
                    useBlur = useBlur,
                ) {
                    ThemeSettingsScreen(settingsViewModel = settingsViewModel)
                }
            }
            entry<Route.SuSFSConfig>(swipeDismiss = swipeBackDirection) {
                ManagerNavEntry(
                    interceptPredictiveBack = interceptPredictiveBack,
                    onBack = onBack,
                    themeConfig = themeConfig,
                    backgroundRenderState = backgroundRenderState,
                    useBlur = useBlur,
                ) {
                    SuSFSConfigScreen()
                }
            }
            entry<Route.UmountManager>(swipeDismiss = swipeBackDirection) {
                ManagerNavEntry(
                    interceptPredictiveBack = interceptPredictiveBack,
                    onBack = onBack,
                    themeConfig = themeConfig,
                    backgroundRenderState = backgroundRenderState,
                    useBlur = useBlur,
                ) {
                    UmountManagerScreen()
                }
            }
            entry<Route.DynamicManager>(swipeDismiss = swipeBackDirection) {
                ManagerNavEntry(
                    interceptPredictiveBack = interceptPredictiveBack,
                    onBack = onBack,
                    themeConfig = themeConfig,
                    backgroundRenderState = backgroundRenderState,
                    useBlur = useBlur,
                ) {
                    DynamicManagerScreen()
                }
            }
            entry<Route.KernelFlash>(swipeDismiss = NavSwipeDirection.None) { key ->
                ManagerNavEntry(
                    interceptPredictiveBack = interceptPredictiveBack,
                    onBack = onBack,
                    themeConfig = themeConfig,
                    backgroundRenderState = backgroundRenderState,
                    useBlur = useBlur,
                ) {
                    KernelFlashScreen(key.kernelUri, key.selectedSlot)
                }
            }
        }
    }
}

@Composable
private fun ManagerNavEntry(
    interceptPredictiveBack: Boolean,
    onBack: () -> Unit,
    themeConfig: ThemeConfig,
    backgroundRenderState: BackgroundRenderState,
    useBlur: Boolean,
    content: @Composable () -> Unit,
) {
    val navigationEventState = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(
        state = navigationEventState,
        isBackEnabled = interceptPredictiveBack,
        onBackCompleted = onBack,
    )
    val snackBarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    var backgroundBlurAnchorCoordinates by remember {
        mutableStateOf<LayoutCoordinates?>(null)
    }

    LaunchedEffect(backgroundRenderState.imagePainter) {
        if (backgroundRenderState.imagePainter == null) {
            backgroundBlurAnchorCoordinates = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (!themeConfig.backgroundImageLoaded) Modifier.background(
                    MaterialTheme.colorScheme.surfaceContainer
                ) else Modifier
            )
    ) {
        val surfaceContainer =
            MaterialTheme.colorScheme.surfaceContainer

        CompositionLocalProvider(
            LocalBlurState provides rememberMaterial3BlurBackdrop(
                enableBlur = useBlur
            ),
            LocalSnackbarHost provides snackBarHostState,
            LocalBackgroundBlurAnchor provides backgroundBlurAnchorCoordinates,
        ) {
            backgroundRenderState.imagePainter?.let {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(-1f)
                        .onGloballyPositioned { newCoordinates ->
                            backgroundBlurAnchorCoordinates =
                                newCoordinates.takeIf { coordinates ->
                                    coordinates.isAttached
                                }
                        }
                        .paint(
                            painter = it,
                            contentScale = ContentScale.Crop,
                        )
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                color = surfaceContainer.copy(
                                    alpha = themeConfig.backgroundDim
                                )
                            )
                        }
                )
            }
            content()
        }
    }
}

private fun Route.Flash.toFlashIt(): FlashIt = when (type) {
    Route.Flash.TYPE_BOOT -> FlashIt.FlashBoot(
        boot = bootUri,
        lkmUri = lkmUri,
        kmi = kmi,
        ota = ota,
        partition = partition,
    )

    Route.Flash.TYPE_MODULE -> FlashIt.FlashModule(uris.firstOrNull().orEmpty())
    Route.Flash.TYPE_MODULES -> FlashIt.FlashModules(uris, currentIndex)
    Route.Flash.TYPE_MODULE_UPDATE -> FlashIt.FlashModuleUpdate(uris.firstOrNull().orEmpty())
    Route.Flash.TYPE_RESTORE -> FlashIt.FlashRestore
    Route.Flash.TYPE_UNINSTALL -> FlashIt.FlashUninstall
    else -> FlashIt.FlashModule(uris.firstOrNull().orEmpty())
}

/**
 * Remember a LayerBackdrop for Material 3 with a surfaceContainer background
 * to prevent alpha-blending artifacts.
 *
 * @param enableBlur Whether the blur effect is globally enabled.
 * @return A LayerBackdrop instance if supported and enabled, null otherwise.
 */
@Composable
fun rememberMaterial3BlurBackdrop(
    enableBlur: Boolean,
    pagerState: PagerState? = null,
    pagerPage: Int? = null,
): LayerBackdrop? {
    val themeConfig: ThemeConfig = koinInject()
    val backgroundRenderState = LocalBackgroundRenderState.current
    if (!enableBlur || !isRenderEffectSupported()) return null

    val backgroundColor =
        MaterialTheme.colorScheme.surfaceContainer
    val layoutDirection = LocalLayoutDirection.current
    val backgroundAnchor = LocalBackgroundBlurAnchor.current

    return rememberLayerBackdrop {
        if (themeConfig.isEnableBlurExp) {
            backgroundRenderState.imagePainter?.let { painter ->
                val backgroundViewportSize = backgroundAnchor
                    ?.takeIf { it.isAttached && it.size.width > 0 && it.size.height > 0 }
                    ?.size
                    ?: backgroundRenderState.blurViewportSize
                val backgroundWidth = backgroundViewportSize.width
                    .takeIf { it > 0 }
                    ?.toFloat()
                    ?: size.width
                val backgroundHeight = backgroundViewportSize.height
                    .takeIf { it > 0 }
                    ?.toFloat()
                    ?: size.height
                val pagerViewportWidth = pagerState
                    ?.layoutInfo
                    ?.viewportSize
                    ?.width
                    ?.takeIf { it > 0 }
                    ?.toFloat()
                    ?: size.width
                val leadingNavigationWidth =
                    (backgroundWidth - pagerViewportWidth).coerceAtLeast(0f)
                val pagerViewportLeft = if (layoutDirection == LayoutDirection.Ltr) {
                    leadingNavigationWidth
                } else {
                    0f
                }
                val pageOffset = if (
                    pagerState != null &&
                    pagerPage != null &&
                    pagerPage in 0 until pagerState.pageCount
                ) {
                    pagerState.getOffsetDistanceInPages(pagerPage)
                } else {
                    0f
                }
                val physicalPageOffset = pageOffset * pagerViewportWidth *
                    if (layoutDirection == LayoutDirection.Ltr) 1f else -1f
                val backgroundOffset = pagerViewportLeft + physicalPageOffset
                val backgroundBitmap = backgroundRenderState.imageBitmap

                if (
                    backgroundBitmap != null &&
                    backgroundBitmap.width > 0 &&
                    backgroundBitmap.height > 0
                ) {
                    val backgroundScale = maxOf(
                        backgroundWidth / backgroundBitmap.width,
                        backgroundHeight / backgroundBitmap.height,
                    )
                    val renderedLeft =
                        (backgroundWidth - backgroundBitmap.width * backgroundScale) / 2f
                    val renderedTop =
                        (backgroundHeight - backgroundBitmap.height * backgroundScale) / 2f

                    translate(
                        left = -backgroundOffset + renderedLeft,
                        top = renderedTop,
                    ) {
                        scale(
                            scaleX = backgroundScale,
                            scaleY = backgroundScale,
                            pivot = Offset.Zero,
                        ) {
                            drawImage(
                                image = backgroundBitmap,
                                filterQuality = FilterQuality.Low,
                            )
                        }
                    }
                } else {
                    translate(left = -backgroundOffset) {
                        with(painter) {
                            draw(size = Size(backgroundWidth, backgroundHeight))
                        }
                    }
                }
            }
        } else {
            drawRect(backgroundColor)
        }

        drawRect(
            color = backgroundColor.copy(alpha = themeConfig.backgroundDim)
        )

        drawContent()
    }
}

@Composable
private fun ShortcutIntentHandler(
    intentState: MutableStateFlow<Int>
) {
    val navigator = LocalNavigator.current
    val activity = LocalActivity.current ?: return
    val context = LocalContext.current
    val intentStateValue by intentState.collectAsState()
    LaunchedEffect(intentStateValue) {
        val intent = activity.intent
        val type = intent?.getStringExtra("shortcut_type") ?: return@LaunchedEffect
        when (type) {
            "module_action" -> {
                val moduleId = intent.getStringExtra("module_id") ?: return@LaunchedEffect
                navigator.push(Route.ExecuteModuleAction(moduleId))
            }

            "module_webui" -> {
                val moduleId = intent.getStringExtra("module_id") ?: return@LaunchedEffect
                val moduleName = intent.getStringExtra("module_name") ?: moduleId

                val webIntent = Intent(context, WebUIActivity::class.java)
                    .setData("kernelsu://webui/$moduleId".toUri())
                    .putExtra("id", moduleId)
                    .putExtra("name", moduleName)
                    .putExtra("from_webui_shortcut", true)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TASK
                    )
                context.startActivity(webIntent)
            }

            else -> return@LaunchedEffect
        }
    }
}
