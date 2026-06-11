package com.resukisu.resukisu.ui.theme

import android.R
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.zIndex
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.scale
import androidx.core.net.toUri
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.kyant.m3color.hct.Hct
import com.kyant.m3color.quantize.QuantizerCelebi
import com.kyant.m3color.scheme.SchemeTonalSpot
import com.kyant.m3color.score.Score
import com.resukisu.resukisu.data.appPreferences
import com.resukisu.resukisu.ui.theme.util.BackgroundTransformation
import com.resukisu.resukisu.ui.theme.util.saveTransformedBackground
import com.resukisu.resukisu.ui.util.LocalBackgroundBlurAnchor
import com.resukisu.resukisu.ui.util.LocalBlurState
import com.resukisu.resukisu.ui.webui.MonetColorsProvider
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

@Stable
object ThemeConfig {
    // 主题状态
    var customBackgroundUri by mutableStateOf<Uri?>(null)
    var backgroundDim by mutableFloatStateOf(0f)
    var forceDarkMode by mutableStateOf<Boolean?>(null)
    var currentTheme by mutableStateOf<ThemeColors>(ThemeColors.Default)
    var useDynamicColor by mutableStateOf(false)

    // 背景状态
    var backgroundImageLoaded by mutableStateOf(false)
    var isThemeChanging by mutableStateOf(false)
    var preventBackgroundRefresh by mutableStateOf(false)
    var isHighContrastMode by mutableStateOf(false)
    var isEnableBlur by mutableStateOf(false)
    var isEnableBlurExp by mutableStateOf(false)
    var isUseBackgroundSeedColor by mutableStateOf(false)

    // 主题变化检测
    private var lastDarkModeState: Boolean? = null

    fun detectThemeChange(currentDarkMode: Boolean): Boolean {
        val hasChanged = lastDarkModeState != null && lastDarkModeState != currentDarkMode
        lastDarkModeState = currentDarkMode
        return hasChanged
    }

    fun resetBackgroundState() {
        if (!preventBackgroundRefresh) {
            backgroundImageLoaded = false
        }
        isThemeChanging = true
    }

    fun updateTheme(
        theme: ThemeColors? = null,
        dynamicColor: Boolean? = null,
        darkMode: Boolean? = null
    ) {
        theme?.let { currentTheme = it }
        dynamicColor?.let { useDynamicColor = it }
        darkMode?.let { forceDarkMode = it }
    }

    fun reset() {
        customBackgroundUri = null
        forceDarkMode = null
        currentTheme = ThemeColors.Default
        useDynamicColor = false
        backgroundImageLoaded = false
        isThemeChanging = false
        preventBackgroundRefresh = false
        lastDarkModeState = null
    }
}

object ThemeManager {
    fun saveThemeMode(context: Context, forceDark: Boolean?) {
        context.appPreferences.putString(
            "theme_mode", when (forceDark) {
                true -> "dark"
                false -> "light"
                null -> "system"
            }
        )
        ThemeConfig.forceDarkMode = forceDark
    }

    fun loadThemeMode(context: Context) {
        val mode = context.appPreferences.getString("theme_mode", "system")

        ThemeConfig.forceDarkMode = when (mode) {
            "dark" -> true
            "light" -> false
            else -> null
        }
    }

    fun saveThemeColors(context: Context, themeName: String) {
        context.appPreferences.putString("theme_colors", themeName)
        ThemeConfig.currentTheme = ThemeColors.fromName(themeName)
    }

    fun loadThemeColors(context: Context) {
        val themeName = context.appPreferences.getString("theme_colors", "default") ?: "default"
        ThemeConfig.currentTheme = ThemeColors.fromName(themeName)
    }

    fun saveDynamicColorState(context: Context, enabled: Boolean) {
        context.appPreferences.putBoolean("use_dynamic_color", enabled)
        ThemeConfig.useDynamicColor = enabled
    }


    fun loadDynamicColorState(context: Context) {
        val enabled = context.appPreferences.getBoolean(
            "use_dynamic_color",
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        )
        ThemeConfig.useDynamicColor = enabled
    }
}

object BackgroundManager {
    private const val TAG = "BackgroundManager"

    fun saveBackgroundDim(context: Context, dim: Float) {
        ThemeConfig.backgroundDim = dim
        context.appPreferences.putFloat("background_dim", dim)
    }

    fun saveEnableBlur(context: Context, enable: Boolean) {
        ThemeConfig.isEnableBlur = enable
        context.appPreferences.putBoolean("enable_blur", enable)
    }

    fun saveEnableBlurExp(context: Context, enable: Boolean) {
        ThemeConfig.isEnableBlurExp = enable
        context.appPreferences.putBoolean("enable_blur_exp", enable)
    }

    fun saveUseBackgroundSeedColor(context: Context, enable: Boolean) {
        ThemeConfig.isUseBackgroundSeedColor = enable
        context.appPreferences.putBoolean("use_background_seed_color", enable)
    }

    fun saveEnableHighContrastMode(context: Context, enable: Boolean) {
        ThemeConfig.isHighContrastMode = enable
        context.appPreferences.putBoolean("high_contrast_mode", enable)
    }

    fun saveAndApplyCustomBackground(
        context: Context,
        uri: Uri,
        transformation: BackgroundTransformation? = null
    ) {
        try {
            val finalUri = if (transformation != null) {
                context.saveTransformedBackground(uri, transformation)
            } else {
                copyImageToInternalStorage(context, uri)
            }

            saveBackgroundUri(context, finalUri)
            ThemeConfig.customBackgroundUri = finalUri
            CardConfig.updateBackground(true)
            clearBackgroundBlurCache(context)
            resetBackgroundState(context)

        } catch (e: Exception) {
            Log.e(TAG, "保存背景失败: ${e.message}", e)
        }
    }

    fun clearCustomBackground(context: Context) {
        saveBackgroundUri(context, null)
        ThemeConfig.customBackgroundUri = null
        CardConfig.updateBackground(false)
        clearBackgroundBlurCache(context)
        resetBackgroundState(context)
    }

