package ru.shturman.ui.themes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Dp

// Логотип APEX: буква A — шпилька (вид сверху) с красной точкой апекса; P, E, X — той же линией.
// Геометрия снята с видео-заставки клана. Все размеры — в долях высоты букв (1 = высота).

private const val STROKE = 0.1765f
private const val WORD_W = 3.433f
private const val A_W = 0.937f

private val ApexChalk = Color(0xFFF3F1EA)
private val ApexRed = Color(0xFFFF2B2B)

/** Средняя линия буквы A: ножка — дуга шпильки — ножка. Ножки продлены вниз, низ обрезается по базовой линии. */
private fun aPath(h: Float, ox: Float, oy: Float): Path = Path().apply {
    fun x(v: Float) = ox + v * h
    fun y(v: Float) = oy + v * h
    moveTo(x(0.0466f), y(1.15f))
    lineTo(x(0.3050f), y(0.2130f))
    arcTo(Rect(Offset(x(0.4685f), y(0.2580f)), 0.1693f * h), 195.39f, 149.22f, false)
    lineTo(x(0.8904f), y(1.15f))
}

/** Красная точка апекса со свечением. */
private fun DrawScope.apexDot(h: Float, ox: Float, oy: Float, red: Color) {
    val c = Offset(ox + 0.4685f * h, oy + 0.513f * h)
    val r = 0.0798f * h
    drawCircle(Brush.radialGradient(listOf(red.copy(alpha = 0.45f), red.copy(alpha = 0.15f), Color.Transparent), c, r * 3.2f), r * 3.2f, c)
    drawCircle(red, r, c)
}

/**
 * Знак APEX (буква A с точкой) на тёмной плитке — как иконка приложения. Чёткий при любом размере.
 * tile = false — без плитки, только знак.
 */
@Composable
fun LogoMark(size: Dp, modifier: Modifier = Modifier, tile: Boolean = true) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        if (tile) {
            drawRoundRect(
                Brush.radialGradient(listOf(Color(0xFF1C1B18), Color(0xFF060605)), Offset(s / 2, s * 0.46f), s * 0.72f),
                cornerRadius = CornerRadius(s * 0.22f)
            )
        }
        val h = s * (if (tile) 0.64f else 1f)
        val ox = (s - A_W * h) / 2
        val oy = (s - h) / 2 + (if (tile) s * 0.005f else 0f)
        clipRect(0f, oy - 1f, s, oy + h) {
            drawPath(aPath(h, ox, oy), ApexChalk, style = Stroke(width = STROKE * h, cap = StrokeCap.Butt))
        }
        apexDot(h, ox, oy, ApexRed)
    }
}

/** Надпись APEX как в заставке клана: монолинейные буквы, в A — красная точка апекса. height — высота букв. */
@Composable
fun ApexWordmark(height: Dp, modifier: Modifier = Modifier, color: Color = ApexChalk, dot: Color = ApexRed) {
    Canvas(modifier.size(width = height * WORD_W, height = height)) {
        val h = this.size.height
        val stroke = Stroke(width = STROKE * h, cap = StrokeCap.Butt)
        fun p(x: Float, y: Float) = Offset(x * h, y * h)
        clipRect(0f, 0f, this.size.width, h) {
            // A
            drawPath(aPath(h, 0f, 0f), color, style = stroke)
            // P: ствол и чаша
            drawLine(color, p(1.130f, -0.1f), p(1.130f, 1.1f), STROKE * h, StrokeCap.Butt)
            drawPath(Path().apply {
                moveTo(1.130f * h, 0.0882f * h)
                lineTo(1.416f * h, 0.0882f * h)
                arcTo(Rect(p(1.416f, 0.328f), 0.2395f * h), 270f, 180f, false)
                lineTo(1.130f * h, 0.5675f * h)
            }, color, style = stroke)
            // E: ствол и три перекладины
            drawLine(color, p(1.979f, -0.1f), p(1.979f, 1.1f), STROKE * h, StrokeCap.Butt)
            drawLine(color, p(1.891f, 0.0882f), p(2.542f, 0.0882f), STROKE * h, StrokeCap.Butt)
            drawLine(color, p(1.891f, 0.4916f), p(2.462f, 0.4916f), STROKE * h, StrokeCap.Butt)
            drawLine(color, p(1.891f, 0.9118f), p(2.542f, 0.9118f), STROKE * h, StrokeCap.Butt)
            // X: две диагонали, концы срезаны по верху и низу
            val xc = 3.034f; val dx = 0.29f
            drawLine(color, p(xc - dx * 1.4f, -0.2f), p(xc + dx * 1.4f, 1.2f), STROKE * h, StrokeCap.Butt)
            drawLine(color, p(xc + dx * 1.4f, -0.2f), p(xc - dx * 1.4f, 1.2f), STROKE * h, StrokeCap.Butt)
        }
        apexDot(h, 0f, 0f, dot)
    }
}
