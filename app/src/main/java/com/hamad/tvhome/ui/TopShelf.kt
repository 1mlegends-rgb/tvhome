package com.hamad.tvhome.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hamad.tvhome.AppEntry

private const val BACKDROP_SATURATION = 1.2f
private const val BACKDROP_BRIGHTNESS = 1f
private const val SCROLLED_SCRIM = 0.74f

/** The full-bleed frosted field behind everything, tinted from whichever app the shelf is on. */
@Composable
fun ShelfBackdrop(app: AppEntry?, scrolled: Boolean) {
    val filter = remember {
        val m = ColorMatrix().apply { setToSaturation(BACKDROP_SATURATION) }
        val b = BACKDROP_BRIGHTNESS
        m.timesAssign(ColorMatrix(floatArrayOf(b, 0f, 0f, 0f, 0f, 0f, b, 0f, 0f, 0f, 0f, 0f, b, 0f, 0f, 0f, 0f, 0f, 1f, 0f)))
        ColorFilter.colorMatrix(m)
    }
    val scrim by animateFloatAsState(if (scrolled) SCROLLED_SCRIM else 0f, tween(380), label = "scrim")
    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = app,
            contentKey = { it?.key },
            transitionSpec = { fadeIn(tween(Motion.CROSSFADE_MS)) togetherWith fadeOut(tween(Motion.CROSSFADE_MS)) },
            label = "backdrop",
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
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.10f),
                        0.48f to Color.Black.copy(alpha = 0.22f),
                        0.74f to Color.Black.copy(alpha = 0.62f),
                        1f to Color.Black.copy(alpha = 0.90f),
                    ),
                ),
        )
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = scrim }.background(Color.Black))
    }
}

/** The Top Shelf hero: the app's own wide artwork, its name, and page dots when focused. */
@Composable
fun TopShelfHero(app: AppEntry?, focused: Boolean, pageCount: Int, page: Int) {
    if (app == null) return
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, Motion.focus(), label = "heroScale")
    val lift by animateFloatAsState(if (focused) 1f else 0f, Motion.focus(), label = "heroLift")
    Column(Modifier.padding(start = Dims.HeroLeft, top = Dims.HeroTop)) {
        Row(verticalAlignment = Alignment.Bottom) {
            AnimatedContent(
                targetState = app,
                contentKey = { it.key },
                transitionSpec = { fadeIn(tween(Motion.CROSSFADE_MS)) togetherWith fadeOut(tween(Motion.CROSSFADE_MS)) },
                label = "heroArt",
            ) { a ->
                Box(
                    Modifier
                        .size(Dims.HeroW, Dims.HeroH)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            shadowElevation = (10f + lift * 18f).dp.toPx()
                            shape = Shapes.Hero
                            clip = true
                        },
                ) {
                    Image(
                        bitmap = a.tile,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        filterQuality = FilterQuality.High,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Spacer(Modifier.width(30.dp))
            AnimatedContent(
                targetState = app,
                contentKey = { it.key },
                transitionSpec = { fadeIn(tween(Motion.CROSSFADE_MS)) togetherWith fadeOut(tween(Motion.CROSSFADE_MS)) },
                label = "heroText",
            ) { a ->
                Column(Modifier.padding(bottom = 6.dp)) {
                    BasicText(a.label, style = Type.HeroTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    a.category?.let {
                        Spacer(Modifier.height(4.dp))
                        BasicText(it, style = Type.HeroCaption, maxLines = 1)
                    }
                }
            }
        }
        Spacer(Modifier.height(22.dp))
        PageDots(
            count = pageCount,
            current = page,
            modifier = Modifier
                .offset(x = (Dims.HeroW - 14.dp * pageCount) / 2)
                .graphicsLayer { alpha = lift },
        )
    }
}

@Composable
private fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(count) { i ->
            Box(
                Modifier
                    .size(6.dp)
                    .background(if (i == current) Color.White else Color.White.copy(alpha = 0.35f), CircleShape),
            )
        }
    }
}
