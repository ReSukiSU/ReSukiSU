package com.resukisu.resukisu.ui.wear

import android.annotation.SuppressLint
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.ScrollInfoProvider
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ScreenScaffold
import com.resukisu.resukisu.R

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun WearBrowserPage(url: String, onBack: () -> Unit, onUnavailable: () -> Unit) {
    val context = LocalContext.current
    var offset by remember { mutableIntStateOf(0) }
    var canScroll by remember { mutableStateOf(false) }
    var atBottom by remember { mutableStateOf(false) }
    var renderGone by remember(url) { mutableStateOf(false) }
    val webView = remember(url) { runCatching { WebView(context).apply {
        setBackgroundColor(android.graphics.Color.BLACK)
        settings.javaScriptEnabled = true
        webViewClient = WearBrowserClient(onPageFinished = { view ->
            canScroll = view.canScrollVertically(-1) || view.canScrollVertically(1)
            atBottom = !view.canScrollVertically(1)
        }, onRendererGone = { renderGone = true })
        loadUrl(url)
    } }.getOrNull() }
    if (webView == null) {
        LaunchedEffect(url) { onUnavailable() }
        return
    }
    if (renderGone) {
        LaunchedEffect(url) { onBack() }
        return
    }
    val edgeSpace = with(LocalDensity.current) { 64.dp.toPx() }
    val provider = remember(webView, edgeSpace) { object : ScrollInfoProvider {
        override val isScrollAwayValid get() = true
        override val isScrollable get() = canScroll
        override val isScrollInProgress get() = false
        override val anchorItemOffset get() = offset.toFloat()
        override val lastItemOffset get() = if (atBottom) edgeSpace else 0f
    } }
    DisposableEffect(webView) {
        webView.setOnScrollChangeListener { _, _, y, _, _ ->
            offset = y
            canScroll = webView.canScrollVertically(-1) || webView.canScrollVertically(1)
            atBottom = !webView.canScrollVertically(1)
        }
        onDispose { webView.stopLoading(); webView.destroy() }
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(webView) { focus.requestFocus() }
    ScreenScaffold(scrollInfoProvider = provider, edgeButton = {
        EdgeButton(onClick = onBack) { Icon(Icons.AutoMirrored.Default.ArrowBack, stringResource(R.string.back)) }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize().onRotaryScrollEvent {
                webView.scrollBy(0, it.verticalScrollPixels.toInt()); true
            }.focusRequester(focus).focusable())
        }
    }
}

private class WearBrowserClient(
    private val onPageFinished: (WebView) -> Unit,
    private val onRendererGone: () -> Unit,
) : WebViewClient() {
    override fun onPageFinished(view: WebView, url: String) = onPageFinished(view)

    // A killed renderer leaves this WebView unusable; leave the page instead of crashing the app.
    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        onRendererGone()
        return true
    }
}
