package com.resukisu.resukisu.data.network

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.webkit.WebViewCompat
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import com.resukisu.resukisu.data.webui.WearWebUiProtocol
import com.resukisu.resukisu.data.webui.WearWebUiSession
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "WearLink"

enum class WearLinkTarget { WEBVIEW, PHONE }

/** Why a link could not be opened, so the UI can show the actual reason and offer a mode switch. */
enum class WearLinkFailure { UNSUPPORTED_URL, WEBVIEW_UNAVAILABLE, PHONE_UNAVAILABLE, PHONE_APP_MISSING, PHONE_FAILED }
class WearLinkException(val reason: WearLinkFailure, cause: Throwable? = null) : Exception(reason.name, cause)

/**
 * Links open in the watch's own WebView or are sent to the phone. Watches may ship without WebView,
 * and handing links to an arbitrary watch browser is not offered.
 */
class WearLinkRepository(private val application: Application) {
    // Both the system feature and an installed provider are required; builds that remove WebView
    // drop the feature, and a provider package alone does not prove WebView can be created.
    fun hasWebView(): Boolean = application.packageManager.hasSystemFeature(PackageManager.FEATURE_WEBVIEW) &&
        runCatching { WebViewCompat.getCurrentWebViewPackage(application) != null }.getOrDefault(false)

    suspend fun resolve(url: String, mode: String): WearLinkTarget = withContext(Dispatchers.IO) {
        val uri = Uri.parse(url)
        if (uri.scheme !in listOf("https", "http")) throw WearLinkException(WearLinkFailure.UNSUPPORTED_URL)
        val hasWebView = hasWebView()
        val target = when (mode) {
            "webview" -> if (hasWebView) WearLinkTarget.WEBVIEW else throw WearLinkException(WearLinkFailure.WEBVIEW_UNAVAILABLE)
            "phone" -> WearLinkTarget.PHONE
            // Automatic: the watch WebView when present, otherwise the phone.
            else -> if (hasWebView) WearLinkTarget.WEBVIEW else WearLinkTarget.PHONE
        }
        if (target == WearLinkTarget.PHONE) {
            val helper = RemoteActivityHelper(application, java.util.concurrent.Executor { it.run() })
            val availability = withTimeoutOrNull(1_000) { helper.availabilityStatus.first() }
            if (availability == RemoteActivityHelper.STATUS_UNAVAILABLE ||
                availability == RemoteActivityHelper.STATUS_TEMPORARILY_UNAVAILABLE) {
                throw WearLinkException(WearLinkFailure.PHONE_UNAVAILABLE)
            }
            startOnPhone(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE), null)
        }
        target
    }

    /**
     * Opens a module's WebUI on the paired phone. A connected phone must have this app, found by its
     * Data Layer capability; the phone page is then started with RemoteActivityHelper and may claim
     * a session limited to [moduleId], for that phone only (see [WearWebUiSession]).
     */
    suspend fun openWebUiOnPhone(moduleId: String, moduleName: String) = withContext(Dispatchers.IO) {
        val nodeClient = Wearable.getNodeClient(application)
        val connected = runCatching { Tasks.await(nodeClient.connectedNodes, 5, TimeUnit.SECONDS) }
            .getOrElse { Log.w(TAG, "Connected nodes unavailable", it); throw WearLinkException(WearLinkFailure.PHONE_FAILED, it) }
        Log.i(TAG, "Connected nodes: ${connected.joinToString { "${it.displayName}(${it.id}, nearby=${it.isNearby})" }}")
        if (connected.isEmpty()) throw WearLinkException(WearLinkFailure.PHONE_UNAVAILABLE)
        val localId = runCatching { Tasks.await(nodeClient.localNode, 5, TimeUnit.SECONDS).id }
            .getOrElse { Log.w(TAG, "Local node unavailable", it); throw WearLinkException(WearLinkFailure.PHONE_FAILED, it) }
        // The Data Layer only links apps with the same package name and signing certificate, so a
        // phone app from another build (for example a release signed with another key, or a version
        // without the capability) is not found here.
        val phones = runCatching {
            Tasks.await(Wearable.getCapabilityClient(application)
                .getCapability(WearWebUiProtocol.CAPABILITY, CapabilityClient.FILTER_REACHABLE), 5, TimeUnit.SECONDS).nodes
        }.getOrElse { Log.w(TAG, "Capability query failed", it); throw WearLinkException(WearLinkFailure.PHONE_FAILED, it) }
            .filter { it.id != localId }
        Log.i(TAG, "Nodes with ${WearWebUiProtocol.CAPABILITY}: ${phones.joinToString { "${it.displayName}(${it.id})" }}")
        val phone = phones.firstOrNull { it.isNearby } ?: phones.firstOrNull()
            ?: throw WearLinkException(WearLinkFailure.PHONE_APP_MISSING)
        // The link only names this app's phone activity; that activity claims the session from the
        // watch over the Data Layer, so the intent carries nothing another app could reuse.
        val token = WearWebUiSession.offer(moduleId, moduleName, phone.id)
        val uri = Uri.Builder().scheme(WearWebUiProtocol.SCHEME).authority(WearWebUiProtocol.HOST).build()
        try {
            startOnPhone(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
                .setPackage(application.packageName), phone.id)
        } catch (error: Exception) {
            WearWebUiSession.end(token)
            throw error
        }
    }

    /** Starts [intent] on phone [nodeId], or any connected phone when null; a failed dispatch is reported. */
    private suspend fun startOnPhone(intent: Intent, nodeId: String?) {
        val helper = RemoteActivityHelper(application, java.util.concurrent.Executor { it.run() })
        val future = helper.startRemoteActivity(intent, nodeId)
        try {
            suspendCancellableCoroutine<Unit> { continuation ->
                future.addListener({
                    try { future.get(); if (continuation.isActive) continuation.resume(Unit) }
                    catch (error: Exception) { if (continuation.isActive) continuation.resumeWithException(error) }
                }, java.util.concurrent.Executor { it.run() })
                continuation.invokeOnCancellation { future.cancel(true) }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw WearLinkException(WearLinkFailure.PHONE_FAILED, error)
        }
    }
}