    fun loadCustomBackground(context: Context) {
        val prefs = context.appPreferences
        val uriString = prefs.getString("custom_background", null)

        val newUri = uriString?.toUri()
        val preventRefresh = prefs.getBoolean("prevent_background_refresh", false)

        ThemeConfig.preventBackgroundRefresh = preventRefresh

        if (!preventRefresh || ThemeConfig.customBackgroundUri?.toString() != newUri?.toString()) {
            Log.d(TAG, "加载自定义背景: $uriString")
            ThemeConfig.customBackgroundUri = newUri
            ThemeConfig.backgroundImageLoaded = false
            CardConfig.updateBackground(newUri != null)
        }

        ThemeConfig.backgroundDim = prefs.getFloat("background_dim", 0f).coerceIn(0f, 1f)
        ThemeConfig.isEnableBlur = prefs.getBoolean("enable_blur", false)
        ThemeConfig.isEnableBlurExp = prefs.getBoolean("enable_blur_exp", false)
        ThemeConfig.isUseBackgroundSeedColor = prefs.getBoolean("use_background_seed_color", false)
        ThemeConfig.isHighContrastMode = prefs.getBoolean("high_contrast_mode", false)
    }

    private fun saveBackgroundUri(context: Context, uri: Uri?) {
        context.appPreferences.putString("custom_background", uri?.toString())
        context.appPreferences.putBoolean("prevent_background_refresh", false)
    }

    private fun resetBackgroundState(context: Context) {
        ThemeConfig.backgroundImageLoaded = false
        ThemeConfig.preventBackgroundRefresh = false
        blurBackgroundImageBitmap = null
        context.appPreferences.putBoolean("prevent_background_refresh", false)
    }

    fun clearBackgroundBlurCache(context: Context) {
        runCatching {
            backgroundBlurCacheDir(context).deleteRecursively()
        }.onFailure {
            Log.w(TAG, "Failed to clear background blur cache: ${it.message}")
        }
    }

    private fun copyImageToInternalStorage(context: Context, uri: Uri): Uri? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val fileName = "custom_background.jpg"
            val file = File(context.filesDir, fileName)

            FileOutputStream(file).use { outputStream ->
                val buffer = ByteArray(8 * 1024)
                var read: Int
                while (inputStream.read(buffer).also { read = it } != -1) {
                    outputStream.write(buffer, 0, read)
                }
                outputStream.flush()
            }
            inputStream.close()

            Uri.fromFile(file)
        } catch (e: Exception) {
            Log.e(TAG, "复制图片失败: ${e.message}", e)
            null
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun KernelSUTheme(
    dpi: Int = 0,
    darkTheme: Boolean = isInDarkTheme(ThemeConfig.forceDarkMode),
    dynamicColor: Boolean = ThemeConfig.useDynamicColor,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemIsDark = isSystemInDarkTheme()

    // 初始化主题
    ThemeInitializer(context = context, systemIsDark = systemIsDark)

    // 创建颜色方案
    val colorScheme = createColorScheme(darkTheme, dynamicColor)

    // 系统栏样式
    SystemBarController(darkTheme)

    val systemDensity = LocalDensity.current

    val density = remember(systemDensity, dpi) {
        if (dpi <= 0f) {
            systemDensity
        } else {
            val targetDensity = dpi / 160f
            Density(density = targetDensity, fontScale = systemDensity.fontScale)
        }
    }

    CompositionLocalProvider(
        LocalDensity provides density
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            typography = generateTypography()
        ) {
            MonetColorsProvider.UpdateCss()
            Box(modifier = Modifier.fillMaxSize()) {
                BackgroundLayer()
                content()
            }
        }
    }
}

@Composable
private fun ThemeInitializer(context: Context, systemIsDark: Boolean) {
    val themeChanged = ThemeConfig.detectThemeChange(systemIsDark)
    val scope = rememberCoroutineScope()

    // 处理系统主题变化
    LaunchedEffect(systemIsDark, themeChanged) {
        if (ThemeConfig.forceDarkMode == null && themeChanged) {
            Log.d("ThemeSystem", "系统主题变化: $systemIsDark")
            ThemeConfig.resetBackgroundState()

            if (!ThemeConfig.preventBackgroundRefresh) {
                BackgroundManager.loadCustomBackground(context)
            }

            CardConfig.apply {
                load(context)
                setThemeDefaults(systemIsDark)
                save(context)
            }
        }
    }

    // 初始加载配置
    LaunchedEffect(Unit) {
        scope.launch {
            ThemeManager.loadThemeMode(context)
            ThemeManager.loadThemeColors(context)
            ThemeManager.loadDynamicColorState(context)
            CardConfig.load(context)

            if (!ThemeConfig.backgroundImageLoaded && !ThemeConfig.preventBackgroundRefresh) {
                BackgroundManager.loadCustomBackground(context)
            }
        }
    }
}

@Composable
private fun BackgroundLayer() {
    val context = LocalContext.current
    val backgroundUri = rememberSaveable { mutableStateOf(ThemeConfig.customBackgroundUri) }

    LaunchedEffect(ThemeConfig.customBackgroundUri) {
        backgroundUri.value = ThemeConfig.customBackgroundUri
        if (backgroundUri.value == null) {
            backgroundImagePainter = null
            blurBackgroundImageBitmap = null
            backgroundSeedColor = 0
            context.appPreferences.remove("cached_seed_color")
        }
    }

    val hasBlurBitmap = blurBackgroundImageBitmap != null

    LaunchedEffect(ThemeConfig.isEnableBlurExp, hasBlurBitmap) {
        if (!ThemeConfig.isEnableBlurExp || !hasBlurBitmap) return@LaunchedEffect

        while (true) {
            withFrameNanos { }
            backgroundBlurFrameTick = if (backgroundBlurFrameTick == Int.MAX_VALUE) {
                0
            } else {
                backgroundBlurFrameTick + 1
            }
        }
    }

    // 默认背景
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(-2f)
            .onSizeChanged { size ->
                if (
                    size.width > 0 &&
                    size.height > 0 &&
                    backgroundBlurViewportSize != size
                ) {
                    backgroundBlurViewportSize = size
                    blurBackgroundImageBitmap = null
                }
            }
            .background(
                MaterialTheme.colorScheme.surfaceContainer
            )
    )

    // 自定义背景
    backgroundUri.value?.let { uri ->
        BackgroundInitializer(uri = uri)
    }
}

