package com.resukisu.resukisu.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resukisu.resukisu.data.file.WearDirectory
import com.resukisu.resukisu.data.file.WearFileException
import com.resukisu.resukisu.data.file.WearFileProvider
import com.resukisu.resukisu.data.file.WearFileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

/** [error] is a localized message; [failed] without it means an unexpected, generic failure. */
data class WearFileUiState(val directory: WearDirectory? = null, val loading: Boolean = false,
    val error: String? = null, val failed: Boolean = false, val name: String = "")
sealed interface WearFileEvent {
    /** A picked or newly named file, as a URI of [WearFileProvider]. */
    data class Picked(val uri: String) : WearFileEvent
}

class WearFileViewModel(private val repository: WearFileRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(WearFileUiState())
    val state = mutableState.asStateFlow()
    private val mutableEvents = MutableSharedFlow<WearFileEvent>()
    val events = mutableEvents.asSharedFlow()
    private var task: Job? = null
    fun setName(name: String) { mutableState.update { it.copy(name = name) } }
    fun load(path: String?, mimeTypes: List<String>, directoriesOnly: Boolean) = submit {
        val directory = repository.list(path, mimeTypes, directoriesOnly)
        mutableState.update { it.copy(directory = directory) }
    }
    fun select(path: String) = submit { mutableEvents.emit(WearFileEvent.Picked(WearFileProvider.uriFor(path).toString())) }
    fun create() = submit {
        val current = state.value
        val path = repository.newFile(requireNotNull(current.directory).path, current.name)
        mutableEvents.emit(WearFileEvent.Picked(WearFileProvider.uriFor(path).toString()))
    }
    fun clearError() { mutableState.update { it.copy(error = null, failed = false) } }
    private fun submit(block: suspend () -> Unit) {
        if (task?.isActive == true) return
        task = viewModelScope.launch {
            mutableState.update { it.copy(loading = true, error = null, failed = false) }
            try { block() }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                mutableState.update { it.copy(error = (error as? WearFileException)?.message, failed = true) }
            }
            finally { mutableState.update { it.copy(loading = false) } }
        }
    }
}
