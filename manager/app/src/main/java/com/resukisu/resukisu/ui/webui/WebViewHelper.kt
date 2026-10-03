package com.resukisu.resukisu.ui.webui

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import com.resukisu.resukisu.R
import com.resukisu.resukisu.data.AppSettingsRepository
import com.resukisu.resukisu.data.packageinfo.AppIconDataSource
import com.resukisu.resukisu.data.packageinfo.InstalledPackageRepository
import com.resukisu.resukisu.data.webui.LocalWebUiBackend
import com.resukisu.resukisu.data.webui.WebUiBackend
import com.resukisu.resukisu.data.webui.WebUiRepository
import com.resukisu.resukisu.ui.viewmodel.ModuleUiAction
import com.resukisu.resukisu.ui.viewmodel.ModuleUiEvent
import com.resukisu.resukisu.ui.viewmodel.ModuleViewModel
import com.resukisu.resukisu.ui.viewmodel.SuperUserUiAction
import com.resukisu.resukisu.ui.viewmodel.SuperUserViewModel
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.time.Duration.Companion.milliseconds


@SuppressLint("SetJavaScriptEnabled")
internal suspend fun prepareWebView(
    activity: Activity,
    moduleId: String,
    webUIState: WebUIState,
    moduleViewModel: ModuleViewModel,
    superUserViewModel: SuperUserViewModel,
    settingsRepository: AppSettingsRepository,
    packageRepository: InstalledPackageRepository,
    appIconDataSource: AppIconDataSource,
    webUiRepository: WebUiRepository,
    colorsCssProvider: () -> String,
    recoverFromRenderCrash: Boolean = false,
) {
    withContext(Dispatchers.IO) {
        val refreshEvent = async(start = CoroutineStart.UNDISPATCHED) {
            moduleViewModel.events.first { event ->
                event is ModuleUiEvent.RefreshCompleted || event is ModuleUiEvent.Error
            }
        }
        moduleViewModel.dispatch(ModuleUiAction.Refresh())
        when (val event = withTimeoutOrNull(30_000L.milliseconds) { refreshEvent.await() }) {
            is ModuleUiEvent.Error -> {
                withContext(Dispatchers.Main) {
                    webUIState.uiEvent = WebUIEvent.Error(
                        activity.getString(R.string.module_unavailable, event.message),
                    )
                }
                return@withContext
            }

            null -> {
                withContext(Dispatchers.Main) {
                    webUIState.uiEvent = WebUIEvent.Error(
                        activity.getString(R.string.module_unavailable, moduleId),
                    )
                }
                return@withContext
            }

            else -> Unit
        }

        val moduleInfo =
            moduleViewModel.uiState.value.moduleList.find { info -> info.id == moduleId }

        if (moduleInfo == null) {
            withContext(Dispatchers.Main) {
                webUIState.uiEvent = WebUIEvent.Error(activity.getString(R.string.no_such_module, moduleId))
            }
            return@withContext
        }

        if (!moduleInfo.hasWebUi || !moduleInfo.enabled || moduleInfo.remove) {
            withContext(Dispatchers.Main) {
                webUIState.uiEvent = WebUIEvent.Error(activity.getString(R.string.module_unavailable, moduleInfo.name))
            }
            return@withContext
        }

        webUIState.moduleName = moduleInfo.name
        webUIState.modDir = "/data/adb/modules/${moduleId}"

        if (packageRepository.packages.value.isEmpty()) {
            superUserViewModel.dispatch(SuperUserUiAction.Refresh)
        }
        withContext(Dispatchers.Main) {
            createWebView(
                activity,
                webUIState,
                LocalWebUiBackend(webUiRepository, packageRepository, appIconDataSource),
                settingsRepository,
                colorsCssProvider = colorsCssProvider,
                recoverFromRenderCrash = recoverFromRenderCrash,
            )
        }
    }
}

private fun Uri.isModuleOrigin() = scheme == "https" && host == MODULE_HOST ||
    scheme.equals("ksu", ignoreCase = true) && host.equals("icon", ignoreCase = true)

private const val MODULE_HOST = "mui.kernelsu.org"

/**
 * Creates the module WebView for [webUIState], whose module name and directory are already set,
 * serving files, commands and the `ksu` bridge from [backend]. With [restrictToModule] nothing
 * outside the module's own origin loads into the page, so no other site can reach the bridge; links
 * elsewhere open in the browser. With [recoverFromRenderCrash] a crashed renderer closes the page
 * with an error instead of killing the app, as low-memory watches need. Must run on the main thread.
 */
