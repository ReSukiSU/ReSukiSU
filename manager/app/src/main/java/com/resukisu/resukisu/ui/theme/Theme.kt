package com.resukisu.resukisu.ui.theme

import android.R
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
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
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.zIndex
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.scale
import androidx.core.net.toUri
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.kieronquinn.monetcompat.core.MonetCompat
import com.kieronquinn.monetcompat.interfaces.MonetColorsChangedListener
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.quantize.QuantizerCelebi
import com.materialkolor.score.Score
import com.resukisu.resukisu.data.AppSettingsRepository
import com.resukisu.resukisu.data.theme.ThemeRepository
import com.resukisu.resukisu.ui.overscroll.StretchOverscrollCompensationState
import com.resukisu.resukisu.ui.util.LocalBackgroundBlurAnchor
import com.resukisu.resukisu.ui.util.LocalBlurState
import com.resukisu.resukisu.ui.util.LocalPagerPage
import com.resukisu.resukisu.ui.util.LocalPagerState
import com.resukisu.resukisu.ui.util.LocalStretchOverscrollCompensationState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import top.yukonga.miuix.kmp.blur.BackdropEffectScope
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.runtimeShaderEffect
import top.yukonga.miuix.kmp.blur.textureBlurEffect
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import dev.kdrag0n.monet.theme.ColorScheme as MonetCompatColorScheme

@Stable
class ThemeConfig(
    private val defaultSeedColor: () -> Int,
) {
    // 主题状态
    var customBackgroundUri by mutableStateOf<Uri?>(null)
    var backgroundDim by mutableFloatStateOf(0f)
    var forceDarkMode by mutableStateOf<Boolean?>(null)
    var seedColor by mutableIntStateOf(defaultSeedColor())
    var useDynamicColor by mutableStateOf(false)
    var useBuiltinMonoFont by mutableStateOf(false)
    var monetCompatSeedColor by mutableIntStateOf(defaultSeedColor())
    var dynamicColorSpec by mutableStateOf(ColorSpec.SpecVersion.SPEC_2021)
    var dynamicPaletteStyle by mutableStateOf(PaletteStyle.TonalSpot)

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

    fun reset() {
        customBackgroundUri = null
        forceDarkMode = null
        seedColor = defaultSeedColor()
        useDynamicColor = false
        monetCompatSeedColor = defaultSeedColor()
        dynamicColorSpec = ColorSpec.SpecVersion.SPEC_2021
        dynamicPaletteStyle = PaletteStyle.TonalSpot
        backgroundImageLoaded = false
        isThemeChanging = false
        preventBackgroundRefresh = false
        lastDarkModeState = null
    }
}

private val BuiltinspaceFontFamily = FontFamily(
    Font(com.resukisu.resukisu.R.font.jetbrains_mono)
)

@Composable
fun MonospaceFontFamily(): FontFamily {
    val themeConfig = koinInject<ThemeConfig>()
    return if (themeConfig.useBuiltinMonoFont) {
        BuiltinspaceFontFamily
    } else {
        FontFamily.Monospace
    }
}

@Stable
class BackgroundRenderState {
    var imagePainter: AsyncImagePainter? by mutableStateOf(null)
    var imageBitmap: ImageBitmap? by mutableStateOf(null)
    var blurImageBitmap: ImageBitmap? by mutableStateOf(null)
    var blurViewportSize by mutableStateOf(IntSize(0, 0))
    var blurFrameTick by mutableIntStateOf(0)
    var seedColor by mutableIntStateOf(0)
}

val LocalBackgroundRenderState = compositionLocalOf<BackgroundRenderState> {
    error("BackgroundRenderState is not provided")
}

