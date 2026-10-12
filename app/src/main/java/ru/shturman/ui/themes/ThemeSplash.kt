package ru.shturman.ui.themes

import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import ru.shturman.ui.SplashOverlay
import ru.shturman.ui.theme.ShColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private const val HOLD_MS = 500L // чёрный экран, пока приложение грузится

private fun ease(x: Float): Float { val t = x.coerceIn(0f, 1f); return t * t * (3f - 2f * t) }
private fun span(t: Float, a: Float, b: Float) = ease((t - a) / (b - a))
private fun lin(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)

/**
 * Загрузочный экран: у каждой темы свой. Длительность подогнана под звук заставки темы
 * (splashMs), а главный момент анимации совпадает с пиком звука (splashPeakMs).
 * Классика оставляет прежнюю заставку с самолётом.
 */
@Composable
fun ThemeSplash(onPlaySound: () -> Unit, onFinished: () -> Unit) {
    val th = ShColors.T
    if (th.id == 0) {
        SplashOverlay(onPlaySound = onPlaySound, onFinished = onFinished)
        return
    }
    val total = th.splashMs.toFloat()
    val clock = remember { Animatable(0f) }
    val fade = remember { Animatable(1f) }
    val done = remember { booleanArrayOf(false) }

    LaunchedEffect(Unit) {
        delay(HOLD_MS)
        onPlaySound()
        clock.animateTo(total, tween(total.toInt(), easing = LinearEasing))
        fade.animateTo(0f, tween(350))
        if (!done[0]) { done[0] = true; onFinished() }
    }

    val t = clock.value
    val peak = th.splashPeakMs.toFloat()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(fade.value)
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures { if (!done[0]) { done[0] = true; onFinished() } }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            when (th.id) {
                1 -> drawPrincess(t, peak)
                2 -> drawNascar(t, peak)
                3 -> drawGt(t, peak)
                4 -> drawNight(t, peak)
                5 -> drawStreet(t, peak)
                6 -> drawRally(t, peak)
                7 -> drawDrift(t, peak)
            }
        }
        SplashTitle(th, t, peak)
    }
}

@Composable
private fun SplashTitle(th: ThemeSpec, t: Float, peak: Float) {
    when (th.id) {
        1 -> {
            val a = span(t, peak - 250f, peak + 450f)
            Text(
                text = "Штурман", color = Color(0xFFFFE3F2).copy(alpha = a),
                style = TextStyle(
                    fontFamily = th.numFont, fontWeight = FontWeight.Bold, fontSize = 46.sp,
                    shadow = Shadow(th.accent, Offset.Zero, 28f)
                ),
                modifier = Modifier.graphicsLayer { scaleX = 0.7f + 0.3f * a; scaleY = 0.7f + 0.3f * a }
            )
        }
        2 -> {
            val a = span(t, 700f, 1300f)
            val flash = 1f - lin(t, peak, peak + 400f)
            Text(
                text = "ШТУРМАН", color = Color.White.copy(alpha = a),
                style = TextStyle(
                    fontFamily = th.numFont, fontSize = 68.sp, letterSpacing = 6.sp,
                    shadow = Shadow(Color.White.copy(alpha = 0.6f * (0.3f + flash)), Offset.Zero, 30f)
                )
            )
        }
        3 -> {
            val a = span(t, 1000f, 1500f)
            Text(
                text = "ШТУРМАН", color = Color.White.copy(alpha = a),
                style = TextStyle(fontFamily = th.numFont, fontSize = 52.sp, letterSpacing = 3.sp),
                modifier = Modifier.graphicsLayer {
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
                    scaleY = 0.2f + 0.8f * a
                }
            )
        }
        4 -> {
            val a = span(t, peak - 350f, peak + 250f)
            val glow = 1f - lin(t, peak, peak + 900f)
            Text(
                text = "ШТУРМАН", color = Color(0xFFD8F6FF).copy(alpha = a),
                style = TextStyle(
                    fontFamily = th.numFont, fontWeight = FontWeight.Bold, fontSize = 32.sp, letterSpacing = 8.sp,
                    shadow = Shadow(th.accent.copy(alpha = 0.4f + 0.6f * glow), Offset.Zero, 20f + 40f * glow)
                )
            )
        }
        5 -> {
            val hit = span(t, 250f, peak + 60f)
            val shake = if (t in peak..(peak + 450f)) sin(t / 18f) * 10f * (1f - lin(t, peak, peak + 450f)) else 0f
            Text(
                text = "ШТУРМАН", color = th.accent.copy(alpha = hit),
                style = TextStyle(fontFamily = th.numFont, fontSize = 78.sp, letterSpacing = 3.sp, shadow = Shadow(Color.Black, Offset(4f, 4f), 6f)),
                modifier = Modifier.graphicsLayer {
                    val s = 2.4f - 1.4f * hit
                    scaleX = s; scaleY = s; translationX = shake
                }
            )
        }
        6 -> {
            val a = span(t, 1200f, 2400f)
            Text(
                text = "ШТУРМАН", color = Color(0xFFFFF1E4).copy(alpha = a),
                style = TextStyle(fontFamily = th.numFont, fontSize = 68.sp, letterSpacing = 3.sp, shadow = Shadow(Color(0xFF3A1F08), Offset(3f, 3f), 5f))
            )
        }
        7 -> {
            val a = span(t, 1900f, 3000f)
            Text(
                text = "Штурман", color = Color(0xFFEAF0FF).copy(alpha = a),
                style = TextStyle(
                    fontFamily = th.numFont, fontSize = 40.sp, letterSpacing = 10.sp,
                    shadow = Shadow(th.accent, Offset.Zero, 24f)
                )
            )
        }
    }
}

