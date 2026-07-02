package com.resukisu.resukisu.ui.screen.themeSettings.crop

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
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
import androidx.compose.material.icons.rounded.Rotate90DegreesCcw
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
import androidx.compose.material3.Slider
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
import com.resukisu.resukisu.ui.theme.KernelSUTheme
import com.yalantis.ucrop.UCrop
import com.yalantis.ucrop.callback.BitmapCropCallback
import com.yalantis.ucrop.view.OverlayView
import com.yalantis.ucrop.view.TransformImageView
import com.yalantis.ucrop.view.UCropView
import java.io.Serializable

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

        setContent {
            KernelSUTheme {
                BackgroundCropScreen(
                    inputUri = inputUri,
                    outputUri = outputUri,
                    aspectRatioX = aspectRatioX,
                    aspectRatioY = aspectRatioY,
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
}

@SuppressLint("AutoboxingStateCreation")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BackgroundCropScreen(
    inputUri: Uri,
    outputUri: Uri,
    aspectRatioX: Float,
    aspectRatioY: Float,
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
    var scaleValue by remember { mutableFloatStateOf(1f) }
    var minScaleValue by remember { mutableFloatStateOf(1f) }
    var maxScaleValue by remember { mutableFloatStateOf(1f) }
    var cropViewReloadToken by remember { mutableIntStateOf(0) }
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)

    val primaryColor = MaterialTheme.colorScheme.primary.toArgb()
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.58f).toArgb()

    fun syncCropControls() {
        cropView?.cropImageView?.let { imageView ->
            val minScale = imageView.minScale.takeIf { it.isFinite() && it > 0f }
                ?: return
            val maxScale = imageView.maxScale
                .takeIf { it.isFinite() && it > minScale }
                ?: (minScale * 10)
            val currentScale = imageView.currentScale.takeIf { it.isFinite() && it > 0f }
                ?: minScale
            rotationAngle = imageView.currentAngle.normalizedCropAngle()
            minScaleValue = minScale
            maxScaleValue = maxScale
            scaleValue = currentScale.coerceIn(minScale, maxScale)
        }
    }

    fun updateRotation(targetAngle: Float) {
        cropView?.cropImageView?.let { imageView ->
            val normalizedTarget = targetAngle.normalizedCropAngle()
            val delta = (normalizedTarget - imageView.currentAngle.normalizedCropAngle())
                .normalizedCropAngle()
            imageView.postRotate(delta)
            imageView.setImageToWrapCropBounds()
            rotationAngle = normalizedTarget
        }
    }

    fun updateScale(targetScale: Float) {
        if (!targetScale.isFinite() || minScaleValue <= 0f || maxScaleValue < minScaleValue) {
            return
        }
        cropView?.cropImageView?.let { imageView ->
            val safeScale = targetScale.coerceIn(minScaleValue, maxScaleValue)
            val currentScale = imageView.currentScale.takeIf { it.isFinite() && it > 0f }
                ?: minScaleValue
            if (safeScale > currentScale) {
                imageView.zoomInImage(safeScale)
            } else {
                imageView.zoomOutImage(safeScale)
            }
            scaleValue = safeScale
        }
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
                            scaleValue = 1f
                            minScaleValue = 1f
                            maxScaleValue = 1f
                            cropViewReloadToken++
                        },
                        enabled = !isLoading && !isCropping && !loadFailed
                    ) {
                        Icon(Icons.Rounded.RestartAlt, contentDescription = stringResource(R.string.background_crop_reset))
                    }
                    CropTooltipIconButton(
                        tooltip = stringResource(R.string.background_crop_rotate),
                        onClick = {
                            updateRotation(rotationAngle + 90f)
                        },
                        enabled = !isLoading && !isCropping && !loadFailed
                    ) {
                        Icon(Icons.Rounded.Rotate90DegreesCcw, contentDescription = stringResource(R.string.background_crop_rotate))
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
                    CropAdjustmentSlider(
                        label = stringResource(R.string.background_crop_rotation_angle),
                        value = rotationAngle,
                        valueRange = -180f..180f,
                        displayValue = "${rotationAngle.toInt()}°",
                        enabled = !isLoading && !isCropping && !loadFailed,
                        onValueChange = ::updateRotation
                    )
                    CropAdjustmentSlider(
                        label = stringResource(R.string.background_crop_zoom),
                        value = scaleValue,
                        valueRange = minScaleValue.safeCropScaleRange(maxScaleValue),
                        displayValue = scaleValue.cropScaleDisplay(minScaleValue),
                        enabled = !isLoading && !isCropping && !loadFailed &&
                                minScaleValue.isFinite() &&
                                maxScaleValue.isFinite() &&
                                maxScaleValue > minScaleValue,
                        onValueChange = ::updateScale
                    )
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
                            cropImageView.isRotateEnabled = true
                            cropImageView.isGestureEnabled = true
                            cropImageView.doubleTapScaleSteps = 5
                            cropImageView.setMaxScaleMultiplier(10f)
                            if (aspectRatioX > 0f && aspectRatioY > 0f) {
                                cropImageView.setTargetAspectRatio(aspectRatioX / aspectRatioY)
                                overlayView.setTargetAspectRatio(aspectRatioX / aspectRatioY)
                            }
                            if (maxSizeX > 0) cropImageView.setMaxResultImageSizeX(maxSizeX)
                            if (maxSizeY > 0) cropImageView.setMaxResultImageSizeY(maxSizeY)
                            overlayView.setShowCropFrame(true)
                            overlayView.setShowCropGrid(true)
                            overlayView.setFreestyleCropMode(OverlayView.FREESTYLE_CROP_MODE_DISABLE)
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

                                    override fun onScale(currentScale: Float) {
                                        syncCropControls()
                                    }
                                }
                            )
                            cropView = this
                            runCatching {
                                cropImageView.setImageUri(inputUri, outputUri)
                            }.onFailure {
                                isLoading = false
                                loadFailed = true
                                onError(it)
                            }
                        }
                    },
                    update = {
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

@Composable
private fun CropAdjustmentSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    displayValue: String,
    enabled: Boolean,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(84.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
        ) {
            Slider(
                value = value.coerceIn(valueRange.start, valueRange.endInclusive),
                onValueChange = onValueChange,
                valueRange = valueRange,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Text(
            text = displayValue,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .width(48.dp)
                .padding(start = 8.dp)
        )
    }
}

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

private fun Float.safeCropScaleRange(maxScale: Float): ClosedFloatingPointRange<Float> {
    val safeMin = takeIf { it.isFinite() && it > 0f } ?: 1f
    val safeMax = maxScale.takeIf { it.isFinite() && it > safeMin } ?: safeMin
    return safeMin..safeMax
}

private fun Float.cropScaleDisplay(minScale: Float): String {
    val safeScale = takeIf { it.isFinite() && it > 0f } ?: 1f
    val safeMinScale = minScale.takeIf { it.isFinite() && it > 0f } ?: safeScale
    val scaleFactor = (safeScale / safeMinScale).takeIf { it.isFinite() && it > 0f } ?: 1f
    return "${scaleFactor.coerceAtLeast(1f).formatCropScale()}x"
}

private fun Float.formatCropScale(): String =
    "%.1f".format(java.util.Locale.US, this)
