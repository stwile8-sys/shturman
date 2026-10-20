package ru.shturman.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import ru.shturman.ui.theme.NumberFont
import ru.shturman.ui.theme.ShColors

// На сколько градусов «гнётся» стрелка при каждой резкости: 1 — почти разворот, 6 — едва заметный изгиб.
private fun bendDegrees(severity: Int): Double = when (severity) {
    1 -> 165.0
    2 -> 125.0
    3 -> 90.0
    4 -> 60.0
    5 -> 38.0
    else -> 20.0
}

/**
 * Ралли-стрелка: прямой отрезок и дуга в сторону поворота, на конце наконечник.
 * severity = null — едем прямо, рисуется стрелка вверх.
 */
@Composable
fun RallyArrow(
    severity: Int?,
    isLeft: Boolean,
    modifier: Modifier = Modifier,
    boxSize: Dp = 56.dp,
    color: Color = signColor(severity ?: 6),
    /** Толщина линии в долях размера: 0,13 — прежние знаки, 0,09 — тонкие знаки нового оформления. */
    stroke: Float = 0.13f
) {
    // Путь стрелки строится один раз на размер и резкость (drawWithCache), а не на каждый кадр
    Spacer(modifier = modifier.size(boxSize).drawWithCache {
        val total = if (severity == null) 0f else Math.toRadians(bendDegrees(severity)).toFloat()
        val dir = if (isLeft) -1f else 1f
        val stem = if (severity == null) 2.2f else 1.1f

        // Ломаная линия в «условных» координатах: сначала ствол вверх, потом дуга.
        val pts = ArrayList<Offset>()
        var x = 0f
        var y = 0f
        pts.add(Offset(x, y))
        y -= stem
        pts.add(Offset(x, y))
        val steps = 40
        val dphi = total / steps
        for (i in 0 until steps) {
            val mid = dir * (i + 0.5f) * dphi
            x += sin(mid) * dphi
            y -= cos(mid) * dphi
            pts.add(Offset(x, y))
        }

        // Наконечник стрелки на конце линии.
        val end = dir * total
        val dx = sin(end)
        val dy = -cos(end)
        val px = cos(end)
        val py = sin(end)
        val headLen = 0.6f
        val headHalf = 0.45f
        val tip = Offset(x + dx * headLen, y + dy * headLen)
        val baseL = Offset(x + px * headHalf, y + py * headHalf)
        val baseR = Offset(x - px * headHalf, y - py * headHalf)

        // Вписываем рисунок в квадрат с небольшим полем и центрируем.
        val all = pts + listOf(tip, baseL, baseR)
        val minX = all.minOf { it.x }
        val maxX = all.maxOf { it.x }
        val minY = all.minOf { it.y }
        val maxY = all.maxOf { it.y }
        val w = max(maxX - minX, 0.5f)
        val h = max(maxY - minY, 0.5f)
        val scale = min(this.size.width * 0.86f / w, this.size.height * 0.86f / h)
        val ox = (this.size.width - w * scale) / 2f - minX * scale
        val oy = (this.size.height - h * scale) / 2f - minY * scale
        fun map(o: Offset) = Offset(o.x * scale + ox, o.y * scale + oy)

        val line = Path()
        val first = map(pts[0])
        line.moveTo(first.x, first.y)
        for (i in 1 until pts.size) {
            val p = map(pts[i])
            line.lineTo(p.x, p.y)
        }
        val lineStyle = Stroke(
            width = this.size.minDimension * stroke,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )

        val head = Path()
        val t = map(tip)
        val l = map(baseL)
        val r = map(baseR)
        head.moveTo(t.x, t.y)
        head.lineTo(l.x, l.y)
        head.lineTo(r.x, r.y)
        head.close()
        onDrawBehind {
            drawPath(path = line, color = color, style = lineStyle)
            drawPath(path = head, color = color)
        }
    })
}

/** Стрелка поворота с маленьким кружком-цифрой резкости (1–6) в углу. */
@Composable
fun RallyPacenote(
    severity: Int,
    isLeft: Boolean,
    modifier: Modifier = Modifier,
    boxSize: Dp = 60.dp
) {
    Box(modifier = modifier.size(boxSize)) {
        RallyArrow(severity = severity, isLeft = isLeft, boxSize = boxSize)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(boxSize * 0.38f)
                .clip(signShape())
                .background(signColor(severity)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = severity.toString(),
                color = ShColors.Background,
                fontFamily = NumberFont,
                fontWeight = FontWeight.Bold,
                fontSize = (boxSize.value * 0.22f).sp
            )
        }
    }
}
