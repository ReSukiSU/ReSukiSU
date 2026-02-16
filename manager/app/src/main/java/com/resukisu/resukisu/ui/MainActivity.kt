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
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.rememberNavController
import com.ramcosta.composedestinations.DestinationsNavHost
import com.ramcosta.composedestinations.animations.NavHostAnimatedDestinationStyle
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.NavGraphs
import com.ramcosta.composedestinations.generated.destinations.ExecuteModuleActionScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.spec.NavHostGraphSpec
import com.ramcosta.composedestinations.utils.rememberDestinationsNavigator
import com.resukisu.resukisu.Natives
import com.resukisu.resukisu.ui.activity.component.NavigationBar
import com.resukisu.resukisu.ui.activity.util.ThemeChangeContentObserver
import com.resukisu.resukisu.ui.activity.util.ThemeUtils
import com.resukisu.resukisu.ui.activity.util.UltraActivityUtils
import com.resukisu.resukisu.ui.component.InstallConfirmationDialog
import com.resukisu.resukisu.ui.component.ZipFileInfo
import com.resukisu.resukisu.ui.screen.BottomBarDestination
import com.resukisu.resukisu.ui.theme.KernelSUTheme
import com.resukisu.resukisu.ui.theme.ThemeConfig
import com.resukisu.resukisu.ui.util.LocalHandlePageChange
import com.resukisu.resukisu.ui.util.LocalPagerState
import com.resukisu.resukisu.ui.util.LocalSelectedPage
import com.resukisu.resukisu.ui.util.LocalSnackbarHost
import com.resukisu.resukisu.ui.util.install
import com.resukisu.resukisu.ui.viewmodel.HomeViewModel
import com.resukisu.resukisu.ui.viewmodel.SuperUserViewModel
import com.resukisu.resukisu.ui.webui.WebUIActivity
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
                    val navController = rememberNavController()
                    val snackBarHostState = remember { SnackbarHostState() }

                    val navigator = navController.rememberDestinationsNavigator()

                    InstallConfirmationDialog(
                        show = showConfirmationDialog.value,
                        zipFiles = pendingZipFiles.value,
                        onConfirm = { confirmedFiles ->
                            showConfirmationDialog.value = false
                            UltraActivityUtils.navigateToFlashScreen(this, confirmedFiles, navigator)
                        },
                        onDismiss = {
                            showConfirmationDialog.value = false
                            pendingZipFiles.value = emptyList()
                            finish()
                        }
                    )

                    LaunchedEffect(zipUri) {
                        if (!zipUri.isNullOrEmpty()) {
                            // 检测 ZIP 文件类型并显示确认对话框
                            lifecycleScope.launch {
                                UltraActivityUtils.detectZipTypeAndShowConfirmation(this@MainActivity, zipUri) { infos ->
                                    if (infos.isNotEmpty()) {
                                        pendingZipFiles.value = infos
                                        showConfirmationDialog.value = true
                                    } else {
                                        finish()
                                    }
                                }
                            }
                        }
                    }

                    ShortcutIntentHandler(
                        intentState = intentState,
                        navigator = navigator
                    )
                    val settings by settingsStateFlow.collectAsState()
                    val systemDensity = LocalDensity.current

                    val density = remember(systemDensity, settings.dpi) {
                        if (settings.dpi <= 0f) {
                            systemDensity
                        } else {
                            // 直接计算自定义 DPI 占 160 (标准 MDPI) 的比例
                            // 公式：Density = TargetDPI / 160
                            val targetDensity = settings.dpi / 160f
                            Density(density = targetDensity, fontScale = systemDensity.fontScale)
                        }
                    }

                    CompositionLocalProvider(
                        LocalSnackbarHost provides snackBarHostState,
                        LocalDensity provides density
                    ) {
                        DestinationsNavHost(
                            navGraph = NavGraphs.root as NavHostGraphSpec,
                            navController = navController,
                            defaultTransitions = object :
                                NavHostAnimatedDestinationStyle() {
                                override val enterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
                                    slideInHorizontally(initialOffsetX = { it })
                                }

                                override val exitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
                                    slideOutHorizontally(targetOffsetX = { -it / 4 }) + fadeOut()
                                }

                                override val popEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
                                    slideInHorizontally(initialOffsetX = { -it / 4 }) + fadeIn()
                                }

                                override val popExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
                                    scaleOut(targetScale = 0.9f) + fadeOut()
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

/**
 * @param navigator 页面导航
 */
@Destination<RootGraph>(start = true)
@Composable
fun MainScreen(navigator: DestinationsNavigator) {
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
    val hazeState = if (ThemeConfig.backgroundImageLoaded) rememberHazeState() else null

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
            MainScreenContent(
                isPortrait = isPortrait,
                pages = pages,
                hazeState = hazeState,
                navigator = navigator,
                userScrollEnabled = userScrollEnabled,
                pagerState = pagerState,
            )
        }
    }
}

@Composable
private fun MainScreenContent(
    isPortrait: Boolean,
    pages: List<BottomBarDestination>,
    hazeState: HazeState?,
    navigator: DestinationsNavigator,
    userScrollEnabled: Boolean,
    pagerState: PagerState
) {
    val content = @Composable { paddingBottom: Dp ->
        HorizontalPager(
            modifier = Modifier.fillMaxSize(),
            state = pagerState,
            beyondViewportPageCount = 2,
            userScrollEnabled = userScrollEnabled,
        ) { pageIndex ->
            if (pages.isEmpty()) return@HorizontalPager
            val destination = pages[pageIndex]
            destination.direction(navigator, paddingBottom, hazeState)
        }
    }

    if (isPortrait) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                NavigationBar(
                    destinations = pages,
                    hazeState = hazeState,
                    isBottomBar = true,
                )
            },
            containerColor = Color.Transparent,
        ) { innerPadding ->
            val bottomPadding = remember(innerPadding) { innerPadding.calculateBottomPadding() }
            content(bottomPadding)
        }
    } else {
        Row(modifier = Modifier.fillMaxSize()) {
            NavigationBar(
                destinations = pages,
                hazeState = hazeState,
                isBottomBar = false,
            )
            content(0.dp)
        }
    }
}

@Composable
private fun ShortcutIntentHandler(
    intentState: MutableStateFlow<Int>,
    navigator: DestinationsNavigator
) {
    val activity = LocalActivity.current ?: return
    val context = LocalContext.current
    val intentStateValue by intentState.collectAsState()
    LaunchedEffect(intentStateValue) {
        val intent = activity.intent
        val type = intent?.getStringExtra("shortcut_type") ?: return@LaunchedEffect
        when (type) {
            "module_action" -> {
                val moduleId = intent.getStringExtra("module_id") ?: return@LaunchedEffect
                navigator.navigate(ExecuteModuleActionScreenDestination(moduleId)) {
                    launchSingleTop = true
                }
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