// ---------- общие рисунки ----------

private fun heartPath(cx: Float, cy: Float, s: Float): Path = Path().apply {
    moveTo(cx, cy + s * 0.9f)
    cubicTo(cx - s * 1.5f, cy - s * 0.1f, cx - s * 0.7f, cy - s * 1.2f, cx, cy - s * 0.45f)
    cubicTo(cx + s * 0.7f, cy - s * 1.2f, cx + s * 1.5f, cy - s * 0.1f, cx, cy + s * 0.9f)
    close()
}

// ---------- 2. Принцесса: из центра расходятся сердечки ----------
private fun DrawScope.drawPrincess(t: Float, peak: Float) {
    val c = Offset(size.width / 2, size.height / 2)
    drawRect(Brush.radialGradient(listOf(Color(0xFF3A1038), Color(0xFF0F0510)), c, size.maxDimension * 0.8f))
    val glow = span(t, 100f, peak + 300f)
    drawCircle(
        Brush.radialGradient(listOf(Color(0xFFFF69B4).copy(alpha = 0.45f * glow), Color.Transparent), c, size.width * 0.55f),
        radius = size.width * 0.55f, center = c
    )
    val colors = listOf(Color(0xFFFF69B4), Color(0xFFB57EDC), Color(0xFFFFD700), Color(0xFFFFB3D9))
    val rnd = Random(3)
    val reach = min(size.width, size.height) * 0.62f
    for (i in 0 until 30) {
        val delayMs = i * 38f
        val p = lin(t, 100f + delayMs, 1700f + delayMs)
        if (p <= 0f || p >= 1f) { rnd.nextFloat(); rnd.nextFloat(); continue }
        val ang = i * (2f * PI.toFloat() / 30f) + rnd.nextFloat() * 0.35f
        val dist = reach * (0.35f + rnd.nextFloat() * 0.65f) * ease(p)
        val s = 9f + (i % 4) * 7f
        val pos = Offset(c.x + cos(ang) * dist, c.y + sin(ang) * dist * 1.25f)
        drawPath(heartPath(pos.x, pos.y, s), colors[i % colors.size].copy(alpha = (1f - p) * 0.9f))
    }
}