var backgroundImagePainter: AsyncImagePainter? by mutableStateOf(null)
var blurBackgroundImageBitmap: ImageBitmap? by mutableStateOf(null)
private var backgroundBlurViewportSize by mutableStateOf(IntSize(0, 0))
private var backgroundBlurFrameTick by mutableIntStateOf(0)
var backgroundSeedColor by mutableIntStateOf(0)

private const val BACKGROUND_BLUR_RADIUS = 25f
private const val BACKGROUND_BLUR_CACHE_VERSION = 1

/**
 * Captures background content for blurEffect child nodes,
 * It will only work when blurState available
 * @return modified modifier
 */
@Composable
fun Modifier.blurSource(): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return this

    return LocalBlurState.current?.let {
        this.then(Modifier.layerBackdrop(it))
    } ?: this
}

/**
 * Render blur when backdrop available
 * @return modified modifier
 */
@Composable
fun Modifier.blurEffect(): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return this

    return LocalBlurState.current?.let { backdrop ->
        val blendColor =
            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.8f)

        this.then(
            Modifier.textureBlur(
                backdrop = backdrop,
                shape = RectangleShape,
                blurRadius = 25f,
                colors = BlurColors(
                    blendColors = listOf(
                        BlendColorEntry(color = blendColor)
                    )
                )
            )
        )
    } ?: this
}


fun Modifier.renderBackgroundBlur(
    tintColor: Color? = null
): Modifier = composed {
    if (!ThemeConfig.isEnableBlurExp) return@composed this

    var coordinates by remember {
        mutableStateOf<LayoutCoordinates?>(null)
    }

    val tintColor = (tintColor ?: MaterialTheme.colorScheme.surfaceContainerHighest).copy(
        alpha = CardConfig.cardAlpha
    )
    val backgroundBlurAnchor = LocalBackgroundBlurAnchor.current

    this
        .onGloballyPositioned { newCoordinates ->
            coordinates = newCoordinates.takeIf { it.isAttached }
        }
        .drawWithContent {
            backgroundBlurFrameTick

            val currentBitmap = blurBackgroundImageBitmap
            val currentBoundsInBackground = coordinates?.boundsInBackgroundNow(backgroundBlurAnchor)

            if (
                currentBitmap != null &&
                currentBoundsInBackground != null &&
                currentBoundsInBackground.width > 0f &&
                currentBoundsInBackground.height > 0f
            ) {
                drawBitmapIntersection(
                    bitmap = currentBitmap,
                    boundsInBackground = currentBoundsInBackground,
                )

                drawRect(
                    color = tintColor,
                    blendMode = BlendMode.SrcOver,
                )
            }

            drawContent()
        }
}

private fun LayoutCoordinates.boundsInBackgroundNow(
    backgroundCoordinates: LayoutCoordinates?,
): Rect? {
    if (!isAttached || size.width <= 0 || size.height <= 0) return null

    return backgroundCoordinates
        ?.takeIf { it.isAttached && it.size.width > 0 && it.size.height > 0 }
        ?.let { boundsInCoordinatesNow(it) }
        ?: localBoundsInWindowNow()
}

private fun LayoutCoordinates.boundsInCoordinatesNow(
    targetCoordinates: LayoutCoordinates,
): Rect? {
    fun localToTarget(point: Offset): Offset {
        val screenPoint = localToScreen(point)
        if (!screenPoint.isUsable()) return Offset.Unspecified

        return targetCoordinates.screenToLocal(screenPoint)
    }

    val width = size.width.toFloat()
    val height = size.height.toFloat()

    return boundsFromCorners(
        topLeft = localToTarget(Offset.Zero),
        topRight = localToTarget(Offset(width, 0f)),
        bottomLeft = localToTarget(Offset(0f, height)),
        bottomRight = localToTarget(Offset(width, height)),
    )
}

private fun LayoutCoordinates.localBoundsInWindowNow(): Rect? {
    val width = size.width.toFloat()
    val height = size.height.toFloat()

    return boundsFromCorners(
        topLeft = localToWindow(Offset.Zero),
        topRight = localToWindow(Offset(width, 0f)),
        bottomLeft = localToWindow(Offset(0f, height)),
        bottomRight = localToWindow(Offset(width, height)),
    )
}

private fun boundsFromCorners(
    topLeft: Offset,
    topRight: Offset,
    bottomLeft: Offset,
    bottomRight: Offset,
): Rect? {
    if (
        !topLeft.isUsable() ||
        !topRight.isUsable() ||
        !bottomLeft.isUsable() ||
        !bottomRight.isUsable()
    ) {
        return null
    }

    return Rect(
        left = minOf(topLeft.x, topRight.x, bottomLeft.x, bottomRight.x),
        top = minOf(topLeft.y, topRight.y, bottomLeft.y, bottomRight.y),
        right = maxOf(topLeft.x, topRight.x, bottomLeft.x, bottomRight.x),
        bottom = maxOf(topLeft.y, topRight.y, bottomLeft.y, bottomRight.y),
    )
}

private data class BitmapDrawSegment(
    val srcStart: Int,
    val srcEnd: Int,
    val dstStart: Int,
    val dstEnd: Int,
)

