package com.hamad.tvhome.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import com.hamad.tvhome.HomeViewModel
import com.hamad.tvhome.design.LiquidGlass.liquidGlass
import com.hamad.tvhome.design.TvColors
import com.hamad.tvhome.design.TvDims
import com.hamad.tvhome.design.TvMotion
import com.hamad.tvhome.design.TvShapes
import com.hamad.tvhome.home.ShelfImages
import com.hamad.tvhome.home.ShelfItem
import com.hamad.tvhome.home.ShelfNav

private const val OVERVIEW_WIDTH = 0.55f
private const val GLASS_IDLE = 0.4f
private const val PRESS_DIP = 0.95f

/**
 * HIG carousel-details Top Shelf: attribution, title logo, metadata and synopsis, then the
 * Play / More Info pair, bottom-leading inside the safe area; page dots bottom-centre. Paging
 * slides only the title block — the buttons stay put, as on tvOS.
 */
@Composable
fun ShelfDetails(
    item: ShelfItem?,
    attribution: String?,
    page: Int,
    pageCount: Int,
    pageDir: Int,
    visible: Boolean,
    focusedButton: Int,
    pressedKey: String?,
    nudge: NudgeOffset,
) {
    val shown by animateFloatAsState(if (visible) 1f else 0f, tween(if (visible) 360 else 200), label = "details")
    if (item == null || (shown == 0f && !visible)) return
    val width = LocalConfiguration.current.screenWidthDp.dp * OVERVIEW_WIDTH
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = shown
                translationY = (1f - shown) * 14.dp.toPx()
                translationX = nudge.dx.dp.toPx()
            },
    ) {
        Column(Modifier.align(Alignment.BottomStart).padding(start = Dims.ShelfLeft, bottom = Dims.ShelfBottom)) {
            if (attribution != null) {
                BasicText(attribution, style = Type.ShelfAttribution, maxLines = 1)
                Spacer(Modifier.height(6.dp))
            }
            AnimatedContent(
                targetState = item,
                contentKey = { it.id },
                transitionSpec = {
                    val slide = pageDir * 22
                    (fadeIn(tween(Motion.PAGE_MS)) + slideInHorizontally(tween(Motion.PAGE_MS, easing = FastOutSlowInEasing)) { slide })
                        .togetherWith(fadeOut(tween(Motion.PAGE_MS / 2)))
                },
                label = "details",
            ) { current -> TitleBlock(current, width) }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(Dims.ButtonGap)) {
                ShelfButton(
                    "Play",
                    focused = focusedButton == ShelfNav.PLAY,
                    pressed = pressedKey == HomeViewModel.KEY_PLAY,
                    glyph = true,
                )
                ShelfButton(
                    "More Info",
                    focused = focusedButton == ShelfNav.MORE_INFO,
                    pressed = pressedKey == HomeViewModel.KEY_MORE_INFO,
                )
            }
        }
        PageDots(
            count = pageCount,
            current = page,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = Dims.DotsBottom),
        )
    }
}

@Composable
private fun TitleBlock(item: ShelfItem, width: Dp) {
    Column(Modifier.width(width)) {
        TitleArt(item, width)
        if (item.meta.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            BasicText(item.meta, style = Type.ShelfMeta, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (item.overview.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            BasicText(
                item.overview,
                style = Type.ShelfOverview,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * HIG button: Liquid Glass at rest; focused it turns white, lifts and scales (shape, scale and
 * shadow change too, so colour is never the only cue); selected it dips briefly before opening.
 */
@Composable
private fun ShelfButton(label: String, focused: Boolean, pressed: Boolean, glyph: Boolean = false) {
    val scale by animateFloatAsState(
        when {
            pressed -> TvMotion.BUTTON_FOCUS_SCALE * PRESS_DIP
            focused -> TvMotion.BUTTON_FOCUS_SCALE
            else -> 1f
        },
        TvMotion.focus(),
        label = "btnScale",
    )
    val lift by animateFloatAsState(if (focused) 1f else 0f, TvMotion.focus(), label = "btnLift")
    val fill by animateColorAsState(if (focused) TvColors.FocusedFill else Color.Transparent, tween(150), label = "btnFill")
    val fg = if (focused) TvColors.FocusedLabel else TvColors.Label
    Row(
        Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = (lift * 16f).dp.toPx()
                shape = TvShapes.Capsule
                clip = true
            }
            .liquidGlass(TvShapes.Capsule, GLASS_IDLE)
            .background(fill, TvShapes.Capsule)
            .height(TvDims.ButtonH)
            .padding(horizontal = Dims.ButtonPadH),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (glyph) PlayGlyph(fg)
        BasicText(label, style = Type.ShelfButton.copy(color = fg), maxLines = 1)
    }
}

@Composable
private fun PlayGlyph(color: Color) {
    Canvas(Modifier.size(Dims.PlayGlyph)) {
        val path = Path().apply {
            moveTo(size.width * 0.12f, 0f)
            lineTo(size.width, size.height / 2f)
            lineTo(size.width * 0.12f, size.height)
            close()
        }
        drawPath(path, color)
    }
}

/** tvOS shows the title's logo, not text; Title 1 (emphasized) is only the fallback. */
@Composable
private fun TitleArt(item: ShelfItem, width: Dp) {
    val logo = item.logoUrl
    if (logo == null) {
        TitleText(item.title, width)
        return
    }
    val context = LocalContext.current
    val request = remember(logo) { ShelfImages.logo(context, logo) }
    val painter = rememberAsyncImagePainter(request, contentScale = ContentScale.Fit)
    val state by painter.state.collectAsState()
    if (state is AsyncImagePainter.State.Error) {
        TitleText(item.title, width)
        return
    }
    val alpha by animateFloatAsState(
        if (state is AsyncImagePainter.State.Success) 1f else 0f,
        tween(Motion.CROSSFADE_MS),
        label = "logo",
    )
    Image(
        painter = painter,
        contentDescription = item.title,
        contentScale = ContentScale.Fit,
        alignment = Alignment.BottomStart,
        modifier = Modifier.size(Dims.LogoW, Dims.LogoH).graphicsLayer { this.alpha = alpha },
    )
}

@Composable
private fun TitleText(title: String, width: Dp) {
    Box(Modifier.width(width).height(Dims.LogoH), contentAlignment = Alignment.BottomStart) {
        BasicText(title, style = Type.ShelfTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    if (count < 2) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(Dims.DotGap)) {
        repeat(count) { i ->
            Box(
                Modifier
                    .size(Dims.Dot)
                    .background(if (i == current) Color.White else Palette.Dot, CircleShape),
            )
        }
    }
}
