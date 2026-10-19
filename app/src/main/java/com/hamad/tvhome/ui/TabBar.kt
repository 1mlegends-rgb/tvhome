package com.hamad.tvhome.ui

import android.text.format.DateFormat
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.hamad.tvhome.HomeViewModel
import com.hamad.tvhome.R
import com.hamad.tvhome.design.LiquidGlass.liquidGlass
import com.hamad.tvhome.design.TvColors
import com.hamad.tvhome.design.TvDims
import com.hamad.tvhome.design.TvMotion
import com.hamad.tvhome.design.TvShapes
import com.hamad.tvhome.home.Bar
import com.hamad.tvhome.home.Tab
import com.hamad.tvhome.home.Zone
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

private const val BAR_SLIDE_DP = 22f
private const val PRESS_DIP = 0.94f

/**
 * tvOS 26's floating Liquid Glass tab bar, top-centre: Search · Home · Movies · TV Shows · Apps ·
 * Settings, with the time at the top-right. It is there while focus is on it or on the hero and
 * slides away as the page scrolls into the rows. [shown] is derived from the page scroll target.
 */
@Composable
fun TabBar(vm: HomeViewModel, shown: State<Boolean>, modifier: Modifier = Modifier) {
    val reveal = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        snapshotFlow { shown.value }.collect { visible ->
            reveal.animateTo(if (visible) 1f else 0f, tween(if (visible) 260 else 180))
        }
    }
    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = reveal.value
                translationY = -(1f - reveal.value) * BAR_SLIDE_DP.dp.toPx()
            },
    ) {
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = HomeDims.BarTop)
                .height(HomeDims.BarH)
                .liquidGlass(TvShapes.Capsule, intensity = 0.35f)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            BarGlyph(vm, Bar.SEARCH, R.drawable.ic_search)
            for (tab in Tab.entries) BarTab(vm, tab)
            BarGlyph(vm, Bar.SETTINGS, R.drawable.ic_gear)
        }
        Clock(Modifier.align(Alignment.TopEnd).padding(top = HomeDims.BarTop + 8.dp, end = TvDims.SafeH))
    }
}

/** 0 idle, 1 focused. */
@Composable
private fun slotFocus(vm: HomeViewModel, slot: Int): State<Float> {
    val focused by remember(slot) { derivedStateOf { vm.page.row == Zone.BAR && vm.barSlot == slot } }
    return animateFloatAsState(if (focused) 1f else 0f, TvMotion.focus(), label = "barFocus")
}

/** 1 while the bar itself holds focus (the selected tab then casts a shadow). */
@Composable
private fun barFocus(vm: HomeViewModel): State<Float> {
    val focused by remember { derivedStateOf { vm.page.row == Zone.BAR } }
    return animateFloatAsState(if (focused) 1f else 0f, tween(200), label = "barHasFocus")
}

@Composable
private fun BarTab(vm: HomeViewModel, tab: Tab) {
    val slot = Bar.slotOf(tab)
    val focus by slotFocus(vm, slot)
    val isCurrent by remember(tab) { derivedStateOf { vm.tab == tab } }
    val selected by animateFloatAsState(if (isCurrent) 1f else 0f, tween(200), label = "barSel")
    val pressed = vm.pressedKey == HomeViewModel.barKey(slot)
    val bar by barFocus(vm)
    Box(
        Modifier
            .height(HomeDims.BarItemH)
            .barPill({ focus }, { selected }, { bar }, pressed)
            .padding(horizontal = HomeDims.BarItemPadH),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            tab.label,
            style = Type.BarLabel,
            maxLines = 1,
            color = { labelColor(focus, selected) },
        )
    }
}

@Composable
private fun BarGlyph(vm: HomeViewModel, slot: Int, @DrawableRes icon: Int) {
    val focus by slotFocus(vm, slot)
    val pressed = vm.pressedKey == HomeViewModel.barKey(slot)
    val painter = painterResource(icon)
    Box(
        Modifier
            .size(HomeDims.BarItemH)
            .barPill({ focus }, { 0f }, { 0f }, pressed)
            .drawBehind {
                val g = HomeDims.BarGlyph.toPx()
                translate((size.width - g) / 2, (size.height - g) / 2) {
                    with(painter) { draw(Size(g, g), colorFilter = ColorFilter.tint(labelColor(focus, 0f))) }
                }
            },
    )
}

/** Black on the opaque selected/focused pill, secondary on the translucent bar. */
private fun labelColor(focus: Float, selected: Float): Color =
    lerp(TvColors.SecondaryLabel, TvColors.FocusedLabel, maxOf(focus, selected).coerceIn(0f, 1f))

/**
 * HIG tab bar states, drawn rather than laid out: the bar is translucent and only the selected
 * tab is opaque; the focused item is a white pill that lifts; with the bar focused, the selected
 * tab casts a shadow.
 */
private fun Modifier.barPill(focus: () -> Float, selected: () -> Float, barFocused: () -> Float, pressed: Boolean): Modifier = this
    .graphicsLayer {
        val f = focus()
        val s = 1f + (TvMotion.BUTTON_FOCUS_SCALE - 1f) * f
        val dip = if (pressed) PRESS_DIP else 1f
        scaleX = s * dip
        scaleY = s * dip
        shadowElevation = (10f * maxOf(f, selected() * barFocused())).dp.toPx()
        shape = TvShapes.Capsule
        clip = false
    }
    .drawBehind {
        val r = CornerRadius(size.height / 2)
        val sel = selected()
        if (sel > 0.01f) drawRoundRect(Palette.SelectedTab, cornerRadius = r, alpha = sel)
        val f = focus()
        if (f > 0.01f) drawRoundRect(TvColors.FocusedFill, cornerRadius = r, alpha = f.coerceIn(0f, 1f))
    }

/** The time, top-right, ticking once a minute (one frame per minute, nothing in between). */
@Composable
private fun Clock(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
    val format = remember(pattern) { SimpleDateFormat(pattern, Locale.US) }
    var now by remember { mutableStateOf(format.format(Date())) }
    LaunchedEffect(format) {
        while (true) {
            now = format.format(Date())
            delay(MINUTE_MS - System.currentTimeMillis() % MINUTE_MS + 50)
        }
    }
    BasicText(now, style = Type.Clock, modifier = modifier, maxLines = 1)
}

private const val MINUTE_MS = 60_000L