private fun buildBitmapDrawSegments(
    sourceStart: Float,
    sourceEnd: Float,
    bitmapSize: Int,
    destinationSize: Float,
): List<BitmapDrawSegment> {
    val sourceSpan = sourceEnd - sourceStart
    val destinationEnd = ceil(destinationSize).toInt()

    if (bitmapSize <= 0 || sourceSpan <= 0f || destinationEnd <= 0) {
        return emptyList()
    }

    fun mapToDestination(value: Float): Float {
        return (value - sourceStart) / sourceSpan * destinationSize
    }

    val segments = mutableListOf<BitmapDrawSegment>()

    // Emulate Shader.TileMode.CLAMP for areas that are above / left of the bitmap.
    if (sourceStart < 0f) {
        val dstEnd = ceil(mapToDestination(0f).coerceIn(0f, destinationSize))
            .toInt()
            .coerceIn(0, destinationEnd)

        if (dstEnd > 0) {
            segments += BitmapDrawSegment(
                srcStart = 0,
                srcEnd = 1,
                dstStart = 0,
                dstEnd = dstEnd,
            )
        }
    }

    val clippedStart = sourceStart.coerceIn(0f, bitmapSize.toFloat())
    val clippedEnd = sourceEnd.coerceIn(0f, bitmapSize.toFloat())

    if (clippedEnd > clippedStart) {
        val srcStart = floor(clippedStart).toInt().coerceIn(0, bitmapSize - 1)
        val srcEnd = ceil(clippedEnd).toInt().coerceIn(srcStart + 1, bitmapSize)
        val dstStart = floor(mapToDestination(clippedStart).coerceIn(0f, destinationSize))
            .toInt()
            .coerceIn(0, destinationEnd)
        val dstEnd = ceil(mapToDestination(clippedEnd).coerceIn(0f, destinationSize))
            .toInt()
            .coerceIn(0, destinationEnd)

        if (dstEnd > dstStart) {
            segments += BitmapDrawSegment(
                srcStart = srcStart,
                srcEnd = srcEnd,
                dstStart = dstStart,
                dstEnd = dstEnd,
            )
        }
    }

    // Emulate Shader.TileMode.CLAMP for areas that are below / right of the bitmap.
    if (sourceEnd > bitmapSize.toFloat()) {
        val dstStart = floor(mapToDestination(bitmapSize.toFloat()).coerceIn(0f, destinationSize))
            .toInt()
            .coerceIn(0, destinationEnd)

        if (destinationEnd > dstStart) {
            segments += BitmapDrawSegment(
                srcStart = bitmapSize - 1,
                srcEnd = bitmapSize,
                dstStart = dstStart,
                dstEnd = destinationEnd,
            )
        }
    }

    // The whole destination is outside one side of the source bitmap.
    if (segments.isEmpty()) {
        val src = if (sourceEnd <= 0f) 0 else bitmapSize - 1
        segments += BitmapDrawSegment(
            srcStart = src,
            srcEnd = src + 1,
            dstStart = 0,
            dstEnd = destinationEnd,
        )
    }

    return segments
}

private fun ContentDrawScope.drawBitmapIntersection(
    bitmap: ImageBitmap,
    boundsInBackground: Rect,
) {
    val bitmapWidth = bitmap.width
    val bitmapHeight = bitmap.height

    if (
        bitmapWidth <= 0 ||
        bitmapHeight <= 0 ||
        size.width <= 0f ||
        size.height <= 0f ||
        boundsInBackground.width <= 0f ||
        boundsInBackground.height <= 0f
    ) {
        return
    }

    val xSegments = buildBitmapDrawSegments(
        sourceStart = boundsInBackground.left,
        sourceEnd = boundsInBackground.right,
        bitmapSize = bitmapWidth,
        destinationSize = size.width,
    )
    val ySegments = buildBitmapDrawSegments(
        sourceStart = boundsInBackground.top,
        sourceEnd = boundsInBackground.bottom,
        bitmapSize = bitmapHeight,
        destinationSize = size.height,
    )

    for (xSegment in xSegments) {
        for (ySegment in ySegments) {
            val srcWidth = xSegment.srcEnd - xSegment.srcStart
            val srcHeight = ySegment.srcEnd - ySegment.srcStart
            val dstWidth = xSegment.dstEnd - xSegment.dstStart
            val dstHeight = ySegment.dstEnd - ySegment.dstStart

            if (srcWidth <= 0 || srcHeight <= 0 || dstWidth <= 0 || dstHeight <= 0) {
                continue
            }

            drawImage(
                image = bitmap,
                srcOffset = IntOffset(xSegment.srcStart, ySegment.srcStart),
                srcSize = IntSize(srcWidth, srcHeight),
                dstOffset = IntOffset(xSegment.dstStart, ySegment.dstStart),
                dstSize = IntSize(dstWidth, dstHeight),
                blendMode = BlendMode.SrcOver,
            )
        }
    }
}

private fun Offset.isUsable(): Boolean {
    return x.isFinite() && y.isFinite()
}

private suspend fun Bitmap.extractSeedColor(
    maxColors: Int = 128,
    fallbackColorArgb: Int = -12417548
): Int = withContext(Dispatchers.IO) {
    val scaledBitmap = this@extractSeedColor.scale(128, 128)

    val width = scaledBitmap.width
    val height = scaledBitmap.height
    val pixels = IntArray(width * height)
    scaledBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

    val colorToCountMap: Map<Int, Int> = QuantizerCelebi.quantize(pixels, maxColors)
    val sortedColors: List<Int> = Score.score(colorToCountMap, 10, fallbackColorArgb, true)

    if (scaledBitmap != this@extractSeedColor) {
        scaledBitmap.recycle()
    }

    sortedColors.firstOrNull() ?: fallbackColorArgb
}

