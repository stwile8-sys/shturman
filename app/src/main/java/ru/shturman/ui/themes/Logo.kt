package ru.shturman.ui.themes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Логотип «Штурмана»: компас с оранжевой стрелкой. Рисуется кодом, поэтому чёткий при любом размере. needleDeg — поворот стрелки. */
@Composable
fun LogoMark(size: Dp, modifier: Modifier = Modifier, needleDeg: Float = 0f) {
    Canvas(modifier.size(size)) {
        val c = Offset(this.size.width / 2, this.size.height / 2)
        val r = this.size.minDimension / 2
        val cyan = Color(0xFF2DE0F5)
        drawCircle(Color(0xFF0E1013), r, c)
        drawCircle(Color(0xFF1B2A33), r * 0.97f, c, style = Stroke(r * 0.04f))
        drawCircle(cyan, r * 0.72f, c, style = Stroke(r * 0.035f))
        // засечки вокруг кольца
        for (i in 0 until 12) {
            val a = i * (PI.toFloat() / 6f)
            val big = i % 3 == 0
            val r0 = r * (if (big) 0.80f else 0.83f)
            val r1 = r * 0.90f
            drawLine(
                cyan.copy(alpha = if (big) 1f else 0.7f),
                Offset(c.x + sin(a) * r0, c.y - cos(a) * r0),
                Offset(c.x + sin(a) * r1, c.y - cos(a) * r1),
                strokeWidth = r * (if (big) 0.035f else 0.025f)
            )
        }
        rotate(needleDeg, c) {
            // серый хвост
            val tail = Path().apply {
                moveTo(c.x, c.y + r * 0.50f)
                lineTo(c.x + r * 0.11f, c.y + r * 0.04f)
                lineTo(c.x - r * 0.11f, c.y + r * 0.04f)
                close()
            }
            drawPath(tail, Color(0xFF4A5560))
            // оранжевая стрелка
            val needle = Path().apply {
                moveTo(c.x, c.y - r * 0.60f)
                lineTo(c.x + r * 0.17f, c.y + r * 0.13f)
                lineTo(c.x, c.y + r * 0.03f)
                lineTo(c.x - r * 0.17f, c.y + r * 0.13f)
                close()
            }
            drawPath(needle, Color(0xFFFF8A00))
            drawCircle(Color(0xFF0E1013), r * 0.05f, c)
        }
    }
}
