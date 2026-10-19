package com.hamad.tvhome.settings

import android.view.SoundEffectConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.hamad.tvhome.design.TvColors
import com.hamad.tvhome.design.TvDims
import com.hamad.tvhome.design.TvMotion
import com.hamad.tvhome.design.TvRow
import com.hamad.tvhome.design.TvSectionHeader
import com.hamad.tvhome.design.TvType

private val ArtColumnWidth = 480.dp
private val ListWidth = 446.dp // 430dp rows + room for the focus lift
private val ListTop = 84.dp
private val TitleTop = 32.dp
private val FooterTop = 410.dp
private val FooterWidth = 360.dp
private const val PAGE_SLIDE_MS = 340
private const val FOCUS_ATTEMPTS = 10
private const val PRESSED_SCALE = 0.97f
private val ListClipTop = 76.dp // the list viewport starts below the page title

@Composable
internal fun SettingsScreen(c: SettingsController, onExit: () -> Unit) {
    val prefs by c.prefs.state.collectAsState()
    BackHandler {
        when {
            c.alert != null -> c.alert = null
            !c.pop() -> onExit()
        }
    }
    Box(Modifier.fillMaxSize().background(TvColors.AppBackground)) {
        AnimatedContent(
            targetState = c.stack.last(),
            transitionSpec = {
                val dir = if (c.forward) 1 else -1
                val slide = tween<androidx.compose.ui.unit.IntOffset>(PAGE_SLIDE_MS, easing = FastOutSlowInEasing)
                (slideInHorizontally(slide) { it / 6 * dir } + fadeIn(tween(240, delayMillis = 60))) togetherWith
                    (slideOutHorizontally(slide) { -it / 6 * dir } + fadeOut(tween(160)))
            },
            contentKey = { it.key },
            label = "page",
        ) { dest ->
            val tick = c.tick
            val apps = c.apps
            val spec = remember(dest, tick, prefs, apps) { buildPage(dest, c, prefs) }
            SettingsPage(dest, spec, c, prefs.clickSounds)
        }
        SettingsAlertHost(c)
    }
}

