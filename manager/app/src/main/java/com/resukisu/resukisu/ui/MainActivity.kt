package com.resukisu.resukisu.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModelProvider
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
import com.resukisu.resukisu.KernelSUApplication
import com.resukisu.resukisu.Natives
import com.resukisu.resukisu.ui.activity.PermissionRequestInterface
import com.resukisu.resukisu.ui.activity.component.NavigationBar
import com.resukisu.resukisu.ui.activity.util.ThemeChangeContentObserver
import com.resukisu.resukisu.ui.activity.util.ThemeUtils
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
import com.resukisu.resukisu.ui.screen.BottomBarDestination
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
import com.resukisu.resukisu.ui.screen.moduleRepo.ModuleRepoScreen
import com.resukisu.resukisu.ui.screen.moduleRepo.OnlineModuleDetailScreen
import com.resukisu.resukisu.ui.screen.themeSettings.ThemeSettingsScreen
import com.resukisu.resukisu.ui.screen.themeSettings.util.applyLanguage
import com.resukisu.resukisu.ui.susfs.SuSFSConfigScreen
import com.resukisu.resukisu.ui.theme.KernelSUTheme
import com.resukisu.resukisu.ui.theme.ThemeConfig
import com.resukisu.resukisu.ui.theme.backgroundImagePainter
import com.resukisu.resukisu.ui.theme.blurBackgroundImageBitmap
import com.resukisu.resukisu.ui.theme.blurSource
import com.resukisu.resukisu.ui.util.LocalBackgroundBlurAnchor
import com.resukisu.resukisu.ui.util.LocalBlurState
import com.resukisu.resukisu.ui.util.LocalHandlePageChange
import com.resukisu.resukisu.ui.util.LocalPagerState
import com.resukisu.resukisu.ui.util.LocalPermissionRequestInterface
import com.resukisu.resukisu.ui.util.LocalSelectedPage
import com.resukisu.resukisu.ui.util.LocalSnackbarHost
import com.resukisu.resukisu.ui.util.install
import com.resukisu.resukisu.ui.util.rootAvailable
import com.resukisu.resukisu.ui.viewmodel.HomeViewModel
import com.resukisu.resukisu.ui.viewmodel.ModuleViewModel
import com.resukisu.resukisu.ui.viewmodel.PredictiveBackAnimation
import com.resukisu.resukisu.ui.viewmodel.SettingsViewModel
import com.resukisu.resukisu.ui.viewmodel.SuperUserViewModel
import com.resukisu.resukisu.ui.webui.WebUIActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

class MainActivity : ComponentActivity() {
    private lateinit var superUserViewModel: SuperUserViewModel
    private lateinit var homeViewModel: HomeViewModel
    private lateinit var moduleViewModel: ModuleViewModel
    private lateinit var settingsViewModel: SettingsViewModel

    private var showConfirmationDialog = mutableStateOf(false)
    private var pendingZipFiles = mutableStateOf<List<ZipFileInfo>>(emptyList())

