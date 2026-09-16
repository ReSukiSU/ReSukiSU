package com.resukisu.resukisu.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.resukisu.resukisu.ui.component.liquid.lens
import com.resukisu.resukisu.ui.component.liquid.vibrancy
import com.resukisu.resukisu.ui.theme.CardConfig
import com.resukisu.resukisu.ui.theme.ScreenEdgePadding
import com.resukisu.resukisu.ui.theme.ThemeConfig
import com.resukisu.resukisu.ui.theme.isInDarkTheme
import com.resukisu.resukisu.ui.util.LocalBlurState
import org.koin.compose.koinInject
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop

/** Breathing room inside a pill, and the gap each one keeps from its neighbour. */
private val PillHorizontalPadding = 6.dp

/** Material3's own horizontal padding on the top app bar's navigation and action rows. */
private val TopAppBarIconRowPadding = 4.dp

/** Padding around a title's text inside its pill. Interior spacing, not the screen gutter. */
private val PillTextPadding = 16.dp

/** Strong enough to smear scrolling content into an unreadable wash behind a pill. */
private val PillBlurRadius = 16.dp

/**
 * Inset that puts a top app bar's leading and trailing icon pills on the [ScreenEdgePadding]
 * gutter, making up whatever [TopAppBarIconRowPadding] and [PillHorizontalPadding] leave over.
 */
val TopBarIconEdgeInset = ScreenEdgePadding - TopAppBarIconRowPadding - PillHorizontalPadding

/**
 * Capsule container for top app bar content: a refracted, frosted pill while the blur backdrop
 * is available, a solid one otherwise.
 *
 * Unlike [FloatingBottomBar], which floats over empty space, a pill sits directly on scrolling
 * content — so it tints and blurs hard enough to stop that content reading through it.
 */
@Composable
private fun TopBarPill(
    modifier: Modifier = Modifier,
    shadowRadius: Dp = 10.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
    val isInDark = isInDarkTheme(themeConfig.forceDarkMode)
    val backdrop = LocalBlurState.current
    // Same surface the cards use, so the pills follow the Card transparency setting.
    // cardAlpha is 1f without a custom background, which would hide the blur entirely, so
    // fall back to blurEffect()'s haze-like 0.8f whenever there is a backdrop to show.
    val tintAlpha = when {
        cardConfig.isCustomBackgroundEnabled -> cardConfig.cardAlpha
        backdrop != null -> 0.8f
        else -> 1f
    }
    val containerColor = MaterialTheme.colorScheme.surfaceBright.copy(alpha = tintAlpha)

    Box(
        modifier = modifier
            .dropShadow(
                shape = CircleShape,
                shadow = Shadow(
                    radius = shadowRadius,
                    color = Color.Black,
                    alpha = if (isInDark) 0.2f else 0.1f,
                ),
            )
            .then(
                if (backdrop != null) {
                    Modifier.drawBackdrop(
                        backdrop = backdrop,
                        shape = { CircleShape },
                        effects = {
                            vibrancy()
                            blur(PillBlurRadius.toPx(), PillBlurRadius.toPx())
                            lens(
                                refractionHeight = 12.dp.toPx(),
                                refractionAmount = 12.dp.toPx(),
                            )
                        },
                        onDrawSurface = { drawRect(containerColor) },
                    )
                } else {
                    Modifier.background(containerColor, CircleShape)
                }
            ),
        contentAlignment = Alignment.Center,
        content = content
    )
}

/** Wraps a top app bar title in a [TopBarPill]. */
@Composable
fun TopBarTitlePill(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    TopBarPill(modifier) {
        Box(modifier = Modifier.padding(horizontal = PillTextPadding, vertical = 6.dp)) {
            content()
        }
    }
}

/** A top app bar navigation or action button sitting inside a circular [TopBarPill]. */
@Composable
fun TopBarIconPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    TopBarPill(
        modifier = modifier.padding(horizontal = PillHorizontalPadding),
        shadowRadius = 4.dp
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(40.dp),
            enabled = enabled,
            content = content
        )
    }
}

/** Top app bar colors for the pill design: the bar itself paints nothing. */
@Composable
fun transparentTopAppBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = Color.Transparent,
    scrolledContainerColor = Color.Transparent,
)

/**
 * Window insets for the pill design, topping [base] up so the trailing action pill lands on the
 * [ScreenEdgePadding] gutter. The leading pill gets its own [TopBarIconEdgeInset] instead, so that
 * the title still starts on the gutter on screens with no navigation icon.
 */
@Composable
fun pillTopAppBarWindowInsets(
    base: WindowInsets = TopAppBarDefaults.windowInsets
): WindowInsets = base.add(WindowInsets(right = TopBarIconEdgeInset))
