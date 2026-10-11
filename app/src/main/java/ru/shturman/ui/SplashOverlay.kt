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
        progress.animateTo(1f, tween(2300, easing = CubicBezierEasing(0.22f, 0.8f, 0.3f, 1f)))
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
