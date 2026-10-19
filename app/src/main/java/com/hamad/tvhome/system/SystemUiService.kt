package com.hamad.tvhome.system

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.hamad.tvhome.AppEntry
import com.hamad.tvhome.AppRepository
import com.hamad.tvhome.OrderStore
import com.hamad.tvhome.prefs.LauncherPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * System-wide tvOS chrome, drawn as accessibility overlays over any app:
 * hold HOME → Control Center, double-press HOME → App Switcher, single press → normal Home.
 */
class SystemUiService : AccessibilityService() {

    private val main = Handler(Looper.getMainLooper())
    private var scope: CoroutineScope = MainScope()
    private lateinit var controls: SystemControls
    private lateinit var usage: UsageSource

    private val router = HomeKeyRouter(
        timers = object : HomeKeyRouter.Timers {
            override fun schedule(delayMs: Long, block: () -> Unit): HomeKeyRouter.Cancelable {
                val r = Runnable(block)
                main.postDelayed(r, delayMs)
                return HomeKeyRouter.Cancelable { main.removeCallbacks(r) }
            }
        },
        actions = object : HomeKeyRouter.Actions {
            override fun onSinglePress() = goHome()
            override fun onDoublePress() = showSwitcher()
            override fun onLongPress() = showControlCenter()
        },
    )

    private var overlay: OverlayWindow? = null
    private var overlayState: OverlayState? = null
    /** Key codes whose DOWN an overlay consumed: their UP must not leak to the app below. */
    private val swallowUps = HashSet<Int>()
    private var homeClosesOverlay = false
    /** Our own GLOBAL_ACTION_HOME may be re-delivered as an injected key; let that one through. */
    private var passHomeUntil = 0L

    /** Foreground packages seen this session, newest first — real recency without usage access. */
    private val session = ArrayDeque<Usage>()
    private val dismissedAt = HashMap<String, Long>()
    private var catalog: Deferred<List<AppEntry>>? = null

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            catalog = null
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        scope = MainScope()
        controls = SystemControls(this)
        usage = UsageSource(this)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, packageReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onDestroy() {
        router.reset()
        dismissOverlayNow()
        runCatching { unregisterReceiver(packageReceiver) }
        scope.cancel()
        super.onDestroy()
    }

