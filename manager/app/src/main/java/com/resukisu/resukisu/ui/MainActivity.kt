package com.resukisu.resukisu.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.net.toUri
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
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEvent.Companion.EDGE_LEFT
import androidx.navigationevent.NavigationEventTransitionState.InProgress
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.NavigationEventState
import androidx.navigationevent.compose.rememberNavigationEventState
import com.resukisu.resukisu.Natives
import com.resukisu.resukisu.ui.activity.component.NavigationBar
import com.resukisu.resukisu.ui.activity.util.ThemeChangeContentObserver
import com.resukisu.resukisu.ui.activity.util.ThemeUtils
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
import com.resukisu.resukisu.ui.screen.LogViewerScreen
import com.resukisu.resukisu.ui.screen.TemplateEditorScreen
import com.resukisu.resukisu.ui.screen.UmountManagerScreen
import com.resukisu.resukisu.ui.screen.about.AboutScreen
import com.resukisu.resukisu.ui.screen.about.OpenSourceLicenseScreen
import com.resukisu.resukisu.ui.screen.moduleRepo.ModuleRepoScreen
import com.resukisu.resukisu.ui.screen.moduleRepo.OnlineModuleDetailScreen
import com.resukisu.resukisu.ui.susfs.SuSFSConfigScreen
import com.resukisu.resukisu.ui.theme.CardConfig
import com.resukisu.resukisu.ui.theme.KernelSUTheme
import com.resukisu.resukisu.ui.theme.ThemeConfig
import com.resukisu.resukisu.ui.theme.backgroundImagePainter
import com.resukisu.resukisu.ui.theme.hazeSource
import com.resukisu.resukisu.ui.util.LocalHandlePageChange
import com.resukisu.resukisu.ui.util.LocalHazeState
import com.resukisu.resukisu.ui.util.LocalPagerState
import com.resukisu.resukisu.ui.util.LocalSelectedPage
import com.resukisu.resukisu.ui.util.LocalSnackbarHost
import com.resukisu.resukisu.ui.util.install
import com.resukisu.resukisu.ui.util.rootAvailable
import com.resukisu.resukisu.ui.viewmodel.HomeViewModel
import com.resukisu.resukisu.ui.viewmodel.SuperUserViewModel
import com.resukisu.resukisu.ui.webui.WebUIActivity
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import zako.zako.zako.zakoui.screen.kernelFlash.KernelFlashScreen
import zako.zako.zako.zakoui.screen.moreSettings.MoreSettingsScreen
import zako.zako.zako.zakoui.screen.moreSettings.util.LocaleHelper

class MainActivity : ComponentActivity() {
    private lateinit var superUserViewModel: SuperUserViewModel
    private lateinit var homeViewModel: HomeViewModel
    internal val settingsStateFlow = MutableStateFlow(SettingsState())

    data class SettingsState(
        val isHideOtherInfo: Boolean = false,
        val showKpmInfo: Boolean = false,
        val dpi: Int = 0
    )

    private var showConfirmationDialog = mutableStateOf(false)
    private var pendingZipFiles = mutableStateOf<List<ZipFileInfo>>(emptyList())