private fun Bitmap.softwareFastBlur(radius: Int): Bitmap {
    if (radius < 1) return this

    val w = width
    val h = height
    val pix = IntArray(w * h)
    getPixels(pix, 0, w, 0, 0, w, h)

    val wm = w - 1
    val hm = h - 1
    val wh = w * h
    val div = radius + radius + 1

    val r = IntArray(wh)
    val g = IntArray(wh)
    val b = IntArray(wh)
    var rsum: Int; var gsum: Int; var bsum: Int
    var p: Int; var yp: Int; var yi: Int
    val vmin = IntArray(w.coerceAtLeast(h))

    var divsum = (div + 1) shr 1
    divsum *= divsum
    val dv = IntArray(256 * divsum)
    for (i in 0 until 256 * divsum) {
        dv[i] = i / divsum
    }

    var yw = 0
    yi = 0

    val stack = Array(div) { IntArray(3) }
    var stackpointer: Int
    var stackstart: Int
    var sir: IntArray
    var rbs: Int
    val r1 = radius + 1
    var routsum: Int; var goutsum: Int; var boutsum: Int
    var rinsum: Int; var ginsum: Int; var binsum: Int

    for (y in 0 until h) {
        bsum = 0; gsum = 0; rsum = 0
        boutsum = 0; goutsum = 0; routsum = 0
        binsum = 0; ginsum = 0; rinsum = 0
        for (i in -radius..radius) {
            p = pix[yi + wm.coerceAtMost(i.coerceAtLeast(0))]
            sir = stack[i + radius]
            sir[0] = (p and 0xff0000) shr 16
            sir[1] = (p and 0x00ff00) shr 8
            sir[2] = p and 0x0000ff
            rbs = r1 - abs(i)
            rsum += sir[0] * rbs
            gsum += sir[1] * rbs
            bsum += sir[2] * rbs
            if (i > 0) {
                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]
            } else {
                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]
            }
        }
        stackpointer = radius

        for (x in 0 until w) {
            r[yi] = dv[rsum]
            g[yi] = dv[gsum]
            b[yi] = dv[bsum]

            rsum -= routsum
            gsum -= goutsum
            bsum -= boutsum

            stackstart = stackpointer - radius + div
            sir = stack[stackstart % div]

            routsum -= sir[0]
            goutsum -= sir[1]
            boutsum -= sir[2]

            if (y == 0) vmin[x] = (x + radius + 1).coerceAtMost(wm)
            p = pix[yw + vmin[x]]

            sir[0] = (p and 0xff0000) shr 16
            sir[1] = (p and 0x00ff00) shr 8
            sir[2] = p and 0x0000ff

            rinsum += sir[0]
            ginsum += sir[1]
            binsum += sir[2]

            rsum += rinsum
            gsum += ginsum
            bsum += binsum

            stackpointer = (stackpointer + 1) % div
            sir = stack[stackpointer % div]

            routsum += sir[0]
            goutsum += sir[1]
            boutsum += sir[2]

            rinsum -= sir[0]
            ginsum -= sir[1]
            binsum -= sir[2]

            yi++
        }
        yw += w
    }

    for (x in 0 until w) {
        bsum = 0; gsum = 0; rsum = 0
        boutsum = 0; goutsum = 0; routsum = 0
        binsum = 0; ginsum = 0; rinsum = 0
        yp = -radius * w
        for (i in -radius..radius) {
            yi = (yp.coerceAtLeast(0)) + x
            sir = stack[i + radius]
            sir[0] = r[yi]
            sir[1] = g[yi]
            sir[2] = b[yi]
            rbs = r1 - abs(i)
            rsum += r[yi] * rbs
            gsum += g[yi] * rbs
            bsum += b[yi] * rbs
            if (i > 0) {
                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]
            } else {
                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]
            }
            if (i < hm) yp += w
        }
        yi = x
        stackpointer = radius
        for (y in 0 until h) {
            pix[yi] = (-0x1000000 and pix[yi]) or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]
            rsum -= routsum
            gsum -= goutsum
            bsum -= boutsum
            stackstart = stackpointer - radius + div
            sir = stack[stackstart % div]
            routsum -= sir[0]
            goutsum -= sir[1]
            boutsum -= sir[2]
            if (x == 0) vmin[y] = (y + r1).coerceAtMost(hm) * w
            p = x + vmin[y]
            sir[0] = r[p]
            sir[1] = g[p]
            sir[2] = b[p]
            rinsum += sir[0]
            ginsum += sir[1]
            binsum += sir[2]
            rsum += rinsum
            gsum += ginsum
            bsum += binsum
            stackpointer = (stackpointer + 1) % div
            sir = stack[stackpointer]
            routsum += sir[0]
            goutsum += sir[1]
            boutsum += sir[2]
            rinsum -= sir[0]
            ginsum -= sir[1]
            binsum -= sir[2]
            yi += w
        }
    }

    val outputBitmap = createBitmap(w, h)
    outputBitmap.setPixels(pix, 0, w, 0, 0, w, h)
    return outputBitmap
}

@RequiresApi(Build.VERSION_CODES.S)
private fun Bitmap.blurBitmap(blurRadius: Float): Bitmap {
    val outputBitmap = createBitmap(width, height)
    val outputCanvas = Canvas(outputBitmap)

    if (outputCanvas.isHardwareAccelerated) {
        val renderNode = RenderNode("BlurEffectNode").apply {
            setPosition(0, 0, width, height)
            setRenderEffect(
                RenderEffect.createBlurEffect(
                    blurRadius,
                    blurRadius,
                    Shader.TileMode.CLAMP
                )
            )
        }

        val recordingCanvas = renderNode.beginRecording()
        recordingCanvas.drawBitmap(this, 0f, 0f, null)
        renderNode.endRecording()

        outputCanvas.drawRenderNode(renderNode)
    } else {
        val radiusInt = blurRadius.toInt().coerceIn(1, 25)
        return this.softwareFastBlur(radiusInt)
    }

    return outputBitmap
}


@RequiresApi(Build.VERSION_CODES.S)
private suspend fun Bitmap.createBackgroundBlurImage(
    context: Context,
    sourceUri: Uri,
    viewportSize: IntSize,
    blurRadius: Float,
): ImageBitmap = withContext(Dispatchers.Default) {
    val cacheFile = backgroundBlurCacheFile(
        context = context,
        sourceUri = sourceUri,
        viewportSize = viewportSize,
        blurRadius = blurRadius,
    )

    BitmapFactory.decodeFile(cacheFile.absolutePath)?.let { cachedBitmap ->
        return@withContext cachedBitmap.asImageBitmap()
    }

    val blurSource = createBackgroundBlurSource(viewportSize)

    val blurredBitmap = try {
        blurSource.blurBitmap(blurRadius)
    } finally {
        if (blurSource !== this@createBackgroundBlurImage) {
            blurSource.recycle()
        }
    }

    saveBackgroundBlurCache(cacheFile, blurredBitmap)
    blurredBitmap.asImageBitmap()
}

