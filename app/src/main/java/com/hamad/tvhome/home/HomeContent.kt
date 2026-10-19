package com.hamad.tvhome.home

import androidx.compose.runtime.Immutable

/** The four destinations of the floating tab bar (tvOS 26 Apple TV app). */
enum class Tab(val label: String) {
    Home("Home"),
    Movies("Movies"),
    Shows("TV Shows"),
    Apps("Apps"),
}

enum class CardStyle { Landscape, Poster }

/** One TMDB title, already formatted for display. Everything comes from TMDB — nothing invented. */
@Immutable
data class Title(
    /** "movie/123" — stable across rows, used as the lazy-list key. */
    val id: String,
    val tmdbId: Long,
    /** "movie" or "tv". */
    val mediaType: String,
    val name: String,
    val meta: String,
    val overview: String,
    /** Full-bleed hero artwork (w1280). */
    val heroUrl: String?,
    /** 16:9 card artwork (w780). */
    val cardUrl: String?,
    /** 2:3 poster (w342). */
    val posterUrl: String?,
)

/** One page of the hero carousel. [providerPackage] is the streaming app it is featured on. */
@Immutable
data class HeroItem(
    val title: Title,
    val attribution: String,
    val logoUrl: String?,
    val providerPackage: String?,
)

@Immutable
data class ContentRow(
    val id: String,
    val heading: String,
    val style: CardStyle,
    val items: List<Title>,
)