// ---------- 3. NASCAR: прожектор и машина по нижней кромке ----------
private fun DrawScope.drawNascar(t: Float, peak: Float) {
    drawRect(Color(0xFF0A0A0A))
    val on = when {
        t < 150f -> 0f
        t < 280f -> 0.6f
        t < 380f -> 0.1f
        t < 800f -> ease((t - 380f) / 420f)
        else -> 1f
    }
    val top = Offset(size.width / 2, 0f)
    val cone = Path().apply {
        moveTo(top.x - 14f, 0f); lineTo(top.x + 14f, 0f)
        lineTo(size.width * 0.92f, size.height * 0.86f); lineTo(size.width * 0.08f, size.height * 0.86f); close()
    }
    drawPath(cone, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f * on), Color.White.copy(alpha = 0.10f * on)), 0f, size.height * 0.86f))
    drawOval(Color.White.copy(alpha = 0.18f * on), Offset(size.width * 0.08f, size.height * 0.80f), Size(size.width * 0.84f, size.height * 0.10f))
    // Полосы NASCAR по нижней кромке
    val bh = size.height * 0.012f
    drawRect(Color.White, Offset(0f, size.height - bh * 3), Size(size.width, bh))
    drawRect(Color(0xFFE60019), Offset(0f, size.height - bh * 2), Size(size.width, bh))
    drawRect(Color(0xFF1E4FBF), Offset(0f, size.height - bh), Size(size.width, bh))
    // Машина
    val carW = size.width * 0.34f
    val p = lin(t, 1500f, 7600f)
    val x = -carW + (size.width + carW * 2f) * p
    val y = size.height - bh * 3 - size.height * 0.035f
    if (p > 0f && p < 1f) {
        for (k in 1..5) {
            drawCircle(Color.White.copy(alpha = 0.10f * (1f - k / 6f)), radius = carW * (0.05f + 0.025f * k), center = Offset(x - carW * 0.12f * k, y - carW * 0.03f))
        }
        carSilhouette(x, y, carW)
    }
}

private fun DrawScope.carSilhouette(x: Float, y: Float, w: Float) {
    val h = w * 0.22f
    val body = Path().apply {
        moveTo(x, y); lineTo(x + w * 0.05f, y - h * 0.55f); lineTo(x + w * 0.28f, y - h * 0.62f)
        lineTo(x + w * 0.40f, y - h * 1.05f); lineTo(x + w * 0.70f, y - h * 1.05f); lineTo(x + w * 0.80f, y - h * 0.62f)
        lineTo(x + w * 0.97f, y - h * 0.55f); lineTo(x + w, y); close()
    }
    drawPath(body, Color(0xFF050505))
    drawPath(body, Color(0xFFE60019), style = Stroke(width = 2.5f))
    drawCircle(Color(0xFF050505), w * 0.07f, Offset(x + w * 0.2f, y))
    drawCircle(Color(0xFF050505), w * 0.07f, Offset(x + w * 0.8f, y))
    drawCircle(Color.White.copy(alpha = 0.7f), w * 0.07f, Offset(x + w * 0.2f, y), style = Stroke(2f))
    drawCircle(Color.White.copy(alpha = 0.7f), w * 0.07f, Offset(x + w * 0.8f, y), style = Stroke(2f))
}

// ---------- 4. GT Racing: красные полосы складываются в логотип ----------
private fun DrawScope.drawGt(t: Float, peak: Float) {
    drawRect(Color(0xFF0A0505))
    var x = -size.height
    while (x < size.width) {
        drawLine(Color.White.copy(alpha = 0.035f), Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = 3f)
        x += 9f
    }
    val cy = size.height / 2
    val bandH = size.height * 0.032f
    val settle = span(t, 900f, 1350f)
    for (i in 0 until 5) {
        val start = i * 110f
        val p = span(t, start, start + 700f)
        val startY = cy + (i - 2) * size.height * 0.075f
        val y = startY + (cy + (i - 2) * bandH * 1.5f - startY) * settle
        val len = size.width * (0.55f + 0.1f * (i % 3))
        val xEnd = -len + (size.width + len) * p
        val xLeft = if (settle > 0f) xEnd + (size.width / 2 - len / 2 - xEnd) * settle else xEnd
        drawRect(Color(0xFFE60019).copy(alpha = 0.95f), Offset(xLeft, y - bandH / 2), Size(len, bandH))
    }
    val flash = 1f - lin(t, peak, peak + 300f)
    if (t >= peak && flash > 0f) drawRect(Color.White.copy(alpha = 0.18f * flash))
    // Стикер-подложка под названием
    val a = span(t, 1000f, 1450f)
    drawRect(Color(0xFFE60019).copy(alpha = 0.9f * a), Offset(size.width * 0.17f, cy - size.height * 0.04f), Size(size.width * 0.66f * a, size.height * 0.08f))
}