@Composable
private fun SettingsPage(dest: Dest, spec: PageSpec, c: SettingsController, clicks: Boolean) {
    val listState = rememberLazyListState()
    val requesters = remember { HashMap<String, FocusRequester>() }
    val focus = remember { PageFocus() }
    val scope = rememberCoroutineScope()
    var footer by remember { mutableStateOf<String?>(null) }
    val latestSpec by rememberUpdatedState(spec)
    val alertShown = c.alert != null
    val view = LocalView.current

    // First entry, return from a pushed page, alert dismissal and late-loading rows (Apps) all
    // land focus on the remembered row.
    // Window focus is included because Compose hands focus to the first row when the window
    // gains focus (launch, return from Android's settings) — after which we re-assert ours.
    val hasRows = spec.entries.any { it is Item }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(alertShown, hasRows, windowFocused) {
        if (!alertShown && hasRows) restoreFocus(dest, latestSpec, c, listState, requesters, focus)
    }

    Box(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown) focus.userMoved = true
                if (clicks && e.type == KeyEventType.KeyDown) {
                    when (e.key) {
                        Key.DirectionUp -> view.playSoundEffect(SoundEffectConstants.NAVIGATION_UP)
                        Key.DirectionDown -> view.playSoundEffect(SoundEffectConstants.NAVIGATION_DOWN)
                    }
                }
                false
            },
    ) {
        BasicText(spec.title, style = SettingsText.PageTitle, maxLines = 1, modifier = Modifier.align(Alignment.TopCenter).padding(top = TitleTop))
        Box(Modifier.fillMaxHeight().width(ArtColumnWidth)) {
            SettingsArt(spec.art, Modifier.align(Alignment.Center))
            Crossfade(
                footer,
                animationSpec = tween(200),
                label = "footer",
                modifier = Modifier.align(Alignment.TopCenter).padding(top = FooterTop).width(FooterWidth),
            ) { text ->
                if (text != null) {
                    BasicText(text, style = SettingsText.Description.copy(textAlign = TextAlign.Center), modifier = Modifier.fillMaxSize())
                }
            }
        }
        SettingsList(
            dest, spec, c, listState, requesters, Modifier.align(Alignment.TopEnd),
            onRowFocused = { id ->
                val target = focus.target
                if (!focus.userMoved && target != null && id != target) {
                    // The target row may have been scrolled out (and disposed): restore fully.
                    scope.launch { restoreFocus(dest, latestSpec, c, listState, requesters, focus) }
                    false
                } else {
                    true
                }
            },
        ) { footer = it }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SettingsList(
    dest: Dest,
    spec: PageSpec,
    c: SettingsController,
    listState: LazyListState,
    requesters: HashMap<String, FocusRequester>,
    modifier: Modifier,
    onRowFocused: (String) -> Boolean,
    onFooter: (String?) -> Unit,
) {
    CompositionLocalProvider(LocalBringIntoViewSpec provides KeepFocusInBand) {
        LazyColumn(
            state = listState,
            modifier = modifier.padding(top = ListClipTop, end = TvDims.SafeH - 8.dp).width(ListWidth).fillMaxHeight(),
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = ListTop - ListClipTop, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(TvDims.RowGap),
        ) {
            itemsIndexed(spec.entries, key = { _, e -> e.id }, contentType = { _, e -> e::class }) { index, e ->
                when (e) {
                    is Header -> TvSectionHeader(e.text)
                    is Note -> BasicText(e.text, style = SettingsText.Description, modifier = Modifier.padding(16.dp))
                    is Item -> {
                        val fr = remember { FocusRequester() }
                        DisposableEffect(e.id) {
                            requesters[e.id] = fr
                            onDispose { if (requesters[e.id] === fr) requesters.remove(e.id) }
                        }
                        SettingsRow(e, Modifier.pressFeedback().focusRequester(fr)) {
                            if (onRowFocused(e.id)) {
                                c.focusMemory[dest.key] = FocusMemo(e.id, index)
                                onFooter(e.footer)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(item: Item, modifier: Modifier, onFocused: () -> Unit) {
    val checked = item.checked
    val icon = item.icon
    val leading: (@Composable (Boolean) -> Unit)? = when {
        checked != null -> { focused ->
            Box(Modifier.width(16.dp), contentAlignment = Alignment.CenterStart) {
                if (checked) Checkmark(if (focused) TvColors.FocusedLabel else TvColors.Label)
            }
        }
        icon != null -> { _ ->
            Image(
                icon,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(width = 40.dp, height = 22.5.dp).clip(RoundedCornerShape(3.dp)),
            )
        }
        else -> null
    }
    TvRow(
        title = item.title,
        onClick = item.onClick ?: {},
        modifier = modifier,
        value = item.value,
        chevron = item.chevron,
        leading = leading,
        onFocus = onFocused,
    )
}

/** HIG "highlighted" state: a brief press-in while select is held. */
@Composable
private fun Modifier.pressFeedback(): Modifier {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) PRESSED_SCALE else 1f, TvMotion.focus(), label = "press")
    return this
        .onPreviewKeyEvent { e ->
            if (e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter) {
                pressed = e.type == KeyEventType.KeyDown
            }
            false
        }
        .onFocusChanged { if (!it.isFocused) pressed = false }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
}

/**
 * Where focus should be on this page until the user presses a key. On a cold start the framework
 * focuses the ComposeView late and Compose then moves focus to the first row; [target] lets the
 * row callback undo that.
 */
private class PageFocus {
    var target: String? = null
    var userMoved = false
}

private suspend fun restoreFocus(
    dest: Dest,
    spec: PageSpec,
    c: SettingsController,
    listState: LazyListState,
    requesters: Map<String, FocusRequester>,
    focus: PageFocus,
) {
    val entries = spec.entries
    val memo = c.focusMemory[dest.key]
    var idx = memo?.let { m -> entries.indexOfFirst { it.id == m.id } } ?: -1
    if (idx < 0 && memo != null) idx = entries.indices.lastOrNull { it <= memo.index && entries[it] is Item } ?: -1
    // Pickers open on the current choice, like tvOS.
    if (idx < 0) idx = entries.indexOfFirst { it is Item && it.checked == true }
    if (idx < 0) idx = entries.indexOfFirst { it is Item }
    if (idx < 0) return
    withFrameNanos { }
    if (listState.layoutInfo.visibleItemsInfo.none { it.index == idx }) {
        listState.scrollToItem((idx - 3).coerceAtLeast(0))
    }
    val id = entries[idx].id
    focus.target = id
    requestWhenAttached(requesters, id)
}

private suspend fun requestWhenAttached(requesters: Map<String, FocusRequester>, id: String): Boolean {
    repeat(FOCUS_ATTEMPTS) {
        val fr = requesters[id]
        if (fr != null && runCatching { fr.requestFocus() }.isSuccess) return true
        withFrameNanos { }
    }
    return false
}

/**
 * SF Pro roles for this surface: Display Semibold with slight negative tracking for titles,
 * SF Pro Text Regular for descriptive copy (rows keep the kit's Medium labels).
 */
internal object SettingsText {
    val PageTitle = TvType.Title3.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp)
    val Description = TvType.Callout.copy(fontWeight = FontWeight.Normal, color = TvColors.SecondaryLabel)
}

/** Scrolls only once focus leaves the middle band, so the focused row never hugs an edge. */
@OptIn(ExperimentalFoundationApi::class)
private val KeepFocusInBand = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val margin = containerSize * 0.2f
        val end = offset + size
        return when {
            offset < margin -> offset - margin
            end > containerSize - margin -> end - (containerSize - margin)
            else -> 0f
        }
    }
}
