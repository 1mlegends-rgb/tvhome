package com.hamad.tvhome.dashboard

import android.service.dreams.DreamService
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The prayer-times dashboard as a screen saver. Compose redraws only when a value changes — the
 * clock ticks on the minute, prices every 10 minutes, weather every 30 — so it sits idle between.
 */
class PrayerDashboardDream : DreamService(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    private var scope: CoroutineScope? = null
    private lateinit var data: DashboardData
    private var state by mutableStateOf<DashboardState?>(null)
    private var weather: List<CityWeather> = emptyList()
    private var btc: BtcQuote? = null

    override fun onCreate() {
        super.onCreate()
        savedState.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        data = DashboardData(this)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = true
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@PrayerDashboardDream)
            setViewTreeSavedStateRegistryOwner(this@PrayerDashboardDream)
            setViewTreeViewModelStoreOwner(this@PrayerDashboardDream)
            setContent { state?.let { DashboardScreen(it) } }
        }
        setContentView(view)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        refresh()
        start()
    }

    private fun start() {
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { scope = it }
        s.launch {
            while (true) {
                refresh()
                val now = System.currentTimeMillis()
                delay(MINUTE_MS - now % MINUTE_MS + 200)
            }
        }
        s.launch {
            while (true) {
                btc = data.bitcoin() ?: btc
                refresh()
                delay(BTC_MS)
            }
        }
        s.launch {
            while (true) {
                weather = data.weather().ifEmpty { weather }
                refresh()
                delay(WEATHER_MS)
            }
        }
    }

    private fun refresh() {
        val now = System.currentTimeMillis()
        val minute = (now / MINUTE_MS).toInt()
        val shift = SHIFTS[minute.mod(SHIFTS.size)]
        state = DashboardState(DashboardClock.at(now), weather, btc, shift.first.dp to shift.second.dp)
    }

    override fun onDetachedFromWindow() {
        scope?.cancel()
        scope = null
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        super.onDetachedFromWindow()
    }

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
        super.onDestroy()
    }

    private companion object {
        const val MINUTE_MS = 60_000L
        const val BTC_MS = 10 * MINUTE_MS
        const val WEATHER_MS = 30 * MINUTE_MS
        /** A small orbit so no pixel stays lit in the same place for hours. */
        val SHIFTS = listOf(0 to 0, 3 to 2, 5 to 0, 3 to -2, 0 to -3, -3 to -2, -5 to 0, -3 to 2)
    }
}
