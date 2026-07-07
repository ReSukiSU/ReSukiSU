package com.resukisu.resukisu.ui.screen.themeSettings.crop

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.PopupPositionProvider
import com.resukisu.resukisu.R
import com.resukisu.resukisu.ui.component.KeyPointSlider
import com.resukisu.resukisu.ui.theme.KernelSUTheme
import com.yalantis.ucrop.UCrop
import com.yalantis.ucrop.callback.BitmapCropCallback
import com.yalantis.ucrop.view.OverlayView
import com.yalantis.ucrop.view.TransformImageView
import com.yalantis.ucrop.view.UCropView
import java.io.Serializable
import kotlin.math.max
import kotlin.math.min

class BackgroundCropActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val inputUri = intent.getParcelableExtraCompat<Uri>(UCrop.EXTRA_INPUT_URI)
        val outputUri = intent.getParcelableExtraCompat<Uri>(UCrop.EXTRA_OUTPUT_URI)
        val aspectRatioX = intent.getFloatExtra(UCrop.EXTRA_ASPECT_RATIO_X, 0f)
        val aspectRatioY = intent.getFloatExtra(UCrop.EXTRA_ASPECT_RATIO_Y, 0f)
        val maxSizeX = intent.getIntExtra(UCrop.EXTRA_MAX_SIZE_X, 0)
        val maxSizeY = intent.getIntExtra(UCrop.EXTRA_MAX_SIZE_Y, 0)

        if (inputUri == null || outputUri == null) {
            finishWithError(IllegalArgumentException("Crop input or output Uri is missing."))
            return
        }

        val sourceImageAspectRatio = resolveImageAspectRatio(inputUri)

        setContent {
            KernelSUTheme {
                BackgroundCropScreen(
                    inputUri = inputUri,
                    outputUri = outputUri,
                    aspectRatioX = aspectRatioX,
                    aspectRatioY = aspectRatioY,
                    sourceImageAspectRatio = sourceImageAspectRatio,
                    maxSizeX = maxSizeX,
                    maxSizeY = maxSizeY,
                    onCancel = {
                        setResult(RESULT_CANCELED)
                        finish()
                    },
                    onCropped = { uri, width, height, offsetX, offsetY ->
                        finishWithResult(uri, width, height, offsetX, offsetY)
                    },
                    onError = ::finishWithError
                )
            }
        }
    }

    private fun finishWithResult(
        uri: Uri,
        width: Int,
        height: Int,
        offsetX: Int,
        offsetY: Int
    ) {
        val result = Intent()
            .putExtra(UCrop.EXTRA_OUTPUT_URI, uri)
            .putExtra(UCrop.EXTRA_OUTPUT_IMAGE_WIDTH, width)
            .putExtra(UCrop.EXTRA_OUTPUT_IMAGE_HEIGHT, height)
            .putExtra(UCrop.EXTRA_OUTPUT_OFFSET_X, offsetX)
            .putExtra(UCrop.EXTRA_OUTPUT_OFFSET_Y, offsetY)
        setResult(RESULT_OK, result)
        finish()
    }

    private fun finishWithError(error: Throwable) {
        val result = Intent().putExtra(UCrop.EXTRA_ERROR, error as Serializable)
        setResult(UCrop.RESULT_ERROR, result)
        finish()
    }

    private fun resolveImageAspectRatio(uri: Uri): Float? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        return runCatching {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, options)
            }
            val width = options.outWidth
            val height = options.outHeight
            if (width > 0 && height > 0) {
                width.toFloat() / height.toFloat()
            } else {
                null
            }
        }.getOrNull()
    }
}

