package com.hamad.tvhome.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.hamad.tvhome.AppEntry
import com.hamad.tvhome.Nudge
import kotlinx.coroutines.launch

private const val NUDGE_DP = 9f
private const val TILT_Y_DEG = 2.8f
private const val TILT_X_DEG = 2.0f
private const val JIGGLE_DEG = 1.5f

@Composable
fun AppTile(
    app: AppEntry,
    index: Int,
    x: Dp,
    y: Dp,
    focused: Boolean,
    dimmed: Boolean,
    editing: Boolean,
    pressed: Boolean,
    nudge: Nudge?,
) {
    val ax by animateDpAsState(x, Motion.move(), label = "x")
    val ay by animateDpAsState(y, Motion.move(), label = "y")
    val targetScale = when {
        pressed -> Motion.PRESS_SCALE
        focused && editing -> Motion.EDIT_SCALE
        focused -> Motion.FOCUS_SCALE
        else -> 1f
    }
    val scale by animateFloatAsState(targetScale, Motion.focus(), label = "scale")
    val lift by animateFloatAsState(if (focused) 1f else 0f, Motion.focus(), label = "lift")
    val alpha by animateFloatAsState(if (dimmed) Motion.DIM_ALPHA else 1f, tween(260), label = "alpha")

    val nudgeX = remember { Animatable(0f) }
    val nudgeY = remember { Animatable(0f) }
    LaunchedEffect(nudge?.id) {
        if (!focused || nudge == null) return@LaunchedEffect
        launch {
            nudgeX.animateTo(nudge.dir.dx * NUDGE_DP, tween(80))
            nudgeX.animateTo(0f, spring(dampingRatio = 0.38f, stiffness = 700f))
        }
        launch {
            nudgeY.animateTo(nudge.dir.dy * NUDGE_DP, tween(80))
            nudgeY.animateTo(0f, spring(dampingRatio = 0.38f, stiffness = 700f))
        }
    }

    val tilt = if (focused && !editing) idleTilt() else Offset.Zero
    val jiggle = if (editing && !focused) jiggleAngle(index) else 0f

    Box(
        Modifier
            .offset { IntOffset(ax.roundToPx(), ay.roundToPx()) }
            .zIndex(if (focused) 2f else 0f)
            .size(Dims.TileW, Dims.TileH),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = nudgeX.value.dp.toPx()
                    translationY = nudgeY.value.dp.toPx()
                    rotationX = tilt.y * lift
                    rotationY = tilt.x * lift
                    rotationZ = jiggle
                    cameraDistance = 18f * density
                    shadowElevation = (lift * 22f).dp.toPx()
                    shape = Shapes.Tile
                    clip = true
                    this.alpha = alpha
                }
                .drawWithContent {
                    drawContent()
                    if (lift <= 0.01f) return@drawWithContent
                    // tvOS's specular sheen: a soft top-down gloss plus a hotspot that tracks the tilt.
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.16f * lift),
                            0.55f to Color.Transparent,
                        ),
                    )
                    drawRect(
                        Brush.radialGradient(
                            0f to Color.White.copy(alpha = 0.20f * lift),
                            1f to Color.Transparent,
                            center = Offset(size.width * (0.5f + tilt.x / 9f), -size.height * 0.25f),
                            radius = size.width * 0.8f,
                        ),
                    )
                },
        ) {
            Image(
                bitmap = app.tile,
                contentDescription = app.label,
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.High,
                modifier = Modifier.fillMaxSize(),
            )
        }
        BasicText(
            text = app.label,
            style = Type.TileLabel.copy(textAlign = TextAlign.Center),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = Dims.TileH + Dims.LabelTop)
                .requiredWidth(Dims.TileW + 36.dp)
                .graphicsLayer { this.alpha = if (editing) 0f else lift * alpha },
        )
    }
}

/** The idle parallax drift a focused tvOS icon has while the remote is at rest. */
@Composable
private fun idleTilt(): Offset {
    val t = rememberInfiniteTransition(label = "tilt")
    val tx by t.animateFloat(
        -TILT_Y_DEG, TILT_Y_DEG,
        infiniteRepeatable(tween(3400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "tx",
    )
    val ty by t.animateFloat(
        TILT_X_DEG, -TILT_X_DEG,
        infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ty",
    )
    return Offset(tx, ty)
}

@Composable
private fun jiggleAngle(index: Int): Float {
    val t = rememberInfiniteTransition(label = "jiggle")
    val sign = if (index % 2 == 0) 1f else -1f
    val a by t.animateFloat(
        -JIGGLE_DEG * sign, JIGGLE_DEG * sign,
        infiniteRepeatable(tween(150 + (index % 3) * 18), RepeatMode.Reverse, StartOffset(index * 41)),
        label = "a",
    )
    return a
}
