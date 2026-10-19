package com.hamad.tvhome.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hamad.tvhome.AppEntry
import com.hamad.tvhome.home.ShelfItem

private const val BACKDROP_SATURATION = 1.2f
private const val SCROLLED_SCRIM = 0.62f
private const val ART_PARALLAX = 0.5f

/**
 * The full-screen Top Shelf field: the ambient glow tinted from the dock app (always there, and
 * all that shows for apps without content), the app's real artwork on top, then the legibility
 * gradients. Scrolling into the grid slides the artwork away and settles on the dark material.
 */
@Composable
fun ShelfBackground(
    app: AppEntry?,
    item: ShelfItem?,
    pageDir: Int,
    shelfFocused: Boolean,
    scrolled: Boolean,
    scroll: State<Dp>,
    nudge: NudgeOffset,
) {
    val art by animateFloatAsState(if (scrolled) 0f else 1f, tween(Motion.CROSSFADE_MS), label = "art")
    val scrim by animateFloatAsState(if (scrolled) SCROLLED_SCRIM else 0f, tween(380), label = "scrim")
    val side by animateFloatAsState(if (shelfFocused) 1f else 0f, tween(320), label = "side")
    Box(Modifier.fillMaxSize()) {
        AmbientField(app)
        ArtworkStack(
            url = item?.backdropUrl,
            dir = pageDir,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = art
                    translationX = nudge.dx.dp.toPx()
                    translationY = -scroll.value.toPx() * ART_PARALLAX
                },
        )
        Box(Modifier.fillMaxSize().background(BottomShade))
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = side }.background(SideShade))
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = scrim }.background(Color.Black))
    }
}

private val BottomShade = Brush.verticalGradient(
    0f to Color.Black.copy(alpha = 0.34f),
    0.16f to Color.Black.copy(alpha = 0.06f),
    0.42f to Color.Transparent,
    0.64f to Color.Black.copy(alpha = 0.30f),
    0.82f to Color.Black.copy(alpha = 0.70f),
    1f to Color.Black.copy(alpha = 0.90f),
)

private val SideShade = Brush.horizontalGradient(
    0f to Color.Black.copy(alpha = 0.55f),
    0.34f to Color.Black.copy(alpha = 0.28f),
    0.62f to Color.Transparent,
)

/** The v1 glow: a CPU-blurred thumbnail of the app's banner, stretched full screen. */
@Composable
private fun AmbientField(app: AppEntry?) {
    val filter = remember {
        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(BACKDROP_SATURATION) })
    }
    AnimatedContent(
        targetState = app,
        contentKey = { it?.key },
        transitionSpec = { fadeIn(tween(Motion.CROSSFADE_MS)) togetherWith fadeOut(tween(Motion.CROSSFADE_MS)) },
        label = "ambient",
    ) { a ->
        if (a == null) {
            Box(Modifier.fillMaxSize().background(Color.Black))
        } else {
            Image(
                bitmap = a.backdrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.High,
                colorFilter = filter,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
