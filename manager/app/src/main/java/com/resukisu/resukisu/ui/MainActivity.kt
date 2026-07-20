package com.resukisu.resukisu.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.resukisu.resukisu.KernelSUApplication
import com.resukisu.resukisu.Natives
import com.resukisu.resukisu.ui.activity.util.ThemeChangeContentObserver
import com.resukisu.resukisu.ui.activity.util.ThemeUtils
import com.resukisu.resukisu.ui.component.ZipFileInfo
import com.resukisu.resukisu.ui.screen.themeSettings.util.applyLanguage
import com.resukisu.resukisu.ui.theme.KernelSUTheme
import com.resukisu.resukisu.ui.util.install
import com.resukisu.resukisu.ui.viewmodel.HomeViewModel
import com.resukisu.resukisu.ui.viewmodel.ModuleViewModel
import com.resukisu.resukisu.ui.viewmodel.SettingsViewModel
import com.resukisu.resukisu.ui.viewmodel.SuperUserViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var superUserViewModel: SuperUserViewModel
    private lateinit var homeViewModel: HomeViewModel
    private lateinit var moduleViewModel: ModuleViewModel
    private lateinit var settingsViewModel: SettingsViewModel

    private var showConfirmationDialog: MutableState<Boolean> = mutableStateOf(false)
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
            }

            val isManager = Natives.isManager
            if (isManager && !Natives.requireNewKernel()) {
                install()
            }

            // Initialize app state once.
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
                    NavContainer(
                        zipUri = zipUri,
                        intentState = intentState,
                        settingsViewModel = settingsViewModel,
                        showConfirmationDialog = showConfirmationDialog,
                        pendingZipFiles = pendingZipFiles,
                    )
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

        // Register theme change observer.
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

        // Initialize theme settings.
        ThemeUtils.initializeThemeSettings(this, settingsViewModel)
    }
    
    // 新增刷新方法代码开始
    private fun refreshDataOnResume() {
        // 1. 主页：静默刷新（与下拉刷新逻辑一致，仅无动画）
        if (::homeViewModel.isInitialized) {
            homeViewModel.refreshDataSilently(applicationContext)
        }
        // 2. 模块页面：静默刷新（manualRefresh = true 完整检查更新，silent = true 无动画）
        if (::moduleViewModel.isInitialized) {
            moduleViewModel.fetchModuleList(manualRefresh = true, silent = true)
        }
        // 3. 超级用户页面：静默刷新
        if (::superUserViewModel.isInitialized) {
            lifecycleScope.launch {
                superUserViewModel.fetchAppList(silent = true)
            }
        }
    }
    // 新增刷新方法代码结束
    
    override fun onResume() {
        try {
            super.onResume()
            ThemeUtils.onActivityResume()
            refreshDataOnResume()  //新增刷新方法调用
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