    private lateinit var themeChangeObserver: ThemeChangeContentObserver
    private var isInitialized = false

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase?.let { LocaleHelper.applyLanguage(it) })
    }

    private val intentState = MutableStateFlow(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        try {

            // Enable edge to edge
            enableEdgeToEdge()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }

            super.onCreate(savedInstanceState)

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
                    val snackBarHostState = remember { SnackbarHostState() }

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

                    val settings by settingsStateFlow.collectAsState()
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

                    CompositionLocalProvider(
                        LocalNavigator provides navigator,
                        LocalSnackbarHost provides snackBarHostState,
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

                        val exitAnimatable = remember { Animatable(0f) }
                        var exitingPageKey by remember { mutableStateOf<String?>(null) }
                        var gestureState: NavigationEventState<SceneInfo<NavKey>>? = null
                        val navigationScope = rememberCoroutineScope()
                        val onBack: (() -> Unit) -> Unit = { callBack ->
                            navigationScope.launch {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                    exitingPageKey = navigator.current().toString()
                                    exitAnimatable.animateTo(
                                        targetValue = 1f,
                                        animationSpec = tween(
                                            durationMillis = 200,
                                            easing = FastOutSlowInEasing
                                        )
                                    )
                                    exitAnimatable.snapTo(0f)
                                }

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
                        val windowInfo = LocalWindowInfo.current
                        val containerHeightPx = windowInfo.containerSize.height
                        val containerWidthPx = windowInfo.containerSize.width.toFloat()

                        val entries =
                            rememberDecoratedNavEntries(
                                backStack = navigator.backStack,
                                entryDecorators = listOf(
                                    rememberSaveableStateHolderNavEntryDecorator(),
                                    rememberViewModelStoreNavEntryDecorator(),
                                    NavEntryDecorator { content ->
                                        val pageKey = content.contentKey.toString()
                                        val navContent = LocalNavAnimatedContentScope.current
                                        val transition = navContent.transition

                                        val tripe =
                                            if (pageKey == navigator.current()
                                                    .toString() || exitingPageKey == content.contentKey.toString()
                                            ) {
                                                val animatedScale by transition.animateFloat(
                                                    label = "PredictiveScale"
                                                ) { state ->
                                                    when (state) {
                                                        EnterExitState.PostExit -> 0.85f
                                                        else -> 1f
                                                    }
                                                }

                                                val touchY =
                                                    (gestureState?.transitionState as? InProgress)?.latestEvent?.touchY

                                                val currentPivotY =
                                                    if (touchY != null && containerHeightPx > 0) {
                                                        (touchY / containerHeightPx).coerceIn(
                                                            0.1f,
                                                            0.9f
                                                        )
                                                    } else 0.5f

                                                val edge =
                                                    (gestureState?.transitionState as? InProgress)?.latestEvent?.swipeEdge
                                                        ?: 0

                                                val directionMultiplier =
                                                    if (edge == EDGE_LEFT) 1f else -1f
                                                val currentPivotX =
                                                    if (edge == EDGE_LEFT) 0.8f else 0.2f

                                                val progress = if (pageKey != navigator.current()
                                                        .toString()
                                                ) 1f else exitAnimatable.value
                                                val animatedTranslationX =
                                                    containerWidthPx * progress * directionMultiplier

                                                val modifier = Modifier.graphicsLayer {
                                                    scaleX = animatedScale
                                                    scaleY = animatedScale
                                                    translationX = animatedTranslationX
                                                    transformOrigin = TransformOrigin(
                                                        currentPivotX,
                                                        currentPivotY
                                                    )
                                                }
                                                val backgroundColor =
                                                    if (CardConfig.isCustomBackgroundEnabled)
                                                        Color.Transparent
                                                    else
                                                        MaterialTheme.colorScheme.surfaceContainer

                                                Triple(
                                                    modifier,
                                                    backgroundColor,
                                                    if (gestureState?.transitionState is InProgress) 16.dp else 0.dp
                                                )
                                            } else {
                                                val modifier =
                                                    if (gestureState?.transitionState is InProgress) {
                                                        val progress = exitAnimatable.value
                                                        val dynamicAlpha = 0.5f * (1f - progress)

                                                        Modifier
                                                            .graphicsLayer()
                                                            .drawWithContent {
                                                                drawContent()
                                                                drawRect(
                                                                    color = Color.Black.copy(
                                                                        alpha = dynamicAlpha
                                                                    )
                                                                )
                                                            }
                                                    } else Modifier

                                                Triple(modifier, Color.Transparent, 0.dp)
                                            }

                                        val surfaceContainer =
                                            MaterialTheme.colorScheme.surfaceContainer

                                        CompositionLocalProvider(
                                            LocalHazeState provides if (CardConfig.isCustomBackgroundEnabled) rememberHazeState() else null
                                        ) {
                                            Surface(
                                                modifier = tripe.first,
                                                color = tripe.second,
                                                shape = RoundedCornerShape(tripe.third),
                                            ) {
                                                Box(
                                                    modifier = backgroundImagePainter?.let {
                                                        Modifier
                                                            .fillMaxSize()
                                                            .zIndex(-1f)
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
                                                            .hazeSource()
                                                    } ?: Modifier
                                                )
                                                content.Content()
                                            }
                                        }
                                    }
                                ),
                                entryProvider = entryProvider {
                                    entry<Route.About> { AboutScreen() }
                                    entry<Route.OpenSourceLicense> { OpenSourceLicenseScreen() }
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
                                    entry<Route.MoreSettings> { MoreSettingsScreen() }
                                    entry<Route.SuSFSConfig> { SuSFSConfigScreen() }
                                    entry<Route.LogViewer> { LogViewerScreen() }
                                    entry<Route.UmountManager> { UmountManagerScreen() }
                                    entry<Route.KernelFlash> { key ->
                                        KernelFlashScreen(
                                            key.kernelUri,
                                            key.selectedSlot,
                                            key.kpmPatchEnabled,
                                            key.kpmUndoPatch
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
                            predictivePopTransitionSpec = {
                                ContentTransform(
                                    targetContentEnter = EnterTransition.None,
                                    initialContentExit = ExitTransition.None,
                                    sizeTransform = null
                                )
                            },
                            popTransitionSpec = {
                                ContentTransform(
                                    targetContentEnter = slideInHorizontally(initialOffsetX = { -it / 4 }) + fadeIn(),
                                    initialContentExit = scaleOut(targetScale = 0.9f) + fadeOut(),
                                    sizeTransform = null
                                )
                            },
                            transitionSpec = {
                                ContentTransform(
                                    targetContentEnter = slideInHorizontally(initialOffsetX = { it }),
                                    initialContentExit = slideOutHorizontally(targetOffsetX = { -it / 4 }) + fadeOut(),
                                    sizeTransform = null
                                )
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
        superUserViewModel = SuperUserViewModel()
        homeViewModel = HomeViewModel()

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
        ThemeUtils.initializeThemeSettings(this, settingsStateFlow)
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

@Composable
fun MainScreen() {
    // 页面隐藏处理
    val activity = LocalActivity.current as MainActivity
    val settings by activity.settingsStateFlow.collectAsState()

    var savedPages by rememberSaveable<MutableState<List<BottomBarDestination>>> {
        mutableStateOf(emptyList())
    }

    val pages by produceState(initialValue = savedPages) {
        value = withContext(Dispatchers.IO) {
            savedPages = BottomBarDestination.getPages(settings)
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

    BackHandler {
        if (pagerState.currentPage != 0) {
            handlePageChange(0)
        } else {
            activity.moveTaskToBack(true)
        }
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
                    modifier = Modifier.fillMaxSize(),
                    state = pagerState,
                    userScrollEnabled = userScrollEnabled,
                ) { pageIndex ->
                    if (pages.isEmpty()) return@HorizontalPager
                    val destination = pages[pageIndex]
                    destination.direction(paddingBottom)
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