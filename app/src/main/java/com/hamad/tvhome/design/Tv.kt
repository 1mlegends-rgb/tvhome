package com.hamad.tvhome.design

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hamad.tvhome.R

/**
 * The shared tvOS design kit. Every surface in the app (home, settings, search, control center,
 * app switcher, screensaver) builds on these tokens — never hardcode colours, sizes or springs.
 *
 * Unit rule: the box is 1920×1080 @ 320 dpi, so 1 dp = 2 px, and a tvOS point at 1080p is 0.5 dp.
 * Every value below is the tvOS HIG number halved.
 */
object TvColors {
    // Dark appearance (tvOS default). Labels per UIKit's dark label colours.
    val Label = Color(0xFFFFFFFF)
    val SecondaryLabel = Color(0x99EBEBF5)
    val TertiaryLabel = Color(0x4DEBEBF5)
    val FocusedFill = Color(0xFFFFFFFF)
    val FocusedLabel = Color(0xFF000000)
    val FocusedSecondaryLabel = Color(0x993C3C43)
    val IdleFill = Color(0x1FFFFFFF)
    val Separator = Color(0x33FFFFFF)

    val Blue = Color(0xFF0A84FF)
    val Red = Color(0xFFFF453A)
    val Green = Color(0xFF30D158)
    val Orange = Color(0xFFFF9F0A)
    val Yellow = Color(0xFFFFD60A)

    /** Approximations of UIBlurEffect materials (API 28 has no RenderEffect blur). */
    val MaterialUltraThin = Color(0x8C1C1C1E)
    val MaterialThin = Color(0xB31C1C1E)
    val MaterialRegular = Color(0xD11C1C1E)
    val MaterialThick = Color(0xEB1C1C1E)
    val Scrim = Color(0xB3000000)

    /** Full-screen app background used by Settings / Search. */
    val AppBackground = Brush.verticalGradient(
        0f to Color(0xFF2A2A2E),
        1f to Color(0xFF0B0B0D),
    )
}

object TvFonts {
    val Display = FontFamily(
        Font(R.font.sf_display_bold, FontWeight.Bold),
        Font(R.font.sf_display_semibold, FontWeight.SemiBold),
    )
    val Text = FontFamily(
        Font(R.font.sf_text_regular, FontWeight.Normal),
        Font(R.font.sf_text_medium, FontWeight.Medium),
        Font(R.font.sf_text_semibold, FontWeight.SemiBold),
        Font(R.font.sf_text_bold, FontWeight.Bold),
    )
}

/** tvOS Dynamic Type (Large), halved: Title1 76pt → 38sp, Body 29pt → 14.5sp, … */
object TvType {
    private val shadow = Shadow(Color(0x59000000), Offset(0f, 2f), 10f)

    val Title1 = TextStyle(fontFamily = TvFonts.Display, fontWeight = FontWeight.Bold, fontSize = 38.sp, color = TvColors.Label)
    val Title2 = TextStyle(fontFamily = TvFonts.Display, fontWeight = FontWeight.Bold, fontSize = 28.5.sp, color = TvColors.Label)
    val Title3 = TextStyle(fontFamily = TvFonts.Display, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, color = TvColors.Label)
    val Headline = TextStyle(fontFamily = TvFonts.Text, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, color = TvColors.Label)
    val Body = TextStyle(fontFamily = TvFonts.Text, fontWeight = FontWeight.Medium, fontSize = 14.5.sp, color = TvColors.Label)
    val Callout = TextStyle(fontFamily = TvFonts.Text, fontWeight = FontWeight.Normal, fontSize = 15.5.sp, color = TvColors.Label)
    val Subhead = TextStyle(fontFamily = TvFonts.Text, fontWeight = FontWeight.Normal, fontSize = 14.5.sp, color = TvColors.SecondaryLabel)
    val Footnote = TextStyle(fontFamily = TvFonts.Text, fontWeight = FontWeight.Normal, fontSize = 11.5.sp, color = TvColors.SecondaryLabel)
    val Caption1 = TextStyle(fontFamily = TvFonts.Text, fontWeight = FontWeight.Medium, fontSize = 12.5.sp, color = TvColors.SecondaryLabel)
    val Caption2 = TextStyle(fontFamily = TvFonts.Text, fontWeight = FontWeight.Medium, fontSize = 11.5.sp, color = TvColors.SecondaryLabel)

    /** For text laid directly over artwork. */
    fun TextStyle.overArt(): TextStyle = copy(shadow = shadow)
}

object TvDims {
    /** tvOS safe area: 90pt horizontal, 60pt vertical. */
    val SafeH = 45.dp
    val SafeV = 30.dp
    val ButtonH = 33.dp
    val RowH = 33.dp
    val RowGap = 4.dp
    val CornerSmall = 6.dp
    val Corner = 10.dp
    val CornerLarge = 14.dp
}

object TvShapes {
    val Small = RoundedCornerShape(TvDims.CornerSmall)
    val Regular = RoundedCornerShape(TvDims.Corner)
    val Large = RoundedCornerShape(TvDims.CornerLarge)
    val Capsule = RoundedCornerShape(percent = 50)
}

object TvMotion {
    /** Focus lift for buttons and rows. Icons use their own larger lift in the home screen. */
    const val BUTTON_FOCUS_SCALE = 1.08f
    const val ROW_FOCUS_SCALE = 1.03f
    const val CARD_FOCUS_SCALE = 1.1f

    fun <T> focus() = spring<T>(dampingRatio = 0.74f, stiffness = 420f)
    fun <T> move() = spring<T>(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)
    fun <T> panel() = spring<T>(dampingRatio = 0.9f, stiffness = 300f)
}
