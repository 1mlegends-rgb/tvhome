package com.hamad.tvhome.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import com.hamad.tvhome.AppEntry
import com.hamad.tvhome.home.ShelfImages
import com.hamad.tvhome.home.ShelfItem

private const val BACKDROP_SATURATION = 1.2f
private const val SCROLLED_SCRIM = 0.62f
private const val OVERVIEW_WIDTH = 0.55f
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

/** Title logo, metadata and synopsis, bottom-left inside the safe area, plus the page dots. */
@Composable
fun ShelfDetails(
    item: ShelfItem?,
    page: Int,
    pageCount: Int,
    pageDir: Int,
    visible: Boolean,
    nudge: NudgeOffset,
) {
    val shown by animateFloatAsState(if (visible) 1f else 0f, tween(if (visible) 360 else 200), label = "details")
    if (item == null && shown == 0f) return
    val screenW = LocalConfiguration.current.screenWidthDp.dp
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = shown
                translationY = (1f - shown) * 14.dp.toPx()
                translationX = nudge.dx.dp.toPx()
            },
    ) {
        AnimatedContent(
            targetState = item,
            contentKey = { it?.id },
            transitionSpec = {
                val slide = pageDir * 22
                (fadeIn(tween(Motion.PAGE_MS)) + slideInHorizontally(tween(Motion.PAGE_MS, easing = FastOutSlowInEasing)) { slide })
                    .togetherWith(fadeOut(tween(Motion.PAGE_MS / 2)))
            },
            modifier = Modifier.align(Alignment.BottomStart).padding(start = Dims.ShelfLeft, bottom = Dims.ShelfBottom),
            label = "details",
        ) { current ->
            if (current != null) DetailColumn(current, width = screenW * OVERVIEW_WIDTH)
        }
        PageDots(
            count = pageCount,
            current = page,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = Dims.DotsBottom),
        )
    }
}

@Composable
private fun DetailColumn(item: ShelfItem, width: Dp) {
    Column(Modifier.width(width)) {
        TitleArt(item, width)
        if (item.meta.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            BasicText(item.meta, style = Type.ShelfMeta, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (item.overview.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
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

/** tvOS shows the title's logo, not text; text in Title1 is only the fallback. */
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