class BackgroundManager(
    private val settings: AppSettingsRepository,
    private val config: ThemeConfig,
    private val cardConfig: CardConfig,
) {
    private val tag = "BackgroundManager"

    fun saveBackgroundDim(dim: Float) {
        config.backgroundDim = dim
        settings.putFloat("background_dim", dim)
    }

    fun saveEnableBlur(enable: Boolean) {
        config.isEnableBlur = enable
        settings.putBoolean("enable_blur", enable)
    }

    fun saveEnableBlurExp(enable: Boolean) {
        config.isEnableBlurExp = enable
        settings.putBoolean("enable_blur_exp", enable)
    }

    fun saveUseBackgroundSeedColor(enable: Boolean) {
        config.isUseBackgroundSeedColor = enable
        settings.putBoolean("use_background_seed_color", enable)
    }

    fun saveEnableHighContrastMode(enable: Boolean) {
        config.isHighContrastMode = enable
        settings.putBoolean("high_contrast_mode", enable)
    }

    suspend fun saveAndApplyCustomBackground(
        context: Context,
        uri: Uri
    ): Boolean {
        val appContext = context.applicationContext
        return try {
            val finalUri = withContext(Dispatchers.IO) {
                copyImageToInternalStorage(appContext, uri)
            } ?: return false

            saveBackgroundUri(finalUri)
            config.customBackgroundUri = finalUri
            cardConfig.updateBackground(true)
            withContext(Dispatchers.IO) {
                clearBackgroundBlurCache(appContext)
            }
            resetBackgroundState()

            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(tag, "保存背景失败: ${e.message}", e)
            false
        }
    }

    fun clearCustomBackground(context: Context) {
        saveBackgroundUri(null)
        config.customBackgroundUri = null
        cardConfig.updateBackground(false)
        clearBackgroundBlurCache(context)
        resetBackgroundState()
    }

    fun loadCustomBackground() {
        val prefs = settings
        val uriString = prefs.getString("custom_background", null)

        val newUri = uriString?.toUri()
        val preventRefresh = prefs.getBoolean("prevent_background_refresh", false)

        config.preventBackgroundRefresh = preventRefresh

        if (config.customBackgroundUri?.toString() != newUri?.toString()) {
            Log.d(tag, "加载自定义背景: $uriString")
            config.customBackgroundUri = newUri
            config.backgroundImageLoaded = false
            cardConfig.updateBackground(newUri != null)
        }

        config.backgroundDim = prefs.getFloat("background_dim", 0f).coerceIn(0f, 1f)
        config.isEnableBlur = prefs.getBoolean("enable_blur", false)
        config.isEnableBlurExp = prefs.getBoolean("enable_blur_exp", false)
        config.isUseBackgroundSeedColor = prefs.getBoolean("use_background_seed_color", false)
        config.isHighContrastMode = prefs.getBoolean("high_contrast_mode", false)
    }

    private fun saveBackgroundUri(uri: Uri?) {
        settings.putString("custom_background", uri?.toString())
        settings.putBoolean("prevent_background_refresh", false)
    }

    private fun resetBackgroundState() {
        config.backgroundImageLoaded = false
        config.preventBackgroundRefresh = false
        settings.putBoolean("prevent_background_refresh", false)
    }

    fun clearBackgroundBlurCache(context: Context) {
        runCatching {
            backgroundBlurCacheFile(context).delete()
            legacyBackgroundBlurCacheDir(context).deleteRecursively()
        }.onFailure {
            Log.w(tag, "Failed to clear background blur cache: ${it.message}")
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
            Log.e(tag, "复制图片失败: ${e.message}", e)
            null
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun KernelSUTheme(
    dpi: Int = 0,
    darkTheme: Boolean? = null,
    dynamicColor: Boolean? = null,
    content: @Composable () -> Unit
) {
    val themeConfig = koinInject<ThemeConfig>()
    val themeRepository = koinInject<ThemeRepository>()
    val backgroundManager = koinInject<BackgroundManager>()
    val cardConfig = koinInject<CardConfig>()
    val settings = koinInject<AppSettingsRepository>()
    val backgroundRenderState = remember { BackgroundRenderState() }
    val resolvedDarkTheme = darkTheme ?: isInDarkTheme(themeConfig.forceDarkMode)
    val resolvedDynamicColor = dynamicColor ?: themeConfig.useDynamicColor
    val context = LocalContext.current
    val systemIsDark = isSystemInDarkTheme()

    // 初始化主题
    ThemeInitializer(
        context = context,
        systemIsDark = systemIsDark,
        themeConfig = themeConfig,
        themeRepository = themeRepository,
        backgroundManager = backgroundManager,
        cardConfig = cardConfig,
    )

    // 创建颜色方案
    val colorScheme = createColorScheme(
        resolvedDarkTheme,
        resolvedDynamicColor,
        themeConfig,
        backgroundRenderState,
    )

    // 系统栏样式
    SystemBarController(resolvedDarkTheme)

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
        LocalDensity provides density,
        LocalBackgroundRenderState provides backgroundRenderState,
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            typography = generateTypography(themeConfig)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                BackgroundLayer(themeConfig, settings, backgroundRenderState)
                content()
            }
        }
    }
}

@Composable
private fun ThemeInitializer(
    context: Context,
    systemIsDark: Boolean,
    themeConfig: ThemeConfig,
    themeRepository: ThemeRepository,
    backgroundManager: BackgroundManager,
    cardConfig: CardConfig,
) {
    val themeChanged = themeConfig.detectThemeChange(systemIsDark)
    val scope = rememberCoroutineScope()

    // 处理系统主题变化
    LaunchedEffect(systemIsDark, themeChanged) {
        if (themeConfig.forceDarkMode == null && themeChanged) {
            Log.d("ThemeSystem", "系统主题变化: $systemIsDark")
            themeConfig.resetBackgroundState()

            if (!themeConfig.preventBackgroundRefresh) {
                backgroundManager.loadCustomBackground()
            }

            cardConfig.apply {
                load()
                setThemeDefaults(systemIsDark)
                save()
            }
        }
    }

    // 初始加载配置
    LaunchedEffect(Unit) {
        scope.launch {
            themeConfig.forceDarkMode = themeRepository.loadThemeMode()
            themeConfig.seedColor = themeRepository.loadSeedColor()
            themeConfig.useDynamicColor = themeRepository.loadDynamicColorState()
            themeConfig.dynamicColorSpec = themeRepository.loadDynamicColorSpec()
            themeConfig.dynamicPaletteStyle = themeRepository.loadDynamicPaletteStyle(
                themeConfig.dynamicColorSpec,
            )
            cardConfig.load()

            if (!themeConfig.backgroundImageLoaded && !themeConfig.preventBackgroundRefresh) {
                backgroundManager.loadCustomBackground()
            }
        }
    }

    MonetCompatInitializer(context, themeConfig)
}