@SuppressLint("AutoboxingStateCreation")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BackgroundCropScreen(
    inputUri: Uri,
    outputUri: Uri,
    aspectRatioX: Float,
    aspectRatioY: Float,
    sourceImageAspectRatio: Float?,
    maxSizeX: Int,
    maxSizeY: Int,
    onCancel: () -> Unit,
    onCropped: (Uri, Int, Int, Int, Int) -> Unit,
    onError: (Throwable) -> Unit
) {
    val context = LocalContext.current
    var cropView by remember { mutableStateOf<UCropView?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isCropping by remember { mutableStateOf(false) }
    var loadFailed by remember { mutableStateOf(false) }
    var rotationAngle by remember { mutableFloatStateOf(0f) }
    var cropViewReloadToken by remember { mutableIntStateOf(0) }
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)

    val primaryColor = MaterialTheme.colorScheme.primary.toArgb()
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.58f).toArgb()

    fun syncCropControls() {
        cropView?.cropImageView?.let { imageView ->
            rotationAngle = imageView.currentAngle.normalizedCropAngle()
        }
    }

    fun updateRotation(targetAngle: Float) {
        cropView?.cropImageView?.let { imageView ->
            val normalizedTarget = targetAngle.normalizedCropAngle()
            val delta = (normalizedTarget - imageView.currentAngle.normalizedCropAngle())
                .normalizedCropAngle()
            imageView.cancelAllAnimations()
            imageView.postRotate(delta)
            rotationAngle = normalizedTarget
        }
    }

    fun finishRotationAdjustment() {
        cropView?.cropImageView?.setImageToWrapCropBounds()
    }

    SideEffect {
        topAppBarState.heightOffset = topAppBarState.heightOffsetLimit
        topAppBarState.contentOffset = 0f
    }

    fun cropCurrentImage() {
        val imageView = cropView?.cropImageView ?: return
        isCropping = true
        imageView.setImageToWrapCropBounds()
        imageView.cropAndSaveImage(
            Bitmap.CompressFormat.JPEG,
            90,
            object : BitmapCropCallback {
                override fun onBitmapCropped(
                    resultUri: Uri,
                    offsetX: Int,
                    offsetY: Int,
                    imageWidth: Int,
                    imageHeight: Int
                ) {
                    (context as Activity).runOnUiThread {
                        isCropping = false
                        onCropped(resultUri, imageWidth, imageHeight, offsetX, offsetY)
                    }
                }

                override fun onCropFailure(t: Throwable) {
                    (context as Activity).runOnUiThread {
                        isCropping = false
                        onError(t)
                    }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.background_crop_title)) },
                navigationIcon = {
                    CropTooltipIconButton(
                        tooltip = stringResource(R.string.cancel),
                        enabled = !isCropping,
                        onClick = onCancel
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cancel))
                    }
                },
                actions = {
                    CropTooltipIconButton(
                        tooltip = stringResource(R.string.background_crop_reset),
                        onClick = {
                            cropView = null
                            isLoading = true
                            loadFailed = false
                            rotationAngle = 0f
                            cropViewReloadToken++
                        },
                        enabled = !isLoading && !isCropping && !loadFailed
                    ) {
                        Icon(Icons.Rounded.RestartAlt, contentDescription = stringResource(R.string.background_crop_reset))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp)),
                scrollBehavior = scrollBehavior
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.wrapContentHeight(),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 3.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.background_crop_rotation_angle),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(84.dp)
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            KeyPointSlider(
                                value = rotationAngle.coerceIn(-180f, 180f),
                                onValueChange = ::updateRotation,
                                valueRange = -180f..180f,
                                keyPoints = listOf(-90f, 0f, 90f),
                                enabled = !isLoading && !isCropping && !loadFailed,
                                onValueChangeFinished = ::finishRotationAdjustment,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Text(
                            text = "${rotationAngle.toInt()}\u00B0",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .width(48.dp)
                                .padding(start = 8.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = ::cropCurrentImage,
                            enabled = !isLoading && !isCropping && !loadFailed
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null
                            )
                            Text(
                                text = stringResource(R.string.background_crop_apply),
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            key(cropViewReloadToken) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { viewContext ->
                        UCropView(viewContext, null).apply {
                            layoutParams = FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT
                            )
                            cropImageView.isScaleEnabled = true
                            cropImageView.isRotateEnabled = false
                            cropImageView.isGestureEnabled = true
                            cropImageView.doubleTapScaleSteps = 5
                            cropImageView.setMaxScaleMultiplier(10f)
                            val cropAspectRatio = if (aspectRatioX > 0f && aspectRatioY > 0f) {
                                aspectRatioX / aspectRatioY
                            } else {
                                null
                            }
                            if (aspectRatioX > 0f && aspectRatioY > 0f) {
                                cropAspectRatio?.let {
                                    cropImageView.setTargetAspectRatio(it)
                                    overlayView.setTargetAspectRatio(it)
                                }
                            }
                            if (maxSizeX > 0) cropImageView.setMaxResultImageSizeX(maxSizeX)
                            if (maxSizeY > 0) cropImageView.setMaxResultImageSizeY(maxSizeY)
                            overlayView.setShowCropFrame(true)
                            overlayView.setShowCropGrid(true)
                            overlayView.setFreestyleCropMode(OverlayView.FREESTYLE_CROP_MODE_ENABLE)
                            cropAspectRatio?.let(overlayView::setAspectLockedCornerResize)
                            overlayView.setCropFrameColor(primaryColor)
                            overlayView.setCropGridColor(onSurfaceColor)
                            overlayView.setDimmedColor(scrimColor)
                            cropImageView.setTransformImageListener(
                                object : TransformImageView.TransformImageListener {
                                    override fun onLoadComplete() {
                                        isLoading = false
                                        loadFailed = false
                                        syncCropControls()
                                    }

                                    override fun onLoadFailure(e: Exception) {
                                        isLoading = false
                                        loadFailed = true
                                        onError(e)
                                    }

                                    override fun onRotate(currentAngle: Float) {
                                        rotationAngle = currentAngle.normalizedCropAngle()
                                    }

                                    override fun onScale(currentScale: Float) = Unit
                                }
                            )
                            cropView = this
                            runCatching {
                                cropImageView.setImageUri(inputUri, outputUri)
                            }.onFailure {
                                visibility = View.INVISIBLE
                                isLoading = false
                                loadFailed = true
                                onError(it)
                            }
                        }
                    },
                    update = {
                        it.visibility =
                            if (isLoading || loadFailed) View.INVISIBLE else View.VISIBLE
                        it.overlayView.setCropFrameColor(primaryColor)
                        it.overlayView.setCropGridColor(onSurfaceColor)
                        it.overlayView.setDimmedColor(scrimColor)
                    }
                )
            }

            if (isLoading || isCropping) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LoadingIndicator()
                    Text(
                        text = stringResource(R.string.background_crop_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }
        }
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("DEPRECATION")
@Composable
private fun CropTooltipIconButton(
    tooltip: String,
    enabled: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    TooltipBox(
        positionProvider = remember { BelowAnchorTooltipPositionProvider() },
        tooltip = {
            PlainTooltip {
                Text(tooltip)
            }
        },
        state = rememberTooltipState()
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled
        ) {
            icon()
        }
    }
}

