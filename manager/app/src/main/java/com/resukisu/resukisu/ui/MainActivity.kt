package com.resukisu.resukisu.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.resukisu.resukisu.domain.model.StartupState
import com.resukisu.resukisu.domain.usecase.ApplyLanguageUseCase
import com.resukisu.resukisu.domain.usecase.EnsureManagerInstalledUseCase
import com.resukisu.resukisu.domain.usecase.ObserveStartupStateUseCase
import com.resukisu.resukisu.ui.activity.util.ThemeChangeContentObserver
import com.resukisu.resukisu.ui.activity.util.ThemeUtils
import com.resukisu.resukisu.ui.component.ZipFileInfo
import com.resukisu.resukisu.ui.theme.KernelSUTheme
import com.resukisu.resukisu.ui.viewmodel.HomeUiAction
import com.resukisu.resukisu.ui.viewmodel.HomeViewModel
import com.resukisu.resukisu.ui.viewmodel.ModuleUiAction
import com.resukisu.resukisu.ui.viewmodel.ModuleViewModel
import com.resukisu.resukisu.ui.viewmodel.SettingsUiAction
import com.resukisu.resukisu.ui.viewmodel.SettingsUiEvent
import com.resukisu.resukisu.ui.viewmodel.SettingsViewModel
import com.resukisu.resukisu.ui.viewmodel.SuperUserUiAction
import com.resukisu.resukisu.ui.viewmodel.SuperUserViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity() {
    private val superUserViewModel: SuperUserViewModel by viewModel()
    private val homeViewModel: HomeViewModel by viewModel()
    private val moduleViewModel: ModuleViewModel by viewModel()
    private val settingsViewModel: SettingsViewModel by viewModel()
    private val observeStartupState: ObserveStartupStateUseCase by inject()
    private val ensureManagerInstalled: EnsureManagerInstalledUseCase by inject()
    private val themeUtils: ThemeUtils by inject()
    private val applyLanguage: ApplyLanguageUseCase by inject()
    private val startupState by lazy { observeStartupState() }

    private var showConfirmationDialog: MutableState<Boolean> = mutableStateOf(false)
    private var pendingZipFiles = mutableStateOf<List<ZipFileInfo>>(emptyList())

    private lateinit var themeChangeObserver: ThemeChangeContentObserver
    private var isInitialized = false

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase?.let(applyLanguage::invoke))
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

            splashScreen.setKeepOnScreenCondition {
                shouldKeepStartupSplash(
                    startupState = startupState.value,
                    homeInitialDataLoaded = homeViewModel.state.value.isInitialDataLoaded,
                )
            }

            lifecycleScope.launch { ensureManagerInstalled() }
            lifecycleScope.launch {
                settingsViewModel.events.collect { event ->
                    when (event) {
                        is SettingsUiEvent.Error -> if (event.message.isNotBlank()) {
                            Toast.makeText(this@MainActivity, event.message, Toast.LENGTH_LONG)
                                .show()
                        }

                        is SettingsUiEvent.Message -> {
                            val message = event.formatArg?.let {
                                getString(event.stringResource, it)
                            } ?: getString(event.stringResource)
                            Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                        }

                        SettingsUiEvent.RestartActivity -> recreate()
                    }
                }
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
                    when (val state = startupState.collectAsStateWithLifecycle().value) {
                        is StartupState.Failed -> StartupFailureContent(state.message)
                        else -> NavContainer(
                            zipUri = zipUri,
                            intentState = intentState,
                            settingsViewModel = settingsViewModel,
                            showConfirmationDialog = showConfirmationDialog,
                            pendingZipFiles = pendingZipFiles,
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
        // Register theme change observer.
        themeChangeObserver = themeUtils.registerThemeChangeObserver(this)
    }

    private fun initializeData() {
        lifecycleScope.launch {
            try {
                homeViewModel.dispatch(HomeUiAction.Refresh(showIndicator = false))
                superUserViewModel.dispatch(SuperUserUiAction.Refresh)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Initialize theme settings.
        themeUtils.initializeThemeSettings(this, settingsViewModel)
    }

    override fun onResume() {
        try {
            super.onResume()
            themeUtils.onActivityResume(this)
            synchronizeUiSettings()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun synchronizeUiSettings() {
        if (!isInitialized) return

        settingsViewModel.dispatch(SettingsUiAction.Initialize)
        moduleViewModel.dispatch(ModuleUiAction.ReloadSettings)
    }

    override fun onPause() {
        try {
            super.onPause()
            themeUtils.onActivityPause()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        try {
            themeUtils.unregisterThemeChangeObserver(this, themeChangeObserver)
            super.onDestroy()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@androidx.compose.runtime.Composable
private fun StartupFailureContent(reason: String) {
    Column(
        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = reason,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
