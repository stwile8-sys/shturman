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
import androidx.compose.ui.draw.drawBehind
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
    // Вместо надписи на всех заставках показывается логотип. Появляется в свой момент и по-своему для каждой темы.
    val start = when (th.id) {
        1 -> peak - 250f
        2 -> 3300f
        3 -> 600f
        4 -> peak - 350f
        5 -> 250f
        6 -> 1200f
        else -> 1900f
    }
    val a = when (th.id) {
        1 -> span(t, start, start + 700f)
        2 -> span(t, start, start + 600f)
        3 -> span(t, start, start + 450f)
        4 -> span(t, start, start + 600f)
        5 -> span(t, start, peak + 60f)
        6 -> span(t, start, start + 1200f)
        else -> span(t, start, start + 1100f)
    }
    if (a <= 0f) return
    val swing = sin((t - start) / 110f) * 26f * (1f - lin(t, start, start + 1500f))
    val shake = if (th.id == 5 && t in peak..(peak + 450f)) sin(t / 18f) * 10f * (1f - lin(t, peak, peak + 450f)) else 0f
    val glow = when (th.id) {
        1 -> th.accent
        4 -> th.accent
        7 -> th.accent
        5 -> th.accent
        else -> Color.White
    }
    LogoMark(
        size = 170.dp,
        needleDeg = swing,
        modifier = Modifier
            .drawBehind {
                drawCircle(
                    Brush.radialGradient(listOf(glow.copy(alpha = 0.55f * a), Color.Transparent), center, this.size.minDimension * 0.9f),
                    radius = this.size.minDimension * 0.9f, center = center
                )
            }
            .graphicsLayer {
                this.alpha = a
                when (th.id) {
                    1 -> { val s = 0.7f + 0.3f * a; scaleX = s; scaleY = s }
                    3 -> { transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f); scaleY = 0.2f + 0.8f * a }
                    5 -> { val s = 2.4f - 1.4f * a; scaleX = s; scaleY = s; translationX = shake }
                    7 -> { val s = 1.08f - 0.08f * a; scaleX = s; scaleY = s }
                    else -> Unit
                }
            }
    )
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
        val delayMs = i * 70f
        val p = lin(t, 100f + delayMs, 2600f + delayMs)
        if (p <= 0f || p >= 1f) { rnd.nextFloat(); rnd.nextFloat(); continue }
        val ang = i * (2f * PI.toFloat() / 30f) + rnd.nextFloat() * 0.35f
        val dist = reach * (0.35f + rnd.nextFloat() * 0.65f) * ease(p)
        val s = 9f + (i % 4) * 7f
        val pos = Offset(c.x + cos(ang) * dist, c.y + sin(ang) * dist * 1.25f)
        drawPath(heartPath(pos.x, pos.y, s), colors[i % colors.size].copy(alpha = (1f - p) * 0.9f))
    }
}

