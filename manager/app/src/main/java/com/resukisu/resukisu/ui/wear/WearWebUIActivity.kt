package com.resukisu.resukisu.ui.wear

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.wear.compose.foundation.ScrollInfoProvider
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.resukisu.resukisu.R
import com.resukisu.resukisu.data.AppSettingsRepository
import com.resukisu.resukisu.data.packageinfo.AppIconDataSource
import com.resukisu.resukisu.data.packageinfo.InstalledPackageRepository
import com.resukisu.resukisu.data.webui.WebUiRepository
import com.resukisu.resukisu.domain.usecase.GetStringPreferenceUseCase
import com.resukisu.resukisu.ui.component.rememberCustomDialog
import com.resukisu.resukisu.ui.wear.component.WearBackground
import com.resukisu.resukisu.ui.wear.component.WearSubPage
import com.resukisu.resukisu.ui.wear.component.WearSwipeToDismissBox
import com.resukisu.resukisu.ui.wear.component.WearTextInputPage
import com.resukisu.resukisu.ui.wear.component.WearTimeText
import com.resukisu.resukisu.ui.viewmodel.ModuleViewModel
import com.resukisu.resukisu.ui.viewmodel.SuperUserViewModel
import com.resukisu.resukisu.ui.webui.HandleConfigurationChanges
import com.resukisu.resukisu.ui.webui.HandleWebViewLifecycle
import com.resukisu.resukisu.ui.webui.MonetColorsProvider
import com.resukisu.resukisu.ui.webui.WebUIEvent
import com.resukisu.resukisu.ui.webui.WebUIState
import com.resukisu.resukisu.ui.webui.prepareWebView
import org.koin.android.ext.android.inject
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/** A module WebUI on the watch: the phone's WebView setup and `ksu` bridge in a watch surface. */
class WearWebUIActivity : ComponentActivity() {
    private val getPreference: GetStringPreferenceUseCase by inject()

    // Standard SAF requests fall back to the app's own picker where the device has no usable one.
    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?) {
        super.startActivityForResult(withDocumentPickerFallback(intent, getPreference("wear_file_picker")), requestCode, options)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WearManagerTheme { WearWebUIContent(this) { finish() } }
        }
    }
}

@Composable
private fun WearWebUIContent(activity: ComponentActivity, onFinish: () -> Unit) {
    val moduleId = remember { activity.intent.getStringExtra("id") }
    val webUIState = remember { WebUIState() }
    val moduleViewModel = koinViewModel<ModuleViewModel>()
    val superUserViewModel = koinViewModel<SuperUserViewModel>()
    val settingsRepository = koinInject<AppSettingsRepository>()
    val packageRepository = koinInject<InstalledPackageRepository>()
    val appIconDataSource = koinInject<AppIconDataSource>()
    val webUiRepository = koinInject<WebUiRepository>()
    val colorsCss = koinInject<MonetColorsProvider>().getColorsCss()
    val currentColorsCss = rememberUpdatedState(colorsCss)

    LaunchedEffect(moduleId) {
        if (moduleId == null) {
            onFinish()
            return@LaunchedEffect
        }
        prepareWebView(
            activity,
            moduleId,
            webUIState,
            moduleViewModel,
            superUserViewModel,
            settingsRepository,
            packageRepository,
            appIconDataSource,
            webUiRepository,
            { currentColorsCss.value },
            recoverFromRenderCrash = true,
        )
    }
    DisposableEffect(Unit) {
        onDispose { webUIState.dispose() }
    }
    when (val event = webUIState.uiEvent) {
        is WebUIEvent.Error -> LaunchedEffect(event) {
            Toast.makeText(activity, event.message, Toast.LENGTH_SHORT).show()
            onFinish()
        }
        is WebUIEvent.Close -> LaunchedEffect(event) { onFinish() }
        else -> Unit
    }
    WearWebUIScreen(webUIState, onFinish)
}

