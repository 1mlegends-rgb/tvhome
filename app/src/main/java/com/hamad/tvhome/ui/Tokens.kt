package com.hamad.tvhome.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hamad.tvhome.R

/**
 * The tvOS home screen measured at 1920×1080 and halved into dp (the box is 320 dpi, so
 * 1 dp = 2 px = 1 tvOS point at 1080p / 2). Five 5:3 icons per row inside the 90 pt safe area.
 */
object Dims {
    val TileW: Dp = 154.dp
    val TileH: Dp = 92.dp
    val Gap: Dp = 24.dp
    val GridLeft: Dp = 47.dp
    val Pitch: Dp = 128.dp
    val DockTop: Dp = 392.dp
    val ScrolledTop: Dp = 46.dp
    val ShelfDrop: Dp = 30.dp
    val TileRadius: Dp = 8.dp
    val LabelTop: Dp = 14.dp

    val HeroLeft: Dp = 47.dp
    val HeroTop: Dp = 56.dp
    val HeroW: Dp = 336.dp
    val HeroH: Dp = 189.dp
    val HeroRadius: Dp = 12.dp

    val SheetButtonW: Dp = 300.dp
    val SheetButtonH: Dp = 40.dp
    val SheetButtonRadius: Dp = 10.dp

    fun tileX(col: Int): Dp = GridLeft + (TileW + Gap) * col
}

object Shapes {
    val Tile = RoundedCornerShape(Dims.TileRadius)
    val Hero = RoundedCornerShape(Dims.HeroRadius)
    val SheetButton = RoundedCornerShape(Dims.SheetButtonRadius)
}

object Motion {
    const val FOCUS_SCALE = 1.16f
    const val EDIT_SCALE = 1.2f
    const val PRESS_SCALE = 1.07f
    const val DIM_ALPHA = 0.5f
    const val CROSSFADE_MS = 420

    fun <T> focus() = spring<T>(dampingRatio = 0.74f, stiffness = 420f)
    fun <T> move() = spring<T>(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)
    fun <T> scroll() = spring<T>(dampingRatio = 0.92f, stiffness = 260f)
}

object Palette {
    val Label = Color(0xF2FFFFFF)
    val Secondary = Color(0x99FFFFFF)
    val Hint = Color(0xB3FFFFFF)
    val ButtonIdle = Color(0x24FFFFFF)
    val ButtonFocused = Color.White
    val OnFocused = Color(0xFF1C1C1E)
    val Destructive = Color(0xFFFF453A)
    val DestructiveOnFocused = Color(0xFFE5342A)
    val Scrim = Color(0xE0000000)
}

object Type {
    private val display = FontFamily(
        Font(R.font.sf_display_bold, FontWeight.Bold),
        Font(R.font.sf_display_semibold, FontWeight.SemiBold),
    )
    private val text = FontFamily(
        Font(R.font.sf_text_regular, FontWeight.Normal),
        Font(R.font.sf_text_medium, FontWeight.Medium),
        Font(R.font.sf_text_semibold, FontWeight.SemiBold),
    )
    private val lift = Shadow(Color(0x66000000), Offset(0f, 2f), 10f)

    val HeroTitle = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 34.sp, color = Color.White, shadow = lift, letterSpacing = 0.2.sp)
    val HeroCaption = TextStyle(fontFamily = text, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = Palette.Secondary, shadow = lift)
    val TileLabel = TextStyle(fontFamily = text, fontWeight = FontWeight.Medium, fontSize = 12.5.sp, color = Palette.Label, shadow = lift)
    val SheetTitle = TextStyle(fontFamily = display, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, color = Color.White)
    val SheetButton = TextStyle(fontFamily = text, fontWeight = FontWeight.Medium, fontSize = 15.sp)
    val Hint = TextStyle(fontFamily = text, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = Palette.Hint, shadow = lift)
}
