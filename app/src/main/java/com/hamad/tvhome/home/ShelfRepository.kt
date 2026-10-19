package com.hamad.tvhome.home

import android.util.Log
import com.hamad.tvhome.tmdb.TmdbClient
import com.hamad.tvhome.tmdb.TmdbItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class ShelfRepository(private val tmdb: TmdbClient) {

    /** Never throws: a dead network or an unconfigured token simply yields an empty shelf. */
    suspend fun load(source: ShelfSource, region: String): List<ShelfItem> = try {
        val picked = ShelfFormat.usable(fetch(source, region))
        coroutineScope {
            picked.map { item -> async { toShelfItem(item) } }.awaitAll().filterNotNull()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Top Shelf load failed for $source", e)
        emptyList()
    }

    private suspend fun fetch(source: ShelfSource, region: String): List<TmdbItem> = when (source) {
        ShelfSource.Trending -> tmdb.trending("all")
        is ShelfSource.Provider -> {
            val local = provider(source.providerId, region)
            if (local.isNotEmpty() || region == FALLBACK_REGION) local
            else provider(source.providerId, FALLBACK_REGION)
        }
    }

    private suspend fun provider(id: Int, region: String): List<TmdbItem> = coroutineScope {
        val tv = async { ShelfFormat.usable(tmdb.onProvider(id, "tv", region)) }
        val movie = async { ShelfFormat.usable(tmdb.onProvider(id, "movie", region)) }
        ShelfFormat.interleave(tv.await(), movie.await())
    }

    private suspend fun toShelfItem(item: TmdbItem): ShelfItem? {
        val backdrop = TmdbClient.image(item.backdropPath, BACKDROP_SIZE) ?: return null
        return ShelfItem(
            id = "${item.mediaType}/${item.id}",
            tmdbId = item.id,
            mediaType = item.mediaType,
            title = item.title,
            meta = ShelfFormat.meta(item),
            overview = item.overview,
            backdropUrl = backdrop,
            logoUrl = TmdbClient.image(tmdb.logoPath(item), LOGO_SIZE),
        )
    }

    private companion object {
        const val TAG = "TopShelf"
        const val FALLBACK_REGION = "US"
        const val BACKDROP_SIZE = "w1280"
        const val LOGO_SIZE = "w500"
    }
}