private fun Bitmap.createBackgroundBlurSource(viewportSize: IntSize): Bitmap {
    val targetWidth = viewportSize.width
    val targetHeight = viewportSize.height

    if (
        targetWidth <= 0 ||
        targetHeight <= 0 ||
        (width == targetWidth && height == targetHeight)
    ) {
        return this
    }

    val scale = maxOf(
        targetWidth / width.toFloat(),
        targetHeight / height.toFloat(),
    )
    val scaledWidth = width * scale
    val scaledHeight = height * scale
    val left = (targetWidth - scaledWidth) / 2f
    val top = (targetHeight - scaledHeight) / 2f

    return createBitmap(targetWidth, targetHeight).also { outputBitmap ->
        Canvas(outputBitmap).drawBitmap(
            this,
            null,
            RectF(left, top, left + scaledWidth, top + scaledHeight),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
        )
    }
}

private fun backgroundBlurCacheDir(context: Context): File =
    File(context.filesDir, "background_blur_cache")

private fun backgroundBlurCacheFile(
    context: Context,
    sourceUri: Uri,
    viewportSize: IntSize,
    blurRadius: Float,
): File {
    val sourceSignature = buildBackgroundSourceSignature(sourceUri)
    val key = listOf(
        "v$BACKGROUND_BLUR_CACHE_VERSION",
        sourceSignature,
        "${viewportSize.width}x${viewportSize.height}",
        blurRadius.toString(),
        Build.VERSION.SDK_INT.toString(),
    ).joinToString("|").sha256()

    return File(backgroundBlurCacheDir(context), "$key.png")
}

private fun buildBackgroundSourceSignature(sourceUri: Uri): String {
    val path = sourceUri.path
    val file = if (sourceUri.scheme == "file" && path != null) File(path) else null
    return if (file != null && file.exists()) {
        "${sourceUri}|${file.length()}|${file.lastModified()}"
    } else {
        sourceUri.toString()
    }
}

private fun saveBackgroundBlurCache(cacheFile: File, bitmap: Bitmap) {
    runCatching {
        cacheFile.parentFile?.mkdirs()
        val tempFile = File(cacheFile.parentFile, "${cacheFile.name}.tmp")
        FileOutputStream(tempFile).use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        if (!tempFile.renameTo(cacheFile)) {
            tempFile.copyTo(cacheFile, overwrite = true)
            tempFile.delete()
        }
    }.onFailure {
        Log.w("ThemeSystem", "Failed to save background blur cache: ${it.message}")
    }
}

private fun String.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
}

@Composable
private fun BackgroundInitializer(uri: Uri) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val dynamicColorFromSystem =
        if (Build.VERSION.SDK_INT >= 31)
            colorResource(id = R.color.system_accent1_500).toArgb()
        else -12417548

    val calcedCachedSeedColor =
        context.appPreferences.getInt("cached_seed_color", dynamicColorFromSystem)

    LaunchedEffect(ThemeConfig.isEnableBlurExp, backgroundBlurViewportSize) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ThemeConfig.isEnableBlurExp &&
            backgroundBlurViewportSize.width > 0 &&
            backgroundBlurViewportSize.height > 0
        ) {
            backgroundImagePainter?.let {
                if (it.state !is AsyncImagePainter.State.Success) return@let

                val bitmap = (it.state as AsyncImagePainter.State.Success).result.drawable.toBitmap()
                blurBackgroundImageBitmap = bitmap.createBackgroundBlurImage(
                    context = context,
                    sourceUri = uri,
                    viewportSize = backgroundBlurViewportSize,
                    blurRadius = BACKGROUND_BLUR_RADIUS,
                )
            }
        } else {
            blurBackgroundImageBitmap = null
        }
    }

    backgroundImagePainter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context)
            .data(uri)
            .allowHardware(false)
            .crossfade(true)
            .build(),
        onError = { error ->
            Log.e("ThemeSystem", "背景加载失败: ${error.result.throwable.message}")
            ThemeConfig.customBackgroundUri = null
        },
        onSuccess = {
            Log.d("ThemeSystem", "背景加载成功")
            ThemeConfig.backgroundImageLoaded = true
            ThemeConfig.isThemeChanging = false

            val bitmap = it.result.drawable.toBitmap()
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ThemeConfig.isEnableBlurExp &&
                backgroundBlurViewportSize.width > 0 &&
                backgroundBlurViewportSize.height > 0
            ) {
                coroutineScope.launch {
                    blurBackgroundImageBitmap = bitmap.createBackgroundBlurImage(
                        context = context,
                        sourceUri = uri,
                        viewportSize = backgroundBlurViewportSize,
                        blurRadius = BACKGROUND_BLUR_RADIUS,
                    )
                }
            } else {
                blurBackgroundImageBitmap = null
            }

            backgroundSeedColor = calcedCachedSeedColor
            coroutineScope.launch {
                backgroundSeedColor = bitmap.extractSeedColor(
                    fallbackColorArgb = calcedCachedSeedColor
                )

                context.appPreferences.putInt("cached_seed_color", backgroundSeedColor)
            }
        }
    )
}