// ---------- 3. NASCAR: ночной стадион, прожекторы, трасса, машины проносятся ----------
private fun DrawScope.drawNascar(t: Float, peak: Float) {
    val w = size.width
    val h = size.height
    val horizon = h * 0.52f
    drawRect(Brush.verticalGradient(listOf(Color(0xFF020308), Color(0xFF0B0E1C), Color(0xFF05060A)), 0f, h))

    // трибуны: огоньки и вспышки фотокамер
    val rnd = Random(77)
    for (i in 0 until 70) {
        val x = rnd.nextFloat() * w
        val y = horizon - rnd.nextFloat() * h * 0.20f
        val ph = rnd.nextFloat() * 6.28f
        val tw = 0.35f + 0.65f * (0.5f + 0.5f * sin(t / (180f + rnd.nextFloat() * 300f) + ph))
        val col = if (i % 5 == 0) Color(0xFFFFE9A0) else if (i % 7 == 0) Color(0xFF8FB4FF) else Color.White
        drawCircle(col.copy(alpha = 0.22f * tw * span(t, 300f, 1200f)), 1.5f + rnd.nextFloat() * 2f, Offset(x, y))
    }

    // прожекторы включаются по очереди
    for (i in 0 until 5) {
        val on0 = 400f + i * 170f
        val flick = if (t < on0 + 260f) (if (((t - on0) / 55f).toInt() % 2 == 0) 0.4f else 1f) else 1f
        val on = (if (t < on0) 0f else 1f) * flick
        if (on <= 0f) continue
        val x = w * (0.10f + 0.20f * i)
        val beam = Path().apply {
            moveTo(x - 10f, h * 0.02f); lineTo(x + 10f, h * 0.02f)
            lineTo(x + w * 0.20f * (if (i < 2) 1f else if (i > 2) -1f else 0.2f), horizon); lineTo(x - w * 0.12f * (if (i > 2) -1f else 1f), horizon); close()
        }
        drawPath(beam, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.20f * on), Color.Transparent), h * 0.02f, horizon))
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.7f * on), Color.Transparent), Offset(x, h * 0.03f), w * 0.16f), w * 0.16f, Offset(x, h * 0.03f))
        for (k in 0 until 6) drawCircle(Color.White.copy(alpha = on), 3.5f, Offset(x - 12f + (k % 3) * 12f, h * 0.02f + (k / 3) * 10f))
    }

    // трасса в перспективе
    val speed = when {
        t < 1000f -> 0.2f
        t < peak -> 0.2f + 1.6f * ease((t - 1000f) / (peak - 1000f))
        else -> 1.8f - 1.4f * ease((t - peak) / 2600f)
    }
    val scroll = nascarScroll(t, peak)
    val cx = w / 2
    val road = Path().apply {
        moveTo(cx - w * 0.04f, horizon); lineTo(cx + w * 0.04f, horizon)
        lineTo(w * 1.1f, h); lineTo(-w * 0.1f, h); close()
    }
    drawRect(Color(0xFF07090C), Offset(0f, horizon), Size(w, h - horizon))
    drawPath(road, Color(0xFF16181E))
    val n = 16
    for (i in 0 until n) {
        val z0 = ((i + scroll) % n) / n
        val z1 = z0 + 0.55f / n
        fun yAt(z: Float) = horizon + (h - horizon) * z * z
        fun halfW(z: Float) = w * 0.04f + w * 0.56f * z
        val y0 = yAt(z0); val y1 = yAt(z1)
        val c0 = if (i % 2 == 0) Color(0xFFE60019) else Color.White
        for (side in intArrayOf(-1, 1)) {
            val q = Path().apply {
                moveTo(cx + side * halfW(z0), y0); lineTo(cx + side * (halfW(z0) + w * 0.03f * (0.3f + z0)), y0)
                lineTo(cx + side * (halfW(z1) + w * 0.03f * (0.3f + z1)), y1); lineTo(cx + side * halfW(z1), y1); close()
            }
            drawPath(q, c0)
        }
        val dash = Path().apply {
            moveTo(cx - w * 0.006f * (0.2f + z0 * 3f), y0); lineTo(cx + w * 0.006f * (0.2f + z0 * 3f), y0)
            lineTo(cx + w * 0.006f * (0.2f + z1 * 3f), y1); lineTo(cx - w * 0.006f * (0.2f + z1 * 3f), y1); close()
        }
        drawPath(dash, Color.White.copy(alpha = 0.8f))
    }
    // свет на асфальте
    drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f * span(t, 400f, 1400f)), Color.Transparent), horizon, h * 0.8f), Offset(0f, horizon), Size(w, h * 0.3f))

    // машина проносится справа налево на пике звука, ещё одна чуть позже
    fun pass(from: Float, to: Float, y: Float, cw: Float) {
        val p = lin(t, from, to)
        if (p <= 0f || p >= 1f) return
        val x = w + cw - (w + cw * 2f) * ease(p)
        for (k in 1..7) drawCircle(Color.White.copy(alpha = 0.13f * (1f - k / 8f)), cw * (0.05f + 0.03f * k), Offset(x + cw * (0.55f + 0.11f * k), y - cw * 0.04f))
        for (k in 0 until 4) drawLine(Color.White.copy(alpha = 0.25f * (1f - p)), Offset(x + cw, y - cw * (0.03f + 0.05f * k)), Offset(x + cw * (1.6f + 0.2f * k), y - cw * (0.03f + 0.05f * k)), strokeWidth = 3f)
        carSilhouette(x, y, cw)
    }
    pass(peak - 500f, peak + 700f, h * 0.84f, w * 0.52f)
    pass(peak + 900f, peak + 2000f, h * 0.70f, w * 0.30f)
    // вспышка при первом проезде
    val fl = 1f - lin(t, peak, peak + 350f)
    if (t >= peak && fl > 0f) drawRect(Color.White.copy(alpha = 0.16f * fl))
    // луч на логотип
    val spot = span(t, 3200f, 3700f)
    if (spot > 0f) {
        val cone = Path().apply { moveTo(cx - 12f, 0f); lineTo(cx + 12f, 0f); lineTo(cx + w * 0.30f, h * 0.62f); lineTo(cx - w * 0.30f, h * 0.62f); close() }
        drawPath(cone, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.28f * spot), Color.White.copy(alpha = 0.05f * spot)), 0f, h * 0.62f))
    }
    // полосы NASCAR по нижней кромке
    val bh = h * 0.012f
    drawRect(Color.White, Offset(0f, h - bh * 3), Size(w, bh))
    drawRect(Color(0xFFE60019), Offset(0f, h - bh * 2), Size(w, bh))
    drawRect(Color(0xFF1E4FBF), Offset(0f, h - bh), Size(w, bh))
}

