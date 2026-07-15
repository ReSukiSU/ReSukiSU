package com.resukisu.resukisu.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageInfo
import android.os.Build
import android.os.IBinder
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import com.resukisu.zako.IKsuInterface
import com.topjohnwu.superuser.ipc.RootService

/**
 * @author ShirkNeko
 * @date 2025/10/17.
 */
class KsuService : RootService() {

    private val TAG = "KsuService"

    private val cacheLock = Any()
    private var _all: List<PackageInfo>? = null
    private val allPackages: List<PackageInfo>
        get() = synchronized(cacheLock) {
            _all ?: loadAllPackages().also { _all = it }
        }

    private fun loadAllPackages(): List<PackageInfo> {
        val tmp = arrayListOf<PackageInfo>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val userManager = getSystemService(USER_SERVICE) as UserManager
                for (user in userManager.userProfiles) {
                    val userId = user.getUserIdCompat()
                    tmp += getInstalledPackagesAsUser(userId)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "loadAllPackages with UserManager failed", e)
                tmp += getInstalledPackagesFallback()
            }
        } else {
            tmp += getInstalledPackagesFallback()
        }
        return tmp
    }

    private fun getInstalledPackagesFallback(): List<PackageInfo> {
        return try {
            packageManager.getInstalledPackages(0)
        } catch (e: Throwable) {
            Log.e(TAG, "getInstalledPackages fallback failed", e)
            emptyList()
        }
    }

    internal inner class Stub : IKsuInterface.Stub() {
        override fun getPackageCount(): Int = allPackages.size

        override fun getPackages(start: Int, maxCount: Int): List<PackageInfo> {
            val list = allPackages
            val end = (start + maxCount).coerceAtMost(list.size)
            return if (start >= list.size) emptyList()
            else list.subList(start, end)
        }
    }

    override fun onBind(intent: Intent): IBinder = Stub()

    @SuppressLint("PrivateApi")
    private fun getInstalledPackagesAsUser(userId: Int): List<PackageInfo> {
        return try {
            val pm = packageManager
            val m = pm.javaClass.getDeclaredMethod(
                "getInstalledPackagesAsUser",
                Int::class.java,
                Int::class.java
            )
            @Suppress("UNCHECKED_CAST")
            m.invoke(pm, 0, userId) as List<PackageInfo>
        } catch (e: Throwable) {
            Log.e(TAG, "getInstalledPackagesAsUser for userId $userId failed", e)
            getInstalledPackagesFallback()
        }
    }

    private fun UserHandle.getUserIdCompat(): Int {
        return try {
            javaClass.getDeclaredField("identifier").apply { isAccessible = true }.getInt(this)
        } catch (_: NoSuchFieldException) {
            javaClass.getDeclaredMethod("getIdentifier").invoke(this) as Int
        } catch (e: Throwable) {
            Log.e("KsuService", "getUserIdCompat", e)
            0
        }
    }
}