package ru.shturman.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Заставка при запуске: кольцо компаса собирается, оранжевая стрелка делает оборот и встаёт на север,
 * проявляется название. Играет около 2,6 секунды, потом плавно гаснет.
 */
@Composable
fun SplashOverlay(onFinished: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val fade = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(2900, easing = CubicBezierEasing(0.22f, 0.8f, 0.3f, 1f)))
        fade.animateTo(0f, tween(350))
        onFinished()
    }

    val p = progress.value
    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(fade.value)
            .background(Color(0xFF0A0C10)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height * 0.42f)
            val r = min(size.width, size.height) * 0.26f * (0.7f + 0.3f * p)
            val cyan = Color(0xFF3DDCFF)
            val orange = Color(0xFFFF8A00)

            // звёзды, мерцающие на фоне
            for (i in 0 until 46) {
                val sx = ((i * 7919) % 1000) / 1000f * size.width
                val sy = ((i * 104729) % 1000) / 1000f * size.height
                val tw = (0.5f + 0.5f * sin(p * 22f + i)).coerceIn(0f, 1f)
                drawCircle(Color.White.copy(alpha = 0.12f + 0.4f * tw * min(1f, p * 2f)), radius = 1.5f + (i % 3), center = Offset(sx, sy))
            }
            // расходящиеся пульсирующие кольца
            for (k in 0 until 3) {
                val t = (p * 1.7f - k * 0.22f).coerceIn(0f, 1f)
                if (t > 0f && t < 1f) {
                    drawCircle(cyan.copy(alpha = (1f - t) * 0.35f), radius = r * (1.3f + 1.4f * t), center = c, style = Stroke(width = r * 0.025f))
                }
            }
            // радарный луч, который крутится и гаснет
            if (p < 0.9f) {
                drawArc(
                    color = cyan.copy(alpha = 0.16f * (1f - p)),
                    startAngle = p * 900f,
                    sweepAngle = 55f,
                    useCenter = true,
                    topLeft = Offset(c.x - r * 1.25f, c.y - r * 1.25f),
                    size = Size(r * 2.5f, r * 2.5f)
                )
            }
            // внешнее тёмное кольцо и голубое кольцо, которое рисуется по дуге
            drawCircle(Color(0xFF1E242C).copy(alpha = p), radius = r * 1.27f, center = c, style = Stroke(width = r * 0.09f))
            drawArc(
                color = cyan,
                startAngle = -90f,
                sweepAngle = 360f * min(1f, p * 1.4f),
                useCenter = false,
                topLeft = Offset(c.x - r, c.y - r),
                size = Size(r * 2, r * 2),
                style = Stroke(width = r * 0.04f, cap = StrokeCap.Round)
            )
            // деления
            if (p > 0.35f) {
                val a = ((p - 0.35f) / 0.4f).coerceIn(0f, 1f)
                for (deg in 0 until 360 step 30) {
                    val big = deg % 90 == 0
                    val r2 = if (big) r * 1.15f else r * 1.08f
                    val rad = deg * PI / 180
                    drawLine(
                        color = (if (big) cyan else Color(0xFF2E6E80)).copy(alpha = a),
                        start = Offset(c.x + (r * sin(rad)).toFloat(), c.y - (r * cos(rad)).toFloat()),
                        end = Offset(c.x + (r2 * sin(rad)).toFloat(), c.y - (r2 * cos(rad)).toFloat()),
                        strokeWidth = r * 0.05f,
                        cap = StrokeCap.Round
                    )
                }
            }
            // стрелка: делает оборот и встаёт на север с лёгким покачиванием
            val spin = (1f - p) * (1f - p) * 720f
            val wobble = if (p > 0.8f) (sin((p - 0.8f) * 40f) * (1f - p) * 25f) else 0f
            rotate(degrees = spin + wobble, pivot = c) {
                val needle = Path().apply {
                    moveTo(c.x, c.y - r * 0.8f)
                    lineTo(c.x + r * 0.29f, c.y + r * 0.2f)
                    lineTo(c.x, c.y)
                    lineTo(c.x - r * 0.29f, c.y + r * 0.2f)
                    close()
                }
                drawPath(needle, orange.copy(alpha = min(1f, p * 3f)))
                val tail = Path().apply {
                    moveTo(c.x, c.y + r * 0.8f)
                    lineTo(c.x - r * 0.13f, c.y + r * 0.35f)
                    lineTo(c.x, c.y + r * 0.2f)
                    lineTo(c.x + r * 0.13f, c.y + r * 0.35f)
                    close()
                }
                drawPath(tail, Color(0xFF2A2F38).copy(alpha = min(1f, p * 3f)))
            }
            drawCircle(Color(0xFF0A0C10), radius = r * 0.065f, center = c)

            // дорога в перспективе: оранжевая разметка летит к зрителю, фары-огоньки уходят вдаль
            run {
                val horizon = size.height * 0.68f
                val roadA = (p * 2.2f).coerceIn(0f, 1f)
                val cx = size.width / 2
                val half = size.width * 0.5f
                val edge = Path().apply {
                    moveTo(cx - half * 0.06f, horizon); lineTo(cx + half * 0.06f, horizon)
                    lineTo(cx + half * 1.1f, size.height); lineTo(cx - half * 1.1f, size.height); close()
                }
                drawPath(edge, Color(0xFF12161C).copy(alpha = roadA))
                drawLine(cyan.copy(alpha = 0.5f * roadA), Offset(cx - half * 0.06f, horizon), Offset(cx - half * 1.1f, size.height), strokeWidth = 3f)
                drawLine(cyan.copy(alpha = 0.5f * roadA), Offset(cx + half * 0.06f, horizon), Offset(cx + half * 1.1f, size.height), strokeWidth = 3f)
                val phase = (p * 5f) % 1f
                for (i in 0 until 7) {
                    val t0 = ((i + phase) / 7f)
                    val t1 = t0 + 0.06f
                    val y0 = horizon + (size.height - horizon) * t0 * t0
                    val y1 = horizon + (size.height - horizon) * min(1f, t1) * min(1f, t1)
                    val w0 = 2f + 16f * t0 * t0
                    val w1 = 2f + 16f * min(1f, t1) * min(1f, t1)
                    val dash = Path().apply {
                        moveTo(cx - w0, y0); lineTo(cx + w0, y0); lineTo(cx + w1, y1); lineTo(cx - w1, y1); close()
                    }
                    drawPath(dash, orange.copy(alpha = roadA * (0.25f + 0.75f * t0)))
                }
                drawCircle(orange.copy(alpha = 0.18f * roadA), radius = size.width * 0.35f, center = Offset(cx, horizon))
            }
        }
        Text(
            text = "ШТУРМАН",
            color = Color.White,
            style = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = 10.sp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 150.dp)
                .alpha(((p - 0.45f) / 0.4f).coerceIn(0f, 1f))
        )
    }
}
