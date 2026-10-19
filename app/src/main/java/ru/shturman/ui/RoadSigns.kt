package ru.shturman.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.shturman.routing.Lane
import ru.shturman.ui.theme.NumberFont
import ru.shturman.ui.theme.ShColors
import kotlin.math.cos
import kotlin.math.sin

/**
 * Знак кольца: круг, въезд снизу, наш путь по кольцу против часовой стрелки (движение правостороннее)
 * и выезд со стрелкой туда, куда уходим. В центре — номер съезда.
 * exitAngle — куда уходим относительно въезда, градусы: 0 — прямо, +90 — направо, −90 — налево.
 */
@Composable
fun RoundaboutSign(exit: Int, exitAngle: Double, boxSize: Dp, color: Color, modifier: Modifier = Modifier) {
    val numberSize = with(LocalDensity.current) { (boxSize * 0.22f).toSp() }
    Box(modifier.size(boxSize), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(boxSize)) {
            val s = size.minDimension
            val c = Offset(size.width / 2f, size.height / 2f)
            val r = s * 0.23f
            val w = s * 0.085f
            drawCircle(color.copy(alpha = 0.3f), r, c, style = Stroke(w))
            // въезд снизу
            drawLine(color, Offset(c.x, size.height * 0.97f), Offset(c.x, c.y + r), w, StrokeCap.Round)
            // путь по кольцу: от низа (90° в координатах экрана) против часовой стрелки до съезда
            var sweep = (exitAngle - 180.0).toFloat()
            if (sweep > -15f) sweep -= 360f
            sweep = sweep.coerceIn(-345f, -15f)
            drawArc(color, 90f, sweep, false, Offset(c.x - r, c.y - r), Size(2 * r, 2 * r), style = Stroke(w, cap = StrokeCap.Round))
            // выезд: от точки на кольце наружу, со стрелкой
            val th = Math.toRadians(90.0 + sweep)
            val dir = Math.toRadians(-90.0 + exitAngle)
            val ux = cos(dir).toFloat()
            val uy = sin(dir).toFloat()
            val p = Offset(c.x + r * cos(th).toFloat(), c.y + r * sin(th).toFloat())
            val q = Offset(p.x + ux * s * 0.17f, p.y + uy * s * 0.17f)
            drawLine(color, p, q, w, StrokeCap.Round)
            arrowHead(q, ux, uy, s * 0.13f, color)
        }
        Text(
            text = exit.toString(), color = color, fontSize = numberSize,
            fontFamily = NumberFont, fontWeight = FontWeight.Bold
        )
    }
}

/** Треугольный наконечник стрелки в точке at, направление (ux, uy) — единичный вектор. */
private fun DrawScope.arrowHead(at: Offset, ux: Float, uy: Float, len: Float, color: Color) {
    val nx = -uy
    val ny = ux
    val tip = Offset(at.x + ux * len * 0.6f, at.y + uy * len * 0.6f)
    val back = Offset(at.x - ux * len * 0.4f, at.y - uy * len * 0.4f)
    drawPath(Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(back.x + nx * len * 0.55f, back.y + ny * len * 0.55f)
        lineTo(back.x - nx * len * 0.55f, back.y - ny * len * 0.55f)
        close()
    }, color)
}

/** Направления на полосе (бит Valhalla → угол от «прямо», градусы; минус — влево). */
private val LANE_DIRS = listOf(
    2 to 0f, 16 to -40f, 32 to 40f, 8 to -85f, 64 to 85f, 4 to -135f, 128 to 135f, 512 to -22f, 1024 to 22f
)

/**
 * Полосы перед манёвром слева направо: стрелки каждой полосы. Полосы, по которым нам ехать, подсвечены,
 * и на них ярче нужная стрелка; остальные — блёклые.
 */
@Composable
fun LaneStrip(lanes: List<Lane>, modifier: Modifier = Modifier, laneWidth: Dp = 20.dp, laneHeight: Dp = 26.dp) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        for (lane in lanes.take(8)) LaneIcon(lane, Modifier.size(laneWidth, laneHeight))
    }
}

@Composable
private fun LaneIcon(lane: Lane, modifier: Modifier) {
    val good = lane.good
    val bright = ShColors.TextPrimary
    val dim = ShColors.TextSecondary.copy(alpha = 0.4f)
    Canvas(
        modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (good) ShColors.Accent.copy(alpha = 0.22f) else Color.Transparent)
    ) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.13f
        val dirs = if (lane.directions <= 1) 2 else lane.directions
        val chosen = if (lane.active != 0) lane.active else lane.valid
        val base = Offset(w / 2f, h * 0.92f)
        val knee = Offset(w / 2f, h * 0.52f)
        // сначала блёклые стрелки, сверху — яркие
        for (pass in 0..1) {
            for ((bit, ang) in LANE_DIRS) {
                if (dirs and bit == 0) continue
                val isBright = good && (chosen == 0 || chosen == bit)
                if ((pass == 1) != isBright) continue
                val col = if (isBright) bright else dim
                val rad = Math.toRadians(ang.toDouble())
                val ux = sin(rad).toFloat()
                val uy = -cos(rad).toFloat()
                val len = h * 0.3f
                val end = Offset(knee.x + ux * len, knee.y + uy * len)
                drawLine(col, base, knee, stroke, StrokeCap.Round)
                drawLine(col, knee, end, stroke, StrokeCap.Round)
                arrowHead(end, ux, uy, w * 0.42f, col)
            }
            if (dirs and 256 != 0) {
                val isBright = good && (chosen == 0 || chosen == 256)
                if ((pass == 1) == isBright) {
                    // разворот: вверх, крючок влево и вниз
                    val col = if (isBright) bright else dim
                    val top = h * 0.3f
                    val left = w / 2f - w * 0.3f
                    drawLine(col, base, Offset(w / 2f, top), stroke, StrokeCap.Round)
                    drawArc(col, 0f, -180f, false, Offset(left, top - w * 0.15f), Size(w * 0.3f, w * 0.3f), style = Stroke(stroke, cap = StrokeCap.Round))
                    drawLine(col, Offset(left, top), Offset(left, h * 0.55f), stroke, StrokeCap.Round)
                    arrowHead(Offset(left, h * 0.55f), 0f, 1f, w * 0.42f, col)
                }
            }
        }
    }
}

/** Совет сбавить скорость перед крутым изгибом: синий квадрат с числом, как знак рекомендуемой скорости. */
@Composable
fun CurveAdvicePlate(kmh: Int, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Color(0xFF1F5FD1)),
            contentAlignment = Alignment.Center
        ) {
            Text(kmh.toString(), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = NumberFont)
        }
        Spacer(Modifier.width(6.dp))
        Text("крутой поворот", color = ShColors.Sign3, fontSize = 12.sp, maxLines = 1)
    }
}

/** Полоска до поворота: полная за 600 м, к повороту укорачивается. */
@Composable
fun TurnDistanceBar(distanceM: Double, color: Color, modifier: Modifier = Modifier, fromM: Double = 600.0) {
    val frac = (distanceM / fromM).toFloat().coerceIn(0f, 1f)
    Box(
        modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.12f))
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(frac)
                .clip(RoundedCornerShape(50))
                .background(color)
        )
    }
}
