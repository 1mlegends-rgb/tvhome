package com.hamad.tvhome.prefs

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * User-facing launcher preferences, shared by Home (reads), Settings (writes), the screensaver
 * and the system overlays. One process-wide instance; observe [state].
 */
data class LauncherSettings(
    val hiddenApps: Set<String> = emptySet(),
    /** TMDB watch-provider region for Top Shelf content (ISO 3166-1). */
    val contentRegion: String = "US",
    val topShelfAutoplay: Boolean = true,
    val aerialShowLocation: Boolean = true,
    val aerialShowClock: Boolean = true,
    val aerialQuality4k: Boolean = false,
    val clickSounds: Boolean = true,
)

class LauncherPrefs private constructor(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<LauncherSettings> = _state.asStateFlow()

    fun update(transform: (LauncherSettings) -> LauncherSettings) {
        val next = transform(_state.value)
        prefs.edit()
            .putStringSet(K_HIDDEN, next.hiddenApps)
            .putString(K_REGION, next.contentRegion)
            .putBoolean(K_AUTOPLAY, next.topShelfAutoplay)
            .putBoolean(K_AERIAL_LOC, next.aerialShowLocation)
            .putBoolean(K_AERIAL_CLOCK, next.aerialShowClock)
            .putBoolean(K_AERIAL_4K, next.aerialQuality4k)
            .putBoolean(K_CLICKS, next.clickSounds)
            .apply()
        _state.value = next
    }

    private fun read(): LauncherSettings {
        val d = LauncherSettings()
        val country = Locale.getDefault().country.takeIf { it.length == 2 } ?: d.contentRegion
        return LauncherSettings(
            hiddenApps = prefs.getStringSet(K_HIDDEN, emptySet())?.toSet().orEmpty(),
            contentRegion = prefs.getString(K_REGION, null) ?: country,
            topShelfAutoplay = prefs.getBoolean(K_AUTOPLAY, d.topShelfAutoplay),
            aerialShowLocation = prefs.getBoolean(K_AERIAL_LOC, d.aerialShowLocation),
            aerialShowClock = prefs.getBoolean(K_AERIAL_CLOCK, d.aerialShowClock),
            aerialQuality4k = prefs.getBoolean(K_AERIAL_4K, d.aerialQuality4k),
            clickSounds = prefs.getBoolean(K_CLICKS, d.clickSounds),
        )
    }

    companion object {
        private const val K_HIDDEN = "hidden"
        private const val K_REGION = "region"
        private const val K_AUTOPLAY = "shelf_autoplay"
        private const val K_AERIAL_LOC = "aerial_location"
        private const val K_AERIAL_CLOCK = "aerial_clock"
        private const val K_AERIAL_4K = "aerial_4k"
        private const val K_CLICKS = "click_sounds"

        @Volatile private var instance: LauncherPrefs? = null

        fun get(context: Context): LauncherPrefs =
            instance ?: synchronized(this) {
                instance ?: LauncherPrefs(context.applicationContext).also { instance = it }
            }
    }
}
