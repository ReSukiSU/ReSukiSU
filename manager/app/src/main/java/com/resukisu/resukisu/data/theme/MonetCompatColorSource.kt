package com.resukisu.resukisu.data.theme

import android.app.Application
import com.kieronquinn.monetcompat.core.MonetCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

class MonetCompatColorSource(
    private val application: Application,
) {
    suspend fun initialize() {
        MonetCompat.useSystemColorsOnAndroid12 = false
        runCatching { MonetCompat.enablePaletteCompat() }

        val monet = MonetCompat.setup(application)
        val wallpaperSeedColor = try {
            withTimeoutOrNull(2000.milliseconds) {
                monet.getSelectedWallpaperColor()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
        monet.wallpaperPrimaryColor = wallpaperSeedColor
        monet.updateMonetColors()
    }

    fun seedColor(): Int {
        val monet = MonetCompat.getInstance()
        return monet.wallpaperPrimaryColor ?: requireNotNull(monet.defaultPrimaryColor)
    }
}