@Composable
private fun generateTypography(): androidx.compose.material3.Typography {
    val darkMode = isInDarkTheme(ThemeConfig.forceDarkMode)

    fun generateShadow(originalShadow: Shadow?): Shadow? {
        if (!ThemeConfig.isHighContrastMode) return originalShadow
        val shadow = originalShadow ?: Shadow(
            offset = Offset(1.5f, 1.5f),
            blurRadius = 0f
        )
        return shadow.copy(
            color = if (darkMode) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.7f)
        )
    }

    fun TextStyle.applyShadow() = this.copy(shadow = generateShadow(shadow))
    val typography = MaterialTheme.typography

    return typography.copy(
        displayLarge = typography.displayLarge.applyShadow(),
        displayMedium = typography.displayMedium.applyShadow(),
        displaySmall = typography.displaySmall.applyShadow(),
        headlineLarge = typography.headlineLarge.applyShadow(),
        headlineMedium = typography.headlineMedium.applyShadow(),
        headlineSmall = typography.headlineSmall.applyShadow(),
        titleLarge = typography.titleLarge.applyShadow(),
        titleMedium = typography.titleMedium.applyShadow(),
        titleSmall = typography.titleSmall.applyShadow(),
        bodyLarge = typography.bodyLarge.applyShadow(),
        bodyMedium = typography.bodyMedium.applyShadow(),
        bodySmall = typography.bodySmall.applyShadow(),
        labelLarge = typography.labelLarge.applyShadow(),
        labelMedium = typography.labelMedium.applyShadow(),
        labelSmall = typography.labelSmall.applyShadow(),
        displayLargeEmphasized = typography.displayLargeEmphasized.applyShadow(),
        displayMediumEmphasized = typography.displayMediumEmphasized.applyShadow(),
        displaySmallEmphasized = typography.displaySmallEmphasized.applyShadow(),
        headlineLargeEmphasized = typography.headlineLargeEmphasized.applyShadow(),
        headlineMediumEmphasized = typography.headlineMediumEmphasized.applyShadow(),
        headlineSmallEmphasized = typography.headlineSmallEmphasized.applyShadow(),
        titleLargeEmphasized = typography.titleLargeEmphasized.applyShadow(),
        titleMediumEmphasized = typography.titleMediumEmphasized.applyShadow(),
        titleSmallEmphasized = typography.titleSmallEmphasized.applyShadow(),
        bodyLargeEmphasized = typography.bodyLargeEmphasized.applyShadow(),
        bodyMediumEmphasized = typography.bodyMediumEmphasized.applyShadow(),
        bodySmallEmphasized = typography.bodySmallEmphasized.applyShadow(),
        labelLargeEmphasized = typography.labelLargeEmphasized.applyShadow(),
        labelMediumEmphasized = typography.labelMediumEmphasized.applyShadow(),
        labelSmallEmphasized = typography.labelSmallEmphasized.applyShadow(),
    )
}

// TODO migrate to MaterialKolor, provide scheme settings/dynamic seed color/spec 2025 to user settings
@Composable
private fun createColorScheme(
    darkTheme: Boolean,
    dynamicColor: Boolean
): ColorScheme {
    return when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val seedColor =
                if (ThemeConfig.isUseBackgroundSeedColor) backgroundSeedColor else colorResource(id = R.color.system_accent1_500).toArgb()
            val hct = Hct.fromInt(seedColor)
            val scheme = SchemeTonalSpot(hct, darkTheme, 0.0)

            fun Int.toColor(): Color = Color(this)
            MaterialTheme.colorScheme.copy(
                primary = scheme.primary.toColor(),
                onPrimary = scheme.onPrimary.toColor(),
                primaryContainer = scheme.primaryContainer.toColor(),
                onPrimaryContainer = scheme.onPrimaryContainer.toColor(),
                inversePrimary = scheme.inversePrimary.toColor(),
                secondary = scheme.secondary.toColor(),
                onSecondary = scheme.onSecondary.toColor(),
                secondaryContainer = scheme.secondaryContainer.toColor(),
                onSecondaryContainer = scheme.onSecondaryContainer.toColor(),
                tertiary = scheme.tertiary.toColor(),
                onTertiary = scheme.onTertiary.toColor(),
                tertiaryContainer = scheme.tertiaryContainer.toColor(),
                onTertiaryContainer = scheme.onTertiaryContainer.toColor(),
                background = scheme.background.toColor(),
                onBackground = scheme.onBackground.toColor(),
                surface = scheme.surface.toColor(),
                onSurface = scheme.onSurface.toColor(),
                surfaceVariant = scheme.surfaceVariant.toColor(),
                onSurfaceVariant = scheme.onSurfaceVariant.toColor(),
                surfaceTint = scheme.primary.toColor(),
                inverseSurface = scheme.inverseSurface.toColor(),
                inverseOnSurface = scheme.inverseOnSurface.toColor(),
                error = scheme.error.toColor(),
                onError = scheme.onError.toColor(),
                errorContainer = scheme.errorContainer.toColor(),
                onErrorContainer = scheme.onErrorContainer.toColor(),
                outline = scheme.outline.toColor(),
                outlineVariant = scheme.outlineVariant.toColor(),
                scrim = scheme.scrim.toColor(),
                surfaceBright = scheme.surfaceBright.toColor(),
                surfaceDim = scheme.surfaceDim.toColor(),
                surfaceContainer = scheme.surfaceContainer.toColor(),
                surfaceContainerHigh = scheme.surfaceContainerHigh.toColor(),
                surfaceContainerHighest = scheme.surfaceContainerHighest.toColor(),
                surfaceContainerLow = scheme.surfaceContainerLow.toColor(),
                surfaceContainerLowest = scheme.surfaceContainerLowest.toColor(),
            )
        }

        darkTheme -> createDarkColorScheme()
        else -> createLightColorScheme()
    }
}

@Composable
private fun SystemBarController(darkMode: Boolean) {
    val context = LocalContext.current
    val activity = context as ComponentActivity

    SideEffect {
        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                Color.Transparent.toArgb(),
                Color.Transparent.toArgb(),
            ) { darkMode },
            navigationBarStyle = if (darkMode) {
                SystemBarStyle.dark(Color.Transparent.toArgb())
            } else {
                SystemBarStyle.light(
                    Color.Transparent.toArgb(),
                    Color.Transparent.toArgb()
                )
            }
        )
    }
}