@Composable
private fun WearWebUIScreen(state: WebUIState, onFinish: () -> Unit) {
    val back: () -> Unit = { if (state.webCanGoBack) state.webView?.goBack() else onFinish() }
    BackHandler { back() }
    AppScaffold(timeText = { WearTimeText() }) {
        WearSwipeToDismissBox(onDismissed = back) { background ->
            if (!background) {
                when (val event = state.uiEvent) {
                    // A waiting spinner; the branded loading screen is only shown at app startup.
                    WebUIEvent.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    else -> Box(Modifier.fillMaxSize()) {
                        // Keep the WebView attached while a prompt is open, including its history,
                        // file result launcher and lifecycle observer.
                        WearWebContent(state, back)
                        // Pages over the WebView take the opaque app backdrop so it does not show through.
                        if (!state.pageLoaded) WearBackground {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                        if (event is WebUIEvent.ShowPrompt) WearSubPage({ state.onPromptResult(null) }) {
                            WearBackground { WearTextInputPage(event.message, event.defaultValue) { state.onPromptResult(it) } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WearWebContent(state: WebUIState, onBack: () -> Unit) {
    val webView = state.webView
    var offset by remember(webView) { mutableIntStateOf(0) }
    var scrollable by remember(webView) { mutableStateOf(false) }
    var atBottom by remember(webView) { mutableStateOf(false) }
    LaunchedEffect(webView, state.pageLoaded) {
        offset = webView?.scrollY ?: 0
        scrollable = webView?.let { it.canScrollVertically(-1) || it.canScrollVertically(1) } == true
        atBottom = webView?.canScrollVertically(1) != true
    }
    val edgeSpace = with(LocalDensity.current) { 64.dp.toPx() }
    val provider = remember(webView, edgeSpace) {
        object : ScrollInfoProvider {
            override val isScrollAwayValid get() = true
            override val isScrollable get() = scrollable
            override val isScrollInProgress get() = false
            override val anchorItemOffset get() = offset.toFloat()
            override val lastItemOffset get() = if (atBottom) edgeSpace else 0f
        }
    }
    DisposableEffect(webView) {
        webView?.isVerticalScrollBarEnabled = true
        webView?.setBackgroundColor(android.graphics.Color.BLACK)
        webView?.setOnScrollChangeListener { _, _, y, _, _ ->
            offset = y
            scrollable = webView.canScrollVertically(-1) || webView.canScrollVertically(1)
            atBottom = !webView.canScrollVertically(1)
        }
        onDispose { webView?.setOnScrollChangeListener(null) }
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(webView) { if (webView != null) focus.requestFocus() }
    // An inset rectangle keeps module controls inside the circular display. Module HTML remains
    // unchanged; the asset loader and JS bridge stay shared with the phone.
    val configuration = LocalConfiguration.current
    ScreenScaffold(scrollInfoProvider = provider, edgeButton = {
        EdgeButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Default.ArrowBack, stringResource(R.string.back))
        }
    }) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val side = if (configuration.isScreenRound) maxWidth * 0.1465f else 8.dp
            Box(Modifier.fillMaxSize().padding(horizontal = side, vertical = 4.dp)) {
                WearWebView(
                    state,
                    Modifier
                        .onRotaryScrollEvent {
                            webView?.scrollBy(0, it.verticalScrollPixels.toInt())
                            true
                        }
                        .focusRequester(focus)
                        .focusable(),
                )
            }
        }
    }
}

private const val WEBUI_HOME_PAGE = "https://mui.kernelsu.org/index.html"

/** The module WebView, loading the module's home page once it has been laid out. */
@Composable
private fun WearWebView(state: WebUIState, modifier: Modifier) {
    val webView = state.webView ?: return
    AndroidView(modifier = modifier.fillMaxSize(), factory = {
        webView.apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            if (!state.isUrlLoaded) {
                addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
                    override fun onLayoutChange(
                        v: View, left: Int, top: Int, right: Int, bottom: Int,
                        oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int,
                    ) {
                        if (v.width > 0 && v.height > 0 && !state.isUrlLoaded) {
                            (v as WebView).loadUrl(WEBUI_HOME_PAGE)
                            state.isUrlLoaded = true
                            v.removeOnLayoutChangeListener(this)
                        }
                    }
                })
            }
        }
    })
    HandleWearWebUIEvent(state)
    HandleWebViewLifecycle(state)
    HandleConfigurationChanges(state)
}

@Composable
private fun HandleWearWebUIEvent(state: WebUIState) {
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        val uris: Array<Uri>? = if (result.resultCode != Activity.RESULT_OK || data == null) null
        else data.clipData?.let { clip -> Array(clip.itemCount) { clip.getItemAt(it).uri } }
            ?: data.data?.let { arrayOf(it) }
        state.onFileChooserResult(uris)
    }
    val event = state.uiEvent
    if (event is WebUIEvent.ShowFileChooser) {
        LaunchedEffect(event) {
            runCatching { fileLauncher.launch(event.intent) }.onFailure { state.onFileChooserResult(null) }
        }
        return
    }
    if (event !is WebUIEvent.ShowAlert && event !is WebUIEvent.ShowConfirm) return
    val message = when (event) {
        is WebUIEvent.ShowAlert -> event.message
        is WebUIEvent.ShowConfirm -> event.message
    }
    val title = stringResource(R.string.module_webui_alert, state.moduleName)
    val dialog = rememberCustomDialog { dismiss ->
        fun respond(confirmed: Boolean) {
            if (event is WebUIEvent.ShowAlert) state.onAlertResult() else state.onConfirmResult(confirmed)
            dismiss()
        }
        AlertDialog(
            visible = true, onDismissRequest = { respond(false) },
            title = { Text(title) }, text = { Text(message) },
            confirmButton = { AlertDialogDefaults.ConfirmButton(onClick = { respond(true) }) },
            dismissButton = { AlertDialogDefaults.DismissButton(onClick = { respond(false) }) },
        )
    }
    LaunchedEffect(event) { dialog.show() }
}
