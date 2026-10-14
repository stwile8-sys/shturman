package ru.shturman.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.math.sin

// Хронология заставки, миллисекунды от конца первой чёрной паузы
private const val BLACK_HOLD_MS = 900L      // чёрный экран, пока приложение грузится
private const val SPONSOR_END = 1700f       // «спонсорская» заставка
private const val SKY_START = 2200f         // из чёрного проявляется небо
private const val SOUND_AT = 2900f          // начало появления самолёта и звук
private const val REVEAL_END = 6000f        // самолёт полностью проявился (под длину звука)
private const val TITLE_START = 5000f
private const val TOTAL = 8700f

private fun ease(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun span(t: Float, a: Float, b: Float) = ease((t - a) / (b - a))

/**
 * Заставка: чёрный экран, пока приложение грузится; короткая «заставка спонсора»; затем на фоне неба
 * из чёрной фигуры медленно проявляется самолёт, в этот момент играет звук. Касание пропускает заставку.
 * Камера, блик и уход кругом из самолёта — общий «кинослой» SplashStage.
 */
@Composable
fun SplashOverlay(onPlaySound: () -> Unit, onFinished: () -> Unit) {
    ru.shturman.ui.themes.SplashStage(
        totalMs = TOTAL,
        peakMs = REVEAL_END,
        holdMs = BLACK_HOLD_MS,
        soundAtMs = SOUND_AT,
        look = ru.shturman.ui.themes.splashLookFor(0),
        onPlaySound = onPlaySound,
        onFinished = onFinished
    ) { t ->
        Canvas(modifier = Modifier.fillMaxSize()) {
            val sponsorA = if (t < SPONSOR_END) span(t, 0f, 400f) * (1f - span(t, 1300f, SPONSOR_END)) else 0f
            if (sponsorA > 0f) drawSponsor(sponsorA, t)

            val skyA = span(t, SKY_START, SKY_START + 700f)
            if (skyA > 0f) {
                drawSky(skyA, t)
                val reveal = span(t, SOUND_AT, REVEAL_END)
                drawPlane(t, skyA, reveal)
            }
        }
        // Спонсорская подпись
        val sponsorA = if (t < SPONSOR_END) span(t, 200f, 600f) * (1f - span(t, 1300f, SPONSOR_END)) else 0f
        if (sponsorA > 0f) {
            Text(text = stringResource(ru.shturman.R.string.s_938c5e9f),
                color = Color.White.copy(alpha = 0.7f * sponsorA),
                style = TextStyle(fontSize = 13.sp, letterSpacing = 6.sp),
                modifier = Modifier.align(Alignment.Center).padding(top = 150.dp)
            )
        }
        val titleA = span(t, TITLE_START, TITLE_START + 700f)
        if (titleA > 0f) {
            Text(text = stringResource(ru.shturman.R.string.s_63090ef7),
                color = Color.White.copy(alpha = titleA),
                style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = 9.sp),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 130.dp)
            )
        }
    }
}

/** «Спонсор»: тонкое кольцо рисуется по дуге, внутри светится точка, под ним название. */
private fun DrawScope.drawSponsor(a: Float, t: Float) {
    val c = Offset(size.width / 2, size.height * 0.45f)
    val r = min(size.width, size.height) * 0.11f
    val cyan = Color(0xFF3DDCFF)
    val orange = Color(0xFFFF8A00)
    val sweep = 360f * span(t, 100f, 1100f)
    drawArc(
        color = cyan.copy(alpha = a), startAngle = -90f, sweepAngle = sweep, useCenter = false,
        topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2),
        style = Stroke(width = r * 0.07f, cap = StrokeCap.Round)
    )
    drawCircle(orange.copy(alpha = a * (0.6f + 0.4f * sin(t / 120f))), radius = r * 0.22f, center = c)
    drawCircle(orange.copy(alpha = a * 0.15f), radius = r * 0.55f, center = c)
}

private fun DrawScope.drawSky(a: Float, t: Float) {
    drawRect(
        brush = Brush.verticalGradient(
            0f to Color(0xFF081A36).copy(alpha = a),
            0.45f to Color(0xFF2A5A9C).copy(alpha = a),
            0.78f to Color(0xFFF0A35E).copy(alpha = a),
            1f to Color(0xFFFFD6A0).copy(alpha = a)
        )
    )
    // последние звёзды наверху гаснут по мере того, как светлеет
    val rnd = kotlin.random.Random(12)
    for (i in 0 until 40) {
        val sx = rnd.nextFloat() * size.width; val sy = rnd.nextFloat() * size.height * 0.3f; val ph = rnd.nextFloat() * 6.28f
        val sa = a * (1f - span(t, SKY_START + 400f, REVEAL_END)) * (0.4f + 0.4f * sin(t / 350f + ph))
        if (sa > 0f) drawCircle(Color.White.copy(alpha = sa.coerceIn(0f, 1f)), 1.2f + (i % 3), Offset(sx, sy))
    }
    // лучи от солнца медленно вращаются
    val sunC = Offset(size.width * 0.78f, size.height * 0.74f)
    for (k in 0 until 9) {
        val ang = k * 0.7f + t / 9000f
        val len = size.width * 0.9f
        val ex = sunC.x + kotlin.math.cos(ang) * len; val ey = sunC.y + sin(ang) * len
        val nx = -sin(ang) * size.width * 0.05f; val ny = kotlin.math.cos(ang) * size.width * 0.05f
        drawPath(
            Path().apply { moveTo(sunC.x, sunC.y); lineTo(ex + nx, ey + ny); lineTo(ex - nx, ey - ny); close() },
            Brush.radialGradient(listOf(Color(0xFFFFE6B0).copy(alpha = 0.10f * a), Color.Transparent), sunC, len)
        )
    }
    // солнце у горизонта
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color(0xFFFFE6B0).copy(alpha = 0.8f * a), Color.Transparent),
            center = Offset(size.width * 0.78f, size.height * 0.74f), radius = size.width * 0.5f
        ),
        radius = size.width * 0.5f, center = Offset(size.width * 0.78f, size.height * 0.74f)
    )
    // облака медленно плывут влево
    for (i in 0 until 7) {
        val speed = 0.012f + (i % 3) * 0.006f
        val x = ((i * 0.23f - t * speed / 100f) % 1.3f + 1.3f) % 1.3f - 0.15f
        val y = 0.12f + (i * 0.11f) % 0.6f
        val w = size.width * (0.35f + (i % 3) * 0.12f)
        for (k in 0 until 3) {
            drawCircle(
                Color.White.copy(alpha = 0.13f * a),
                radius = w * (0.11f + 0.03f * k),
                center = Offset(x * size.width + k * w * 0.18f, y * size.height + (k % 2) * w * 0.03f)
            )
        }
    }
}