@Composable
private fun MonetCompatInitializer(context: Context, themeConfig: ThemeConfig) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return

    val scope = rememberCoroutineScope()

    DisposableEffect(context) {
        val monet = MonetCompat.setup(context)
        monet.defaultPrimaryColor = themeConfig.seedColor
        monet.defaultSecondaryColor = themeConfig.seedColor
        monet.defaultAccentColor = themeConfig.seedColor

        val listener = object : MonetColorsChangedListener {
            override fun onMonetColorsChanged(
                monet: MonetCompat,
                monetColors: MonetCompatColorScheme,
                isInitialChange: Boolean
            ) {
                scope.launch {
                    themeConfig.monetCompatSeedColor =
                        monet.getSelectedWallpaperColor() ?: themeConfig.seedColor
                }
            }
        }

        monet.addMonetColorsChangedListener(listener, notifySelf = true)
        onDispose {
            monet.removeMonetColorsChangedListener(listener)
        }
    }

    LaunchedEffect(themeConfig.useDynamicColor, themeConfig.seedColor) {
        if (!themeConfig.useDynamicColor) return@LaunchedEffect

        val monet = MonetCompat.setup(context)
        monet.defaultPrimaryColor = themeConfig.seedColor
        monet.defaultSecondaryColor = themeConfig.seedColor
        monet.defaultAccentColor = themeConfig.seedColor
        monet.updateConfiguration(context)
        themeConfig.monetCompatSeedColor =
            monet.getSelectedWallpaperColor() ?: themeConfig.seedColor
        monet.updateMonetColors()
    }
}

@Composable
private fun BackgroundLayer(
    themeConfig: ThemeConfig,
    settings: AppSettingsRepository,
    renderState: BackgroundRenderState,
) {
    val backgroundUri = rememberSaveable { mutableStateOf(themeConfig.customBackgroundUri) }

    LaunchedEffect(themeConfig.customBackgroundUri) {
        backgroundUri.value = themeConfig.customBackgroundUri
        renderState.imageBitmap = null
        if (backgroundUri.value == null) {
            renderState.imagePainter = null
            renderState.blurImageBitmap = null
            renderState.seedColor = 0
            settings.remove("cached_seed_color")
        }
    }

    val hasBackgroundBitmap = renderState.imageBitmap != null
    val hasBlurBitmap = renderState.blurImageBitmap != null
    val needsFallbackFrames = hasBackgroundBitmap &&
            (!themeConfig.isEnableBlur || Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
    val needsBlurFrames =
        (themeConfig.isEnableBlurExp && hasBlurBitmap) ||
                (themeConfig.isEnableBlur && hasBackgroundBitmap)

    LaunchedEffect(needsFallbackFrames, needsBlurFrames) {
        if (!needsFallbackFrames && !needsBlurFrames) return@LaunchedEffect

        while (true) {
            withFrameNanos { }
            renderState.blurFrameTick = if (renderState.blurFrameTick == Int.MAX_VALUE) {
                0
            } else {
                renderState.blurFrameTick + 1
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
                    renderState.blurViewportSize != size
                ) {
                    renderState.blurViewportSize = size
                    renderState.blurImageBitmap = null
                }
            }
            .background(
                MaterialTheme.colorScheme.surfaceContainer
            )
    )

    // 自定义背景
    backgroundUri.value?.let { uri ->
        BackgroundInitializer(
            uri = uri,
            themeConfig = themeConfig,
            settings = settings,
            renderState = renderState,
        )
    }
}

private const val BACKGROUND_BLUR_RADIUS = 45f

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
 * Falls back to redrawing the app background below the content so translucent bars do not show
 * scrolling content through them when blur is disabled or unavailable.
 * @param compensateHorizontalOverscroll reverse horizontal stretch in the sampled background
 * @param compensateVerticalOverscroll reverse vertical stretch in the sampled background
 * @param useFixedSurfaceBoundsForOverscroll use this surface's bounds with the published stretch
 * amount; fixed navigation surfaces use this to stay visually stationary outside the scroller
 * @return modified modifier
 */
@Composable
fun Modifier.blurEffect(
    compensateHorizontalOverscroll: Boolean = true,
    compensateVerticalOverscroll: Boolean = false,
    useFixedSurfaceBoundsForOverscroll: Boolean = false,
): Modifier {
    val themeConfig = koinInject<ThemeConfig>()
    val cardConfig = koinInject<CardConfig>()
    if (!themeConfig.isEnableBlur || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return renderBackgroundFallback(
            compensateHorizontalOverscroll = compensateHorizontalOverscroll,
            compensateVerticalOverscroll = compensateVerticalOverscroll,
            useFixedSurfaceBoundsForOverscroll = useFixedSurfaceBoundsForOverscroll,
        )
    }

    val renderState = LocalBackgroundRenderState.current
    val backgroundAnchor = LocalBackgroundBlurAnchor.current
    val stretchOverscrollState = LocalStretchOverscrollCompensationState.current
    var coordinates by remember {
        mutableStateOf<LayoutCoordinates?>(null)
    }

    return LocalBlurState.current?.let { backdrop ->
        val blendColor =
            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = cardConfig.cardAlpha)

        this.then(
            Modifier
                .onGloballyPositioned { newCoordinates ->
                    coordinates = newCoordinates.takeIf { it.isAttached }
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RectangleShape },
                    effects = {
                        renderState.blurFrameTick

                        textureBlurEffect(
                            blurRadiusX = 25f,
                            colors = BlurColors(
                                blendColors = listOf(
                                    BlendColorEntry(color = blendColor)
                                )
                            ),
                        )

                        val boundsInBackground =
                            coordinates?.boundsInBackgroundNow(backgroundAnchor)
                        val stretchCompensation = stretchOverscrollState
                            ?.resolveBitmapCompensation(
                                backgroundCoordinates = backgroundAnchor,
                                compensateHorizontal = compensateHorizontalOverscroll,
                                compensateVertical = compensateVerticalOverscroll,
                                compensationCoordinates = coordinates
                                    .takeIf { useFixedSurfaceBoundsForOverscroll },
                            )

                        if (boundsInBackground != null && stretchCompensation != null) {
                            stretchOverscrollCompensationEffect(
                                boundsInBackground = boundsInBackground,
                                compensation = stretchCompensation,
                            )
                        }
                    },
                )
        )
    } ?: renderBackgroundFallback(
        compensateHorizontalOverscroll = compensateHorizontalOverscroll,
        compensateVerticalOverscroll = compensateVerticalOverscroll,
        useFixedSurfaceBoundsForOverscroll = useFixedSurfaceBoundsForOverscroll,
    )
}

