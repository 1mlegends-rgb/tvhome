package com.hamad.tvhome.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hamad.tvhome.R

object DashColors {
    val BgTop = Color(0xFF0C1438)
    val BgBottom = Color(0xFF070B22)
    val Card = Color(0xFF131D4C)
    val CardFill = Color(0xC7131D4C)
    val Border = Color(0xB33A56B2)
    val Highlight = Color(0xFF0B1235)
    val HighlightBorder = Color(0xB3E6ECFF)
    val Label = Color(0xFFF3F6FF)
    val Secondary = Color(0xFFAAB6DF)
    val Accent = Color(0xFF9DC1FF)
    val Bitcoin = Color(0xFFF7931A)
    val Down = Color(0xFFF0629B)
    val Up = Color(0xFF4CD98A)
}

private object DashFonts {
    val Arabic = FontFamily(
        Font(R.font.sf_arabic_regular, FontWeight.Normal),
        Font(R.font.sf_arabic_medium, FontWeight.Medium),
        Font(R.font.sf_arabic_semibold, FontWeight.SemiBold),
        Font(R.font.sf_arabic_bold, FontWeight.Bold),
    )
    val Rounded = FontFamily(Font(R.font.sf_arabic_rounded_semibold, FontWeight.SemiBold))
}

private fun text(size: TextUnit, weight: FontWeight = FontWeight.Medium, color: Color = DashColors.Label, family: FontFamily = DashFonts.Arabic) =
    TextStyle(fontFamily = family, fontWeight = weight, fontSize = size, color = color, textAlign = TextAlign.Start)

data class DashboardState(
    val moment: DashboardMoment,
    val weather: List<CityWeather>,
    val btc: BtcQuote?,
    /** Burn-in guard: the whole layout drifts a few dp each minute. */
    val shift: Pair<Dp, Dp>,
)

/** The idle dashboard: clock + dates, next prayer, the five prayers, two-city weekly weather. RTL. */
@Composable
fun DashboardScreen(state: DashboardState) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(DashColors.BgTop, DashColors.BgBottom))),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .offset(state.shift.first, state.shift.second)
                    .padding(horizontal = 48.dp, vertical = 30.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    ClockBlock(state, Modifier.weight(1f).padding(top = 16.dp))
                    NextPrayerCard(state.moment, Modifier.width(300.dp))
                }
                Spacer(Modifier.height(26.dp))
                PrayerRow(state.moment)
                if (state.weather.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    WeatherCard(state.weather)
                }
            }
        }
    }
}

@Composable
private fun GlassCard(modifier: Modifier = Modifier, padding: Dp = 18.dp, content: @Composable BoxScope.() -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .background(DashColors.CardFill, shape)
            .border(1.dp, DashColors.Border, shape)
            .padding(padding),
        content = content,
    )
}

@Composable
private fun ClockBlock(state: DashboardState, modifier: Modifier) {
    val m = state.moment
    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            BasicText(ArabicText.clock(m.minuteOfDay), style = text(70.sp, FontWeight.Normal, DashColors.Accent))
            Spacer(Modifier.width(10.dp))
            BasicText(ArabicText.meridiem(m.minuteOfDay), style = text(22.sp, FontWeight.Medium, DashColors.Accent), modifier = Modifier.padding(bottom = 14.dp))
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlyphIcon(Glyph.Calendar, 16.dp, DashColors.Secondary)
            Spacer(Modifier.width(8.dp))
            val dates = if (m.hijri.isNotEmpty()) "${m.gregorian}  •  ${m.hijri}" else m.gregorian
            BasicText(dates, style = text(16.sp))
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            m.daysToRamadan?.let {
                GlyphIcon(Glyph.Crescent, 16.dp, DashColors.Label)
                Spacer(Modifier.width(8.dp))
                BasicText("باقي على رمضان ${ArabicText.days(it)}", style = text(14.sp))
                Spacer(Modifier.width(30.dp))
            }
            state.btc?.let { BitcoinLine(it) }
        }
    }
}

