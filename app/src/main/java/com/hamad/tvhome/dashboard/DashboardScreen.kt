package com.hamad.tvhome.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hamad.tvhome.R
import com.hamad.tvhome.design.TvDims

/*
 * Direction: photographic ground (Apple Aerial video) · no cards, no borders · white-only type with
 * opacity hierarchy (100 / 78 / 58 / 40) · one dominant element = next prayer · the day as a quiet
 * timeline · colour only where semantic (SF-Symbol weather, price up/down) · zero motion.
 */
private object Ink {
    val Primary = Color.White
    val Secondary = Color.White.copy(alpha = 0.78f)
    val Tertiary = Color.White.copy(alpha = 0.58f)
    val Passed = Color.White.copy(alpha = 0.40f)
    val Track = Color.White.copy(alpha = 0.22f)
    val Up = Color(0xFF30D158)
    val Down = Color(0xFFFF453A)
    val ScrimTop = Color.Black.copy(alpha = 0.55f)
    val ScrimBottom = Color.Black.copy(alpha = 0.78f)
    val TextShadow = Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 2f), 12f)
    /** Dark Liquid Glass: neutral, never tinted — the footage supplies the colour. */
    val GlassBody = Color.Black.copy(alpha = 0.30f)
    val GlassSheen = Color.White.copy(alpha = 0.07f)
    val GlassRimTop = Color.White.copy(alpha = 0.22f)
    val GlassRimBottom = Color.White.copy(alpha = 0.05f)
}

private val PlatterShape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)

private fun Modifier.glassPlatter(): Modifier = this
    .background(Ink.GlassBody, PlatterShape)
    .background(Brush.verticalGradient(listOf(Ink.GlassSheen, Color.Transparent)), PlatterShape)
    .border(1.dp, Brush.verticalGradient(listOf(Ink.GlassRimTop, Ink.GlassRimBottom)), PlatterShape)

private object Faces {
    val Arabic = FontFamily(
        Font(R.font.sf_arabic_regular, FontWeight.Normal),
        Font(R.font.sf_arabic_medium, FontWeight.Medium),
        Font(R.font.sf_arabic_semibold, FontWeight.SemiBold),
        Font(R.font.sf_arabic_bold, FontWeight.Bold),
    )
}

private fun type(size: TextUnit, weight: FontWeight, color: Color) =
    TextStyle(fontFamily = Faces.Arabic, fontWeight = weight, fontSize = size, color = color, shadow = Ink.TextShadow)

private object Scale {
    val Hero = type(72.sp, FontWeight.SemiBold, Ink.Primary)
    val HeroTime = type(30.sp, FontWeight.Medium, Ink.Secondary)
    val HeroCountdown = type(22.sp, FontWeight.Medium, Ink.Primary)
    val Eyebrow = type(14.sp, FontWeight.Medium, Ink.Tertiary)
    val Clock = type(44.sp, FontWeight.Medium, Ink.Primary)
    val Meridiem = type(18.sp, FontWeight.Medium, Ink.Secondary)
    val Line = type(15.sp, FontWeight.Medium, Ink.Secondary)
    val Small = type(13.sp, FontWeight.Medium, Ink.Tertiary)
    val Temp = type(52.sp, FontWeight.Normal, Ink.Primary)
    val City = type(15.sp, FontWeight.SemiBold, Ink.Secondary)
    val DayName = type(11.5.sp, FontWeight.Medium, Ink.Tertiary)
    val DayTemp = type(13.sp, FontWeight.SemiBold, Ink.Secondary)
    val SlotName = type(14.sp, FontWeight.Medium, Ink.Secondary)
    val SlotTime = type(18.sp, FontWeight.SemiBold, Ink.Secondary)
}

data class DashboardState(
    val moment: DashboardMoment,
    val weather: List<CityWeather>,
    val btc: BtcQuote?,
    /** Burn-in guard: the whole layout drifts a few dp each minute. */
    val shift: Pair<Dp, Dp>,
)

