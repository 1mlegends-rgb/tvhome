package com.hamad.tvhome.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/** Line glyphs drawn in code (no icon font on the box). Each fits a square [size]. */
enum class Glyph { Sun, Moon, PartlyCloudy, Cloud, Fog, Rain, Snow, Thunder, Mosque, Calendar, Crescent }

/** WMO weather code → glyph. Night swaps the clear sun for a moon. */
fun weatherGlyph(code: Int, isDay: Boolean = true): Glyph = when (code) {
    0 -> if (isDay) Glyph.Sun else Glyph.Moon
    1, 2 -> Glyph.PartlyCloudy
    3 -> Glyph.Cloud
    45, 48 -> Glyph.Fog
    in 51..67, in 80..82 -> Glyph.Rain
    in 71..77, 85, 86 -> Glyph.Snow
    in 95..99 -> Glyph.Thunder
    else -> Glyph.Cloud
}

@Composable
fun GlyphIcon(glyph: Glyph, size: Dp, color: Color = Color.White, occlude: Color = DashColors.Card, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val stroke = Stroke(width = this.size.minDimension * 0.085f, cap = StrokeCap.Round)
        when (glyph) {
            Glyph.Sun -> sun(Offset(center.x, center.y), this.size.minDimension * 0.2f, color, stroke)
            Glyph.Moon -> moon(color)
            Glyph.PartlyCloudy -> {
                sun(Offset(this.size.width * 0.38f, this.size.height * 0.38f), this.size.minDimension * 0.15f, color, stroke)
                cloud(color, occlude, stroke, dx = 0.08f, dy = 0.1f, scale = 0.8f)
            }
            Glyph.Cloud -> cloud(color, occlude, stroke)
            Glyph.Fog -> {
                cloud(color, occlude, stroke, dy = -0.08f, scale = 0.85f)
                line(color, stroke, 0.22f, 0.86f, 0.78f)
            }
            Glyph.Rain -> {
                cloud(color, occlude, stroke, dy = -0.1f, scale = 0.85f)
                for (x in listOf(0.36f, 0.52f, 0.68f)) {
                    drawLine(color, Offset(this.size.width * x, this.size.height * 0.78f), Offset(this.size.width * (x - 0.05f), this.size.height * 0.92f), stroke.width, StrokeCap.Round)
                }
            }
            Glyph.Snow -> {
                cloud(color, occlude, stroke, dy = -0.1f, scale = 0.85f)
                for (x in listOf(0.36f, 0.52f, 0.68f)) drawCircle(color, stroke.width * 0.8f, Offset(this.size.width * x, this.size.height * 0.86f))
            }
            Glyph.Thunder -> {
                cloud(color, occlude, stroke, dy = -0.1f, scale = 0.85f)
                val p = Path().apply {
                    moveTo(this@Canvas.size.width * 0.55f, this@Canvas.size.height * 0.66f)
                    lineTo(this@Canvas.size.width * 0.44f, this@Canvas.size.height * 0.82f)
                    lineTo(this@Canvas.size.width * 0.56f, this@Canvas.size.height * 0.82f)
                    lineTo(this@Canvas.size.width * 0.46f, this@Canvas.size.height * 0.97f)
                }
                drawPath(p, color, style = stroke)
            }
            Glyph.Mosque -> mosque(color, stroke)
            Glyph.Calendar -> calendar(color, stroke)
            Glyph.Crescent -> crescentOutline(color, stroke)
        }
    }
}

private fun DrawScope.sun(c: Offset, r: Float, color: Color, stroke: Stroke) {
    drawCircle(color, r, c, style = stroke)
    for (i in 0 until 8) {
        val a = Math.toRadians(i * 45.0)
        val from = Offset(c.x + cos(a).toFloat() * r * 1.55f, c.y + sin(a).toFloat() * r * 1.55f)
        val to = Offset(c.x + cos(a).toFloat() * r * 2.1f, c.y + sin(a).toFloat() * r * 2.1f)
        drawLine(color, from, to, stroke.width, StrokeCap.Round)
    }
}