/** Положение разметки трассы по времени: чем быстрее едем, тем быстрее она бежит (интеграл скорости). */
private fun nascarScroll(t: Float, peak: Float): Float {
    var s = 0f
    var x = 0f
    while (x < t) {
        val sp = when {
            x < 1000f -> 0.2f
            x < peak -> 0.2f + 1.6f * ease((x - 1000f) / (peak - 1000f))
            else -> 1.8f - 1.4f * ease((x - peak) / 2600f)
        }
        s += sp * 0.012f
        x += 16f
    }
    return s
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
    drawRect(Color(0xFF1E4FBF), Offset(x + w * 0.1f, y - h * 0.32f), Size(w * 0.8f, h * 0.10f))
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
    val settle = span(t, 650f, 1000f)
    for (i in 0 until 5) {
        val start = i * 70f
        val p = span(t, start, start + 500f)
        val startY = cy + (i - 2) * size.height * 0.075f
        val y = startY + (cy + (i - 2) * bandH * 1.5f - startY) * settle
        val len = size.width * (0.55f + 0.1f * (i % 3))
        val xEnd = -len + (size.width + len) * p
        val xLeft = if (settle > 0f) xEnd + (size.width / 2 - len / 2 - xEnd) * settle else xEnd
        drawRect(Color(0xFFE60019).copy(alpha = 0.95f), Offset(xLeft, y - bandH / 2), Size(len, bandH))
    }
    val flash = 1f - lin(t, peak, peak + 300f)
    if (t >= peak && flash > 0f) drawRect(Color.White.copy(alpha = 0.18f * flash))
}

// ---------- 5. Night Rider: струя синего пламени ----------
private fun DrawScope.drawNight(t: Float, peak: Float) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF02040C), Color(0xFF0A1A3A))))
    val cx = size.width / 2
    val base = size.height * 0.98f
    val grow = span(t, 200f, 2200f)
    val strength = 0.6f + 0.4f * span(t, peak - 400f, peak)
    val fade = 1f - span(t, 6200f, 7700f) * 0.7f
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
    val inward = span(t, 0f, 800f)
    val outward = span(t, 900f, 2300f)
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
    val lights = span(t, 1500f, 2200f)
    drawCircle(Color(0xFFB4FF00).copy(alpha = 0.7f * lights), 5f, Offset(size.width * 0.5f - 12f, size.height * 0.62f))
    drawCircle(Color(0xFFB4FF00).copy(alpha = 0.7f * lights), 5f, Offset(size.width * 0.5f + 12f, size.height * 0.62f))
}