private fun Modifier.renderBackgroundFallback(
    compensateHorizontalOverscroll: Boolean,
    compensateVerticalOverscroll: Boolean,
    useFixedSurfaceBoundsForOverscroll: Boolean,
): Modifier = composed {
    val themeConfig = koinInject<ThemeConfig>()
    val renderState = LocalBackgroundRenderState.current
    var coordinates by remember {
        mutableStateOf<LayoutCoordinates?>(null)
    }

    val backgroundColor = MaterialTheme.colorScheme.surfaceContainer
    val dimColor = backgroundColor.copy(alpha = themeConfig.backgroundDim)
    val backgroundBitmap = renderState.imageBitmap
    val backgroundAnchor = LocalBackgroundBlurAnchor.current
    val pagerPage = LocalPagerPage.current
    val pagerState = if (pagerPage != null) LocalPagerState.current else null
    val layoutDirection = LocalLayoutDirection.current
    val stretchOverscrollState = LocalStretchOverscrollCompensationState.current
    val stretchRenderer = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            StretchBackgroundRenderer()
        } else {
            null
        }
    }

    this
        .onGloballyPositioned { newCoordinates ->
            coordinates = newCoordinates.takeIf { it.isAttached }
        }
        .drawWithContent {
            renderState.blurFrameTick

            val boundsInBackground = coordinates?.boundsInBackgroundNow(backgroundAnchor)
            val viewportSize = backgroundAnchor
                ?.takeIf { it.isAttached && it.size.width > 0 && it.size.height > 0 }
                ?.size
                ?: renderState.blurViewportSize
            val currentPagerState = pagerState?.takeIf { state ->
                pagerPage != null && pagerPage in 0 until state.pageCount
            }
            val hasPagerPage = currentPagerState != null
            val pagerViewportSize = currentPagerState?.layoutInfo?.viewportSize?.takeIf {
                it.width > 0 && it.height > 0
            }
            val pagerViewportWidth = pagerViewportSize?.width ?: viewportSize.width
            val pageOffset = if (currentPagerState != null && pagerPage != null) {
                currentPagerState.getOffsetDistanceInPages(pagerPage)
            } else {
                0f
            }
            val physicalPageOffset = pageOffset * pagerViewportWidth *
                    if (layoutDirection == LayoutDirection.Ltr) 1f else -1f
            val offsetBoundsInBackground = boundsInBackground?.let { bounds ->
                if (hasPagerPage) {
                    val leadingNavigationWidth =
                        (viewportSize.width - pagerViewportWidth).coerceAtLeast(0)
                    val pagerViewportLeft = if (layoutDirection == LayoutDirection.Ltr) {
                        leadingNavigationWidth.toFloat()
                    } else {
                        0f
                    }

                    // Equivalent to translating the backdrop painter by -physicalPageOffset:
                    // crop from the page offset and account for a leading landscape navigation rail
                    // so the background remains fixed while the page moves.
                    Rect(
                        left = pagerViewportLeft + physicalPageOffset,
                        top = bounds.top,
                        right = pagerViewportLeft + physicalPageOffset + bounds.width,
                        bottom = bounds.bottom,
                    )
                } else {
                    bounds
                }
            }
            val bitmapBounds = if (
                themeConfig.backgroundImageLoaded &&
                backgroundBitmap != null &&
                offsetBoundsInBackground != null
            ) {
                offsetBoundsInBackground.mapToBitmapBounds(
                    bitmap = backgroundBitmap,
                    viewportSize = viewportSize,
                )
            } else {
                null
            }
            // Fixed navigation surfaces are outside the pager's stretch RenderNode. Their fallback
            // draws the original, unstretched bitmap, so applying the published inverse mapping
            // here would introduce movement instead of cancelling it.
            val stretchCompensation = if (useFixedSurfaceBoundsForOverscroll) {
                null
            } else {
                stretchOverscrollState
                    ?.resolveBitmapCompensation(
                        backgroundCoordinates = backgroundAnchor,
                        compensateHorizontal = compensateHorizontalOverscroll,
                        compensateVertical = compensateVerticalOverscroll,
                    )
                    ?.mapToBitmapCoordinates(
                        bitmap = backgroundBitmap,
                        viewportSize = viewportSize,
                    )
            }

            if (backgroundBitmap != null && bitmapBounds != null) {
                if (stretchCompensation != null) {
                    // in fact no need check sdk int. but make lint happy
                    if (stretchRenderer != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        stretchRenderer.draw(
                            scope = this,
                            bitmap = backgroundBitmap,
                            boundsInBackground = bitmapBounds,
                            compensation = stretchCompensation,
                        )
                    } else {
                        drawStretchCompensatedBitmap(
                            bitmap = backgroundBitmap,
                            boundsInBackground = bitmapBounds,
                            compensation = stretchCompensation,
                        )
                    }
                } else {
                    drawBitmapIntersection(
                        bitmap = backgroundBitmap,
                        boundsInBackground = bitmapBounds,
                    )
                }
                drawRect(color = dimColor)
            } else {
                drawRect(color = backgroundColor)
            }

            drawContent()
        }
}

