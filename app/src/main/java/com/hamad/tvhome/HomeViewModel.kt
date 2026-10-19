package com.hamad.tvhome

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hamad.tvhome.home.ShelfItem
import com.hamad.tvhome.home.ShelfRepository
import com.hamad.tvhome.home.ShelfSource
import com.hamad.tvhome.prefs.LauncherPrefs
import com.hamad.tvhome.prefs.LauncherSettings
import com.hamad.tvhome.tmdb.TmdbClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SheetAction(val label: String, val destructive: Boolean = false) {
    Move("Move"),
    Hide("Hide App"),
    Info("App Info"),
    Uninstall("Uninstall", destructive = true),
    Cancel("Cancel"),
}

data class Sheet(val app: AppEntry, val actions: List<SheetAction>, val focus: Int = 0)

data class Nudge(val dir: Dir, val id: Long)

data class HomeState(
    val apps: List<AppEntry> = emptyList(),
    /** Grid index, or [SHELF] when the Top Shelf itself is focused. */
    val focus: Int = 0,
    /** Which dock (top row) app the Top Shelf is showing. */
    val shelfIndex: Int = 0,
    /** Which of that app's Top Shelf items is showing. */
    val shelfPage: Int = 0,
    /** Direction of the last manual page change (−1/+1); 0 for autoplay and app switches. */
    val pageDir: Int = 0,
    /** Top Shelf content by package name. Apps absent here show the ambient field. */
    val shelf: Map<String, List<ShelfItem>> = emptyMap(),
    val autoplay: Boolean = true,
    val resumed: Boolean = true,
    val editing: Boolean = false,
    val sheet: Sheet? = null,
    val nudge: Nudge? = null,
    val pressedKey: String? = null,
    val loaded: Boolean = false,
) {
    val dockCount: Int get() = minOf(apps.size, GridNav.COLUMNS)
    val focusedRow: Int get() = if (focus == SHELF) -1 else focus / GridNav.COLUMNS
    val dockFocused: Boolean get() = focus in 0 until dockCount
    val shelfApp: AppEntry? get() = apps.getOrNull(shelfIndex)
    val shelfItems: List<ShelfItem> get() = shelfApp?.let { shelf[it.packageName] }.orEmpty()
    val shelfItem: ShelfItem? get() = shelfItems.getOrNull(shelfPage.coerceAtMost(shelfItems.size - 1))

    companion object { const val SHELF = -1 }
}

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AppRepository(app)
    private val order = OrderStore(app)
    private val prefs = LauncherPrefs.get(app)
    private val shelfRepo = ShelfRepository(TmdbClient.get(app))
    private val _intents = MutableSharedFlow<Intent>(extraBufferCapacity = 4)
    val intents: SharedFlow<Intent> = _intents
    private val _ticks = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** One event per successful focus move, for the click sound. */
    val ticks: SharedFlow<Unit> = _ticks

    var state by mutableStateOf(HomeState())
        private set

    /** Every installed app in the user's order, hidden ones included (they keep their place). */
    private var allApps: List<AppEntry> = emptyList()
    private var settings: LauncherSettings = prefs.state.value
    private val shelfLoading = HashSet<String>()
    private var shelfRegion: String = settings.contentRegion
    private var nudgeSeq = 0L

    init {
        state = state.copy(autoplay = settings.topShelfAutoplay)
        reload()
        viewModelScope.launch { prefs.state.collect(::applySettings) }
    }

    fun reload() {
        viewModelScope.launch {
            allApps = withContext(Dispatchers.Default) { order.arrange(repo.load()) }
            publishApps()
            loadShelves()
        }
    }

    fun onResume() {
        state = state.copy(resumed = true)
        applySettings(prefs.state.value)
        loadShelves()
    }

    fun onPause() {
        state = state.copy(resumed = false)
    }

    // ── Settings & content ───────────────────────────────────────────────────────────────────

    private fun applySettings(next: LauncherSettings) {
        val prev = settings
        settings = next
        if (next.topShelfAutoplay != state.autoplay) state = state.copy(autoplay = next.topShelfAutoplay)
        if (next.hiddenApps != prev.hiddenApps) publishApps()
        if (next.contentRegion != shelfRegion) {
            shelfRegion = next.contentRegion
            state = state.copy(shelf = emptyMap(), shelfPage = 0)
            loadShelves()
        }
    }

    /** Re-derives the visible grid from [allApps] minus hidden apps, keeping focus on the same app. */
    private fun publishApps() {
        val s = state
        val visible = allApps.filter { it.key !in settings.hiddenApps }
        val focusKey = s.apps.getOrNull(if (s.focus == HomeState.SHELF) s.shelfIndex else s.focus)?.key
        val keyIndex = visible.indexOfFirst { it.key == focusKey }
        val last = (visible.size - 1).coerceAtLeast(0)
        val focus = if (keyIndex >= 0) keyIndex else s.focus.coerceIn(0, last)
        val dock = (minOf(visible.size, GridNav.COLUMNS) - 1).coerceAtLeast(0)
        val shelfIndex = if (keyIndex in 0..dock) keyIndex else s.shelfIndex.coerceIn(0, dock)
        val keepShelf = s.focus == HomeState.SHELF && keyIndex in 0..dock
        state = s.copy(
            apps = visible,
            focus = if (keepShelf) HomeState.SHELF else focus,
            shelfIndex = shelfIndex,
            shelfPage = if (visible.getOrNull(shelfIndex)?.key == s.shelfApp?.key) s.shelfPage else 0,
            editing = s.editing && visible.isNotEmpty(),
            sheet = null,
            loaded = true,
        )
    }

    /** Fetches Top Shelf content for every installed app that has a source and none yet. */
    private fun loadShelves() {
        val region = shelfRegion
        for (app in allApps) {
            val pkg = app.packageName
            val source = ShelfSource.forPackage(pkg) ?: continue
            if (pkg in state.shelf || !shelfLoading.add(pkg)) continue
            viewModelScope.launch {
                val items = shelfRepo.load(source, region)
                shelfLoading.remove(pkg)
                if (region != shelfRegion) return@launch
                if (items.isEmpty()) {
                    Log.i(TAG, "No Top Shelf content for $pkg ($region); using the ambient field")
                } else {
                    state = state.copy(shelf = state.shelf + (pkg to items))
                }
            }
        }
    }

    fun advanceShelf() {
        val s = state
        val count = s.shelfItems.size
        if (count < 2) return
        state = s.copy(shelfPage = (s.shelfPage + 1) % count, pageDir = 0)
    }

    // ── Input ────────────────────────────────────────────────────────────────────────────────

    fun onDirection(dir: Dir) {
        val s = state
        when {
            s.sheet != null -> moveSheet(dir)
            s.editing -> moveApp(dir)
            s.focus == HomeState.SHELF -> moveInShelf(dir)
            else -> moveFocus(dir)
        }
    }

    fun onSelect() {
        val s = state
        when {
            s.sheet != null -> runSheetAction(s.sheet.actions[s.sheet.focus], s.sheet.app)
            s.editing -> finishEditing()
            s.focus == HomeState.SHELF -> s.shelfApp?.let(::launch)
            else -> s.apps.getOrNull(s.focus)?.let(::launch)
        }
    }

    fun onLongSelect() {
        val s = state
        when {
            s.sheet != null -> Unit
            s.editing -> openSheet(fromEditing = true)
            s.focus == HomeState.SHELF -> Unit
            s.apps.isNotEmpty() -> state = s.copy(editing = true)
        }
    }

    fun onMenu() {
        val s = state
        when {
            s.sheet != null -> state = s.copy(sheet = null)
            s.focus == HomeState.SHELF -> Unit
            else -> openSheet(fromEditing = s.editing)
        }
    }

    /** Back on the home screen never leaves it; it unwinds one level, like tvOS's Menu button. */
    fun onBack() {
        val s = state
        when {
            s.sheet != null -> state = s.copy(sheet = null)
            s.editing -> finishEditing()
            s.focus == HomeState.SHELF -> focusTo(s.copy(focus = s.shelfIndex))
            s.focusedRow > 0 -> focusTo(s.copy(focus = s.shelfIndex))
        }
    }

    /** Home pressed while already home: back to the dock, everything dismissed. */
    fun onHome() {
        if (state.editing) persistOrder(state.apps)
        state = state.copy(sheet = null, editing = false, focus = state.shelfIndex, pressedKey = null)
    }

    // ── Movement ─────────────────────────────────────────────────────────────────────────────

    private fun moveFocus(dir: Dir) {
        val s = state
        if (dir == Dir.Up && s.focusedRow == 0) {
            val withShelf = s.copy(shelfIndex = s.focus)
            if (withShelf.shelfItems.isEmpty()) return nudge(dir)
            return focusTo(withShelf.copy(focus = HomeState.SHELF, pageDir = 0))
        }
        val next = GridNav.move(s.focus, s.apps.size, dir) ?: return nudge(dir)
        val shelf = if (next < GridNav.COLUMNS) next else s.shelfIndex
        focusTo(s.copy(focus = next, shelfIndex = shelf, shelfPage = if (shelf == s.shelfIndex) s.shelfPage else 0, pageDir = 0))
    }

    private fun moveInShelf(dir: Dir) {
        val s = state
        when (dir) {
            Dir.Down -> focusTo(s.copy(focus = s.shelfIndex))
            Dir.Up -> nudge(dir)
            Dir.Left, Dir.Right -> {
                val next = s.shelfPage + dir.dx
                if (next in s.shelfItems.indices) focusTo(s.copy(shelfPage = next, pageDir = dir.dx)) else nudge(dir)
            }
        }
    }

    private fun moveApp(dir: Dir) {
        val s = state
        val target = GridNav.move(s.focus, s.apps.size, dir) ?: return nudge(dir)
        val apps = GridNav.reorder(s.apps, s.focus, target)
        val shelf = if (target < GridNav.COLUMNS) target else s.shelfIndex.coerceAtMost(apps.size - 1)
        focusTo(s.copy(apps = apps, focus = target, shelfIndex = shelf, shelfPage = 0, pageDir = 0))
    }

    private fun moveSheet(dir: Dir) {
        val sheet = state.sheet ?: return
        val next = sheet.focus + dir.dy
        if (dir.dy != 0 && next in sheet.actions.indices) {
            focusTo(state.copy(sheet = sheet.copy(focus = next)))
        }
    }

    private fun focusTo(next: HomeState) {
        state = next
        _ticks.tryEmit(Unit)
    }

    private fun nudge(dir: Dir) {
        state = state.copy(nudge = Nudge(dir, ++nudgeSeq))
    }

    // ── Actions ──────────────────────────────────────────────────────────────────────────────

    private fun finishEditing() {
        persistOrder(state.apps)
        state = state.copy(editing = false)
    }

    /** Saves the visible order; hidden apps keep their relative order after it. */
    private fun persistOrder(visible: List<AppEntry>) {
        val visibleKeys = visible.mapTo(HashSet()) { it.key }
        allApps = visible + allApps.filter { it.key !in visibleKeys }
        order.save(allApps)
    }

    private fun openSheet(fromEditing: Boolean) {
        val app = state.apps.getOrNull(state.focus) ?: return
        val actions = buildList {
            if (!fromEditing) add(SheetAction.Move)
            if (!app.isPseudo) add(SheetAction.Hide)
            if (!app.isPseudo) add(SheetAction.Info)
            if (app.isRemovable) add(SheetAction.Uninstall)
            add(SheetAction.Cancel)
        }
        state = state.copy(sheet = Sheet(app, actions))
    }

    private fun runSheetAction(action: SheetAction, app: AppEntry) {
        state = state.copy(sheet = null)
        when (action) {
            SheetAction.Move -> state = state.copy(editing = true)
            SheetAction.Hide -> {
                if (state.editing) finishEditing()
                prefs.update { it.copy(hiddenApps = it.hiddenApps + app.key) }
            }
            SheetAction.Info -> _intents.tryEmit(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            SheetAction.Uninstall -> {
                if (state.editing) finishEditing()
                _intents.tryEmit(
                    Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            SheetAction.Cancel -> Unit
        }
    }

    private fun launch(app: AppEntry) {
        state = state.copy(pressedKey = app.key)
        viewModelScope.launch {
            delay(PRESS_MS)
            _intents.emit(app.launchIntent)
            delay(PRESS_MS)
            if (state.pressedKey == app.key) state = state.copy(pressedKey = null)
        }
    }

    private companion object {
        const val TAG = "TvHome"
        const val PRESS_MS = 130L
    }
}
