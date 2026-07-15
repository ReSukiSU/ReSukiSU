package com.resukisu.resukisu.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.paint
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.zIndex
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.SceneInfo
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.scene.rememberSceneState
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.NavigationEventState
import androidx.navigationevent.compose.rememberNavigationEventState
import com.resukisu.resukisu.ui.activity.PermissionRequestInterface
import com.resukisu.resukisu.ui.animation.predictiveback.AOSPCrossActivityAnimation
import com.resukisu.resukisu.ui.animation.predictiveback.KernelSUClassicPredictiveBackAnimation
import com.resukisu.resukisu.ui.animation.predictiveback.MiuixPredictiveBackAnimation
import com.resukisu.resukisu.ui.animation.predictiveback.NoPredictiveBackAnimation
import com.resukisu.resukisu.ui.animation.predictiveback.ScalePredictiveBackAnimation
import com.resukisu.resukisu.ui.component.InstallConfirmationDialog
import com.resukisu.resukisu.ui.component.ZipFileDetector
import com.resukisu.resukisu.ui.component.ZipFileInfo
import com.resukisu.resukisu.ui.component.ZipType
import com.resukisu.resukisu.ui.navigation.HandleDeepLink
import com.resukisu.resukisu.ui.navigation.LocalNavigator
import com.resukisu.resukisu.ui.navigation.Route
import com.resukisu.resukisu.ui.navigation.rememberNavigator
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
import com.resukisu.resukisu.ui.screen.themeSettings.ThemeSettingsScreen
import com.resukisu.resukisu.ui.susfs.SuSFSConfigScreen
import com.resukisu.resukisu.ui.theme.ThemeConfig
import com.resukisu.resukisu.ui.theme.backgroundImagePainter
import com.resukisu.resukisu.ui.util.LocalBackgroundBlurAnchor
import com.resukisu.resukisu.ui.util.LocalBlurState
import com.resukisu.resukisu.ui.util.LocalPermissionRequestInterface
import com.resukisu.resukisu.ui.util.LocalSnackbarHost
import com.resukisu.resukisu.ui.util.rootAvailable
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
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
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
    val activity = LocalActivity.current as MainActivity
    val context = LocalContext.current

    LaunchedEffect(zipUri) {
        if (zipUri.isNullOrEmpty()) return@LaunchedEffect

        activity.lifecycleScope.launch(Dispatchers.IO) {
            val zipFileInfos = zipUri.map { uri ->
                ZipFileDetector.parseZipFile(context, uri)
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

    val density = remember(systemDensity, settings.dpi) {
        if (settings.dpi <= 0f) {
            systemDensity
        } else {
            val targetDensity = settings.dpi / 160f
            Density(density = targetDensity, fontScale = systemDensity.fontScale)
        }
    }

    val navigator = rememberNavigator(Route.Main)

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
                            if (kernelUris.size == 1 && rootAvailable()) {
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
                                    Route.Flash(
                                        FlashIt.FlashModules(ArrayList(moduleUris))
                                    )
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

        val predictiveBackAnimationHandler = remember(
            settings.predictiveBackAnimation,
            settings.predictiveBackExitDirection
        ) {
            when (settings.predictiveBackAnimation) {
                PredictiveBackAnimation.None -> NoPredictiveBackAnimation()
                PredictiveBackAnimation.AOSP -> AOSPCrossActivityAnimation(settings.predictiveBackExitDirection)
                PredictiveBackAnimation.Scale -> ScalePredictiveBackAnimation(
                    settings.predictiveBackExitDirection
                )

                PredictiveBackAnimation.KernelSUClassic -> KernelSUClassicPredictiveBackAnimation()
                PredictiveBackAnimation.MIUIX -> MiuixPredictiveBackAnimation()
            }
        }

        var gestureState: NavigationEventState<SceneInfo<NavKey>>? = null
        val navigationScope = rememberCoroutineScope()
        val onBack: (() -> Unit) -> Unit = { callBack ->
            navigationScope.launch {
                predictiveBackAnimationHandler.onBackPressed(
                    transitionState = gestureState?.transitionState,
                    currentPageKey = navigator.current()
                )

                callBack()

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

        val entries =
            rememberDecoratedNavEntries(
                backStack = navigator.backStack,
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                    NavEntryDecorator(
                        onPop = { key ->
                            predictiveBackAnimationHandler.onPagePop(
                                contentPageKey = key,
                                animationScope = navigationScope
                            )
                        }
                    ) { content ->
                        val snackBarHostState = remember { SnackbarHostState() }
                        var backgroundBlurAnchorCoordinates by remember {
                            mutableStateOf<LayoutCoordinates?>(null)
                        }

                        LaunchedEffect(backgroundImagePainter) {
                            if (backgroundImagePainter == null) {
                                backgroundBlurAnchorCoordinates = null
                            }
                        }

                        with(predictiveBackAnimationHandler) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .predictiveBackAnimationDecorator(
                                        gestureState?.transitionState,
                                        content.contentKey,
                                        navigator.current()
                                    )
                                    .then(
                                        if (!ThemeConfig.backgroundImageLoaded) Modifier.background(
                                            MaterialTheme.colorScheme.surfaceContainer
                                        ) else Modifier
                                    )
                            ) {
                                val surfaceContainer =
                                    MaterialTheme.colorScheme.surfaceContainer

                                CompositionLocalProvider(
                                    LocalBlurState provides rememberMaterial3BlurBackdrop(
                                        ThemeConfig.isEnableBlur
                                    ),
                                    LocalSnackbarHost provides snackBarHostState,
                                    LocalBackgroundBlurAnchor provides backgroundBlurAnchorCoordinates,
                                ) {
                                    backgroundImagePainter?.let {
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
                                                            alpha = ThemeConfig.backgroundDim
                                                        )
                                                    )
                                                }
                                        )
                                    }
                                    content.Content()
                                }
                            }
                        }
                    }
                ),
                entryProvider = entryProvider {
                    entry<Route.About> { AboutScreen() }
                    entry<Route.OpenSourceLicense> { OpenSourceLicenseScreen() }
                    entry<Route.Sulog> { SulogScreen() }
                    entry<Route.Main> { MainScreen() }
                    entry<Route.AppProfileTemplate> { AppProfileTemplateScreen() }
                    entry<Route.TemplateEditor> { key ->
                        TemplateEditorScreen(
                            key.template,
                            key.readOnly
                        )
                    }
                    entry<Route.AppProfile> { key -> AppProfileScreen(key.appGroup) }
                    entry<Route.ModuleRepo> { ModuleRepoScreen() }
                    entry<Route.ModuleRepoDetail> { key ->
                        OnlineModuleDetailScreen(
                            key.module
                        )
                    }
                    entry<Route.Install> { key -> InstallScreen(key.preselectedKernelUri) }
                    entry<Route.Flash> { key -> FlashScreen(key.flashIt) }
                    entry<Route.ExecuteModuleAction> { key ->
                        ExecuteModuleActionScreen(
                            key.moduleId
                        )
                    }
                    entry<Route.Home> { MainScreen() }
                    entry<Route.SuperUser> { MainScreen() }
                    entry<Route.Module> { MainScreen() }
                    entry<Route.Settings> { MainScreen() }
                    entry<Route.ThemeSettings> { ThemeSettingsScreen() }
                    entry<Route.SuSFSConfig> { SuSFSConfigScreen() }
                    entry<Route.UmountManager> { UmountManagerScreen() }
                    entry<Route.DynamicManager> { DynamicManagerScreen() }
                    entry<Route.KernelFlash> { key ->
                        KernelFlashScreen(
                            key.kernelUri,
                            key.selectedSlot
                        )
                    }
                },
            )

        val sceneState =
            rememberSceneState(
                entries = entries,
                sceneStrategies = listOf(SinglePaneSceneStrategy()),
                sceneDecoratorStrategies = emptyList(),
                sharedTransitionScope = null,
                onBack = {
                    onBack {}
                },
            )
        val scene = sceneState.currentScene

        // Predictive Back Handling
        val currentInfo = SceneInfo(scene)
        val previousSceneInfos = sceneState.previousScenes.map { SceneInfo(it) }
        gestureState = rememberNavigationEventState(
            currentInfo = currentInfo,
            backInfo = previousSceneInfos
        )

        NavigationBackHandler(
            state = gestureState,
            isBackEnabled = scene.previousEntries.isNotEmpty(),
            onBackCompleted = { callBack ->
                onBack(callBack)
            },
            onBackCancelled = { callBack ->
                callBack()
            }
        )

        NavDisplay(
            sceneState = sceneState,
            navigationEventState = gestureState,
            contentAlignment = Alignment.TopStart,
            sizeTransform = null,
            predictivePopTransitionSpec = { swipeEdge ->
                with(predictiveBackAnimationHandler) {
                    onPredictivePopTransitionSpec(swipeEdge = swipeEdge)
                }
            },
            popTransitionSpec = {
                with(predictiveBackAnimationHandler) {
                    onPopTransitionSpec()
                }
            },
            transitionSpec = {
                with(predictiveBackAnimationHandler) {
                    onTransitionSpec()
                }
            },
        )
    }
}

/**
 * Remember a LayerBackdrop for Material 3 with a surfaceContainer background
 * to prevent alpha-blending artifacts.
 *
 * @param enableBlur Whether the blur effect is globally enabled.
 * @return A LayerBackdrop instance if supported and enabled, null otherwise.
 */
@Composable
fun rememberMaterial3BlurBackdrop(enableBlur: Boolean): LayerBackdrop? {
    if (!enableBlur || !isRenderEffectSupported()) return null

    val backgroundColor =
        MaterialTheme.colorScheme.surfaceContainer

    return rememberLayerBackdrop {
        if (ThemeConfig.isEnableBlurExp) {
            backgroundImagePainter?.let { painter ->
                with(painter) {
                    draw(size = drawContext.size)
                }
            }
        } else {
            drawRect(backgroundColor)
        }

        drawRect(
            color = backgroundColor.copy(alpha = ThemeConfig.backgroundDim)
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
