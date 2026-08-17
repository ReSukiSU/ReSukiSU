package com.resukisu.resukisu.data.packageinfo

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Process
import android.os.UserManager
import android.util.Log
import android.util.LruCache
import me.zhanghai.android.appiconloader.AppIconLoader
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.util.concurrent.ConcurrentHashMap

const val TAG = "AppIconDataSource"
const val PER_USER_RANGE = 100000

/** Loads package icons for platform surfaces such as the WebView bridge. */
class AppIconDataSource(
    private val application: Application,
    private val packageCache: InstalledPackageCache,
) {
    private val maxMemoryKb = Runtime.getRuntime().maxMemory() / 1024
    private val iconLoaders = ConcurrentHashMap<Int, AppIconLoader>()
    private val iconCache = object : LruCache<String, Bitmap>((maxMemoryKb / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount / 1024
    }

    @Volatile
    private var otherProfileGroupUserIds = emptySet<Int>()

    @Volatile
    private var cachedMainUserId: Int? = null

    fun findCachedPackageInfo(packageName: String): PackageInfo? =
        packageCache.find(packageName)

    fun loadPackageInfo(packageName: String): PackageInfo? =
        findCachedPackageInfo(packageName) ?: runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                application.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                application.packageManager.getPackageInfo(packageName, 0)
            }
        }.getOrNull()

    /** Loads a software bitmap suitable for WebView response encoding. */
    fun loadSync(packageName: String, sizePx: Int): Bitmap? {
        if (sizePx <= 0) return null
        val applicationInfo = loadPackageInfo(packageName)?.applicationInfo ?: return null
        return runCatching { loadIconSync(applicationInfo, sizePx) }
            .onFailure { error -> Log.w(TAG, "Failed to load icon for $packageName", error) }
            .getOrNull()
    }

    private fun loadIconSync(applicationInfo: ApplicationInfo, sizePx: Int): Bitmap {
        val key = buildCacheKey(applicationInfo, sizePx)
        iconCache.get(key)?.let { cached ->
            if (!cached.isRecycled) return cached
            iconCache.remove(key)
        }

        val loader = iconLoaders.getOrPut(sizePx) {
            AppIconLoader(sizePx, false, application)
        }
        val bitmap = if (isOtherProfileGroupUser(applicationInfo.uid)) {
            loader.loadIcon(applicationInfo.withMainUserUid())
        } else {
            try {
                loader.loadIcon(applicationInfo)
            } catch (_: SecurityException) {
                Log.d(
                    TAG,
                    "Cannot load icon for user ${userId(applicationInfo.uid)}; retrying as the main user",
                )
                markOtherProfileGroupUser(applicationInfo.uid)
                loader.loadIcon(applicationInfo.withMainUserUid())
            }
        }

        bitmap.prepareToDraw()
        iconCache.put(key, bitmap)
        return bitmap
    }

    private fun ApplicationInfo.withMainUserUid(): ApplicationInfo {
        val targetUid = getMainUserId() * PER_USER_RANGE + uid % PER_USER_RANGE
        if (uid == targetUid) return this
        return ApplicationInfo(this).apply { uid = targetUid }
    }

    private fun getMainUserId(): Int {
        cachedMainUserId?.let { return it }
        synchronized(this) {
            cachedMainUserId?.let { return it }

            val userManager = application.getSystemService(Context.USER_SERVICE) as UserManager
            var mainUserId = userId(Process.myUid())
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    HiddenApiBypass.addHiddenApiExemptions("Landroid/os/UserManager;->hasBadge(I)Z")
                }
                val hasBadge = UserManager::class.java.getMethod(
                    "hasBadge",
                    Int::class.javaPrimitiveType,
                )
                for (profile in userManager.userProfiles) {
                    val profileId = profile.hashCode()
                    if (hasBadge.invoke(userManager, profileId) == false) {
                        mainUserId = profileId
                        break
                    }
                }
            } catch (error: Exception) {
                Log.w(TAG, "Failed to resolve the main user ID", error)
            }
            cachedMainUserId = mainUserId
            return mainUserId
        }
    }

    private fun isOtherProfileGroupUser(uid: Int): Boolean =
        userId(uid) in otherProfileGroupUserIds

    private fun markOtherProfileGroupUser(uid: Int) {
        val userId = userId(uid)
        if (userId in otherProfileGroupUserIds) return
        synchronized(this) {
            otherProfileGroupUserIds = otherProfileGroupUserIds + userId
        }
    }

    private fun buildCacheKey(applicationInfo: ApplicationInfo, sizePx: Int): String =
        "${applicationInfo.packageName}:${applicationInfo.uid}:${applicationInfo.sourceDir}:$sizePx"

    private fun userId(uid: Int): Int = uid / PER_USER_RANGE
}