    override fun onInterrupt() {
        router.reset()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // The cached banners are ~1 MB each; the box has 2 GB. Rebuild on next open instead.
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW && overlay == null) catalog = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg in TRANSIENT_PACKAGES) return
        session.removeAll { it.packageName == pkg }
        session.addFirst(Usage(pkg, System.currentTimeMillis()))
        while (session.size > SESSION_LIMIT) session.removeLast()
    }

    // ---- Key routing -------------------------------------------------------------------------

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (code == KeyEvent.KEYCODE_HOME) return onHomeKey(event)
        val shown = overlay
        if (shown == null) {
            return event.action == KeyEvent.ACTION_UP && swallowUps.remove(code)
        }
        return when {
            code in CLOSE_KEYS -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    swallowUps += code
                    if (event.repeatCount == 0) closeOverlay()
                } else {
                    swallowUps.remove(code)
                }
                true
            }
            code in OWNED_KEYS -> {
                if (event.action == KeyEvent.ACTION_DOWN) swallowUps += code else swallowUps.remove(code)
                shown.keys.dispatch(event)
                true
            }
            // Volume, media, power and anything else keep working underneath.
            else -> shown.keys.dispatch(event)
        }
    }

    private fun onHomeKey(event: KeyEvent): Boolean {
        val injectedBySystem = (event.flags and KeyEvent.FLAG_FROM_SYSTEM) != 0
        if (injectedBySystem && SystemClock.uptimeMillis() < passHomeUntil) return false
        when (event.action) {
            KeyEvent.ACTION_DOWN -> when {
                overlay != null && !router.isTracking -> if (event.repeatCount == 0) homeClosesOverlay = true
                else -> router.onDown(event.repeatCount, (event.flags and KeyEvent.FLAG_LONG_PRESS) != 0)
            }
            KeyEvent.ACTION_UP -> when {
                homeClosesOverlay -> {
                    homeClosesOverlay = false
                    closeOverlay()
                }
                else -> router.onUp(event.isCanceled)
            }
        }
        return true
    }

    private fun goHome() {
        passHomeUntil = SystemClock.uptimeMillis() + PASS_HOME_MS
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    // ---- Overlays ----------------------------------------------------------------------------

    private fun showOverlay(content: @Composable (OverlayState) -> Unit): Boolean {
        if (overlay != null) return false
        val window = OverlayWindow(this)
        val state = OverlayState(window.keys) { onOverlayDismissed(window) }
        overlay = window
        overlayState = state
        if (!window.show { content(state) }) {
            overlay = null
            overlayState = null
            return false
        }
        return true
    }

    private fun closeOverlay() {
        overlayState?.close() ?: dismissOverlayNow()
    }

    /** The composable finished its exit spring. Tear down outside its own composition. */
    private fun onOverlayDismissed(window: OverlayWindow) {
        if (overlay === window) {
            overlay = null
            overlayState = null
        }
        main.post { window.destroy() }
    }

    private fun dismissOverlayNow() {
        val window = overlay ?: return
        overlay = null
        overlayState = null
        window.destroy()
    }

    private fun showControlCenter() {
        showOverlay { state ->
            ControlCenter(state, controls) { kind -> onControlSelected(kind) }
        }
    }

    private fun onControlSelected(kind: CcKind) {
        when (kind) {
            // The display is about to go dark: an exit animation would stall until wake.
            CcKind.Sleep, CcKind.ScreenSaver -> {
                dismissOverlayNow()
                controls.perform(kind)
            }
            else -> {
                controls.perform(kind)
                closeOverlay()
            }
        }
    }

    private fun showSwitcher() {
        if (overlay != null) return
        val cards = mutableStateOf<List<SwitcherCard>>(listOf(SwitcherCard.Home(emptyList())))
        val pending = catalogAsync()
        val sessionSnapshot = session.toList()
        val dismissedSnapshot = dismissedAt.toMap()
        // First load renders every banner (slow on the box): open at once and fill in.
        var opened = !pending.isCompleted && openSwitcher(cards)
        scope.launch {
            val entries = runCatching { pending.await() }
                .onFailure { Log.e(TAG, "App catalog failed", it) }
                .getOrDefault(emptyList())
            cards.value = withContext(Dispatchers.IO) { buildCards(entries, sessionSnapshot, dismissedSnapshot) }
            if (!opened) opened = openSwitcher(cards)
        }
    }

    private fun openSwitcher(cards: State<List<SwitcherCard>>): Boolean =
        showOverlay { state ->
            AppSwitcher(
                state = state,
                cards = cards.value,
                onOpen = { card -> openCard(card) },
                onClosed = { card -> closeApp(card.entry) },
            )
        }

    private fun buildCards(entries: List<AppEntry>, seen: List<Usage>, dismissed: Map<String, Long>): List<SwitcherCard> {
        val hidden = LauncherPrefs.get(this).state.value.hiddenApps
        val arranged = OrderStore(this).arrange(entries).filter { it.key !in hidden }
        val excluded = usage.homePackages()
        val apps = arranged.filter { !it.isPseudo && it.packageName !in excluded }
        val recents = orderRecents(apps, { it.packageName }, usage.recent(), seen, dismissed)
        return listOf(SwitcherCard.Home(arranged)) + recents.map { SwitcherCard.App(it) }
    }

    private fun openCard(card: SwitcherCard) {
        when (card) {
            is SwitcherCard.Home -> goHome()
            is SwitcherCard.App -> runCatching { startActivity(card.entry.launchIntent) }
                .onFailure { Log.w(TAG, "Cannot open ${card.entry.packageName}", it) }
        }
        closeOverlay()
    }

    /** tvOS "swipe up to close": hide the card until next use and stop the app if we may. */
    private fun closeApp(entry: AppEntry) {
        dismissedAt[entry.packageName] = System.currentTimeMillis()
        session.removeAll { it.packageName == entry.packageName }
        val canKill = ContextCompat.checkSelfPermission(this, Manifest.permission.KILL_BACKGROUND_PROCESSES) ==
            PackageManager.PERMISSION_GRANTED
        if (canKill) {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            runCatching { am.killBackgroundProcesses(entry.packageName) }
                .onFailure { Log.w(TAG, "killBackgroundProcesses failed", it) }
        }
    }

    private fun catalogAsync(): Deferred<List<AppEntry>> =
        catalog ?: scope.async(Dispatchers.Default) { AppRepository(this@SystemUiService).load() }
            .also { catalog = it }

    private companion object {
        const val TAG = "SystemUi"
        const val PASS_HOME_MS = 500L
        const val SESSION_LIMIT = 30
        val CLOSE_KEYS = setOf(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE)
        val OWNED_KEYS = setOf(
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
        )
        /** Windows that come and go over an app without being "the app the user is in". */
        val TRANSIENT_PACKAGES = setOf("com.android.systemui", "android")
    }
}
