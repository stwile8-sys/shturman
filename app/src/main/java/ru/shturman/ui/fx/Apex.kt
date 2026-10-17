package ru.shturman.ui.fx

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

// Общие рисунки темы APEX: метки по углам панелей, строки развёртки и «зажигание».

/** Мел, пыль, шов и красная точка APEX. */
val ApexChalk = Color(0xFFF3F1EA)
val ApexDust = Color(0xFFA6A39A)
val ApexSeam = Color(0xFF2B2A27)
val ApexRed = Color(0xFFFF2B2B)
val ApexTick = Color(0xFF8A877F)

/** Метки по четырём углам: тонкие уголки, как на визире. len и stroke в пикселях. */
fun DrawScope.apexTicks(color: Color = ApexTick, len: Float = 10f * density, stroke: Float = 1f * density) {
    val w = size.width
    val h = size.height
    // верх-лево
    drawRect(color, Offset(0f, 0f), Size(len, stroke))
    drawRect(color, Offset(0f, 0f), Size(stroke, len))
    // верх-право
    drawRect(color, Offset(w - len, 0f), Size(len, stroke))
    drawRect(color, Offset(w - stroke, 0f), Size(stroke, len))
    // низ-лево
    drawRect(color, Offset(0f, h - stroke), Size(len, stroke))
    drawRect(color, Offset(0f, h - len), Size(stroke, len))
    // низ-право
    drawRect(color, Offset(w - len, h - stroke), Size(len, stroke))
    drawRect(color, Offset(w - stroke, h - len), Size(stroke, len))
}

/** Строки развёртки, как на старом мониторе: тёмная линия через каждые step. */
fun DrawScope.apexScanlines(alpha: Float = 0.16f, step: Float = 3f * density) {
    if (alpha <= 0f) return
    val c = Color.Black.copy(alpha = alpha)
    var y = 0f
    val line = density.coerceAtLeast(1f)
    while (y < size.height) {
        drawRect(c, Offset(0f, y), Size(size.width, line))
        y += step
    }
}

/**
 * «Зажигание»: элемент не появляется плавно, а загорается — три кадра мерцания, потом полный свет.
 * v от 0 до 1, результат — прозрачность.
 */
fun apexIgnite(v: Float): Float = when {
    v >= 1f -> 1f
    v < 0.30f -> 0f
    v < 0.45f -> 0.8f
    v < 0.60f -> 0.2f
    v < 0.75f -> 1f
    else -> 0.6f
}

/** Смена цифр на табло: старые сегменты гаснут, новые загораются за два кадра. */
fun apexFlick(v: Float): Float = when {
    v >= 1f -> 1f
    v < 0.25f -> 0.25f
    v < 0.45f -> 1f
    v < 0.6f -> 0.45f
    else -> 1f
}

/** Погасшие сегменты под числом: каждая цифра (и пробел перед числом) становится «8». Null — если цифр нет. */
fun apexGhost(text: String): String? {
    if (text.none { it.isDigit() }) return null
    var lead = true
    val sb = StringBuilder(text.length)
    for (c in text) {
        if (c != ' ') lead = false
        sb.append(if (c.isDigit() || (lead && c == ' ')) '8' else c)
    }
    return sb.toString()
}