@SuppressLint("ClickableViewAccessibility")
private fun OverlayView.setAspectLockedCornerResize(aspectRatio: Float) {
    if (!aspectRatio.isFinite() || aspectRatio <= 0f) {
        return
    }

    val touchThreshold = 48f * resources.displayMetrics.density
    val minCropSize = resources
        .getDimensionPixelSize(com.yalantis.ucrop.R.dimen.ucrop_default_crop_rect_min_size)
        .toFloat()
    var activeCornerIndex = -1

    setOnTouchListener { view, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activeCornerIndex = cropViewRect.findTouchedCropCorner(
                    touchX = event.x,
                    touchY = event.y,
                    threshold = touchThreshold
                )
                if (activeCornerIndex != -1) {
                    view.onTouchEvent(event)
                } else {
                    false
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (activeCornerIndex == -1 || event.pointerCount != 1) {
                    false
                } else {
                    val lockedPoint = cropViewRect.aspectLockedCornerPoint(
                        cornerIndex = activeCornerIndex,
                        touchX = event.x,
                        touchY = event.y,
                        aspectRatio = aspectRatio,
                        minX = paddingLeft.toFloat(),
                        minY = paddingTop.toFloat(),
                        maxX = (width - paddingRight).toFloat(),
                        maxY = (height - paddingBottom).toFloat(),
                        minCropSize = minCropSize
                    )
                    val adjustedEvent = event.copyWithLocation(lockedPoint.x, lockedPoint.y)
                    try {
                        view.onTouchEvent(adjustedEvent)
                    } finally {
                        adjustedEvent.recycle()
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val handled = if (activeCornerIndex != -1) {
                    view.onTouchEvent(event)
                } else {
                    false
                }
                activeCornerIndex = -1
                handled
            }

            else -> false
        }
    }
}

private fun RectF.findTouchedCropCorner(
    touchX: Float,
    touchY: Float,
    threshold: Float
): Int {
    val thresholdSquared = threshold * threshold
    val corners = floatArrayOf(
        left, top,
        right, top,
        right, bottom,
        left, bottom
    )

    var closestCornerIndex = -1
    var closestDistance = thresholdSquared
    for (index in corners.indices step 2) {
        val deltaX = touchX - corners[index]
        val deltaY = touchY - corners[index + 1]
        val distance = deltaX * deltaX + deltaY * deltaY
        if (distance <= closestDistance) {
            closestDistance = distance
            closestCornerIndex = index / 2
        }
    }
    return closestCornerIndex
}

private fun RectF.aspectLockedCornerPoint(
    cornerIndex: Int,
    touchX: Float,
    touchY: Float,
    aspectRatio: Float,
    minX: Float,
    minY: Float,
    maxX: Float,
    maxY: Float,
    minCropSize: Float
): CropTouchPoint {
    val anchorX: Float
    val anchorY: Float
    val directionX: Float
    val directionY: Float
    when (cornerIndex) {
        0 -> {
            anchorX = right
            anchorY = bottom
            directionX = -1f
            directionY = -1f
        }

        1 -> {
            anchorX = left
            anchorY = bottom
            directionX = 1f
            directionY = -1f
        }

        2 -> {
            anchorX = left
            anchorY = top
            directionX = 1f
            directionY = 1f
        }

        else -> {
            anchorX = right
            anchorY = top
            directionX = -1f
            directionY = 1f
        }
    }

    val projectedDistance = (
            (touchX - anchorX) * directionX * aspectRatio +
                    (touchY - anchorY) * directionY
            ) / (aspectRatio * aspectRatio + 1f)
    val maxWidth = if (directionX > 0f) maxX - anchorX else anchorX - minX
    val maxHeight = if (directionY > 0f) maxY - anchorY else anchorY - minY
    val maxDistance = min(maxWidth / aspectRatio, maxHeight).coerceAtLeast(0f)
    val minDistance = max(minCropSize / aspectRatio, minCropSize)
    val safeDistance = if (maxDistance <= minDistance) {
        maxDistance
    } else {
        projectedDistance.coerceIn(minDistance, maxDistance)
    }

    return CropTouchPoint(
        x = anchorX + directionX * aspectRatio * safeDistance,
        y = anchorY + directionY * safeDistance
    )
}

private fun MotionEvent.copyWithLocation(x: Float, y: Float): MotionEvent =
    MotionEvent.obtain(this).apply {
        setLocation(x, y)
    }

private data class CropTouchPoint(
    val x: Float,
    val y: Float
)

private class BelowAnchorTooltipPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val centeredX = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2
        val x = centeredX.coerceIn(0, windowSize.width - popupContentSize.width)
        val preferredY = anchorBounds.bottom + 8
        val fallbackY = anchorBounds.top - popupContentSize.height - 8
        val y = if (preferredY + popupContentSize.height <= windowSize.height) {
            preferredY
        } else {
            fallbackY.coerceAtLeast(0)
        }
        return IntOffset(x, y)
    }
}

private inline fun <reified T> Intent.getParcelableExtraCompat(name: String): T? =
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(name, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(name)
    }

private fun Float.normalizedCropAngle(): Float {
    var angle = this % 360f
    if (angle > 180) angle -= 360f
    if (angle < -180) angle += 360f
    return angle
}

