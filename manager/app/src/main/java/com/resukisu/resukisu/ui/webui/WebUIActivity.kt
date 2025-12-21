package com.resukisu.resukisu.ui.webui

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebViewAssetLoader
import com.dergoogler.mmrl.platform.model.ModId
import com.dergoogler.mmrl.webui.interfaces.WXOptions
import com.resukisu.resukisu.ui.util.createRootShell
import com.resukisu.resukisu.ui.viewmodel.SuperUserViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

@SuppressLint("SetJavaScriptEnabled")
class WebUIActivity : ComponentActivity() {
    private val rootShell by lazy { createRootShell(true) }

    private lateinit var insets: Insets
    private var webView = null as WebView?
    private lateinit var container: FrameLayout
    private var isInsetsEnabled = false

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {

        // Enable edge to edge
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        super.onCreate(savedInstanceState)

        setContent {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator()
            }
        }

        lifecycleScope.launch {
            SuperUserViewModel.isAppListLoaded.first { it }
            setupWebView()
        }
    }
    private fun setupWebView() {
        val moduleId = intent.getStringExtra("id") ?: finishAndRemoveTask().let { return }
        val name = intent.getStringExtra("name") ?: finishAndRemoveTask().let { return }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            @Suppress("DEPRECATION")
            setTaskDescription(ActivityManager.TaskDescription("SukiSU-Ultra - $name"))
        } else {
            val taskDescription =
                ActivityManager.TaskDescription.Builder().setLabel("SukiSU-Ultra - $name").build()
            setTaskDescription(taskDescription)
        }

        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        WebView.setWebContentsDebuggingEnabled(prefs.getBoolean("enable_web_debugging", false))

        val moduleDir = "/data/adb/modules/${moduleId}"
        val webRoot = File("${moduleDir}/webroot")
        insets = Insets(0, 0, 0, 0)
        val webViewAssetLoader = WebViewAssetLoader.Builder()
            .setDomain("mui.kernelsu.org")
            .addPathHandler(
                "/",
                SuFilePathHandler(webRoot, rootShell, { insets }, { enable ->
                    enableInsets(enable)
                })
            )
            .build()

        val webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                val url = request.url
                // Handle ksu://icon/[packageName] to serve app icon via WebView
                if (url.scheme.equals("ksu", ignoreCase = true) && url.host.equals("icon", ignoreCase = true)) {
                    val packageName = url.path?.substring(1)
                    if (!packageName.isNullOrEmpty()) {
                        val icon = AppIconUtil.loadAppIconSync(this@WebUIActivity, packageName, 512)
                        if (icon != null) {
                            val stream = java.io.ByteArrayOutputStream()
                            icon.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
                            val inputStream = java.io.ByteArrayInputStream(stream.toByteArray())
                            return WebResourceResponse("image/png", null, inputStream)
                        }
                    }
                }
                return webViewAssetLoader.shouldInterceptRequest(url)
            }
        }

        container = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        this.webView = WebView(this).apply {

            setBackgroundColor(Color.TRANSPARENT)
            val density = resources.displayMetrics.density

            ViewCompat.setOnApplyWindowInsetsListener(container) { view, windowInsets ->
                val inset = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                insets = Insets(
                    top = (inset.top / density).toInt(),
                    bottom = (inset.bottom / density).toInt(),
                    left = (inset.left / density).toInt(),
                    right = (inset.right / density).toInt()
                )
                if (isInsetsEnabled) {
                    view.setPadding(0, 0, 0, 0)
                } else {
                    view.setPadding(inset.left, inset.top, inset.right, inset.bottom)
                }
                WindowInsetsCompat.CONSUMED
            }
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            addJavascriptInterface(WebViewInterface(WXOptions(this@WebUIActivity, this, ModId(moduleId))), "ksu")
            setWebViewClient(webViewClient)
            loadUrl("https://mui.kernelsu.org/index.html")
        }

        container.addView(this.webView)
        setContentView(container)
    }

    fun enableInsets(enable: Boolean = true) {
        runOnUiThread {
            if (isInsetsEnabled != enable) {
                isInsetsEnabled = enable
                ViewCompat.requestApplyInsets(container)
            }
        }
    }

    override fun onDestroy() {
        rootShell.runCatching { close() }
        webView?.apply {
            stopLoading()
            removeAllViews()
            destroy()
            webView = null
        }
        super.onDestroy()
    }
}