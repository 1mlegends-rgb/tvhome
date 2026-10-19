package com.hamad.tvhome.tmdb

import android.content.Context
import android.util.Log
import com.hamad.tvhome.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

data class TmdbItem(
    val id: Long,
    /** "movie" or "tv". */
    val mediaType: String,
    val title: String,
    val overview: String,
    val backdropPath: String?,
    val posterPath: String?,
    val year: String?,
    val genres: List<String>,
    val rating: Double,
)

/**
 * Thin TMDB v3 client (real data only — Top Shelf, Search). All calls are suspend, run on IO,
 * and return empty results instead of throwing when the network is down; failures are logged.
 */
class TmdbClient private constructor(context: Context) {
    private val http = OkHttpClient.Builder()
        .cache(Cache(File(context.cacheDir, "tmdb"), CACHE_BYTES))
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()
    private val genreNames = HashMap<Int, String>()
    private val logoCache = HashMap<String, String?>()

    val isConfigured: Boolean get() = BuildConfig.TMDB_READ_TOKEN.isNotBlank()

    suspend fun trending(mediaType: String = "all", window: String = "week"): List<TmdbItem> =
        list("trending/$mediaType/$window", emptyMap(), mediaType.takeIf { it != "all" })

    /** Titles streaming on a provider (Netflix 8, Prime Video 9/119, Disney+ 337, Apple TV+ 350). */
    suspend fun onProvider(providerId: Int, mediaType: String, region: String): List<TmdbItem> =
        list(
            "discover/$mediaType",
            mapOf(
                "with_watch_providers" to providerId.toString(),
                "watch_region" to region,
                "sort_by" to "popularity.desc",
                "with_watch_monetization_types" to "flatrate",
            ),
            mediaType,
        )

    suspend fun search(query: String): List<TmdbItem> =
        if (query.isBlank()) emptyList()
        else list("search/multi", mapOf("query" to query, "include_adult" to "false"), null)
            .filter { it.mediaType == "movie" || it.mediaType == "tv" }

    /** One title by id — used when another surface (Top Shelf "More Info") hands over an id. */
    suspend fun details(id: Long, mediaType: String): TmdbItem? {
        ensureGenres()
        val json = get(url("$mediaType/$id", emptyMap())) ?: return null
        return parse(json, mediaType)
    }

    /** Best English (or language-neutral) title-logo path, as tvOS Top Shelf shows logos, not text. */
    suspend fun logoPath(item: TmdbItem): String? {
        val key = "${item.mediaType}/${item.id}"
        synchronized(logoCache) { if (key in logoCache) return logoCache[key] }
        val json = get(url("$key/images", mapOf("include_image_language" to "en,null"))) ?: return null
        val logos = json.optJSONArray("logos") ?: JSONArray()
        var best: String? = null
        var bestScore = -1.0
        for (i in 0 until logos.length()) {
            val l = logos.getJSONObject(i)
            val score = l.optDouble("vote_average") + if (l.optString("iso_639_1") == "en") 10 else 0
            if (score > bestScore && !l.optString("file_path").endsWith(".svg")) {
                bestScore = score
                best = l.optString("file_path")
            }
        }
        synchronized(logoCache) { logoCache[key] = best }
        return best
    }

    private suspend fun list(path: String, params: Map<String, String>, forcedType: String?): List<TmdbItem> {
        ensureGenres()
        val json = get(url(path, params)) ?: return emptyList()
        val results = json.optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).mapNotNull { parse(results.getJSONObject(it), forcedType) }
    }

    private fun parse(o: JSONObject, forcedType: String?): TmdbItem? {
        val type = forcedType ?: o.optString("media_type").ifEmpty { return null }
        val title = o.optString(if (type == "tv") "name" else "title").ifEmpty { return null }
        val date = o.optString(if (type == "tv") "first_air_date" else "release_date")
        val ids = o.optJSONArray("genre_ids") ?: JSONArray().also { arr ->
            // Detail responses carry full genre objects instead of ids.
            val full = o.optJSONArray("genres") ?: return@also
            for (i in 0 until full.length()) arr.put(full.getJSONObject(i).optInt("id"))
        }
        return TmdbItem(
            id = o.optLong("id"),
            mediaType = type,
            title = title,
            overview = o.optString("overview"),
            backdropPath = o.optString("backdrop_path").takeIf { it.startsWith("/") },
            posterPath = o.optString("poster_path").takeIf { it.startsWith("/") },
            year = date.take(4).takeIf { it.length == 4 },
            genres = (0 until ids.length()).mapNotNull { synchronized(genreNames) { genreNames[ids.getInt(it)] } },
            rating = o.optDouble("vote_average", 0.0),
        )
    }

    private suspend fun ensureGenres() {
        if (synchronized(genreNames) { genreNames.isNotEmpty() }) return
        for (type in listOf("movie", "tv")) {
            val arr = get(url("genre/$type/list", emptyMap()))?.optJSONArray("genres") ?: continue
            synchronized(genreNames) {
                for (i in 0 until arr.length()) {
                    val g = arr.getJSONObject(i)
                    genreNames[g.getInt("id")] = g.getString("name")
                }
            }
        }
    }

    private fun url(path: String, params: Map<String, String>): HttpUrl =
        "$API/$path".toHttpUrl().newBuilder().apply {
            addQueryParameter("language", "en-US")
            params.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()

    private suspend fun get(url: HttpUrl): JSONObject? = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext null
        val req = Request.Builder().url(url)
            .header("Authorization", "Bearer ${BuildConfig.TMDB_READ_TOKEN}")
            .header("Accept", "application/json")
            .build()
        try {
            http.newCall(req).execute().use { res ->
                if (!res.isSuccessful) {
                    Log.w(TAG, "TMDB ${res.code} for ${url.encodedPath}")
                    null
                } else {
                    res.body?.string()?.let(::JSONObject)
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "TMDB request failed: ${url.encodedPath}", e)
            null
        } catch (e: org.json.JSONException) {
            Log.w(TAG, "TMDB bad JSON: ${url.encodedPath}", e)
            null
        }
    }

    companion object {
        private const val TAG = "Tmdb"
        private const val API = "https://api.themoviedb.org/3"
        private const val IMG = "https://image.tmdb.org/t/p"
        private const val CACHE_BYTES = 16L * 1024 * 1024

        const val PROVIDER_NETFLIX = 8
        const val PROVIDER_PRIME = 9
        const val PROVIDER_DISNEY = 337
        const val PROVIDER_APPLE_TV = 350

        /** Sizes: backdrops w780/w1280/original, posters w342/w500, logos w500. */
        fun image(path: String?, size: String = "w1280"): String? = path?.let { "$IMG/$size$it" }

        @Volatile private var instance: TmdbClient? = null

        fun get(context: Context): TmdbClient =
            instance ?: synchronized(this) {
                instance ?: TmdbClient(context.applicationContext).also { instance = it }
            }
    }
}
