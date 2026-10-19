package com.hamad.tvhome.ui

import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hamad.tvhome.design.TvColors
import com.hamad.tvhome.design.TvDims
import com.hamad.tvhome.design.TvMotion
import com.hamad.tvhome.design.TvType
import com.hamad.tvhome.design.TvType.overArt

/**
 * Home-screen geometry, measured on tvOS at 1920×1080 and halved into dp (the box is 320 dpi,
 * so 1 dp = 2 px). Five 5:3 icons per row fill the 90 pt safe area; colours, type and springs
 * come from the shared design kit.
 */
object Dims {
    val TileW: Dp = 154.dp
    val TileH: Dp = 92.dp
    val Gap: Dp = 24.dp
    val GridLeft: Dp = 47.dp
    val Pitch: Dp = 138.dp
    val DockTop: Dp = 392.dp
    val ScrolledTop: Dp = 46.dp
    val TileRadius: Dp = 8.dp
    val LabelTop: Dp = 14.dp

    /** How far the dock sinks out of view while the Top Shelf itself is focused. */
    val ShelfDrop: Dp = 160.dp

    /** Rows below the dock wait fully off-screen until focus scrolls into the grid. */
    val LowerRowsDrop: Dp = 70.dp

    val ShelfLeft: Dp = GridLeft
    val ShelfBottom: Dp = 62.dp
    val LogoW: Dp = 300.dp
    val LogoH: Dp = 110.dp
    val DotsBottom: Dp = TvDims.SafeV
    val Dot: Dp = 5.dp
    val DotGap: Dp = 7.dp
    val PageSlide: Dp = 28.dp

    val SheetButtonW: Dp = 300.dp
    val SheetButtonH: Dp = 40.dp
    val SheetButtonRadius: Dp = 10.dp

    fun tileX(col: Int): Dp = GridLeft + (TileW + Gap) * col
}

object Shapes {
    val Tile = RoundedCornerShape(Dims.TileRadius)
    val SheetButton = RoundedCornerShape(Dims.SheetButtonRadius)
}

object Motion {
    const val FOCUS_SCALE = 1.16f
    const val EDIT_SCALE = 1.2f
    const val PRESS_SCALE = 1.07f
    const val CROSSFADE_MS = 420
    const val ART_FADE_MS = 700
    const val PAGE_MS = 380
    const val SHELF_AUTOPLAY_MS = 8_000L

    fun <T> focus() = TvMotion.focus<T>()
    fun <T> move() = TvMotion.move<T>()
    fun <T> scroll() = spring<T>(dampingRatio = 0.92f, stiffness = 260f)
}

object Palette {
    val Label = TvColors.Label
    val Secondary = TvColors.SecondaryLabel
    val Hint = Color(0xB3FFFFFF)
    val ButtonIdle = TvColors.IdleFill
    val ButtonFocused = TvColors.FocusedFill
    val OnFocused = TvColors.FocusedLabel
    val Destructive = TvColors.Red
    val DestructiveOnFocused = Color(0xFFE5342A)
    val Scrim = Color(0xE0000000)
    val Dot = Color(0x59FFFFFF)
}

object Type {
    val ShelfTitle = TvType.Title1.overArt()
    val ShelfMeta = TvType.Caption1.copy(fontWeight = FontWeight.SemiBold, color = Color(0xCCFFFFFF)).overArt()
    val ShelfOverview = TvType.Callout.copy(color = Color(0xE6FFFFFF), lineHeight = 21.sp).overArt()
    val TileLabel = TvType.Caption1.copy(color = TvColors.Label).overArt()
    val SheetTitle = TvType.Title3
    val SheetButton = TvType.Body.copy(fontSize = 15.sp)
    val Hint = TvType.Caption2.copy(color = Palette.Hint).overArt()
}