/** Prayer-times idle screen drawn over the Aerial video. RTL, Arabic-Indic digits. */
@Composable
fun DashboardScreen(state: DashboardState) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().height(220.dp).background(Brush.verticalGradient(listOf(Ink.ScrimTop, Color.Transparent))))
            Box(
                Modifier.fillMaxWidth().height(340.dp).align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Ink.ScrimBottom))),
            )
            Box(
                Modifier.fillMaxSize()
                    .offset(state.shift.first, state.shift.second)
                    .padding(horizontal = TvDims.SafeH, vertical = TvDims.SafeV),
            ) {
                ClockAndDates(state, Modifier.align(Alignment.TopStart))
                if (state.weather.isNotEmpty()) Weather(state.weather, Modifier.align(Alignment.TopEnd))
                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
                    NextPrayer(state.moment)
                    Spacer(Modifier.height(26.dp))
                    Box(Modifier.fillMaxWidth().glassPlatter().padding(horizontal = 8.dp, vertical = 18.dp)) {
                        DayTimeline(state.moment)
                    }
                }
            }
        }
    }
}

@Composable
private fun ClockAndDates(state: DashboardState, modifier: Modifier) {
    val m = state.moment
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            BasicText(ArabicText.clock(m.minuteOfDay), style = Scale.Clock)
            Spacer(Modifier.width(8.dp))
            BasicText(ArabicText.meridiem(m.minuteOfDay), style = Scale.Meridiem, modifier = Modifier.padding(bottom = 8.dp))
        }
        BasicText(if (m.hijri.isNotEmpty()) "${m.gregorian} · ${m.hijri}" else m.gregorian, style = Scale.Line)
        Spacer(Modifier.height(4.dp))
        m.daysToRamadan?.let { BasicText("رمضان بعد ${ArabicText.days(it)}", style = Scale.Small) }
        state.btc?.let { q ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText("بيتكوين  ${ArabicText.grouped(q.usd)} $", style = Scale.Small)
                Spacer(Modifier.width(8.dp))
                val down = q.change24h < 0
                BasicText(
                    "${if (down) "▼" else "▲"} ${ArabicText.oneDecimal(q.change24h)}٪",
                    style = Scale.Small.copy(color = if (down) Ink.Down else Ink.Up),
                )
            }
        }
    }
}

@Composable
private fun Weather(cities: List<CityWeather>, modifier: Modifier) {
    val main = cities.first()
    val today = main.days.firstOrNull()
    Column(modifier, horizontalAlignment = Alignment.End) {
        BasicText(main.name, style = Scale.City)
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlyphIcon(weatherGlyph(main.currentCode, main.isDay), 34.dp)
            Spacer(Modifier.width(10.dp))
            BasicText(ArabicText.degrees(main.currentTemp), style = Scale.Temp)
        }
        BasicText(condition(main.currentCode), style = Scale.Line)
        if (today != null) {
            BasicText("العظمى ${ArabicText.degrees(today.max)}   الصغرى ${ArabicText.degrees(today.min)}", style = Scale.Small)
        }
        cities.drop(1).forEach { c ->
            BasicText("${c.name}  ${ArabicText.degrees(c.currentTemp)}", style = Scale.Small)
        }
        Spacer(Modifier.height(16.dp))
        Row {
            main.days.drop(1).take(FORECAST_DAYS).forEach { d ->
                Column(
                    Modifier.width(FORECAST_COL),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    BasicText(ArabicText.weekday(d.weekday), style = Scale.DayName)
                    GlyphIcon(weatherGlyph(d.code), 20.dp)
                    BasicText(ArabicText.degrees(d.max), style = Scale.DayTemp)
                }
            }
        }
    }
}

