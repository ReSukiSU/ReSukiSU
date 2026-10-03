package com.resukisu.resukisu.ui.webui

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.resukisu.resukisu.R
import com.resukisu.resukisu.data.AppSettingsRepository
import com.resukisu.resukisu.data.webui.RemoteWebUiBackend
import com.resukisu.resukisu.data.webui.WearWebUiProtocol
import com.resukisu.resukisu.ui.theme.KernelSUTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/**
 * The phone side of a watch module's WebUI, opened by the watch through RemoteActivityHelper with a
 * `resukisu-webui://open` link. The link carries no data and anyone may send it: the activity only
 * shows a session that a watch offered to this phone and that it claims over the Data Layer. The
 * page, its files and its `ksu` bridge commands are all served by the watch, so every change the
 * page makes is applied on the watch; closing the page tells the watch to end the session and
 * reload its modules.
 */
class RemoteWebUIActivity : ComponentActivity() {
    private var backend: RemoteWebUiBackend? = null

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        super.onCreate(savedInstanceState)

        if (intent.data?.scheme != WearWebUiProtocol.SCHEME) {
            finish()
            return
        }

        setContent {
            KernelSUTheme {
                val webUIState = remember { WebUIState() }
                val settingsRepository = koinInject<AppSettingsRepository>()
                val colorsCss = koinInject<MonetColorsProvider>().getColorsCss()
                val currentColorsCss = rememberUpdatedState(colorsCss)
                val claimFailed = stringResource(R.string.operation_failed)
                LaunchedEffect(Unit) {
                    val remote = withContext(Dispatchers.IO) { RemoteWebUiBackend.claim(applicationContext) }
                    if (remote == null) {
                        webUIState.uiEvent = WebUIEvent.Error(claimFailed)
                        return@LaunchedEffect
                    }
                    backend = remote
                    webUIState.moduleName = remote.moduleName
                    webUIState.modDir = "/data/adb/modules/${remote.moduleId}"
                    createWebView(this@RemoteWebUIActivity, webUIState, remote, settingsRepository,
                        restrictToModule = true, colorsCssProvider = { currentColorsCss.value })
                }
                DisposableEffect(Unit) { onDispose { webUIState.dispose() } }

                when (val event = webUIState.uiEvent) {
                    is WebUIEvent.Error -> LaunchedEffect(event) {
                        Toast.makeText(this@RemoteWebUIActivity, event.message, Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    is WebUIEvent.Close -> LaunchedEffect(event) { finish() }
                    else -> Unit
                }
                Crossfade(targetState = webUIState.uiEvent is WebUIEvent.Loading, animationSpec = tween(300)) { loading ->
                    if (loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
                    else WebUIScreen(webUIState = webUIState)
                }
            }
        }
    }

    override fun onDestroy() {
        if (isFinishing) backend?.close(applicationContext)
        super.onDestroy()
    }
}