private fun Rect.mapToBitmapBounds(
    bitmap: ImageBitmap,
    viewportSize: IntSize,
): Rect? {
    if (
        bitmap.width <= 0 ||
        bitmap.height <= 0 ||
        viewportSize.width <= 0 ||
        viewportSize.height <= 0
    ) {
        return null
    }

    val scale = maxOf(
        viewportSize.width / bitmap.width.toFloat(),
        viewportSize.height / bitmap.height.toFloat(),
    )
    val renderedWidth = bitmap.width * scale
    val renderedHeight = bitmap.height * scale
    val renderedLeft = (viewportSize.width - renderedWidth) / 2f
    val renderedTop = (viewportSize.height - renderedHeight) / 2f

    return Rect(
        left = (left - renderedLeft) / scale,
        top = (top - renderedTop) / scale,
        right = (right - renderedLeft) / scale,
        bottom = (bottom - renderedTop) / scale,
    )
}


fun Modifier.renderBackgroundBlur(
    tintColor: Color? = null
): Modifier = composed {
    val themeConfig = koinInject<ThemeConfig>()
    val cardConfig = koinInject<CardConfig>()
    val renderState = LocalBackgroundRenderState.current
    if (!themeConfig.isEnableBlurExp) return@composed this

    var coordinates by remember {
        mutableStateOf<LayoutCoordinates?>(null)
    }

    val tintColor = (tintColor ?: MaterialTheme.colorScheme.surfaceBright).copy(
        alpha = cardConfig.cardAlpha
    )
    val backgroundBlurAnchor = LocalBackgroundBlurAnchor.current
    val stretchOverscrollState = LocalStretchOverscrollCompensationState.current
    val stretchRenderer = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            StretchBackgroundRenderer()
        } else {
            null
        }
    }

    this
        .onGloballyPositioned { newCoordinates ->
            coordinates = newCoordinates.takeIf { it.isAttached }
        }
        .drawWithContent {
            renderState.blurFrameTick

            val currentBitmap = renderState.blurImageBitmap
            val currentBoundsInBackground = coordinates?.boundsInBackgroundNow(backgroundBlurAnchor)
            val stretchCompensation = stretchOverscrollState
                ?.resolveBitmapCompensation(backgroundBlurAnchor)

            if (
                currentBitmap != null &&
                currentBoundsInBackground != null &&
                currentBoundsInBackground.width > 0f &&
                currentBoundsInBackground.height > 0f
            ) {
                if (stretchCompensation != null) {
                    // in fact no need check sdk int. but make lint happy
                    if (stretchRenderer != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        stretchRenderer.draw(
                            scope = this,
                            bitmap = currentBitmap,
                            boundsInBackground = currentBoundsInBackground,
                            compensation = stretchCompensation,
                        )
                    } else {
                        drawStretchCompensatedBitmap(
                            bitmap = currentBitmap,
                            boundsInBackground = currentBoundsInBackground,
                            compensation = stretchCompensation,
                        )
                    }
                } else {
                    drawBitmapIntersection(
                        bitmap = currentBitmap,
                        boundsInBackground = currentBoundsInBackground,
                    )
                }

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

private data class StretchBitmapCompensation(
    val horizontalAmount: Float,
    val horizontalOrigin: Float,
    val horizontalSize: Float,
    val verticalAmount: Float,
    val verticalOrigin: Float,
    val verticalSize: Float,
)

private fun StretchBitmapCompensation.mapToBitmapCoordinates(
    bitmap: ImageBitmap?,
    viewportSize: IntSize,
): StretchBitmapCompensation? {
    if (
        bitmap == null ||
        bitmap.width <= 0 ||
        bitmap.height <= 0 ||
        viewportSize.width <= 0 ||
        viewportSize.height <= 0
    ) {
        return null
    }

    val scale = maxOf(
        viewportSize.width / bitmap.width.toFloat(),
        viewportSize.height / bitmap.height.toFloat(),
    )
    val renderedLeft = (viewportSize.width - bitmap.width * scale) / 2f
    val renderedTop = (viewportSize.height - bitmap.height * scale) / 2f

    return StretchBitmapCompensation(
        horizontalAmount = horizontalAmount,
        horizontalOrigin = (horizontalOrigin - renderedLeft) / scale,
        horizontalSize = horizontalSize / scale,
        verticalAmount = verticalAmount,
        verticalOrigin = (verticalOrigin - renderedTop) / scale,
        verticalSize = verticalSize / scale,
    )
}

private fun StretchOverscrollCompensationState.resolveBitmapCompensation(
    backgroundCoordinates: LayoutCoordinates?,
    compensateHorizontal: Boolean = true,
    compensateVertical: Boolean = true,
    compensationCoordinates: LayoutCoordinates? = null,
): StretchBitmapCompensation? {
    val compensationBounds = compensationCoordinates
        ?.boundsInBackgroundNow(backgroundCoordinates)
    val horizontalAxis = horizontal
        ?.takeIf { compensateHorizontal && it.amount != 0f }
    val verticalAxis = vertical
        ?.takeIf { compensateVertical && it.amount != 0f }
    val horizontalBounds = horizontalAxis?.let { axis ->
        compensationBounds
            ?.takeIf { it.width > 0f }
            ?: axis.coordinates
                .boundsInBackgroundNow(backgroundCoordinates)
                ?.takeIf { it.width > 0f }
    }
    val verticalBounds = verticalAxis?.let { axis ->
        compensationBounds
            ?.takeIf { it.height > 0f }
            ?: axis.coordinates
                .boundsInBackgroundNow(backgroundCoordinates)
                ?.takeIf { it.height > 0f }
    }

    if (horizontalBounds == null && verticalBounds == null) return null

    return StretchBitmapCompensation(
        horizontalAmount = if (horizontalBounds != null) horizontalAxis.amount else 0f,
        horizontalOrigin = horizontalBounds?.left ?: 0f,
        horizontalSize = horizontalBounds?.width ?: 1f,
        verticalAmount = if (verticalBounds != null) verticalAxis.amount else 0f,
        verticalOrigin = verticalBounds?.top ?: 0f,
        verticalSize = verticalBounds?.height ?: 1f,
    )
}

private fun BackdropEffectScope.stretchOverscrollCompensationEffect(
    boundsInBackground: Rect,
    compensation: StretchBitmapCompensation,
) {
    val inputScale = downscaleFactor.coerceAtLeast(1).toFloat()
    val inputPadding = padding

    runtimeShaderEffect(
        key = "StretchOverscrollCompensation",
        shaderString = STRETCH_BACKGROUND_SHADER,
        uniformShaderName = "background",
    ) {
        setFloatUniform(
            "cardOrigin",
            boundsInBackground.left - inputPadding,
            boundsInBackground.top - inputPadding,
        )
        setFloatUniform("cardScale", inputScale, inputScale)
        setFloatUniform(
            "inputOrigin",
            (-boundsInBackground.left + inputPadding) / inputScale,
            (-boundsInBackground.top + inputPadding) / inputScale,
        )
        setFloatUniform("inputScale", 1f / inputScale, 1f / inputScale)
        setFloatUniform(
            "stretchAmount",
            compensation.horizontalAmount,
            compensation.verticalAmount,
        )
        setFloatUniform(
            "stretchOrigin",
            compensation.horizontalOrigin,
            compensation.verticalOrigin,
        )
        setFloatUniform(
            "stretchSize",
            compensation.horizontalSize,
            compensation.verticalSize,
        )
    }
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

private const val STRETCH_BACKGROUND_SHADER = """
    uniform shader background;
    uniform float2 cardOrigin;
    uniform float2 cardScale;
    uniform float2 inputOrigin;
    uniform float2 inputScale;
    uniform float2 stretchAmount;
    uniform float2 stretchOrigin;
    uniform float2 stretchSize;

    float reverseMapStart(float overscroll, float sourcePosition) {
        float numerator =
            (-sourcePosition * overscroll * overscroll) -
            (2.0 * sourcePosition * overscroll) -
            sourcePosition;
        float denominator =
            1.0 +
            (0.3 * overscroll) +
            (0.7 * sourcePosition * overscroll * overscroll) +
            (0.7 * sourcePosition * overscroll);
        return -(numerator / denominator);
    }

    float reverseMapEnd(float overscroll, float sourcePosition) {
        float numerator =
            (0.3 * overscroll * overscroll) -
            (0.3 * sourcePosition * overscroll * overscroll) +
            (1.3 * sourcePosition * overscroll) -
            overscroll -
            sourcePosition;
        float denominator =
            (0.7 * sourcePosition * overscroll * overscroll) -
            (0.7 * sourcePosition * overscroll) -
            (0.7 * overscroll * overscroll) +
            overscroll -
            1.0;
        return numerator / denominator;
    }

    float reverseStretch(float overscroll, float sourcePosition) {
        float distanceStretched = 1.0 / (1.0 + abs(overscroll));
        float distanceDiff = distanceStretched - 1.0;

        if (overscroll > 0.0) {
            float mappedPosition = reverseMapStart(overscroll, sourcePosition);
            if (mappedPosition <= 1.0) {
                return mappedPosition;
            } else if (mappedPosition >= distanceStretched) {
                return mappedPosition - distanceDiff;
            }
        }

        if (overscroll < 0.0) {
            float mappedPosition = reverseMapEnd(overscroll, sourcePosition);
            if (mappedPosition >= 0.0) {
                return mappedPosition;
            } else {
                return mappedPosition + distanceDiff;
            }
        }

        return sourcePosition;
    }

    float compensateAxis(float position, float amount, float origin, float extent) {
        if (amount == 0.0 || extent <= 0.0) {
            return position;
        }
        float normalized = (position - origin) / extent;
        return origin + reverseStretch(amount, normalized) * extent;
    }

    half4 main(float2 coord) {
        float2 position = cardOrigin + coord * cardScale;
        float2 samplePosition = float2(
            compensateAxis(position.x, stretchAmount.x, stretchOrigin.x, stretchSize.x),
            compensateAxis(position.y, stretchAmount.y, stretchOrigin.y, stretchSize.y)
        );
        return background.eval(inputOrigin + samplePosition * inputScale);
    }
"""

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class StretchBackgroundRenderer {
    private val runtimeShader = RuntimeShader(STRETCH_BACKGROUND_SHADER)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        shader = runtimeShader
    }
    private var inputBitmap: Bitmap? = null

    fun draw(
        scope: ContentDrawScope,
        bitmap: ImageBitmap,
        boundsInBackground: Rect,
        compensation: StretchBitmapCompensation,
    ) {
        val androidBitmap = bitmap.asAndroidBitmap()
        if (inputBitmap !== androidBitmap) {
            inputBitmap = androidBitmap
            runtimeShader.setInputShader(
                "background",
                BitmapShader(androidBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP),
            )
        }

        with(scope) {
            runtimeShader.setFloatUniform(
                "cardOrigin",
                boundsInBackground.left,
                boundsInBackground.top,
            )
            runtimeShader.setFloatUniform(
                "cardScale",
                boundsInBackground.width / size.width,
                boundsInBackground.height / size.height,
            )
            runtimeShader.setFloatUniform("inputOrigin", 0f, 0f)
            runtimeShader.setFloatUniform("inputScale", 1f, 1f)
            runtimeShader.setFloatUniform(
                "stretchAmount",
                compensation.horizontalAmount,
                compensation.verticalAmount,
            )
            runtimeShader.setFloatUniform(
                "stretchOrigin",
                compensation.horizontalOrigin,
                compensation.verticalOrigin,
            )
            runtimeShader.setFloatUniform(
                "stretchSize",
                compensation.horizontalSize,
                compensation.verticalSize,
            )
            drawContext.canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
        }
    }
}

private val stretchFallbackPaint =
    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

private data class BitmapSourceInterval(
    val start: Int,
    val end: Int,
)

private fun bitmapSourceInterval(
    first: Float,
    second: Float,
    bitmapSize: Int,
): BitmapSourceInterval? {
    if (bitmapSize <= 0) return null

    val lower = minOf(first, second)
    val upper = maxOf(first, second)
    if (upper <= 0f) return BitmapSourceInterval(0, 1)
    if (lower >= bitmapSize) return BitmapSourceInterval(bitmapSize - 1, bitmapSize)

    val start = floor(lower.coerceAtLeast(0f)).toInt().coerceIn(0, bitmapSize - 1)
    val end = ceil(upper.coerceAtMost(bitmapSize.toFloat()))
        .toInt()
        .coerceIn(start + 1, bitmapSize)
    return BitmapSourceInterval(start, end)
}

private fun reverseStretchPosition(
    amount: Float,
    normalizedInput: Float,
): Float {
    if (amount == 0f) return normalizedInput

    val distanceStretched = 1f / (1f + abs(amount))
    val distanceDifference = distanceStretched - 1f

    if (amount > 0f) {
        val numerator =
            (-normalizedInput * amount * amount) -
                    (2f * normalizedInput * amount) -
                    normalizedInput
        val denominator =
            1f +
                    (0.3f * amount) +
                    (0.7f * normalizedInput * amount * amount) +
                    (0.7f * normalizedInput * amount)
        val output = -(numerator / denominator)
        return if (output <= 1f) output else output - distanceDifference
    }

    val numerator =
        (0.3f * amount * amount) -
                (0.3f * normalizedInput * amount * amount) +
                (1.3f * normalizedInput * amount) -
                amount -
                normalizedInput
    val denominator =
        (0.7f * normalizedInput * amount * amount) -
                (0.7f * normalizedInput * amount) -
                (0.7f * amount * amount) +
                amount -
                1f
    val output = numerator / denominator
    return if (output >= 0f) output else output + distanceDifference
}

private fun compensateBackgroundPosition(
    position: Float,
    amount: Float,
    origin: Float,
    extent: Float,
): Float {
    if (amount == 0f || extent <= 0f) return position
    val normalized = (position - origin) / extent
    return origin + reverseStretchPosition(amount, normalized) * extent
}

private fun ContentDrawScope.drawStretchCompensatedBitmap(
    bitmap: ImageBitmap,
    boundsInBackground: Rect,
    compensation: StretchBitmapCompensation,
) {
    if (size.width <= 0f || size.height <= 0f) return

    // Android 12/12L have no RuntimeShader API. A fine mesh is visually continuous for the
    // already-blurred bitmap while preserving the same inverse mapping.
    val xCellCount = if (compensation.horizontalAmount == 0f) {
        1
    } else {
        ceil(size.width / 16f).toInt().coerceAtLeast(1)
    }
    val yCellCount = if (compensation.verticalAmount == 0f) {
        1
    } else {
        ceil(size.height / 16f).toInt().coerceAtLeast(1)
    }
    val androidBitmap = bitmap.asAndroidBitmap()
    val canvas = drawContext.canvas.nativeCanvas

    for (xIndex in 0 until xCellCount) {
        val destinationLeft = size.width * xIndex / xCellCount
        val destinationRight = size.width * (xIndex + 1) / xCellCount
        val backgroundLeft =
            boundsInBackground.left + boundsInBackground.width * destinationLeft / size.width
        val backgroundRight =
            boundsInBackground.left + boundsInBackground.width * destinationRight / size.width
        val sourceX = bitmapSourceInterval(
            first = compensateBackgroundPosition(
                position = backgroundLeft,
                amount = compensation.horizontalAmount,
                origin = compensation.horizontalOrigin,
                extent = compensation.horizontalSize,
            ),
            second = compensateBackgroundPosition(
                position = backgroundRight,
                amount = compensation.horizontalAmount,
                origin = compensation.horizontalOrigin,
                extent = compensation.horizontalSize,
            ),
            bitmapSize = bitmap.width,
        ) ?: continue

        for (yIndex in 0 until yCellCount) {
            val destinationTop = size.height * yIndex / yCellCount
            val destinationBottom = size.height * (yIndex + 1) / yCellCount
            val backgroundTop =
                boundsInBackground.top + boundsInBackground.height * destinationTop / size.height
            val backgroundBottom =
                boundsInBackground.top + boundsInBackground.height * destinationBottom / size.height
            val sourceY = bitmapSourceInterval(
                first = compensateBackgroundPosition(
                    position = backgroundTop,
                    amount = compensation.verticalAmount,
                    origin = compensation.verticalOrigin,
                    extent = compensation.verticalSize,
                ),
                second = compensateBackgroundPosition(
                    position = backgroundBottom,
                    amount = compensation.verticalAmount,
                    origin = compensation.verticalOrigin,
                    extent = compensation.verticalSize,
                ),
                bitmapSize = bitmap.height,
            ) ?: continue

            canvas.drawBitmap(
                androidBitmap,
                android.graphics.Rect(
                    sourceX.start,
                    sourceY.start,
                    sourceX.end,
                    sourceY.end,
                ),
                RectF(
                    destinationLeft,
                    destinationTop,
                    destinationRight,
                    destinationBottom,
                ),
                stretchFallbackPaint,
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
    viewportSize: IntSize,
    blurRadius: Float,
): ImageBitmap = withContext(Dispatchers.Default) {
    val cacheFile = backgroundBlurCacheFile(context)

    BitmapFactory.decodeFile(cacheFile.absolutePath)?.let { cachedBitmap ->
        if (
            cachedBitmap.width == viewportSize.width &&
            cachedBitmap.height == viewportSize.height
        ) {
            return@withContext cachedBitmap.asImageBitmap()
        }

        cachedBitmap.recycle()
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

private fun backgroundBlurCacheFile(context: Context): File =
    File(context.filesDir, "blured_custom_background.jpg")

private fun legacyBackgroundBlurCacheDir(context: Context): File =
    File(context.filesDir, "background_blur_cache")

private fun saveBackgroundBlurCache(cacheFile: File, bitmap: Bitmap) {
    runCatching {
        cacheFile.parentFile?.mkdirs()
        val tempFile = File(cacheFile.parentFile, "${cacheFile.name}.tmp")
        FileOutputStream(tempFile).use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)
        }
        if (!tempFile.renameTo(cacheFile)) {
            tempFile.copyTo(cacheFile, overwrite = true)
            tempFile.delete()
        }
    }.onFailure {
        Log.w("ThemeSystem", "Failed to save background blur cache: ${it.message}")
    }
}

@Composable
private fun BackgroundInitializer(
    uri: Uri,
    themeConfig: ThemeConfig,
    settings: AppSettingsRepository,
    renderState: BackgroundRenderState,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val dynamicColorFromSystem =
        if (Build.VERSION.SDK_INT >= 31)
            colorResource(id = R.color.system_accent1_500).toArgb()
        else -12417548

    val calcedCachedSeedColor =
        settings.getInt("cached_seed_color", dynamicColorFromSystem)

    LaunchedEffect(themeConfig.isEnableBlurExp, renderState.blurViewportSize) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            themeConfig.isEnableBlurExp &&
            renderState.blurViewportSize.width > 0 &&
            renderState.blurViewportSize.height > 0
        ) {
            renderState.imagePainter?.let {
                if (it.state !is AsyncImagePainter.State.Success) return@let

                val bitmap = (it.state as AsyncImagePainter.State.Success).result.drawable.toBitmap()
                renderState.blurImageBitmap = bitmap.createBackgroundBlurImage(
                    context = context,
                    viewportSize = renderState.blurViewportSize,
                    blurRadius = BACKGROUND_BLUR_RADIUS,
                )
            }
        } else {
            renderState.blurImageBitmap = null
        }
    }

    renderState.imagePainter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context)
            .data(uri)
            .allowHardware(false)
            .crossfade(true)
            .build(),
        onError = { error ->
            Log.e("ThemeSystem", "背景加载失败: ${error.result.throwable.message}")
            renderState.imageBitmap = null
            themeConfig.customBackgroundUri = null
        },
        onSuccess = {
            val bitmap = it.result.drawable.toBitmap()
            renderState.imageBitmap = bitmap.asImageBitmap()
            Log.d("ThemeSystem", "背景加载成功")
            themeConfig.backgroundImageLoaded = true
            themeConfig.isThemeChanging = false

            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                themeConfig.isEnableBlurExp &&
                renderState.blurViewportSize.width > 0 &&
                renderState.blurViewportSize.height > 0
            ) {
                coroutineScope.launch {
                    renderState.blurImageBitmap = bitmap.createBackgroundBlurImage(
                        context = context,
                        viewportSize = renderState.blurViewportSize,
                        blurRadius = BACKGROUND_BLUR_RADIUS,
                    )
                }
            } else {
                renderState.blurImageBitmap = null
            }

            renderState.seedColor = calcedCachedSeedColor
            coroutineScope.launch {
                renderState.seedColor = bitmap.extractSeedColor(
                    fallbackColorArgb = calcedCachedSeedColor
                )

                settings.putInt("cached_seed_color", renderState.seedColor)
            }
        }
    )
}

