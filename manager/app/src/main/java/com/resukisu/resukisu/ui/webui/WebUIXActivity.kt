package com.resukisu.resukisu.ui.webui

import android.app.ActivityManager
import android.os.Build
import android.os.Bundle
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dergoogler.mmrl.platform.Platform
import com.dergoogler.mmrl.platform.model.ModId
import com.dergoogler.mmrl.ui.component.Loading
import com.dergoogler.mmrl.webui.model.WebUIConfig
import com.dergoogler.mmrl.webui.screen.WebUIScreen
import com.dergoogler.mmrl.webui.util.rememberWebUIOptions
import com.resukisu.resukisu.BuildConfig
import com.resukisu.resukisu.R
import com.resukisu.resukisu.ksuApp
import com.resukisu.resukisu.ui.theme.KernelSUTheme
import com.resukisu.resukisu.ui.viewmodel.ModuleViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class WebUIXActivity : ComponentActivity() {
    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        webView = WebView(this)

        lifecycleScope.launch {
            initPlatform()
        }

        val moduleId = intent.getStringExtra("id")!!
        val name = intent.getStringExtra("name")!!
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            @Suppress("DEPRECATION")
            setTaskDescription(ActivityManager.TaskDescription("ReSukiSU - $name"))
        } else {
            val taskDescription =
                ActivityManager.TaskDescription.Builder().setLabel("ReSukiSU - $name").build()
            setTaskDescription(taskDescription)
        }

        val prefs = getSharedPreferences("settings", MODE_PRIVATE)

        setContent {
            KernelSUTheme {
                var isLoading by remember { mutableStateOf(true) }
                val moduleViewModel = viewModel<ModuleViewModel>(
                    viewModelStoreOwner = ksuApp
                )

                LaunchedEffect(Unit) {
                    val moduleInfo = moduleViewModel.moduleList.find { info -> info.id == moduleId }

                    if (moduleInfo == null) {
                        Toast.makeText(
                            this@WebUIXActivity,
                            getString(R.string.no_such_module, moduleId),
                            Toast.LENGTH_LONG
                        ).show()
                        finish()
                        return@LaunchedEffect
                    }

                    if (!moduleInfo.hasWebUi || !moduleInfo.enabled || moduleInfo.remove) {
                        Toast.makeText(
                            this@WebUIXActivity,
                            getString(R.string.module_unavailable, moduleInfo.name),
                            Toast.LENGTH_LONG
                        ).show()
                        finish()
                    }
                }

                LaunchedEffect(Platform.isAlive) {
                    while (!Platform.isAlive) {
                        delay(1000)
                    }

                    isLoading = false
                }

                if (isLoading) {
                    Loading()
                    return@KernelSUTheme
                }

                val webDebugging = prefs.getBoolean("enable_web_debugging", false)
                val erudaInject = prefs.getBoolean("use_webuix_eruda", false)
                val dark = isSystemInDarkTheme()

                val options = rememberWebUIOptions(
                    modId = ModId(moduleId),
                    debug = webDebugging,
                    appVersionCode = BuildConfig.VERSION_CODE,
                    isDarkMode = dark,
                    enableEruda = erudaInject,
                    cls = WebUIXActivity::class.java,
                    userAgentString = ksuApp.UserAgent
                )

                // idk why webuix not allow root impl change webuiConfig
                // so we use magic to force exitConfirm shutdown
                val field = WebUIConfig::class.java.getDeclaredField("exitConfirm")
                field.isAccessible = true
                field.set(options.config, false)
                field.isAccessible = false

                WebUIScreen(
                    webView = webView,
                    options = options,
                    interfaces = listOf(
                        WebViewInterface.factory()
                    )
                )
            }
        }
    }
}