package com.resukisu.resukisu.data.kernel

import com.resukisu.resukisu.data.shell.KsuCliRepository
import com.resukisu.resukisu.domain.model.UmountPath
import com.resukisu.resukisu.domain.model.UmountState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray

class UmountRepository(
    private val ksuCliRepository: KsuCliRepository,
) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(UmountState())
    val state: StateFlow<UmountState> = mutableState.asStateFlow()

    suspend fun refresh(): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            mutableState.update { it.copy(isRefreshing = !it.isLoading) }
            runCatching {
                val paths = coroutineScope {
                    val kernel = async {
                        parse(ksuCliRepository.listKernelUmountPaths(), persistent = false)
                    }
                    val config = async {
                        parse(ksuCliRepository.listUmountConfigUmountPaths(), persistent = true)
                    }
                    (kernel.await() + config.await())
                        .groupBy(UmountPath::path)
                        .map { (path, entries) ->
                            UmountPath(
                                path = path,
                                flags = entries.first().flags,
                                persistent = entries.any(UmountPath::persistent),
                            )
                        }
                }
                mutableState.value = UmountState(paths = paths, isLoading = false)
            }.onFailure {
                mutableState.update { current ->
                    current.copy(isLoading = false, isRefreshing = false)
                }
            }
        }
    }

    suspend fun add(path: String, flags: Int): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                check(
                    ksuCliRepository.addUmountConfigUmountPath(path, flags) &&
                            ksuCliRepository.addKernelUmountPath(path, flags)
                )
                mutableState.update { current ->
                    current.copy(
                        paths = current.paths.filterNot { it.path == path } +
                                UmountPath(path = path, flags = flags, persistent = true)
                    )
                }
            }
        }
    }

    suspend fun remove(entry: UmountPath): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                check(
                    (!entry.persistent ||
                            ksuCliRepository.removeUmountConfigUmountPath(entry.path)) &&
                            ksuCliRepository.removeKernelUmountPath(entry.path)
                )
                mutableState.update { current ->
                    current.copy(paths = current.paths.filterNot { it.path == entry.path })
                }
            }
        }
    }

    private fun parse(raw: String, persistent: Boolean): List<UmountPath> {
        if (raw.isBlank() || raw.trim() == "[]") return emptyList()
        val array = JSONArray(raw.trim())
        return (0 until array.length()).map { index ->
            array.getJSONObject(index).let { value ->
                UmountPath(
                    path = value.getString("path"),
                    flags = value.getInt("flags"),
                    persistent = persistent,
                )
            }
        }
    }
}