// ---------- 5. Night Rider: струя синего пламени ----------
private fun DrawScope.drawNight(t: Float, peak: Float) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF02040C), Color(0xFF0A1A3A))))
    val cx = size.width / 2
    val base = size.height * 0.98f
    val grow = span(t, 200f, 1600f)
    val strength = 0.6f + 0.4f * span(t, peak - 400f, peak)
    val fade = 1f - span(t, 5200f, 6600f) * 0.7f
    for (k in 0 until 5) {
        val fl = 1f + 0.06f * sin(t / (35f + k * 7f) + k)
        val hh = size.height * (0.78f - 0.10f * k) * grow * fl * strength
        val bw = size.width * (0.20f - 0.032f * k) * (0.6f + 0.4f * grow)
        val color = when (k) {
            0 -> Color(0xFF0B3CFF); 1 -> Color(0xFF1E7BFF); 2 -> Color(0xFF00D9FF); 3 -> Color(0xFF9EF0FF); else -> Color.White
        }
        val path = Path().apply {
            moveTo(cx - bw, base)
            cubicTo(cx - bw, base - hh * 0.45f, cx - bw * 0.35f, base - hh * 0.8f, cx, base - hh)
            cubicTo(cx + bw * 0.35f, base - hh * 0.8f, cx + bw, base - hh * 0.45f, cx + bw, base)
            close()
        }
        drawPath(path, color.copy(alpha = (0.35f + 0.1f * k) * fade))
    }
    // линии скорости
    val rnd = Random(9)
    for (i in 0 until 22) {
        val lx = rnd.nextFloat() * size.width
        val speed = 0.5f + rnd.nextFloat()
        val len = size.height * (0.06f + rnd.nextFloat() * 0.1f)
        val ly = ((t * 0.0009f * speed + rnd.nextFloat()) % 1f) * (size.height + len) - len
        drawLine(Color(0xFF00D9FF).copy(alpha = 0.18f * grow), Offset(lx, ly), Offset(lx, ly + len), strokeWidth = 2f)
    }
    val pk = 1f - lin(t, peak, peak + 500f)
    if (t >= peak && pk > 0f) drawRect(Color(0xFF00D9FF).copy(alpha = 0.22f * pk))
}

// ---------- 6. Street Underground: красные прожекторы и толпа ----------
private fun DrawScope.drawStreet(t: Float, peak: Float) {
    val shakeAmt = if (t in peak..(peak + 450f)) (1f - lin(t, peak, peak + 450f)) * 12f else 0f
    translate(sin(t / 14f) * shakeAmt, cos(t / 17f) * shakeAmt) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF120403), Color(0xFF2A0C06))))
        // два луча качаются
        for (s in 0..1) {
            val sx = if (s == 0) size.width * 0.12f else size.width * 0.88f
            val sway = sin(t / 380f + s * 2.2f) * 0.35f + (if (s == 0) 0.25f else -0.25f)
            val tipX = size.width / 2 + sway * size.width
            val beam = Path().apply {
                moveTo(sx - 10f, 0f); lineTo(sx + 10f, 0f)
                lineTo(tipX + size.width * 0.18f, size.height * 0.95f); lineTo(tipX - size.width * 0.18f, size.height * 0.95f); close()
            }
            drawPath(beam, Brush.verticalGradient(listOf(Color(0xFFFF2A10).copy(alpha = 0.34f), Color(0xFFFF2A10).copy(alpha = 0.04f))))
        }
        // вспышки
        val f = ((t % 520f) / 520f)
        if (t < 2400f && f < 0.12f) drawRect(Color(0xFFFF3A20).copy(alpha = 0.22f * (1f - f / 0.12f)))
        // толпа бежит по нижнему краю
        for (row in 0..1) {
            val rnd = Random(21 + row)
            val sp = 0.06f + row * 0.04f
            for (i in 0 until 12) {
                val base = rnd.nextFloat()
                val x = (((base + t * sp / 1000f) % 1.2f) - 0.1f) * size.width
                val h = size.height * (0.07f + rnd.nextFloat() * 0.03f) * (1f + row * 0.35f)
                val y = size.height - row * size.height * 0.02f
                val col = Color.Black.copy(alpha = if (row == 0) 0.95f else 0.7f)
                drawRoundRect(col, Offset(x - h * 0.28f, y - h), Size(h * 0.56f, h), CornerRadius(h * 0.2f))
                drawCircle(col, h * 0.17f, Offset(x, y - h * 1.12f))
            }
        }
    }
}

