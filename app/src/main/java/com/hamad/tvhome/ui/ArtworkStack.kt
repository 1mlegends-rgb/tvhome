package com.hamad.tvhome.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import com.hamad.tvhome.home.ShelfImages
import kotlinx.coroutines.launch

@Stable
private class ArtLayer(val id: Long, val url: String, dir: Int) {
    val alpha = Animatable(0f)
    val shift = Animatable(dir.toFloat())
}

/**
 * Full-bleed artwork with tvOS's crossfade. A new image is stacked on top and only starts to
 * fade in once it has actually decoded, so a slow network never flashes black — the previous
 * picture (or the ambient field beneath, when [url] is null) stays until it is covered.
 * [dir] (−1/0/+1) adds a short horizontal slide for manual paging.
 */
@Composable
fun ArtworkStack(url: String?, dir: Int, modifier: Modifier = Modifier) {
    val layers = remember { mutableStateListOf<ArtLayer>() }
    val seq = remember { longArrayOf(0L) }
    LaunchedEffect(url) {
        val top = layers.lastOrNull()
        when {
            url == null -> {
                val fading = layers.toList()
                fading.map { l -> launch { l.alpha.animateTo(0f, tween(Motion.CROSSFADE_MS)) } }.forEach { it.join() }
                layers.removeAll(fading)
            }
            top?.url == url -> top.alpha.animateTo(1f, tween(Motion.CROSSFADE_MS))
            else -> {
                layers.add(ArtLayer(++seq[0], url, dir))
                while (layers.size > MAX_LAYERS) layers.removeAt(0)
            }
        }
    }
    Box(modifier) {
        for (layer in layers) {
            key(layer.id) {
                ArtLayerImage(layer) { layers.removeAll { it.id < layer.id } }
            }
        }
    }
}

@Composable
private fun ArtLayerImage(layer: ArtLayer, onCovered: () -> Unit) {
    val context = LocalContext.current
    val request = remember(layer.url) { ShelfImages.backdrop(context, layer.url) }
    val painter = rememberAsyncImagePainter(request, contentScale = ContentScale.Crop)
    val state by painter.state.collectAsState()
    val loaded = state is AsyncImagePainter.State.Success
    LaunchedEffect(loaded) {
        if (!loaded) return@LaunchedEffect
        launch { layer.shift.animateTo(0f, tween(Motion.PAGE_MS, easing = FastOutSlowInEasing)) }
        layer.alpha.animateTo(1f, tween(if (layer.shift.value != 0f) Motion.PAGE_MS else Motion.ART_FADE_MS))
        onCovered()
    }
    Image(
        painter = painter,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = layer.alpha.value
                translationX = layer.shift.value * Dims.PageSlide.toPx()
            },
    )
}

private const val MAX_LAYERS = 3
