package com.hamad.tvhome.ui

import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hamad.tvhome.design.TvColors
import com.hamad.tvhome.design.TvDims
import com.hamad.tvhome.design.TvMotion
import com.hamad.tvhome.design.TvType
import com.hamad.tvhome.design.TvType.emphasized
import com.hamad.tvhome.design.TvType.overArt

/**
 * Home-screen geometry per HIG › Layout (five-column grid), halved into dp (the box is 320 dpi,
 * so 1 dp = 2 px): 320 pt icons with 40 pt gutters exactly fill the 80 pt side safe area
 * (5·160 + 4·20 + 2·40 = 960 dp), rows ≥100 pt apart. Colours, type and springs come from the
 * shared design kit.
 */
object Dims {
    val TileW: Dp = TvDims.IconW
    val TileH: Dp = TvDims.IconH
    val Gap: Dp = TvDims.GridHSpacing
    val GridLeft: Dp = TvDims.SafeH
    val TileRadius: Dp = TvDims.IconCorner

    /** The label sits inside the HIG's vertical spacing, clear of the focused icon's growth. */
    val LabelTop: Dp = 15.dp
    val Pitch: Dp = TvDims.IconH + TvDims.GridVSpacing

    /** Dock icon top: focused label bottom lands on the 60 pt bottom safe-area line. */
    val DockTop: Dp = 380.dp
    val ScrolledTop: Dp = TvDims.SafeV + 4.dp

    /** How far the dock sinks out of view while the Top Shelf itself is focused. */
    val ShelfDrop: Dp = 170.dp

    /** Rows below the dock wait fully off-screen until focus scrolls into the grid. */
    val LowerRowsDrop: Dp = 40.dp

    val ShelfLeft: Dp = TvDims.SafeH
    val ShelfBottom: Dp = 56.dp
    val LogoW: Dp = 300.dp
    val LogoH: Dp = 96.dp
    val ButtonGap: Dp = 16.dp
    val ButtonPadH: Dp = 24.dp
    val PlayGlyph: Dp = 10.dp
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

/** Every home-screen string uses a HIG tvOS text style unchanged; only colour/shadow vary. */
object Type {
    val ShelfAttribution = TvType.Caption1.overArt()
    val ShelfTitle = TvType.Title1.emphasized().overArt()
    val ShelfMeta = TvType.Caption1.copy(color = TvColors.Label).overArt()
    val ShelfOverview = TvType.Callout.overArt()
    val ShelfButton = TvType.Body
    val TileLabel = TvType.Caption1.copy(color = TvColors.Label).overArt()
    val SheetTitle = TvType.Title3
    val SheetButton = TvType.Body
    val Hint = TvType.Caption2.copy(color = Palette.Hint).overArt()
}
