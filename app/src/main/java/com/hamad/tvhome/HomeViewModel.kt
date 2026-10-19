package com.hamad.tvhome

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SheetAction(val label: String, val destructive: Boolean = false) {
    Move("Move"),
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
    val editing: Boolean = false,
    val sheet: Sheet? = null,
    val nudge: Nudge? = null,
    val pressedKey: String? = null,
    val loaded: Boolean = false,
) {
    val dockCount: Int get() = minOf(apps.size, GridNav.COLUMNS)
    val focusedRow: Int get() = if (focus == SHELF) -1 else focus / GridNav.COLUMNS
    val shelfApp: AppEntry? get() = apps.getOrNull(shelfIndex)

    companion object { const val SHELF = -1 }
}

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AppRepository(app)
    private val order = OrderStore(app)
    private val _intents = MutableSharedFlow<Intent>(extraBufferCapacity = 4)
    val intents: SharedFlow<Intent> = _intents

    var state by mutableStateOf(HomeState())
        private set

    private var nudgeSeq = 0L

    init { reload() }

    fun reload() {
        viewModelScope.launch {
            val apps = withContext(Dispatchers.Default) { order.arrange(repo.load()) }
            val focusKey = state.apps.getOrNull(state.focus)?.key
            val focus = apps.indexOfFirst { it.key == focusKey }.takeIf { it >= 0 }
                ?: state.focus.coerceIn(0, (apps.size - 1).coerceAtLeast(0))
            state = state.copy(
                apps = apps,
                focus = if (state.focus == HomeState.SHELF) HomeState.SHELF else focus,
                shelfIndex = state.shelfIndex.coerceIn(0, (minOf(apps.size, GridNav.COLUMNS) - 1).coerceAtLeast(0)),
                sheet = null,
                loaded = true,
            )
        }
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
            s.focus == HomeState.SHELF -> state = s.copy(focus = s.shelfIndex)
            s.focusedRow > 0 -> state = s.copy(focus = s.shelfIndex)
        }
    }

    /** Home pressed while already home: back to the dock, everything dismissed. */
    fun onHome() {
        if (state.editing) order.save(state.apps)
        state = state.copy(sheet = null, editing = false, focus = state.shelfIndex, pressedKey = null)
    }

    // ── Movement ─────────────────────────────────────────────────────────────────────────────

    private fun moveFocus(dir: Dir) {
        val s = state
        if (dir == Dir.Up && s.focusedRow == 0) {
            state = s.copy(focus = HomeState.SHELF, shelfIndex = s.focus)
            return
        }
        val next = GridNav.move(s.focus, s.apps.size, dir) ?: return nudge(dir)
        val shelf = if (next < GridNav.COLUMNS) next else s.shelfIndex
        state = s.copy(focus = next, shelfIndex = shelf)
    }

    private fun moveInShelf(dir: Dir) {
        val s = state
        when (dir) {
            Dir.Down -> state = s.copy(focus = s.shelfIndex)
            Dir.Up -> nudge(dir)
            Dir.Left, Dir.Right -> {
                val next = s.shelfIndex + dir.dx
                if (next in 0 until s.dockCount) state = s.copy(shelfIndex = next) else nudge(dir)
            }
        }
    }

    private fun moveApp(dir: Dir) {
        val s = state
        val target = GridNav.move(s.focus, s.apps.size, dir) ?: return nudge(dir)
        val apps = GridNav.reorder(s.apps, s.focus, target)
        val shelf = if (target < GridNav.COLUMNS) target else s.shelfIndex.coerceAtMost(apps.size - 1)
        state = s.copy(apps = apps, focus = target, shelfIndex = shelf)
    }

    private fun moveSheet(dir: Dir) {
        val sheet = state.sheet ?: return
        val next = sheet.focus + dir.dy
        if (dir.dy != 0 && next in sheet.actions.indices) {
            state = state.copy(sheet = sheet.copy(focus = next))
        }
    }

    private fun nudge(dir: Dir) {
        state = state.copy(nudge = Nudge(dir, ++nudgeSeq))
    }

    // ── Actions ──────────────────────────────────────────────────────────────────────────────

    private fun finishEditing() {
        order.save(state.apps)
        state = state.copy(editing = false)
    }

    private fun openSheet(fromEditing: Boolean) {
        val app = state.apps.getOrNull(state.focus) ?: return
        val actions = buildList {
            if (!fromEditing) add(SheetAction.Move)
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
        const val PRESS_MS = 130L
    }
}