// ---------- 7. Rally Gravel: пыль и тряска ----------
private fun DrawScope.drawRally(t: Float, peak: Float) {
    fun quake(a: Float, at: Float): Float = if (t >= at && t < at + 500f) (1f - lin(t, at, at + 500f)) * a else 0f
    val q = quake(14f, 600f) + quake(18f, peak)
    translate(sin(t / 16f) * q, cos(t / 13f) * q) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF1A0F08), Color(0xFF3A2410), Color(0xFF241608))))
        // лес по низу
        val rnd0 = Random(4)
        for (i in 0 until 16) {
            val x = i / 15f * size.width
            val h = size.height * (0.10f + rnd0.nextFloat() * 0.10f)
            val tri = Path().apply {
                moveTo(x - h * 0.25f, size.height); lineTo(x, size.height - h); lineTo(x + h * 0.25f, size.height); close()
            }
            drawPath(tri, Color(0xFF0E0904))
        }
        // две волны пыли: сначала из центра, потом при ударе
        val c = Offset(size.width / 2, size.height * 0.52f)
        for (burst in 0..1) {
            val t0 = if (burst == 0) 100f else peak - 100f
            val rnd = Random(30 + burst)
            for (i in 0 until 70) {
                val p = lin(t, t0 + rnd.nextFloat() * 160f, t0 + 2300f)
                val ang = rnd.nextFloat() * 2f * PI.toFloat()
                val spd = (0.12f + rnd.nextFloat() * 0.5f) * size.width
                val r = (10f + rnd.nextFloat() * 26f) * (0.5f + p * 2f)
                if (p <= 0f || p >= 1f) continue
                val d = spd * ease(p)
                drawCircle(Color(0xFFC79A66).copy(alpha = (1f - p) * 0.22f), r, Offset(c.x + cos(ang) * d, c.y + sin(ang) * d * 0.6f))
            }
        }
    }
}

// ---------- 8. Drift Night: туман наползает и расходится ----------
private fun DrawScope.drawDrift(t: Float, peak: Float) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF05081A), Color(0xFF0A0F1E), Color(0xFF141A3A))))
    // горная дорога с изгибом
    val road = Path().apply {
        moveTo(size.width * 0.5f, size.height)
        cubicTo(size.width * 0.9f, size.height * 0.78f, size.width * 0.1f, size.height * 0.6f, size.width * 0.55f, size.height * 0.38f)
        cubicTo(size.width * 0.75f, size.height * 0.28f, size.width * 0.45f, size.height * 0.22f, size.width * 0.5f, size.height * 0.16f)
    }
    drawPath(road, Color(0xFF4A6FFF).copy(alpha = 0.35f * span(t, 0f, 1500f)), style = Stroke(width = size.width * 0.05f))
    drawPath(road, Color.White.copy(alpha = 0.25f * span(t, 0f, 1500f)), style = Stroke(width = 2f))
    // туман
    val inward = span(t, 0f, 1200f)
    val outward = span(t, 1400f, 3000f)
    val k = inward * (1f - outward)
    val centers = listOf(
        Offset(0f, size.height * 0.5f), Offset(size.width, size.height * 0.5f),
        Offset(size.width * 0.5f, 0f), Offset(size.width * 0.5f, size.height),
        Offset(0f, 0f), Offset(size.width, size.height)
    )
    val mid = Offset(size.width / 2, size.height / 2)
    for ((i, o) in centers.withIndex()) {
        val pos = Offset(o.x + (mid.x - o.x) * 0.75f * (if (outward > 0f) k else inward), o.y + (mid.y - o.y) * 0.75f * (if (outward > 0f) k else inward))
        val r = size.width * (0.55f + 0.05f * (i % 3))
        drawCircle(
            Brush.radialGradient(listOf(Color.White.copy(alpha = 0.55f * max(k, 0.0f) + 0.0f), Color.Transparent), pos, r),
            radius = r, center = pos
        )
    }
    // огни машины
    val lights = span(t, 1800f, 2600f)
    drawCircle(Color(0xFFB4FF00).copy(alpha = 0.7f * lights), 5f, Offset(size.width * 0.5f - 12f, size.height * 0.62f))
    drawCircle(Color(0xFFB4FF00).copy(alpha = 0.7f * lights), 5f, Offset(size.width * 0.5f + 12f, size.height * 0.62f))
}
