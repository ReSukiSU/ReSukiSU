package com.resukisu.resukisu.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resukisu.resukisu.R
import com.resukisu.resukisu.ui.util.addKernelUmountPath
import com.resukisu.resukisu.ui.util.addUmountConfigUmountPath
import com.resukisu.resukisu.ui.util.listKernelUmountPaths
import com.resukisu.resukisu.ui.util.listUmountConfigUmountPaths
import com.resukisu.resukisu.ui.util.removeKernelUmountPath
import com.resukisu.resukisu.ui.util.removeUmountConfigUmountPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray

data class UmountManagerUiState(
    val umountPaths: List<UmountManagerScreenViewModel.UmountPathEntry> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
)

class UmountManagerScreenViewModel : ViewModel() {
    companion object {
        const val TAG = "UmountManagerScreenViewModel"
    }

    private val _uiState = MutableStateFlow(UmountManagerUiState())
    val uiState: StateFlow<UmountManagerUiState> = _uiState.asStateFlow()

    private var dirty = true

    private fun parseUmountPaths(
        paths: String,
        fromConfig: Boolean,
        context: Context
    ): List<UmountPathEntry> {
        val trimmed = paths.trim()
        if (trimmed.isEmpty() || trimmed == "[]") return emptyList()

        Log.i(TAG, "Processing umount paths: $paths")

        val array = JSONArray(trimmed)
        return (0 until array.length())
            .asSequence()
            .map { array.getJSONObject(it) }
            .map { obj ->
                UmountPathEntry(
                    persistent = fromConfig,
                    path = obj.getString("path"),
                    flagName = obj.getInt("flags").toUmountFlagName(context),
                )
            }.toList()
    }

    fun refreshData(context: Context) {
        if (!dirty) return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isRefreshing = !it.isLoading) }
            val fetchKernelUmountPathsTask = async {
                parseUmountPaths(listKernelUmountPaths(), false, context)
            }

            val paths = (fetchKernelUmountPathsTask.await() + parseUmountPaths(
                listUmountConfigUmountPaths(),
                true,
                context
            ))
                .groupBy { it.path }
                .map { (path, entries) ->
                    UmountPathEntry(
                        path = path,
                        flagName = entries.first().flagName,
                        persistent = entries.any { it.persistent },
                    )
                }
            _uiState.update {
                it.copy(
                    umountPaths = paths,
                    isLoading = false,
                    isRefreshing = false,
                )
            }
            dirty = false
        }
    }

    fun markUmountPathDirty() {
        dirty = true
    }

    data class UmountPathEntry(
        val path: String,
        val flagName: String,
        val persistent: Boolean,
    )

    private fun Int.toUmountFlagName(context: Context): String {
        return when (this) {
            -1 -> context.getString(R.string.unknown)
            0 -> "UMOUNT_UNUSED"
            1 -> "MNT_FORCE"
            2 -> "MNT_DETACH"
            4 -> "MNT_EXPIRE"
            8 -> "UMOUNT_NOFOLLOW"
            else -> this.toString()
        }
    }

    fun removePath(entry: UmountPathEntry, snackBarHost: SnackbarHostState?, context: Context?) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = if (entry.persistent) {
                removeUmountConfigUmountPath(entry.path)
            } else {
                true
            } && removeKernelUmountPath(entry.path)

            if (!success) {
                context?.let {
                    snackBarHost?.showSnackbar(context.getString(R.string.operation_failed))
                }
                return@launch
            }

            _uiState.update { state ->
                state.copy(umountPaths = state.umountPaths.filter { it != entry })
            }

            context?.let {
                snackBarHost?.showSnackbar(context.getString(R.string.umount_path_removed))
            }
        }
    }

    fun addPath(path: String, flags: Int, snackBarHost: SnackbarHostState?, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = addUmountConfigUmountPath(path, flags) && addKernelUmountPath(path, flags)
            if (!success) {
                snackBarHost?.showSnackbar(context.getString(R.string.operation_failed))
                return@launch
            }
            _uiState.update { state ->
                state.copy(
                    umountPaths = state.umountPaths + UmountPathEntry(
                        path = path,
                        flagName = flags.toUmountFlagName(context),
                        persistent = true
                    )
                )
            }

            snackBarHost?.showSnackbar(context.getString(R.string.umount_path_added))
        }
    }
}