@SuppressLint("SetJavaScriptEnabled")
internal fun createWebView(
    activity: Activity,
    webUIState: WebUIState,
    backend: WebUiBackend,
    settingsRepository: AppSettingsRepository,
    restrictToModule: Boolean = false,
    colorsCssProvider: () -> String,
    recoverFromRenderCrash: Boolean = false,
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        @Suppress("DEPRECATION")
        activity.setTaskDescription(ActivityManager.TaskDescription("KernelSU - ${webUIState.moduleName}"))
    } else {
        val taskDescription = ActivityManager.TaskDescription.Builder()
            .setLabel("KernelSU - ${webUIState.moduleName}")
            .build()
        activity.setTaskDescription(taskDescription)
    }

    val webView = WebView(activity)
    webView.setBackgroundColor(Color.TRANSPARENT)

    WebView.setWebContentsDebuggingEnabled(
        settingsRepository.getBoolean("enable_web_debugging", false)
    )

    webView.settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        allowFileAccess = false
    }

    val webRoot = File("${webUIState.modDir}/webroot")
    val webViewAssetLoader = WebViewAssetLoader.Builder()
        .setDomain(MODULE_HOST)
        .addPathHandler(
            "/",
            SuFilePathHandler(
                webRoot,
                backend,
                { webUIState.currentInsets },
                { enable -> webUIState.isInsetsEnabled = enable },
                colorsCssProvider,
            )
        )
        .build()

    // WebViewClient
    webView.webViewClient = object : WebViewClient() {
        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            webUIState.pageLoaded = false
        }

        override fun onPageFinished(view: WebView, url: String?) {
            webUIState.pageLoaded = true
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            if (!recoverFromRenderCrash) {
                return super.onRenderProcessGone(view, detail)
            }
            (view.parent as? ViewGroup)?.removeView(view)
            view.destroy()
            webUIState.webView = null
            webUIState.uiEvent = WebUIEvent.Error(activity.getString(R.string.operation_failed))
            return true
        }

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            val url = request.url
            if (url.scheme.equals("ksu", ignoreCase = true) && url.host.equals("icon", ignoreCase = true)) {
                val packageName = url.path?.substring(1)
                if (!packageName.isNullOrEmpty()) {
                    val icon = backend.iconPng(packageName, 512)
                    if (icon != null) {
                        return WebResourceResponse(
                            "image/png", null, 200, "OK",
                            mapOf("Access-Control-Allow-Origin" to "*"),
                            ByteArrayInputStream(icon)
                        )
                    }
                }
            }
            return webViewAssetLoader.shouldInterceptRequest(url) ?: if (restrictToModule && !url.isModuleOrigin()) {
                WebResourceResponse("text/plain", null, 403, "Forbidden", emptyMap(), ByteArrayInputStream(ByteArray(0)))
            } else null
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (!restrictToModule || request.url.isModuleOrigin()) return false
            if (request.isForMainFrame && request.url.scheme in listOf("https", "http")) {
                runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
            }
            return true
        }

        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
            webUIState.webCanGoBack = view?.canGoBack() ?: false
            if (webUIState.isInsetsEnabled) webUIState.webView?.evaluateJavascript(webUIState.currentInsets.js, null)
            super.doUpdateVisitedHistory(view, url, isReload)
        }
    }

    // WebChromeClient
    webView.webChromeClient = object : WebChromeClient() {
        override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
            if (message == null || result == null) return false
            webUIState.uiEvent = WebUIEvent.ShowAlert(message, result)
            return true
        }

        override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
            if (message == null || result == null) return false
            webUIState.uiEvent = WebUIEvent.ShowConfirm(message, result)
            return true
        }

        override fun onJsPrompt(view: WebView?, url: String?, message: String?, defaultValue: String?, result: JsPromptResult?): Boolean {
            if (message == null || result == null || defaultValue == null) return false
            webUIState.uiEvent = WebUIEvent.ShowPrompt(message, defaultValue, result)
            return true
        }

        override fun onShowFileChooser(
            webView: WebView?, filePathCallback: ValueCallback<Array<Uri>>?, fileChooserParams: FileChooserParams?
        ): Boolean {
            webUIState.filePathCallback?.onReceiveValue(null)
            webUIState.filePathCallback = filePathCallback

            val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" }
            if (fileChooserParams?.mode == FileChooserParams.MODE_OPEN_MULTIPLE) {
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            webUIState.uiEvent = WebUIEvent.ShowFileChooser(intent)
            return true
        }
    }

    // JS Interface
    val webviewInterface = WebViewInterface(webUIState, backend)
    webUIState.webView = webView
    webView.addJavascriptInterface(webviewInterface, "ksu")
    webUIState.uiEvent = WebUIEvent.WebViewReady
}