@Composable
private fun NextPrayer(m: DashboardMoment) {
    val now = m.minutesSincePrevious < NOW_WINDOW_MIN
    Column {
        BasicText(if (now) "حان الآن وقت صلاة" else "الصلاة القادمة", style = Scale.Eyebrow)
        Row(verticalAlignment = Alignment.Bottom) {
            BasicText(if (now) m.previous.arabic else m.next.arabic, style = Scale.Hero)
            Spacer(Modifier.width(20.dp))
            val at = if (now) m.prayers.firstOrNull { it.first == m.previous }?.second ?: m.nextAt else m.nextAt
            BasicText(ArabicText.time(at), style = Scale.HeroTime, modifier = Modifier.padding(bottom = 16.dp))
        }
        BasicText(
            if (now) "${m.next.arabic} بعد ${ArabicText.duration(m.minutesToNext)}" else "بعد ${ArabicText.duration(m.minutesToNext)}",
            style = Scale.HeroCountdown,
        )
    }
}

/**
 * The five prayers on one hairline: passed slots dim, the next slot full white with a larger dot,
 * and the track filled up to "now" — read right-to-left like the day itself.
 */
@Composable
private fun DayTimeline(m: DashboardMoment) {
    val slots = m.prayers
    val nextIndex = slots.indexOfFirst { it.first == m.next }.let { if (it < 0) slots.size else it }
    val progress = timelineProgress(m)
    Column(Modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(14.dp)) {
            val y = size.height / 2
            val step = size.width / slots.size
            val first = step / 2
            val last = size.width - step / 2
            // RTL: slot 0 sits at the right edge.
            fun xOf(slot: Float) = size.width - (first + slot * step)
            drawLine(Ink.Track, Offset(xOf(0f), y), Offset(xOf((slots.size - 1).toFloat()), y), 2.dp.toPx())
            drawLine(Ink.Primary, Offset(xOf(0f), y), Offset(xOf(progress), y), 2.dp.toPx())
            if (progress > 0f && progress < slots.size - 1) {
                drawCircle(Ink.Primary, 2.5.dp.toPx(), Offset(xOf(progress), y))
            }
            slots.indices.forEach { i ->
                val c = Offset(xOf(i.toFloat()), y)
                when {
                    i == nextIndex -> {
                        drawCircle(Ink.Primary.copy(alpha = 0.25f), 9.dp.toPx(), c)
                        drawCircle(Ink.Primary, 5.dp.toPx(), c)
                    }
                    i < nextIndex -> drawCircle(Ink.Primary, 3.5.dp.toPx(), c)
                    else -> drawCircle(Ink.Track, 3.5.dp.toPx(), c)
                }
            }
            check(last >= first)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            slots.forEachIndexed { i, (prayer, at) ->
                val tint = when {
                    i == nextIndex -> Ink.Primary
                    i < nextIndex -> Ink.Passed
                    else -> Ink.Secondary
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    BasicText(prayer.arabic, style = Scale.SlotName.copy(color = tint))
                    BasicText(
                        ArabicText.time(at),
                        style = Scale.SlotTime.copy(color = tint, fontWeight = if (i == nextIndex) FontWeight.Bold else FontWeight.SemiBold),
                    )
                }
            }
        }
    }
}

/** Fractional slot position of "now" between the previous and next prayer (0 = Fajr … 4 = Isha). */
private fun timelineProgress(m: DashboardMoment): Float {
    val slots = m.prayers
    val now = m.minuteOfDay
    if (now < slots.first().second) return 0f
    for (i in 0 until slots.size - 1) {
        val a = slots[i].second
        val b = slots[i + 1].second
        if (now in a until b) return i + (now - a).toFloat() / (b - a)
    }
    return (slots.size - 1).toFloat()
}

private fun condition(code: Int): String = when (code) {
    0 -> "صافٍ"
    1 -> "صافٍ غالبًا"
    2 -> "غائم جزئيًا"
    3 -> "غائم"
    45, 48 -> "ضباب"
    in 51..57 -> "رذاذ"
    in 61..67 -> "مطر"
    in 71..77, 85, 86 -> "ثلج"
    in 80..82 -> "زخات مطر"
    in 95..99 -> "عاصفة رعدية"
    else -> "غائم"
}

private const val FORECAST_DAYS = 5
private val FORECAST_COL = 52.dp
private const val NOW_WINDOW_MIN = 20