@Composable
private fun createDarkColorScheme() = darkColorScheme(
    primary = ThemeConfig.currentTheme.primaryDark,
    onPrimary = ThemeConfig.currentTheme.onPrimaryDark,
    primaryContainer = ThemeConfig.currentTheme.primaryContainerDark,
    onPrimaryContainer = ThemeConfig.currentTheme.onPrimaryContainerDark,
    secondary = ThemeConfig.currentTheme.secondaryDark,
    onSecondary = ThemeConfig.currentTheme.onSecondaryDark,
    secondaryContainer = ThemeConfig.currentTheme.secondaryContainerDark,
    onSecondaryContainer = ThemeConfig.currentTheme.onSecondaryContainerDark,
    tertiary = ThemeConfig.currentTheme.tertiaryDark,
    onTertiary = ThemeConfig.currentTheme.onTertiaryDark,
    tertiaryContainer = ThemeConfig.currentTheme.tertiaryContainerDark,
    onTertiaryContainer = ThemeConfig.currentTheme.onTertiaryContainerDark,
    error = ThemeConfig.currentTheme.errorDark,
    onError = ThemeConfig.currentTheme.onErrorDark,
    errorContainer = ThemeConfig.currentTheme.errorContainerDark,
    onErrorContainer = ThemeConfig.currentTheme.onErrorContainerDark,
    background = if (CardConfig.isCustomBackgroundEnabled) Color.Transparent else ThemeConfig.currentTheme.backgroundDark,
    onBackground = ThemeConfig.currentTheme.onBackgroundDark,
    surface = if (CardConfig.isCustomBackgroundEnabled) Color.Transparent else ThemeConfig.currentTheme.surfaceDark,
    onSurface = ThemeConfig.currentTheme.onSurfaceDark,
    surfaceVariant = ThemeConfig.currentTheme.surfaceVariantDark,
    onSurfaceVariant = ThemeConfig.currentTheme.onSurfaceVariantDark,
    outline = ThemeConfig.currentTheme.outlineDark,
    outlineVariant = ThemeConfig.currentTheme.outlineVariantDark,
    scrim = ThemeConfig.currentTheme.scrimDark,
    inverseSurface = ThemeConfig.currentTheme.inverseSurfaceDark,
    inverseOnSurface = ThemeConfig.currentTheme.inverseOnSurfaceDark,
    inversePrimary = ThemeConfig.currentTheme.inversePrimaryDark,
    surfaceDim = ThemeConfig.currentTheme.surfaceDimDark,
    surfaceBright = ThemeConfig.currentTheme.surfaceBrightDark,
    surfaceContainerLowest = ThemeConfig.currentTheme.surfaceContainerLowestDark,
    surfaceContainerLow = ThemeConfig.currentTheme.surfaceContainerLowDark,
    surfaceContainer = ThemeConfig.currentTheme.surfaceContainerDark,
    surfaceContainerHigh = ThemeConfig.currentTheme.surfaceContainerHighDark,
    surfaceContainerHighest = ThemeConfig.currentTheme.surfaceContainerHighestDark,
)

@Composable
private fun createLightColorScheme() = lightColorScheme(
    primary = ThemeConfig.currentTheme.primaryLight,
    onPrimary = ThemeConfig.currentTheme.onPrimaryLight,
    primaryContainer = ThemeConfig.currentTheme.primaryContainerLight,
    onPrimaryContainer = ThemeConfig.currentTheme.onPrimaryContainerLight,
    secondary = ThemeConfig.currentTheme.secondaryLight,
    onSecondary = ThemeConfig.currentTheme.onSecondaryLight,
    secondaryContainer = ThemeConfig.currentTheme.secondaryContainerLight,
    onSecondaryContainer = ThemeConfig.currentTheme.onSecondaryContainerLight,
    tertiary = ThemeConfig.currentTheme.tertiaryLight,
    onTertiary = ThemeConfig.currentTheme.onTertiaryLight,
    tertiaryContainer = ThemeConfig.currentTheme.tertiaryContainerLight,
    onTertiaryContainer = ThemeConfig.currentTheme.onTertiaryContainerLight,
    error = ThemeConfig.currentTheme.errorLight,
    onError = ThemeConfig.currentTheme.onErrorLight,
    errorContainer = ThemeConfig.currentTheme.errorContainerLight,
    onErrorContainer = ThemeConfig.currentTheme.onErrorContainerLight,
    background = if (CardConfig.isCustomBackgroundEnabled) Color.Transparent else ThemeConfig.currentTheme.backgroundLight,
    onBackground = ThemeConfig.currentTheme.onBackgroundLight,
    surface = if (CardConfig.isCustomBackgroundEnabled) Color.Transparent else ThemeConfig.currentTheme.surfaceLight,
    onSurface = ThemeConfig.currentTheme.onSurfaceLight,
    surfaceVariant = ThemeConfig.currentTheme.surfaceVariantLight,
    onSurfaceVariant = ThemeConfig.currentTheme.onSurfaceVariantLight,
    outline = ThemeConfig.currentTheme.outlineLight,
    outlineVariant = ThemeConfig.currentTheme.outlineVariantLight,
    scrim = ThemeConfig.currentTheme.scrimLight,
    inverseSurface = ThemeConfig.currentTheme.inverseSurfaceLight,
    inverseOnSurface = ThemeConfig.currentTheme.inverseOnSurfaceLight,
    inversePrimary = ThemeConfig.currentTheme.inversePrimaryLight,
    surfaceDim = ThemeConfig.currentTheme.surfaceDimLight,
    surfaceBright = ThemeConfig.currentTheme.surfaceBrightLight,
    surfaceContainerLowest = ThemeConfig.currentTheme.surfaceContainerLowestLight,
    surfaceContainerLow = ThemeConfig.currentTheme.surfaceContainerLowLight,
    surfaceContainer = ThemeConfig.currentTheme.surfaceContainerLight,
    surfaceContainerHigh = ThemeConfig.currentTheme.surfaceContainerHighLight,
    surfaceContainerHighest = ThemeConfig.currentTheme.surfaceContainerHighestLight,
)

// 向后兼容
@OptIn(DelicateCoroutinesApi::class)
fun Context.saveAndApplyCustomBackground(
    uri: Uri,
    transformation: BackgroundTransformation? = null
) {
    GlobalScope.launch {
        BackgroundManager.saveAndApplyCustomBackground(
            this@saveAndApplyCustomBackground,
            uri,
            transformation
        )
    }
}

fun Context.saveCustomBackground(uri: Uri?) {
    if (uri != null) {
        saveAndApplyCustomBackground(uri)
    } else {
        BackgroundManager.clearCustomBackground(this)
    }
}

fun Context.saveThemeMode(forceDark: Boolean?) {
    ThemeManager.saveThemeMode(this, forceDark)
}


fun Context.saveThemeColors(themeName: String) {
    ThemeManager.saveThemeColors(this, themeName)
}


fun Context.saveDynamicColorState(enabled: Boolean) {
    ThemeManager.saveDynamicColorState(this, enabled)
}

@Composable
@ReadOnlyComposable
fun isInDarkTheme(themeMode: Boolean?): Boolean {
    return when (themeMode) {
        true -> true // 强制深色
        false -> false // 强制浅色
        null -> isSystemInDarkTheme() // 跟随系统
    }
}