@Composable
private fun generateTypography(themeConfig: ThemeConfig): androidx.compose.material3.Typography {
    val darkMode = isInDarkTheme(themeConfig.forceDarkMode)

    fun generateShadow(originalShadow: Shadow?): Shadow? {
        if (!themeConfig.isHighContrastMode) return originalShadow
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

@Composable
private fun createColorScheme(
    darkTheme: Boolean,
    dynamicColor: Boolean,
    themeConfig: ThemeConfig,
    renderState: BackgroundRenderState,
): ColorScheme {
    val seedColor =
        when {
            dynamicColor && themeConfig.isUseBackgroundSeedColor && renderState.seedColor != 0 -> {
                renderState.seedColor
            }

            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                colorResource(id = R.color.system_accent1_500).toArgb()
            }

            dynamicColor -> {
                themeConfig.monetCompatSeedColor
            }

            else -> {
                themeConfig.seedColor
            }
        }

    return dynamicColorScheme(
        seedColor = Color(seedColor),
        isDark = darkTheme,
        style = themeConfig.dynamicPaletteStyle,
        specVersion = themeConfig.dynamicColorSpec,
    )
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
@ReadOnlyComposable
fun isInDarkTheme(themeMode: Boolean?): Boolean {
    return when (themeMode) {
        true -> true // 强制深色
        false -> false // 强制浅色
        null -> isSystemInDarkTheme() // 跟随系统
    }
}
