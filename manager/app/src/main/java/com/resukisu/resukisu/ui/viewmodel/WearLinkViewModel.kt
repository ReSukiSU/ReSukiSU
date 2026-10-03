package com.resukisu.resukisu.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resukisu.resukisu.data.network.WearLinkException
import com.resukisu.resukisu.data.network.WearLinkFailure
import com.resukisu.resukisu.data.network.WearLinkRepository
import com.resukisu.resukisu.data.network.WearLinkTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */
sealed interface WearLinkEvent {
    data class WebView(val url: String) : WearLinkEvent
    data class WebUi(val moduleId: String, val moduleName: String) : WearLinkEvent
    data object SentToPhone : WearLinkEvent
    /** A module WebUI was opened on the phone. */
    data object WebUiOnPhone : WearLinkEvent
    /** Opening a module WebUI on the phone failed for [reason]; null is an unexpected failure. */
    data class WebUiPhoneFailed(val reason: WearLinkFailure?) : WearLinkEvent
    /** A null [reason] is an unexpected failure. [webUi] marks failures of the module WebUI flow. */
    data class Failed(val reason: WearLinkFailure?, val webUi: Boolean = false) : WearLinkEvent
}
class WearLinkViewModel(private val repository: WearLinkRepository) : ViewModel() {
    private val mutableEvents = MutableSharedFlow<WearLinkEvent>()
    val events = mutableEvents.asSharedFlow()
    private val mutableLoading = MutableStateFlow(false)
    val loading = mutableLoading.asStateFlow()
    private var task: Job? = null
    fun open(url: String, mode: String) {
        if (task?.isActive == true) return
        task = viewModelScope.launch {
            mutableLoading.value = true
            try { when (repository.resolve(url, mode)) {
                WearLinkTarget.WEBVIEW -> mutableEvents.emit(WearLinkEvent.WebView(url))
                WearLinkTarget.PHONE -> mutableEvents.emit(WearLinkEvent.SentToPhone)
            } }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { mutableEvents.emit(WearLinkEvent.Failed((error as? WearLinkException)?.reason)) }
            finally { mutableLoading.value = false }
        }
    }

    /**
     * Opens a module WebUI in the watch WebView, or on the phone: when chosen, or automatically when
     * the watch has no WebView. On the phone the page is served by this watch over the Data Layer.
     */
    fun openWebUi(moduleId: String, moduleName: String, mode: String) {
        if (task?.isActive == true) return
        task = viewModelScope.launch {
            val onPhone = mode == "phone" || mode != "webview" && !repository.hasWebView()
            if (!onPhone) {
                mutableEvents.emit(if (repository.hasWebView()) WearLinkEvent.WebUi(moduleId, moduleName)
                    else WearLinkEvent.Failed(WearLinkFailure.WEBVIEW_UNAVAILABLE, webUi = true))
                return@launch
            }
            mutableLoading.value = true
            try {
                repository.openWebUiOnPhone(moduleId, moduleName)
                mutableEvents.emit(WearLinkEvent.WebUiOnPhone)
            }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { mutableEvents.emit(WearLinkEvent.WebUiPhoneFailed((error as? WearLinkException)?.reason)) }
            finally { mutableLoading.value = false }
        }
    }
    fun cancel() { task?.cancel() }
}