private fun DrawScope.cloudPath(dx: Float, dy: Float, scale: Float): Path {
    val w = size.width
    val h = size.height
    fun cx(f: Float) = w * (0.5f + (f - 0.5f) * scale + dx)
    fun cy(f: Float) = h * (0.5f + (f - 0.5f) * scale + dy)
    val a = Path().apply { addOval(Rect(Offset(cx(0.36f), cy(0.58f)), w * 0.17f * scale)) }
    val b = Path().apply { addOval(Rect(Offset(cx(0.58f), cy(0.48f)), w * 0.23f * scale)) }
    val base = Path().apply {
        addRoundRect(RoundRect(Rect(cx(0.18f), cy(0.56f), cx(0.86f), cy(0.78f)), CornerRadius(h * 0.11f * scale)))
    }
    return Path().apply {
        op(a, b, PathOperation.Union)
        op(this, base, PathOperation.Union)
    }
}

private fun DrawScope.cloud(color: Color, occlude: Color, stroke: Stroke, dx: Float = 0f, dy: Float = 0f, scale: Float = 1f) {
    val p = cloudPath(dx, dy, scale)
    drawPath(p, occlude, style = Fill)
    drawPath(p, color, style = stroke)
}

private fun DrawScope.line(color: Color, stroke: Stroke, from: Float, y: Float, to: Float) {
    drawLine(color, Offset(size.width * from, size.height * y), Offset(size.width * to, size.height * y), stroke.width, StrokeCap.Round)
}

private fun DrawScope.crescentPath(): Path {
    val r = size.minDimension * 0.36f
    val outer = Path().apply { addOval(Rect(center, r)) }
    val cut = Path().apply { addOval(Rect(Offset(center.x + r * 0.55f, center.y - r * 0.35f), r * 0.85f)) }
    return Path().apply { op(outer, cut, PathOperation.Difference) }
}

private fun DrawScope.moon(color: Color) = drawPath(crescentPath(), color)

private fun DrawScope.crescentOutline(color: Color, stroke: Stroke) = drawPath(crescentPath(), color, style = stroke)

private fun DrawScope.calendar(color: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    drawRoundRect(color, Offset(w * 0.14f, h * 0.2f), Size(w * 0.72f, h * 0.66f), CornerRadius(w * 0.12f), style = stroke)
    drawLine(color, Offset(w * 0.14f, h * 0.4f), Offset(w * 0.86f, h * 0.4f), stroke.width)
    for (x in listOf(0.34f, 0.66f)) drawLine(color, Offset(w * x, h * 0.1f), Offset(w * x, h * 0.28f), stroke.width, StrokeCap.Round)
    for (row in 0..1) for (col in 0..2) {
        drawCircle(color, stroke.width * 0.55f, Offset(w * (0.32f + col * 0.18f), h * (0.56f + row * 0.15f)))
    }
}

private fun DrawScope.mosque(color: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    // Base, central dome with finial, and two minarets.
    drawLine(color, Offset(w * 0.1f, h * 0.88f), Offset(w * 0.9f, h * 0.88f), stroke.width, StrokeCap.Round)
    val dome = Path().apply {
        moveTo(w * 0.3f, h * 0.88f)
        lineTo(w * 0.3f, h * 0.58f)
        cubicTo(w * 0.3f, h * 0.36f, w * 0.5f, h * 0.34f, w * 0.5f, h * 0.24f)
        cubicTo(w * 0.5f, h * 0.34f, w * 0.7f, h * 0.36f, w * 0.7f, h * 0.58f)
        lineTo(w * 0.7f, h * 0.88f)
    }
    drawPath(dome, color, style = stroke)
    drawLine(color, Offset(w * 0.5f, h * 0.24f), Offset(w * 0.5f, h * 0.12f), stroke.width, StrokeCap.Round)
    for (x in listOf(0.14f, 0.86f)) drawLine(color, Offset(w * x, h * 0.88f), Offset(w * x, h * 0.36f), stroke.width, StrokeCap.Round)
    drawLine(color, Offset(w * 0.44f, h * 0.88f), Offset(w * 0.44f, h * 0.72f), stroke.width)
    drawLine(color, Offset(w * 0.56f, h * 0.88f), Offset(w * 0.56f, h * 0.72f), stroke.width)
}
