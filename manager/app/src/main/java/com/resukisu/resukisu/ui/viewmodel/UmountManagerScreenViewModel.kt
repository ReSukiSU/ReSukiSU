package com.resukisu.resukisu.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.launch
import org.json.JSONArray

/**
 * @author AlexLiuDev233
 * @date 2026/02/16.
 */
class UmountManagerScreenViewModel : ViewModel() {
    companion object {
        const val TAG = "UmountManagerScreenViewModel"
    }

    var umountPaths by mutableStateOf<List<UmountPathEntry>>(emptyList())
        private set

    private var dirty = true
    var isRefreshing by mutableStateOf(false)

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
            val fetchKernelUmountPathsTask = async {
                parseUmountPaths(listKernelUmountPaths(), false, context)
            }

            umountPaths = (fetchKernelUmountPathsTask.await() + parseUmountPaths(
                listUmountConfigUmountPaths(),
                true,
                context
            ))
                .groupBy { it.path }
                .map { (path, entries) -> // first from kernel, second from config
                    UmountPathEntry(
                        path = path,
                        flagName = entries.first().flagName,
                        persistent = entries.any { it.persistent },
                    )
                }
            isRefreshing = false
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

    // https://github.com/torvalds/linux/blob/v6.18/include/linux/fs.h#L1397-L1401
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
                    snackBarHost?.showSnackbar(
                        context.getString(R.string.operation_failed)
                    )
                }
                return@launch
            }

            umountPaths = umountPaths.filter { it != entry }

            context?.let {
                snackBarHost?.showSnackbar(
                    context.getString(R.string.umount_path_removed)
                )
            }
        }
    }

    fun addPath(path: String, flags: Int, snackBarHost: SnackbarHostState?, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = addUmountConfigUmountPath(path, flags) && addKernelUmountPath(path, flags)
            if (!success) {
                snackBarHost?.showSnackbar(
                    context.getString(R.string.operation_failed)
                )
                return@launch
            }
            umountPaths = umountPaths + UmountPathEntry(
                path = path,
                flagName = flags.toUmountFlagName(context),
                persistent = true
            )

            snackBarHost?.showSnackbar(
                context.getString(R.string.umount_path_added)
            )
        }
    }
}