@Composable
private fun BitcoinLine(q: BtcQuote) {
    Box(Modifier.size(16.dp).background(DashColors.Bitcoin, CircleShape), contentAlignment = Alignment.Center) {
        BasicText("₿", style = text(10.sp, FontWeight.Bold, Color.White))
    }
    Spacer(Modifier.width(8.dp))
    BasicText("${ArabicText.grouped(q.usd)} دولار", style = text(14.sp))
    Spacer(Modifier.width(10.dp))
    val down = q.change24h < 0
    BasicText(
        "${if (down) "▼" else "▲"} ${ArabicText.oneDecimal(q.change24h)}٪",
        style = text(13.sp, FontWeight.Medium, if (down) DashColors.Down else DashColors.Up),
    )
}

@Composable
private fun NextPrayerCard(m: DashboardMoment, modifier: Modifier) {
    GlassCard(modifier, padding = 18.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlyphIcon(Glyph.Mosque, 17.dp, DashColors.Secondary)
                Spacer(Modifier.width(8.dp))
                BasicText("الصلاة القادمة", style = text(13.sp, FontWeight.Medium, DashColors.Secondary))
            }
            Row(verticalAlignment = Alignment.Bottom) {
                BasicText(m.next.arabic, style = text(27.sp, FontWeight.Bold))
                Spacer(Modifier.width(14.dp))
                BasicText(ArabicText.time(m.nextAt), style = text(18.sp, FontWeight.Medium, DashColors.Label.copy(alpha = 0.85f)), modifier = Modifier.padding(bottom = 4.dp))
            }
            BasicText("باقي ${ArabicText.duration(m.minutesToNext)}", style = text(19.sp, FontWeight.SemiBold, DashColors.Accent))
            BasicText("مضى على ${m.previous.arabic} ${ArabicText.duration(m.minutesSincePrevious)}", style = text(12.5.sp, FontWeight.Normal, DashColors.Secondary))
        }
    }
}

@Composable
private fun PrayerRow(m: DashboardMoment) {
    GlassCard(Modifier.fillMaxWidth().height(92.dp), padding = 8.dp) {
        Row(Modifier.fillMaxSize()) {
            m.prayers.forEach { (prayer, at) ->
                val active = prayer == m.next
                PrayerCell(prayer.arabic, ArabicText.time(at), active)
            }
        }
    }
}

@Composable
private fun RowScope.PrayerCell(name: String, time: String, active: Boolean) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .weight(1f)
            .fillMaxSize()
            .then(if (active) Modifier.background(DashColors.Highlight, shape).border(1.5.dp, DashColors.HighlightBorder, shape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BasicText(name, style = text(14.5.sp, FontWeight.Medium, DashColors.Label.copy(alpha = 0.88f)))
            BasicText(time, style = text(21.sp, FontWeight.SemiBold))
        }
    }
}

@Composable
private fun WeatherCard(cities: List<CityWeather>) {
    GlassCard(Modifier.fillMaxWidth(), padding = 14.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val days = cities.first().days
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(CITY_WEIGHT))
                days.forEachIndexed { i, d ->
                    BasicText(
                        if (i == 0) "اليوم" else ArabicText.weekday(d.weekday),
                        style = text(12.5.sp, FontWeight.Medium, DashColors.Secondary),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            cities.forEach { CityRow(it) }
        }
    }
}

@Composable
private fun CityRow(c: CityWeather) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(CITY_WEIGHT), verticalAlignment = Alignment.CenterVertically) {
            BasicText(c.name, style = text(17.sp, FontWeight.SemiBold))
            Spacer(Modifier.width(14.dp))
            GlyphIcon(weatherGlyph(c.currentCode, c.isDay), 18.dp, if (c.isDay) DashColors.Label else DashColors.Accent)
            Spacer(Modifier.width(6.dp))
            BasicText(ArabicText.degrees(c.currentTemp), style = text(17.sp, FontWeight.Medium))
        }
        c.days.forEach { d ->
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                GlyphIcon(weatherGlyph(d.code), 15.dp)
                Spacer(Modifier.width(5.dp))
                BasicText(ArabicText.degrees(d.max), style = text(14.sp, FontWeight.SemiBold))
                Spacer(Modifier.width(5.dp))
                BasicText(ArabicText.degrees(d.min), style = text(11.5.sp, FontWeight.Medium, DashColors.Secondary))
            }
        }
    }
}

private const val CITY_WEIGHT = 1.35f
