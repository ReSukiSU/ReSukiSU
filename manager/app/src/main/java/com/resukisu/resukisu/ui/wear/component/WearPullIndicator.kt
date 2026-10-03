package com.resukisu.resukisu.ui.wear.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.LocalReduceMotion
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ProgressIndicatorDefaults

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

/**
 * Feedback for pulling down at the top of a list: a round indicator slides in from the top edge and
 * fills its ring with the pull. Once the pull is far enough to commit on release, the arrow flips
 * up (or the close mark grows) with a small bounce, telling the user to let go.
 *
 * [progress] is the pull relative to the commit threshold; [closing] marks a panel that the pull
 * closes rather than opens.
 */
@Composable
fun WearPullIndicator(progress: Float, closing: Boolean, modifier: Modifier = Modifier) {
    val motion = MaterialTheme.motionScheme
    val reduceMotion = LocalReduceMotion.current
    val shown by animateFloatAsState(progress.coerceIn(0f, 1f),
        if (reduceMotion) snap() else motion.fastEffectsSpec(), label = "pull-shown")
    val armed = progress >= 1f
    val flip by animateFloatAsState(if (armed) 180f else 0f,
        if (reduceMotion) snap() else motion.fastSpatialSpec(), label = "pull-flip")
    val scale by animateFloatAsState(if (armed && !reduceMotion) 1.15f else 1f,
        if (reduceMotion) snap() else motion.fastSpatialSpec(), label = "pull-scale")
    if (shown <= 0.01f) return
    Box(
        modifier.size(36.dp)
            .graphicsLayer {
                translationY = (shown - 1f) * 36.dp.toPx()
                alpha = shown
                scaleX = scale
                scaleY = scale
            }
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val ringColor = MaterialTheme.colorScheme.onPrimaryContainer
        // Once armed, a solid ring shows the pull is complete; before that the ring fills with it.
        if (armed) Box(Modifier.fillMaxSize().border(3.dp, ringColor, CircleShape))
        else CircularProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxSize(),
            colors = ProgressIndicatorDefaults.colors(indicatorColor = ringColor, trackColor = ringColor.copy(alpha = 0.25f)),
            strokeWidth = 3.dp,
        )
        Icon(
            if (closing) Icons.Default.Close else Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(20.dp).graphicsLayer { if (!closing) rotationZ = flip },
        )
    }
}