    private lateinit var themeChangeObserver: ThemeChangeContentObserver
    private var isInitialized = false

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase?.let { applyLanguage(it) })
    }

    private val intentState = MutableStateFlow(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            val splashScreen = installSplashScreen()

            // Enable edge to edge
            enableEdgeToEdge()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }

            super.onCreate(savedInstanceState)

            homeViewModel =
                ViewModelProvider(applicationContext as KernelSUApplication)[HomeViewModel::class.java]
            splashScreen.setKeepOnScreenCondition {
                !homeViewModel.uiState.value.isInitialDataLoaded
                        || (
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                                ThemeConfig.isEnableBlurExp &&
                                blurBackgroundImageBitmap != null
                        )
            }

            val isManager = Natives.isManager
            if (isManager && !Natives.requireNewKernel()) {
                install()
            }

            // 使用标记控制初始化流程
            if (!isInitialized) {
                initializeViewModels()
                initializeData()
                isInitialized = true
            }

            // Check if launched with a ZIP file
            val zipUri: ArrayList<Uri>? = when (intent?.action) {
                Intent.ACTION_SEND -> {
                    val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(Intent.EXTRA_STREAM)
                    }
                    uri?.let { arrayListOf(it) }
                }

                Intent.ACTION_SEND_MULTIPLE -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                    }
                }

                else -> when {
                    intent?.data != null -> arrayListOf(intent.data!!)
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                        intent.getParcelableArrayListExtra("uris", Uri::class.java)
                    }
                    else -> {
                        @Suppress("DEPRECATION")
                        intent.getParcelableArrayListExtra("uris")
                    }
                }
            }

            setContent {
                KernelSUTheme {
                    val context = LocalContext.current

                    LaunchedEffect(zipUri) {
                        if (zipUri.isNullOrEmpty()) return@LaunchedEffect

                        lifecycleScope.launch(Dispatchers.IO) {
                            val zipFileInfos = zipUri.map { uri ->
                                ZipFileDetector.parseZipFile(context, uri)
                            }.filter { it.type != ZipType.UNKNOWN }

                            withContext(Dispatchers.Main) {
                                if (zipFileInfos.isNotEmpty()) {
                                    pendingZipFiles.value = zipFileInfos
                                    showConfirmationDialog.value = true
                                } else {
                                    finish()
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
                            if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
                                callback(true)
                                return
                            }

                            lifecycleScope.launch {
                                mutex.withLock {
                                    suspendCancellableCoroutine { continuation ->
                                        currentCallback = { result ->
                                            callback(result.any { it.value })
                                            continuation.resume(Unit)
                                        }

                                        if (requestDescription.isNotBlank() && ActivityCompat.shouldShowRequestPermissionRationale(
                                                this@MainActivity,
                                                permission
                                            )
                                        )
                                            Toast.makeText(
                                                context,
                                                requestDescription,
                                                Toast.LENGTH_SHORT
                                            ).show()

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
                                checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
                            }.toTypedArray()

                            if (permissionsToRequest.isEmpty()) {
                                callback(permissions.associateWith { true })
                                return
                            }

                            lifecycleScope.launch {
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
                                                    this@MainActivity,
                                                    perm
                                                )
                                            ) {
                                                val msg = requestDescription[perm]
                                                if (!msg.isNullOrBlank()) {
                                                    Toast.makeText(
                                                        this@MainActivity,
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
                                lifecycleScope.launch(Dispatchers.IO) {
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
                                finish()
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
                            }
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Increment intentState to trigger LaunchedEffect re-execution
        intentState.value += 1
    }

    private fun initializeViewModels() {
        superUserViewModel =
            ViewModelProvider(applicationContext as KernelSUApplication)[SuperUserViewModel::class.java]
        homeViewModel =
            ViewModelProvider(applicationContext as KernelSUApplication)[HomeViewModel::class.java]
        settingsViewModel =
            ViewModelProvider(applicationContext as KernelSUApplication)[SettingsViewModel::class.java]
        moduleViewModel =
            ViewModelProvider(applicationContext as KernelSUApplication)[ModuleViewModel::class.java]

        // 设置主题变化监听器
        themeChangeObserver = ThemeUtils.registerThemeChangeObserver(this)
    }

    private fun initializeData() {
        lifecycleScope.launch {
            try {
                superUserViewModel.fetchAppList()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 初始化主题相关设置
        ThemeUtils.initializeThemeSettings(this, settingsViewModel)
    }

    override fun onResume() {
        try {
            super.onResume()
            ThemeUtils.onActivityResume()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onPause() {
        try {
            super.onPause()
            ThemeUtils.onActivityPause(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        try {
            ThemeUtils.unregisterThemeChangeObserver(this, themeChangeObserver)
            super.onDestroy()
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
fun MainScreen() {
    // 页面隐藏处理
    LocalActivity.current as MainActivity

    var savedPages by rememberSaveable<MutableState<List<BottomBarDestination>>> {
        mutableStateOf(emptyList())
    }

    val pages by produceState(initialValue = savedPages) {
        value = withContext(Dispatchers.IO) {
            savedPages = BottomBarDestination.getPages()
            return@withContext savedPages
        }
    }

    val coroutineScope = rememberCoroutineScope()
    var uiSelectedPage by rememberSaveable { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(
        initialPage = uiSelectedPage,
        pageCount = { pages.size }
    )
    var userScrollEnabled by remember { mutableStateOf(true) }
    var animating by remember { mutableStateOf(false) }
    var animateJob by remember { mutableStateOf<Job?>(null) }
    var lastRequestedPage by remember { mutableIntStateOf(pagerState.currentPage) }

    val handlePageChange: (Int) -> Unit = remember(pagerState, coroutineScope) {
        { page ->
            uiSelectedPage = page
            if (page == pagerState.currentPage) {
                if (animateJob != null && lastRequestedPage != page) {
                    animateJob?.cancel()
                    animateJob = null
                    animating = false
                    userScrollEnabled = true
                }
                lastRequestedPage = page
            } else {
                if (animateJob != null && lastRequestedPage == page) {
                    // Already animating to the requested page
                } else {
                    animateJob?.cancel()
                    animating = true
                    userScrollEnabled = false
                    val job = coroutineScope.launch {
                        try {
                            pagerState.animateScrollToPage(page)
                        } finally {
                            if (animateJob === this) {
                                userScrollEnabled = true
                                animating = false
                                animateJob = null
                            }
                        }
                    }
                    animateJob = job
                    lastRequestedPage = page
                }
            }
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            if (!animating) uiSelectedPage = page
        }
    }

    BackHandler(pagerState.currentPage != 0) {
        handlePageChange(0)
    }

    CompositionLocalProvider(
        LocalPagerState provides pagerState,
        LocalHandlePageChange provides handlePageChange,
        LocalSelectedPage provides uiSelectedPage
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            val isPortrait = maxWidth < maxHeight || (maxHeight / maxWidth > 1.4f)
            val content = @Composable { paddingBottom: Dp ->
                HorizontalPager(
                    modifier = Modifier
                        .fillMaxSize()
                        .blurSource(),
                    state = pagerState,
                    userScrollEnabled = userScrollEnabled,
                    beyondViewportPageCount = 1,
                ) { pageIndex ->
                    if (pages.isEmpty()) return@HorizontalPager

                    val snackBarHostState = remember { SnackbarHostState() }
                    CompositionLocalProvider(
                        LocalSnackbarHost provides snackBarHostState,
                        LocalBlurState provides rememberMaterial3BlurBackdrop(ThemeConfig.isEnableBlur),
                    ) {
                        val destination = pages[pageIndex]
                        destination.direction(paddingBottom)
                    }
                }
            }

            if (isPortrait) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        NavigationBar(
                            destinations = pages,
                            isBottomBar = true,
                        )
                    },
                    containerColor = Color.Transparent,
                ) { innerPadding ->
                    content(innerPadding.calculateBottomPadding())
                }
            } else {
                Row(modifier = Modifier.fillMaxSize()) {
                    NavigationBar(
                        destinations = pages,
                        isBottomBar = false,
                    )
                    content(0.dp)
                }
            }
        }
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
