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
import androidx.compose.ui.unit.dp
import com.hamad.tvhome.Dir
import com.hamad.tvhome.GridNav
import com.hamad.tvhome.HomeState
import com.hamad.tvhome.HomeViewModel
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
    val scroll by animateDpAsState(
        if (scrolled) (Dims.DockTop - Dims.ScrolledTop) + Dims.Pitch * extraRows else 0.dp,
        Motion.scroll(),
        label = "scroll",
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { keys.handle(it.nativeKeyEvent) },
    ) {
        ShelfBackdrop(s.shelfApp, scrolled)
        Box(Modifier.fillMaxSize().graphicsLayer { translationY = -scroll.toPx() }) {
            TopShelfHero(s.shelfApp, shelfFocused, s.dockCount, s.shelfIndex)
            s.apps.forEachIndexed { i, app ->
                key(app.key) {
                    val row = i / GridNav.COLUMNS
                    val drop = if (shelfFocused && row == 0) Dims.ShelfDrop else 0.dp
                    AppTile(
                        app = app,
                        index = i,
                        x = Dims.tileX(i % GridNav.COLUMNS),
                        y = Dims.DockTop + Dims.Pitch * row + drop,
                        focused = i == s.focus,
                        dimmed = shelfFocused,
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
