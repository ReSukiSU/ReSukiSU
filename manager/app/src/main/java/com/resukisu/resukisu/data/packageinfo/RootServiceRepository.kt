package com.resukisu.resukisu.data.packageinfo

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageInfo
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.resukisu.rootService.IKsuInterface
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ipc.RootService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

class RootServiceRepository(
    private val application: Application,
) {
    private val requestMutex = Mutex()

    suspend fun getInstalledPackages(): List<PackageInfo> = requestMutex.withLock {
        val intent = Intent(application, KsuService::class.java)
        try {
            val binder = withTimeoutOrNull(10000.milliseconds) {
                connectService(intent)
            } ?: throw IllegalStateException("Root service unavailable")

            withContext(Dispatchers.IO) {
                val service = IKsuInterface.Stub.asInterface(binder)
                val total = service.packageCount
                buildList {
                    var start = 0
                    while (start < total) {
                        val page = service.getPackages(start, 100)
                        if (page.isEmpty()) break
                        addAll(page)
                        start += page.size
                    }
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw IllegalStateException("Root service unavailable", error)
        } finally {
            stopService(intent)
        }
    }

    private suspend fun connectService(intent: Intent): IBinder? =
        withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { continuation ->
                val connection = object : ServiceConnection {
                    override fun onServiceDisconnected(name: ComponentName?) {
                        if (continuation.isActive) continuation.resume(null)
                    }

                    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                        if (continuation.isActive) {
                            continuation.resume(binder)
                        } else {
                            stopServiceOnMain(intent)
                        }
                    }

                    override fun onNullBinding(name: ComponentName?) {
                        if (continuation.isActive) continuation.resume(null)
                    }

                    override fun onBindingDied(name: ComponentName?) {
                        if (continuation.isActive) continuation.resume(null)
                    }
                }

                continuation.invokeOnCancellation { stopServiceOnMain(intent) }
                runCatching {
                    RootService.bind(intent, Shell.EXECUTOR, connection)
                }.onFailure {
                    if (continuation.isActive) continuation.resume(null)
                }
            }
        }

    private suspend fun stopService(intent: Intent) =
        withContext(NonCancellable + Dispatchers.Main.immediate) {
            runCatching { RootService.stop(intent) }
    }

    private fun stopServiceOnMain(intent: Intent) {
        ContextCompat.getMainExecutor(application).execute {
            runCatching { RootService.stop(intent) }
        }
    }
}