/** Самолёт (вид сбоку, летит вправо). Сначала это чёрная фигура, цвета «проливаются» от хвоста к носу. */
private fun DrawScope.drawPlane(t: Float, skyA: Float, reveal: Float) {
    val w = size.width * (0.62f + 0.1f * ease((t - SKY_START) / (REVEAL_END - SKY_START)))
    val cx = size.width * (0.46f + 0.04f * ease((t - SKY_START) / (REVEAL_END - SKY_START)))
    val cy = size.height * 0.42f - size.height * 0.02f * ease((t - SKY_START) / (REVEAL_END - SKY_START)) +
        sin(t / 700f) * size.height * 0.006f
    val tilt = -5f + sin(t / 900f) * 0.8f

    fun P(x: Float, y: Float) = Offset(cx + x * w, cy + y * w)
    fun poly(vararg pts: Pair<Float, Float>): Path = Path().apply {
        moveTo(P(pts[0].first, pts[0].second).x, P(pts[0].first, pts[0].second).y)
        for (i in 1 until pts.size) lineTo(P(pts[i].first, pts[i].second).x, P(pts[i].first, pts[i].second).y)
        close()
    }
    val fuselage = Path().apply {
        moveTo(P(-0.5f, -0.05f).x, P(-0.5f, -0.05f).y)
        lineTo(P(0.30f, -0.075f).x, P(0.30f, -0.075f).y)
        quadraticTo(P(0.5f, -0.05f).x, P(0.5f, -0.05f).y, P(0.53f, 0.0f).x, P(0.53f, 0.0f).y)
        quadraticTo(P(0.48f, 0.065f).x, P(0.48f, 0.065f).y, P(0.30f, 0.078f).x, P(0.30f, 0.078f).y)
        lineTo(P(-0.45f, 0.05f).x, P(-0.45f, 0.05f).y)
        close()
    }
    val fin = poly(-0.5f to -0.05f, -0.57f to -0.25f, -0.45f to -0.25f, -0.33f to -0.06f)
    val stab = poly(-0.47f to 0.0f, -0.6f to 0.05f, -0.55f to 0.065f, -0.38f to 0.02f)
    val wing = poly(0.06f to 0.03f, -0.18f to 0.21f, -0.07f to 0.22f, 0.22f to 0.045f)
    val engine = Path().apply {
        addOval(androidx.compose.ui.geometry.Rect(P(-0.01f, 0.11f), Size(0.13f * w, 0.055f * w)))
    }

    rotate(tilt, pivot = Offset(cx, cy)) {
        // инверсионный след от двигателя, тянется назад и тает
        val trail = span(t, REVEAL_END - 600f, REVEAL_END + 800f)
        if (trail > 0f) {
            val start = P(-0.02f, 0.14f)
            val end = Offset(start.x - size.width * 1.2f * trail, start.y + w * 0.02f)
            drawLine(
                Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.55f)), startX = end.x, endX = start.x),
                start, end, strokeWidth = w * 0.012f, cap = StrokeCap.Round
            )
            drawLine(
                Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.18f)), startX = end.x, endX = start.x),
                start, end, strokeWidth = w * 0.04f, cap = StrokeCap.Round
            )
        }
        // чёрная фигура
        val black = Color.Black.copy(alpha = skyA)
        for (p in listOf(fin, stab, wing, fuselage, engine)) drawPath(p, black)

        // цвета проявляются от хвоста к носу
        val left = cx - 0.6f * w
        val front = left + 1.2f * w * reveal
        if (reveal > 0f) {
            clipRect(left = left, top = cy - 0.4f * w, right = front, bottom = cy + 0.4f * w) {
                drawPath(wing, Color(0xFFC3CCD8))
                drawPath(stab, Color(0xFFE6EBF2))
                drawPath(fuselage, Color(0xFFF4F7FB))
                drawPath(fin, Color(0xFFFF8A00))
                drawPath(engine, Color(0xFF9AA5B4))
                // голубая полоса по борту и окна
                drawRect(Color(0xFF3DDCFF), topLeft = P(-0.47f, 0.03f), size = Size(0.93f * w, 0.014f * w))
                for (i in 0 until 14) {
                    drawCircle(Color(0xFF2A5A9C), radius = 0.0075f * w, center = P(-0.30f + i * 0.045f, -0.025f))
                }
            }
            // светящийся край проявления
            if (reveal < 1f) {
                drawRect(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, Color(0x66FFFFFF), Color.Transparent),
                        startX = front - 0.05f * w, endX = front + 0.05f * w
                    ),
                    topLeft = Offset(front - 0.05f * w, cy - 0.3f * w), size = Size(0.1f * w, 0.6f * w)
                )
            }
        }
    }
}
