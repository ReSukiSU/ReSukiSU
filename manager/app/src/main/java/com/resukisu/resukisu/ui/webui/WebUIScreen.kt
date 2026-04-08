package com.resukisu.resukisu.ui.webui

import android.app.Activity
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.resukisu.resukisu.R
import com.resukisu.resukisu.ui.component.settings.SettingsTextFieldWidget

@Composable
fun WebUIScreen(webUIState: WebUIState) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val drawingInsets = WindowInsets.safeDrawing
    val systemBarsInsets = WindowInsets.systemBars
    val imeInsets = WindowInsets.ime
    val innerPadding = if (webUIState.isInsetsEnabled) imeInsets.asPaddingValues() else drawingInsets.asPaddingValues()

    LaunchedEffect(density, layoutDirection, systemBarsInsets, webUIState.isInsetsEnabled) {
        if (!webUIState.isInsetsEnabled) {
            return@LaunchedEffect
        }
        snapshotFlow {
            val top = (systemBarsInsets.getTop(density) / density.density).toInt()
            val bottom = (systemBarsInsets.getBottom(density) / density.density).toInt()
            val left = (systemBarsInsets.getLeft(density, layoutDirection) / density.density).toInt()
            val right = (systemBarsInsets.getRight(density, layoutDirection) / density.density).toInt()
            Insets(top, bottom, left, right)
        }.collect { newInsets ->
            if (webUIState.currentInsets != newInsets) {
                webUIState.currentInsets = newInsets
                webUIState.webView?.evaluateJavascript(newInsets.js, null)
            }
        }
    }

    BackHandler(enabled = webUIState.webCanGoBack) {
        webUIState.webView?.goBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
    ) {
        if (webUIState.webView != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { _ ->
                    webUIState.webView!!.apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        if (!webUIState.isUrlLoaded) {
                            val homePage = "https://mui.kernelsu.org/index.html"
                            if (width > 0 && height > 0) {
                                loadUrl(homePage)
                                webUIState.isUrlLoaded = true
                            } else {
                                val listener = object : View.OnLayoutChangeListener {
                                    override fun onLayoutChange(
                                        v: View, left: Int, top: Int, right: Int, bottom: Int,
                                        oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int
                                    ) {
                                        if (v.width > 0 && v.height > 0) {
                                            (v as WebView).loadUrl(homePage)
                                            webUIState.isUrlLoaded = true
                                            v.removeOnLayoutChangeListener(this)
                                        }
                                    }
                                }
                                addOnLayoutChangeListener(listener)
                            }
                        }
                    }
                },
                update = { view ->
                    view.requestLayout()
                },
            )
        }
    }

    HandleWebUIEvent(webUIState)
    HandleWebViewLifecycle(webUIState)
    HandleConfigurationChanges(webUIState)
}

@Composable
private fun HandleWebUIEvent(webUIState: WebUIState) {

    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uris: Array<Uri>? = if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { data ->
                if (data.clipData != null) {
                    Array(data.clipData!!.itemCount) { i -> data.clipData!!.getItemAt(i).uri }
                } else {
                    data.data?.let { arrayOf(it) }
                }
            }
        } else null
        webUIState.onFileChooserResult(uris)
    }

    when (val event = webUIState.uiEvent) {
        is WebUIEvent.ShowAlert -> {
            var showDialog by remember(event) { mutableStateOf(true) }

            if (showDialog) {
                AlertDialog(
                    onDismissRequest = {
                        webUIState.onAlertResult()
                        showDialog = false
                    },
                    title = {
                        Text(text = stringResource(R.string.module_webui_alert, webUIState.moduleName))
                    },
                    text = {
                        Text(event.message)
                    },
                    confirmButton = {
                        Button(onClick = {
                            webUIState.onAlertResult()
                            showDialog = false
                        }) {
                            Text(text = stringResource(R.string.confirm))
                        }
                    }
                )
            }
        }

        is WebUIEvent.ShowConfirm -> {
            var showDialog by remember(event) { mutableStateOf(true) }

            if (showDialog) {
                AlertDialog(
                    onDismissRequest = {
                        webUIState.onConfirmResult(false)
                        showDialog = false
                    },
                    title = {
                        Text(text = stringResource(R.string.module_webui_alert, webUIState.moduleName))
                    },
                    text = {
                        Text(event.message)
                    },
                    confirmButton = {
                        Button(onClick = {
                            webUIState.onConfirmResult(true)
                            showDialog = false
                        }) {
                            Text(text = stringResource(R.string.confirm))
                        }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = {
                            webUIState.onConfirmResult(false)
                            showDialog = false
                        }) {
                            Text(text = stringResource(R.string.cancel))
                        }
                    }
                )
            }
        }

        is WebUIEvent.ShowPrompt -> {
            var showDialog by remember(event) { mutableStateOf(true) }
            val state = rememberTextFieldState(event.defaultValue)

            if (showDialog) {
                AlertDialog(
                    onDismissRequest = {
                        webUIState.onPromptResult(null)
                        showDialog = false
                    },
                    title = {
                        Text(text = stringResource(R.string.module_webui_alert, webUIState.moduleName))
                    },
                    text = {
                        Surface(
                            modifier = Modifier.clip(RoundedCornerShape(16.dp)),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            SettingsTextFieldWidget(
                                state = state,
                                title = event.message,
                                lineLimits = TextFieldLineLimits.SingleLine,
                                useLabelAsPlaceholder = true
                            )
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            webUIState.onPromptResult(state.text.toString())
                            showDialog = false
                        }) {
                            Text(text = stringResource(R.string.confirm))
                        }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = {
                            webUIState.onPromptResult(null)
                            showDialog = false
                        }) {
                            Text(text = stringResource(R.string.cancel))
                        }
                    }
                )
            }
        }

        is WebUIEvent.ShowFileChooser -> {
            LaunchedEffect(event) {
                try {
                    fileLauncher.launch(event.intent)
                } catch (_: Exception) {
                    webUIState.onFileChooserResult(null)
                }
            }
        }

        else -> {}
    }
}

@Composable
private fun HandleWebViewLifecycle(webUIState: WebUIState) {
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, webUIState) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> webUIState.webView?.onResume()
                Lifecycle.Event.ON_PAUSE -> webUIState.webView?.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

@Composable
private fun HandleConfigurationChanges(webUIState: WebUIState) {
    val configuration = LocalConfiguration.current
    LaunchedEffect(configuration.fontScale, webUIState.webView) {
        webUIState.webView?.settings?.textZoom = (configuration.fontScale * 100).toInt()
    }
}
