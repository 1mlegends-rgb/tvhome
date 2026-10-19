package com.hamad.tvhome.ui

import android.view.KeyEvent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hamad.tvhome.Dir
import com.hamad.tvhome.GridNav
import com.hamad.tvhome.HomeState
import com.hamad.tvhome.HomeViewModel
import com.hamad.tvhome.home.ShelfImages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val LONG_PRESS_MS = 550L
private const val VISIBLE_ROWS_BEFORE_SCROLL = 2

@Composable
fun HomeScreen(vm: HomeViewModel) {
    val s = vm.state
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val keys = remember { KeyRouter(vm, scope) }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val shelfFocused = s.focus == HomeState.SHELF
    val scrolled = s.focusedRow > 0
    val extraRows = (s.focusedRow - VISIBLE_ROWS_BEFORE_SCROLL).coerceAtLeast(0)
    val scroll = animateDpAsState(
        if (scrolled) (Dims.DockTop - Dims.ScrolledTop) + Dims.Pitch * extraRows else 0.dp,
        Motion.scroll(),
        label = "scroll",
    )
    val shelfNudge = rememberNudge(s.nudge, active = shelfFocused)

    ShelfAutoplay(vm, s)
    ShelfPrefetch(s)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { keys.handle(it.nativeKeyEvent) },
    ) {
        ShelfBackground(
            app = s.shelfApp,
            item = s.shelfItem,
            pageDir = s.pageDir,
            shelfFocused = shelfFocused,
            scrolled = scrolled,
            scroll = scroll,
            nudge = shelfNudge,
        )
        ShelfDetails(
            item = s.shelfItem,
            page = s.shelfPage,
            pageCount = s.shelfItems.size,
            pageDir = s.pageDir,
            visible = shelfFocused,
            nudge = shelfNudge,
        )
        Box(Modifier.fillMaxSize().graphicsLayer { translationY = -scroll.value.toPx() }) {
            s.apps.forEachIndexed { i, app ->
                key(app.key) {
                    val row = i / GridNav.COLUMNS
                    val sunk = shelfFocused && row == 0
                    AppTile(
                        app = app,
                        index = i,
                        x = Dims.tileX(i % GridNav.COLUMNS),
                        y = Dims.DockTop + Dims.Pitch * row + when {
                            sunk -> Dims.ShelfDrop
                            row > 0 && !scrolled -> Dims.LowerRowsDrop
                            else -> 0.dp
                        },
                        focused = i == s.focus,
                        hidden = sunk,
                        editing = s.editing,
                        pressed = s.pressedKey == app.key,
                        nudge = s.nudge,
                    )
                }
            }
        }
        EditHint(visible = s.editing && s.sheet == null)
        ActionSheetOverlay(s.sheet)
    }
}

/** While the dock has focus the shelf pages itself every ~8 s; it holds still otherwise. */
@Composable
private fun ShelfAutoplay(vm: HomeViewModel, s: HomeState) {
    val running = s.autoplay && s.resumed && s.dockFocused && !s.editing && s.sheet == null && s.shelfItems.size > 1
    LaunchedEffect(s.shelfApp?.key, s.shelfPage, running) {
        if (!running) return@LaunchedEffect
        delay(Motion.SHELF_AUTOPLAY_MS)
        vm.advanceShelf()
    }
}

/** Warms the next page and the first page of every dock app so moves never wait on the network. */
@Composable
private fun ShelfPrefetch(s: HomeState) {
    val context = LocalContext.current
    val items = s.shelfItems
    LaunchedEffect(s.shelfApp?.key, s.shelfPage, items.size) {
        if (items.size > 1) ShelfImages.prefetch(context, items[(s.shelfPage + 1) % items.size])
    }
    val firsts = s.apps.take(s.dockCount).mapNotNull { s.shelf[it.packageName]?.firstOrNull() }
    LaunchedEffect(firsts.map { it.id }) {
        firsts.forEach { ShelfImages.prefetch(context, it) }
    }
}

@Composable
private fun EditHint(visible: Boolean) {
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(240), label = "hint")
    Box(Modifier.fillMaxSize().padding(bottom = 22.dp), contentAlignment = Alignment.BottomCenter) {
        BasicText(
            "Use the arrows to move  ·  Press OK when done  ·  Hold OK for more options",
            style = Type.Hint,
            modifier = Modifier.graphicsLayer { this.alpha = alpha },
        )
    }
}

/**
 * Remote input. Select is resolved on key-up so that holding it can mean something else (tvOS's
 * jiggle mode) without a stray launch; arrows act on every down, auto-repeat included.
 */
private class KeyRouter(private val vm: HomeViewModel, private val scope: CoroutineScope) {
    private var longJob: Job? = null
    private var longFired = false
    private var selectDown = false

    fun handle(ev: KeyEvent): Boolean {
        val down = ev.action == KeyEvent.ACTION_DOWN
        return when (ev.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> arrow(down, Dir.Up)
            KeyEvent.KEYCODE_DPAD_DOWN -> arrow(down, Dir.Down)
            KeyEvent.KEYCODE_DPAD_LEFT -> arrow(down, Dir.Left)
            KeyEvent.KEYCODE_DPAD_RIGHT -> arrow(down, Dir.Right)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A -> select(ev, down)
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_INFO, KeyEvent.KEYCODE_BUTTON_Y -> {
                if (down && ev.repeatCount == 0) vm.onMenu()
                true
            }
            else -> false
        }
    }

    private fun arrow(down: Boolean, dir: Dir): Boolean {
        if (down) vm.onDirection(dir)
        return true
    }

    private fun select(ev: KeyEvent, down: Boolean): Boolean {
        if (down) {
            if (ev.repeatCount == 0) {
                selectDown = true
                longFired = false
                longJob?.cancel()
                longJob = scope.launch {
                    delay(LONG_PRESS_MS)
                    longFired = true
                    vm.onLongSelect()
                }
            } else if (ev.isLongPress && !longFired) {
                longJob?.cancel()
                longFired = true
                vm.onLongSelect()
            }
        } else if (selectDown) {
            selectDown = false
            longJob?.cancel()
            if (!longFired) vm.onSelect()
        }
        return true
    }
}
