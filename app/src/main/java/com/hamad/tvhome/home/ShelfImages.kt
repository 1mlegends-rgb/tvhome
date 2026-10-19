package com.hamad.tvhome.home

import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import coil3.size.Precision

/**
 * Every Top Shelf image request is built here so that a prefetch and the later on-screen load
 * produce the same memory-cache key (Coil keys on size). Decodes are capped: a w1280 backdrop is
 * decoded at 1280×720 (3.7 MB) however large the screen is, logos at 600×240.
 */
object ShelfImages {
    private const val BACKDROP_W = 1280
    private const val BACKDROP_H = 720
    private const val LOGO_W = 600
    private const val LOGO_H = 240
    private const val MEMORY_FRACTION = 0.2

    fun install() {
        SingletonImageLoader.setSafe { ctx ->
            ImageLoader.Builder(ctx)
                .memoryCache { MemoryCache.Builder().maxSizePercent(ctx, MEMORY_FRACTION).build() }
                .build()
        }
    }

    fun backdrop(context: Context, url: String): ImageRequest =
        ImageRequest.Builder(context)
            .data(url)
            .size(BACKDROP_W, BACKDROP_H)
            .precision(Precision.INEXACT)
            .build()

    fun logo(context: Context, url: String): ImageRequest =
        ImageRequest.Builder(context)
            .data(url)
            .size(LOGO_W, LOGO_H)
            .precision(Precision.INEXACT)
            .build()

    fun prefetch(context: Context, item: ShelfItem?) {
        if (item == null) return
        val loader = SingletonImageLoader.get(context)
        loader.enqueue(backdrop(context, item.backdropUrl))
        item.logoUrl?.let { loader.enqueue(logo(context, it)) }
    }
}
