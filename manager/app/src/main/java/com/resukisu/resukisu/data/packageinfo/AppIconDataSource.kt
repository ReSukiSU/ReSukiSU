package com.resukisu.resukisu.data.packageinfo

import android.app.Application
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale

/** Loads package icons for platform surfaces such as the WebView bridge. */
class AppIconDataSource(
    private val application: Application,
    private val packageCache: InstalledPackageCache,
) {
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

    fun loadSync(packageName: String, sizePx: Int): Bitmap? = runCatching {
        val drawable = application.packageManager.getApplicationIcon(packageName)
        drawableToBitmap(drawable, sizePx).scale(sizePx, sizePx)
    }.getOrNull()

    private fun drawableToBitmap(drawable: Drawable, size: Int): Bitmap {
        if (drawable is BitmapDrawable) return drawable.bitmap

        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: size
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: size
        return createBitmap(width, height).also { bitmap ->
            Canvas(bitmap).apply {
                drawable.setBounds(0, 0, width, height)
                drawable.draw(this)
            }
        }
    }
}
