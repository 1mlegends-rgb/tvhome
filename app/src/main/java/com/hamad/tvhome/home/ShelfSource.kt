package com.hamad.tvhome.home

import com.hamad.tvhome.tmdb.TmdbClient

/** Where an app's Top Shelf content comes from. Apps not listed here get the static fallback. */
sealed interface ShelfSource {
    /** The HIG's succinct attribution above the title, e.g. "Featured on Netflix". */
    fun attribution(appLabel: String): String

    data class Provider(val providerId: Int) : ShelfSource {
        override fun attribution(appLabel: String) = "Featured on $appLabel"
    }

    data object Trending : ShelfSource {
        override fun attribution(appLabel: String) = "Trending on $appLabel"
    }

    companion object {
        private val byPackage = mapOf(
            "com.netflix.ninja" to Provider(TmdbClient.PROVIDER_NETFLIX),
            "com.amazon.amazonvideo.livingroom" to Provider(TmdbClient.PROVIDER_PRIME),
            "com.disney.disneyplus" to Provider(TmdbClient.PROVIDER_DISNEY),
            "com.apple.atve.androidtv.appletv" to Provider(TmdbClient.PROVIDER_APPLE_TV),
            "com.google.android.youtube.tv" to Trending,
        )

        fun forPackage(packageName: String): ShelfSource? = byPackage[packageName]
    }
}
