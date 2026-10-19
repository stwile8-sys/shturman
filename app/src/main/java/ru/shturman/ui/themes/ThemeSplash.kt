package ru.shturman.ui.themes

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import ru.shturman.ui.SplashOverlay
import ru.shturman.ui.theme.ShColors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

private const val HOLD_MS = 500L // чёрный экран, пока приложение грузится

private fun ease(x: Float): Float { val t = x.coerceIn(0f, 1f); return t * t * (3f - 2f * t) }
private fun span(t: Float, a: Float, b: Float) = ease((t - a) / (b - a))
private fun lin(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)
/** Остаток от деления, всегда неотрицательный (как в Python), чтобы движение по кругу не «прыгало» в минус. */
private fun fmod(a: Float, b: Float): Float = ((a % b) + b) % b

/**
 * Загрузочный экран: у каждой темы свой. Длительность подогнана под звук заставки темы
 * (splashMs), а главный момент анимации совпадает с пиком звука (splashPeakMs).
 * Камера, вспышка на пике, плёнка и уход в приложение — общий «кинослой» (SplashStage).
 * Классика оставляет прежнюю заставку с самолётом.
 */
@Composable
fun ThemeSplash(onPlaySound: () -> Unit, onFinished: () -> Unit) {
    val th = ShColors.T
    // «Спорт-премиум» берёт заставку «Классики»: самолёт и welcome.ogg
    if (th.id == 0 || th.id == Themes.PREMIUM) {
        SplashOverlay(onPlaySound = onPlaySound, onFinished = onFinished)
        return
    }
    if (th.id == Themes.APEX) {
        ApexIntro(onFinished = onFinished)
        return
    }
    val peak = th.splashPeakMs.toFloat()
    SplashStage(
        totalMs = th.splashMs.toFloat(),
        peakMs = peak,
        holdMs = HOLD_MS,
        soundAtMs = 0f,
        look = splashLookFor(th.id),
        onPlaySound = onPlaySound,
        onFinished = onFinished
    ) { t ->
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
    }
}

// ---------- общие рисунки ----------

private val WHITE = Color.White
private val BLACK = Color.Black
private val CLEAR = Color.Transparent

private fun Color.a(x: Float): Color = copy(alpha = x.coerceIn(0f, 1f))
private fun mix(a: Color, b: Color, t: Float): Color = lerp(a, b, t.coerceIn(0f, 1f))
private fun DrawScope.unit(): Float = min(size.width, size.height) / 100f
private fun pathOf(block: Path.() -> Unit): Path = Path().apply(block)

/** Мягкое свечение: круг с радиальным градиентом. */
private fun DrawScope.glow(c: Offset, r: Float, col: Color, a: Float) {
    if (a <= 0.003f || r <= 1f) return
    drawCircle(Brush.radialGradient(listOf(col.a(a), col.a(a * 0.35f), CLEAR), c, r), r, c)
}

/** Четырёхлучевая звёздочка-искра. */
private fun DrawScope.star4(c: Offset, r: Float, col: Color) {
    if (r <= 0f) return
    drawPath(pathOf {
        moveTo(c.x, c.y - r)
        quadraticTo(c.x, c.y, c.x + r, c.y); quadraticTo(c.x, c.y, c.x, c.y + r)
        quadraticTo(c.x, c.y, c.x - r, c.y); quadraticTo(c.x, c.y, c.x, c.y - r); close()
    }, col)
}

/** Затемнение по краям кадра. */
private fun DrawScope.vignette(a: Float) {
    drawRect(Brush.radialGradient(listOf(CLEAR, CLEAR, BLACK.a(a)), Offset(size.width / 2, size.height / 2), max(size.width, size.height) * 0.75f))
}

private fun heartPath(cx: Float, cy: Float, s: Float): Path = pathOf {
    moveTo(cx, cy + s * 0.9f)
    cubicTo(cx - s * 1.5f, cy - s * 0.1f, cx - s * 0.7f, cy - s * 1.2f, cx, cy - s * 0.45f)
    cubicTo(cx + s * 0.7f, cy - s * 1.2f, cx + s * 1.5f, cy - s * 0.1f, cx, cy + s * 0.9f)
    close()
}

/** Классическая кривая сердца, a от 0 до 2π. */
private fun heartPt(a: Float, s: Float, c: Offset): Offset {
    val x = 16f * sin(a).pow(3)
    val y = 13f * cos(a) - 5f * cos(2 * a) - 2f * cos(3 * a) - cos(4 * a)
    return Offset(c.x + x * s / 16f, c.y - y * s / 16f)
}

// ---------- 2. Принцесса: контур сердца рисуется искрой, на пике заливается и разлетается сердечками ----------
private fun DrawScope.drawPrincess(t: Float, peak: Float) {
    val w = size.width; val h = size.height; val u = unit()
    val c = Offset(w / 2, h * 0.46f)
    drawRect(Brush.verticalGradient(listOf(Color(0xFF1A0820), Color(0xFF3A1240), Color(0xFF5A1E4E), Color(0xFF2A0A24))))
    // мягкие «боке»-круги плывут вверх
    var rnd = Random(5)
    val bokeh = listOf(Color(0xFFFF8AC8), Color(0xFFC9A2FF), Color(0xFFFF6FB0))
    for (i in 0 until 26) {
        val bx = rnd.nextFloat() * w; val by0 = rnd.nextFloat(); val sp = 0.03f + rnd.nextFloat() * 0.05f
        val r = u * (3f + rnd.nextFloat() * 9f)
        val y = (fmod(by0 - t / 1000f * sp, 1.2f) - 0.1f) * h
        drawCircle(bokeh[i % 3].a(0.035f + 0.025f * (i % 3)), r, Offset(bx + sin(t / 1300f + i) * u * 3f, y))
    }
    // мерцающие звёздочки
    rnd = Random(11)
    for (i in 0 until 40) {
        val sx = rnd.nextFloat() * w; val sy = rnd.nextFloat() * h; val ph = rnd.nextFloat() * 6.28f
        val tw = 0.5f + 0.5f * sin(t / 260f + ph)
        val a = span(t, 0f, 600f) * (0.25f + 0.75f * tw)
        star4(Offset(sx, sy), u * (0.6f + tw), Color(0xFFFFF0FA).a(0.8f * a))
    }
    // маленькие сердечки разлетаются после пика (позади большого)
    rnd = Random(3)
    val cols = listOf(Color(0xFFFF69B4), Color(0xFFB57EDC), Color(0xFFFFD700), Color(0xFFFFB3D9))
    for (i in 0 until 36) {
        val ang = rnd.nextFloat() * 2f * PI.toFloat(); val spd = 0.35f + rnd.nextFloat() * 0.65f; val dl = rnd.nextFloat() * 300f
        val p = lin(t, peak + dl, peak + dl + 2600f)
        if (p <= 0f || p >= 1f) continue
        val dist = u * 70f * spd * (1f - (1f - p) * (1f - p))
        val pos = Offset(c.x + cos(ang) * dist, c.y + sin(ang) * dist - p * p * u * 25f)
        val s = u * (1.6f + (i % 4) * 0.9f) * (1f - 0.4f * p)
        rotate(sin(t / 200f + i) * 20f, pos) {
            drawPath(heartPath(pos.x, pos.y, s), cols[i % 4].a((1f - p) * 0.95f))
        }
    }
    // большое сердце: контур рисуется точками до пика
    val big = u * 30f
    val draw = lin(t, 0f, peak)
    val n = 120
    for (k in 0 until (n * draw).toInt()) {
        drawCircle(Color(0xFFFFB3DE).a(0.9f), u * 0.7f, heartPt(2f * PI.toFloat() * k / n, big, c))
    }
    if (t < peak) {
        glow(heartPt(2f * PI.toFloat() * draw, big, c), u * 8f, WHITE, 0.9f)
        // искристый хвост за «пером»
        for (k in 1..10) {
            val d = draw - k * 0.012f
            if (d <= 0f) break
            val tp = heartPt(2f * PI.toFloat() * d, big, c)
            val j = Offset(sin(k * 2.3f + t / 90f) * u * 1.2f, cos(k * 1.7f + t / 110f) * u * 1.2f)
            star4(Offset(tp.x + j.x, tp.y + j.y), u * (1.4f - k * 0.1f), Color(0xFFFFE3F3).a(0.8f * (1f - k / 11f)))
        }
    }
    // мягкие лучи за сердцем после пика
    val rays = span(t, peak, peak + 700f) * (1f - 0.6f * span(t, 3600f, 5000f))
    raysFrom(c, 14, w * 0.9f, Color(0xFFFFB3DE), 0.10f * rays, t / 5000f, 0.05f)
    // на пике вспыхивает и заливается, потом мягко «бьётся»
    val fillA = span(t, peak, peak + 500f)
    if (fillA > 0f) {
        val pulse = 1f + 0.04f * sin(max(0f, t - peak) / 140f) * (1f - lin(t, peak, peak + 2500f))
        glow(c, big * 2.2f * pulse, Color(0xFFFF69B4), 0.45f * fillA)
        val hp = Path()
        for (k in 0..n) {
            val p = heartPt(2f * PI.toFloat() * k / n, big * pulse, c)
            if (k == 0) hp.moveTo(p.x, p.y) else hp.lineTo(p.x, p.y)
        }
        hp.close()
        drawPath(hp, Brush.verticalGradient(listOf(Color(0xFFFF9AD5).a(fillA), Color(0xFFE0479E).a(fillA)), c.y - big, c.y + big))
        drawPath(hp, Color(0xFFFFE3F3).a(0.9f * fillA), style = Stroke(width = u * 0.8f))
        drawOval(WHITE.a(0.35f * fillA), Offset(c.x - big * 0.62f, c.y - big * 0.62f), Size(big * 0.42f, big * 0.22f))
        // три искры кружат вокруг сердца
        for (k in 0 until 3) {
            val a = (t - peak) / 900f + k * 2f * PI.toFloat() / 3f
            val sp = Offset(c.x + cos(a) * big * 1.35f, c.y + sin(a) * big * 0.55f - big * 0.05f)
            glow(sp, u * 5f, Color(0xFFFFE6F6), 0.6f * fillA)
            star4(sp, u * (2.2f + 0.6f * sin(t / 120f + k)), WHITE.a(fillA))
        }
    }
    // ударная волна на пике и «удары сердца»
    shockRing(t, peak, c, w * 0.85f, Color(0xFFFFC2E4))
    shockRing(t, peak + 880f, c, w * 0.6f, Color(0xFFFFD6EE), 900f, 0.6f)
    shockRing(t, peak + 1760f, c, w * 0.5f, Color(0xFFFFD6EE), 900f, 0.4f)
    vignette(0.55f)
}

// ---------- 3. NASCAR: ночной стадион, прожекторы, три машины проносятся, клетчатый флаг ----------

/** Сток-кар сбоку, едет влево. x — нос, y — линия земли, len — длина. */
private fun DrawScope.stockCar(x: Float, y: Float, len: Float, body: Color, stripe: Color, numBg: Color) {
    val hh = len * 0.24f
    // шлейф размытия позади
    for (k in 0 until 6) {
        drawRoundRect(body.a(0.10f * (1f - k / 6f)), Offset(x + len * (0.15f + 0.12f * k), y - hh * 0.9f), Size(len * 0.9f, hh * 0.75f), CornerRadius(hh * 0.3f))
    }
    val shell = pathOf {
        moveTo(x, y - hh * 0.18f)
        lineTo(x + len * 0.02f, y - hh * 0.5f); lineTo(x + len * 0.22f, y - hh * 0.62f)
        cubicTo(x + len * 0.30f, y - hh * 1.02f, x + len * 0.36f, y - hh * 1.05f, x + len * 0.62f, y - hh * 1.05f)
        lineTo(x + len * 0.80f, y - hh * 0.70f); lineTo(x + len * 0.98f, y - hh * 0.70f); lineTo(x + len, y - hh * 0.95f)
        lineTo(x + len * 1.02f, y - hh * 0.95f); lineTo(x + len, y - hh * 0.25f); lineTo(x + len * 0.97f, y); lineTo(x + len * 0.03f, y)
        close()
    }
    drawPath(shell, Brush.verticalGradient(listOf(mix(body, WHITE, 0.25f), body, mix(body, BLACK, 0.45f)), y - hh * 1.05f, y))
    drawPath(pathOf {
        moveTo(x + len * 0.31f, y - hh * 0.66f)
        cubicTo(x + len * 0.34f, y - hh * 0.95f, x + len * 0.38f, y - hh * 0.97f, x + len * 0.58f, y - hh * 0.97f)
        lineTo(x + len * 0.70f, y - hh * 0.66f); close()
    }, Color(0xFF0A0F18))
    drawLine(WHITE.a(0.35f), Offset(x + len * 0.40f, y - hh * 0.93f), Offset(x + len * 0.36f, y - hh * 0.7f), unit() * 0.5f)
    drawRect(stripe, Offset(x + len * 0.04f, y - hh * 0.42f), Size(len * 0.92f, hh * 0.12f))
    drawCircle(numBg, hh * 0.27f, Offset(x + len * 0.52f, y - hh * 0.38f))
    drawCircle(BLACK.a(0.85f), hh * 0.27f, Offset(x + len * 0.52f, y - hh * 0.38f), style = Stroke(hh * 0.05f))
    for (wx in floatArrayOf(0.2f, 0.8f)) {
        val cc = Offset(x + len * wx, y - hh * 0.02f)
        drawCircle(Color(0xFF080808), hh * 0.30f, cc)
        drawCircle(Color(0xFFCFCFCF).a(0.85f), hh * 0.17f, cc)
        drawCircle(Color(0xFF202020), hh * 0.07f, cc)
    }
    drawOval(Color(0xFFFFF6C8).a(0.9f), Offset(x + len * 0.005f, y - hh * 0.42f), Size(len * 0.04f, hh * 0.12f))
}

private fun DrawScope.drawNascar(t: Float, peak: Float) {
    val w = size.width; val h = size.height; val u = unit()
    val lightOn = FloatArray(4) { span(t, 250f + it * 220f, 420f + it * 220f) }
    val lit = lightOn.sum() / 4f
    drawRect(Brush.verticalGradient(listOf(Color(0xFF02030A), Color(0xFF0A1024), Color(0xFF151B33)), 0f, h * 0.5f))
    glow(Offset(w / 2, h * 0.32f), w * 0.9f, Color(0xFF9FB6FF), 0.18f * lit)
    // мачты прожекторов включаются по очереди с миганием
    val masts = floatArrayOf(0.08f, 0.36f, 0.64f, 0.92f)
    for (i in 0 until 4) {
        val x = w * masts[i]; val top = h * 0.07f
        drawLine(Color(0xFF1C2130), Offset(x, top), Offset(x, h * 0.36f), u * 0.9f)
        val flick = if (t > 420f + i * 220f + 200f) 1f else if ((t / 50f).toInt() % 2 == 1) 0.35f else 1f
        val a = lightOn[i] * flick
        drawRect(Color(0xFF20263A), Offset(x - u * 5f, top - u * 3f), Size(u * 10f, u * 5f))
        for (r in 0 until 2) for (k in 0 until 5) {
            drawCircle(Color(0xFFFFFBE8).a(0.25f + 0.75f * a), u * 0.7f, Offset(x - u * 4f + k * u * 2f, top - u * 2f + r * u * 2f))
        }
        glow(Offset(x, top), u * 24f, Color(0xFFFFF7DA), 0.75f * a)
        val bx = w / 2 + (x - w / 2) * 0.2f
        drawPath(pathOf {
            moveTo(x - u * 4f, top); lineTo(x + u * 4f, top); lineTo(bx + u * 22f, h * 0.72f); lineTo(bx - u * 22f, h * 0.72f); close()
        }, Brush.verticalGradient(listOf(Color(0xFFFFF7DA).a(0.16f * a), CLEAR), top, h * 0.72f))
    }
    // два поисковых луча шарят по небу перед заездом
    val search = lit * span(t, peak - 900f, peak - 200f) * (1f - span(t, 4200f, 4900f))
    if (search > 0f) {
        for (side in intArrayOf(-1, 1)) {
            val o = Offset(w * (0.5f + side * 0.32f), h * 0.5f)
            val a = -PI.toFloat() / 2f + sin(t / 700f + side * 1.3f) * 0.55f
            val len = h * 0.62f
            val e = Offset(o.x + cos(a) * len, o.y + sin(a) * len)
            val nx = -sin(a) * len * 0.07f; val ny = cos(a) * len * 0.07f
            drawPath(pathOf { moveTo(o.x, o.y); lineTo(e.x + nx, e.y + ny); lineTo(e.x - nx, e.y - ny); close() },
                Brush.radialGradient(listOf(Color(0xFFE8EEFF).a(0.16f * search), CLEAR), o, len))
        }
    }
    // трибуны ступенями
    for (r in 0 until 5) {
        val y0 = h * (0.30f + r * 0.04f)
        drawRect(mix(Color(0xFF0D101C), Color(0xFF1C2236), r / 5f * lit), Offset(0f, y0), Size(w, h * 0.04f))
        drawLine(Color(0xFF2A3150).a(0.6f), Offset(0f, y0), Offset(w, y0), u * 0.3f)
    }
    var rnd = Random(77)
    val crowd = listOf(Color(0xFFE8ECFF), Color(0xFFFF5A5A), Color(0xFF6E9BFF), Color(0xFFFFD86E))
    for (i in 0 until 160) {
        val x = rnd.nextFloat() * w; val y = h * (0.305f + rnd.nextFloat() * 0.19f); val ph = rnd.nextFloat() * 6.28f
        drawCircle(crowd[i % 4].a((0.18f + 0.25f * lit) * (0.6f + 0.4f * sin(t / 230f + ph))), u * 0.55f, Offset(x, y))
    }
    // вспышки фотокамер, чаще у пика
    rnd = Random(91)
    for (i in 0 until 30) {
        val at = rnd.nextFloat() * 5200f; val x = rnd.nextFloat() * w; val y = h * (0.31f + rnd.nextFloat() * 0.18f)
        val near = if (abs(at - peak) < 700f) 1.5f else 1f
        val f = 1f - lin(t, at, at + 140f * near)
        if (t >= at && f > 0f) glow(Offset(x, y), u * 4f * near, WHITE, 0.9f * f)
    }
    // стена с рекламой
    drawRect(Color(0xFF0B0D14), Offset(0f, h * 0.50f), Size(w, h * 0.035f))
    val ads = listOf(Color(0xFFE60019), Color(0xFFFFD100), Color(0xFF1E4FBF), Color(0xFFF2F2F2), Color(0xFF00A651))
    for (k in 0 until 6) drawRect(ads[k % 5].a(0.75f * (0.3f + 0.7f * lit)), Offset(k * w / 6f + u, h * 0.507f), Size(w / 6f - u * 2f, h * 0.02f))
    // трасса, газон, отбойник
    drawRect(Brush.verticalGradient(listOf(mix(Color(0xFF1B1D24), Color(0xFF2A2D38), lit), Color(0xFF0E0F13)), h * 0.535f, h * 0.84f), Offset(0f, h * 0.535f), Size(w, h * 0.305f))
    drawRect(Color(0xFF090A0D).a(0.6f), Offset(0f, h * 0.70f), Size(w, h * 0.05f))
    drawLine(WHITE.a(0.75f), Offset(0f, h * 0.58f), Offset(w, h * 0.58f), u * 0.6f)
    drawLine(Color(0xFFFFD100).a(0.7f), Offset(0f, h * 0.815f), Offset(w, h * 0.815f), u * 0.6f)
    drawRect(Brush.verticalGradient(listOf(mix(Color(0xFF0C2414), Color(0xFF16402A), lit), Color(0xFF07140B)), h * 0.84f, h * 0.93f), Offset(0f, h * 0.84f), Size(w, h * 0.09f))
    for (k in 0 until 8) drawRect(WHITE.a(0.025f * lit), Offset(k * w / 4f - w / 8f, h * 0.84f), Size(w / 8f, h * 0.09f))
    drawRect(Brush.verticalGradient(listOf(mix(Color(0xFF9AA0AA), Color(0xFFD9DDE3), lit), Color(0xFF4A4F58)), h * 0.905f, h * 0.964f), Offset(0f, h * 0.905f), Size(w, h * 0.059f))
    for (k in 0 until 9) drawRect(if (k % 2 == 0) Color(0xFFE60019).a(0.85f) else WHITE.a(0.8f), Offset(k * w / 8f - w / 16f, h * 0.93f), Size(w / 8f, h * 0.012f))
    // свет фар до появления машин
    val pre = span(t, peak - 1100f, peak - 250f) * (1f - span(t, peak, peak + 300f))
    glow(Offset(w * 1.05f, h * 0.70f), w * 0.6f, Color(0xFFFFF3C4), 0.55f * pre)
    // линии скорости вокруг пика
    val sp = span(t, peak - 500f, peak) * (1f - span(t, peak + 900f, peak + 1600f))
    if (sp > 0f) {
        rnd = Random(13)
        for (i in 0 until 18) {
            val y = h * (0.6f + rnd.nextFloat() * 0.2f); val len = w * (0.15f + rnd.nextFloat() * 0.35f)
            val x = fmod(rnd.nextFloat() - (t / 1000f) * (1.5f + rnd.nextFloat()), 1.4f) * w - w * 0.2f
            drawLine(WHITE.a(0.10f * sp), Offset(x, y), Offset(x + len, y), u * 0.4f)
        }
    }
    // три машины проносятся справа налево
    fun carPass(t0: Float, dur: Float, y: Float, len: Float, body: Color, stripe: Color, nb: Color) {
        val p = lin(t, t0, t0 + dur)
        if (p <= 0f || p >= 1f) return
        val x = w + u * 5f - (w + len + u * 10f) * p
        drawOval(BLACK.a(0.5f), Offset(x - len * 0.02f, y - len * 0.02f), Size(len * 1.05f, len * 0.05f))
        stockCar(x, y, len, body, stripe, nb)
        for (k in 0 until 8) {
            val q = k / 8f
            glow(Offset(x + len * (1f + q * 0.9f), y - len * 0.03f), len * (0.12f + 0.18f * q), Color(0xFFBFC4CC), 0.22f * (1f - q))
        }
    }
    carPass(peak - 380f, 900f, h * 0.745f, w * 0.95f, Color(0xFFE60019), WHITE, Color(0xFFFFD100))
    carPass(peak - 120f, 950f, h * 0.665f, w * 0.72f, Color(0xFF1E4FBF), Color(0xFFFFD100), WHITE)
    carPass(peak + 700f, 1000f, h * 0.705f, w * 0.8f, Color(0xFFFFD100), Color(0xFF111111), Color(0xFFE60019))
    // искры из-под днища на пике
    rnd = Random(41)
    for (i in 0 until 40) {
        val st = peak + rnd.nextFloat() * 500f
        val x0 = w * (0.3f + rnd.nextFloat() * 0.6f); val vx = u * (20f + rnd.nextFloat() * 40f); val vy = -u * (8f + rnd.nextFloat() * 18f)
        val p = lin(t, st, st + 450f)
        if (p <= 0f || p >= 1f) continue
        val a = Offset(x0 + vx * p, h * 0.76f + vy * p + u * 30f * p * p)
        val b = Offset(a.x - vx * 0.06f, a.y - (vy + u * 60f * p) * 0.06f)
        drawLine(Color(0xFFFFC14D).a(1f - p), a, b, u * 0.5f, cap = StrokeCap.Round)
    }
    // клетчатый флаг машет сверху
    val fa = span(t, 3500f, 3900f)
    if (fa > 0f) {
        val fx = w * 0.5f; val fy = h * 0.16f; val cell = u * 4.2f
        drawLine(Color(0xFFBDBDBD).a(fa), Offset(fx - u * 18f, fy + u * 30f), Offset(fx - u * 14f, fy - u * 4f), u * 0.9f)
        for (r in 0 until 5) for (k in 0 until 8) {
            val wave = sin(t / 90f - k * 0.8f) * u * 2.2f * (k / 8f)
            val col = if ((r + k) % 2 == 0) WHITE else Color(0xFF111111)
            drawRect(col.a(fa), Offset(fx - u * 14f + k * cell, fy - u * 4f + r * cell + wave), Size(cell + 0.6f, cell + 0.6f))
        }
    }
    // блики от прожекторов и конфетти под флаг
    for (i in 1..2) flareAt(Offset(w * masts[i], h * 0.07f), Color(0xFFFFF3D0), 0.45f * lightOn[i])
    confettiRain(t, 3600f, listOf(WHITE, Color(0xFFE60019), Color(0xFFFFD100), Color(0xFF1E4FBF)), 80, 5)
    // финальная шахматная шторка
    val wipe = lin(t, 4900f, 5600f)
    if (wipe > 0f) {
        val cell = w / 8f
        val rows = (h / cell).toInt() + 2
        for (r in 0 until rows) for (k in 0 until 8) {
            val d = (k + r) / (8f + h / cell)
            val s = (wipe * 1.8f - d).coerceIn(0f, 1f)
            if (s <= 0f) continue
            val sz = cell * s
            drawRect(if ((r + k) % 2 == 0) WHITE else Color(0xFF0A0A0A), Offset(k * cell + (cell - sz) / 2, r * cell + (cell - sz) / 2), Size(sz, sz))
        }
    }
    val bh = h * 0.012f
    drawRect(WHITE, Offset(0f, h - bh * 3), Size(w, bh))
    drawRect(Color(0xFFE60019), Offset(0f, h - bh * 2), Size(w, bh))
    drawRect(Color(0xFF1E4FBF), Offset(0f, h - bh), Size(w, bh))
    vignette(0.5f)
}

// ---------- 4. GT Racing: карбон, полосы, тахометр: стрелка в отсечку на пике, лампы переключения ----------

/** Карбоновое плетение: ряды «пунктиром», сдвинутые через один. */
private fun DrawScope.carbon(dim: Float) {
    val w = size.width; val h = size.height; val cell = unit() * 2.2f
    drawRect(Color(0xFF0B0B0D))
    drawRect(WHITE.a(0.025f * dim))
    var y = 0f; var r = 0
    while (y < h) {
        drawLine(
            WHITE.a(0.035f * dim), Offset(0f, y + cell / 2), Offset(w, y + cell / 2), cell - 0.6f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(cell - 0.6f, cell + 0.6f), if (r % 2 == 0) 0f else cell)
        )
        y += cell; r++
    }
    drawRect(Brush.linearGradient(listOf(CLEAR, WHITE.a(0.06f * dim), CLEAR), Offset.Zero, Offset(w, h)))
}

/** Обороты 0..9: разгон до отсечки на пике, переключение передачи, снова разгон, сброс в конце. */
private fun tachRpm(t: Float, peak: Float): Float = when {
    t < peak -> 1f + 7.6f * ease(t / peak).pow(1.4f)
    t < peak + 160f -> 8.6f + 0.25f * sin((t - peak) / 18f)
    t < peak + 420f -> 8.6f - 4.6f * ease((t - peak - 160f) / 260f)
    t < 2500f -> 4f + 3.8f * ease((t - peak - 420f) / (2500f - peak - 420f))
    else -> 7.8f - 6.8f * ease((t - 2500f) / 700f)
}

private fun DrawScope.drawGt(t: Float, peak: Float) {
    val w = size.width; val h = size.height; val u = unit()
    carbon(span(t, 0f, 300f))
    // диагональные гоночные полосы влетают слева
    val bands = listOf(Color(0xFFE60019) to 0f, WHITE to 0.075f, Color(0xFFE60019) to 0.13f)
    bands.forEachIndexed { k, (col, off) ->
        val p = span(t, k * 60f, 420f + k * 60f)
        val bw = w * (if (k != 1) 0.06f else 0.025f)
        val x0 = -w * 0.6f + (w * 0.95f + off * w) * p
        drawPath(pathOf { moveTo(x0, h); lineTo(x0 + bw, h); lineTo(x0 + bw + h * 0.45f, 0f); lineTo(x0 + h * 0.45f, 0f); close() }, col.a(0.9f))
    }
    val c = Offset(w / 2, h * 0.48f); val rr = u * 38f
    val appear = span(t, 80f, 450f)
    // шевроны «>>>» бегут сверху и снизу, быстрее после пика
    val run = t / 1000f * (if (t < peak) 0.6f else 1.4f)
    for (row in 0..1) {
        val yy = if (row == 0) h * 0.17f else h * 0.83f
        for (k in 0 until 9) {
            val x = fmod(k / 8f + run * (if (row == 0) 1f else -1f), 1.125f) * w * 1.2f - w * 0.1f
            val hot = (k + (t / 120f).toInt()) % 3 == 0 && t > peak
            val a = appear * (0.18f + if (hot) 0.5f else 0f)
            drawPath(pathOf { moveTo(x, yy - u * 3f); lineTo(x + u * 3f, yy); lineTo(x, yy + u * 3f) },
                (if (row == 0) Color(0xFFE60019) else WHITE).a(a), style = Stroke(width = u * 1.1f, cap = StrokeCap.Round))
        }
    }
    val rpm = tachRpm(t, peak)
    val redline = rpm > 7.9f
    // корпус прибора
    glow(c, rr * 1.7f, if (redline) Color(0xFFE60019) else Color(0xFF6E8CFF), (if (redline) 0.35f else 0.15f) * appear)
    if (appear > 0f) {
        drawCircle(Brush.radialGradient(listOf(Color(0xFF1A1B20), Color(0xFF0A0A0C)), c, rr * 1.08f), rr * 1.08f * appear, c)
        drawCircle(Brush.linearGradient(listOf(Color(0xFFDADDE2), Color(0xFF4A4D55), Color(0xFFDADDE2)), Offset(c.x - rr, c.y - rr), Offset(c.x + rr, c.y + rr)),
            rr * 1.08f * appear, c, style = Stroke(u * 1.6f))
    }
    if (appear < 0.05f) { vignette(0.6f); return }
    fun ang(v: Float): Float = ((135f + 270f * v / 9f) * PI / 180f).toFloat()
    // шкала
    for (i in 0..90) {
        val v = i / 10f; val a = ang(v); val major = i % 10 == 0; val mid = i % 5 == 0
        if (!(major || mid || i % 2 == 0)) continue
        val r0 = rr * (if (major) 0.78f else if (mid) 0.86f else 0.9f); val r1 = rr * 0.96f
        val col = if (v >= 7.5f) Color(0xFFFF2A2A) else WHITE
        val litA = if (v <= rpm) 1f else 0.45f
        drawLine(col.a(litA * appear), Offset(c.x + cos(a) * r0, c.y + sin(a) * r0), Offset(c.x + cos(a) * r1, c.y + sin(a) * r1), u * (if (major) 0.9f else 0.45f))
    }
    // красная зона и подсветка пройденной дуги
    val rz = Path()
    for (k in 0..20) { val a = ang(7.5f + 1.5f * k / 20f); val x = c.x + cos(a) * rr; val y = c.y + sin(a) * rr; if (k == 0) rz.moveTo(x, y) else rz.lineTo(x, y) }
    drawPath(rz, Color(0xFFE60019).a(0.9f * appear), style = Stroke(u * 1.6f))
    val arcP = Path()
    for (k in 0..40) { val a = ang(rpm * k / 40f); val x = c.x + cos(a) * rr * 0.72f; val y = c.y + sin(a) * rr * 0.72f; if (k == 0) arcP.moveTo(x, y) else arcP.lineTo(x, y) }
    drawPath(arcP, (if (redline) Color(0xFFFF2A2A) else Color(0xFF6EC1FF)).a(0.55f * appear), style = Stroke(u * 2.2f, cap = StrokeCap.Round))
    // шлейф стрелки: несколько «призраков» прошлых положений
    for (k in 1..5) {
        val ga = ang(tachRpm(max(0f, t - k * 28f), peak))
        val gtip = Offset(c.x + cos(ga) * rr * 0.95f, c.y + sin(ga) * rr * 0.95f)
        drawLine(Color(0xFFFF3B1F).a(0.16f * (1f - k / 6f) * appear), c, gtip, u * 1.1f, cap = StrokeCap.Round)
    }
    // стрелка
    val a = ang(rpm)
    val tip = Offset(c.x + cos(a) * rr * 0.95f, c.y + sin(a) * rr * 0.95f)
    val tail = Offset(c.x - cos(a) * rr * 0.18f, c.y - sin(a) * rr * 0.18f)
    drawLine(Color(0xFFFF3B1F).a(appear), tail, tip, u * 1.3f, cap = StrokeCap.Round)
    glow(tip, u * 5f, Color(0xFFFF6A3D), 0.7f * appear)
    drawCircle(Color(0xFF2A2C33), u * 4f, c)
    drawCircle(Color(0xFF8A8F99), u * 4f, c, style = Stroke(u * 0.6f))
    // лампы переключения передач
    val n = 10
    for (k in 0 until n) {
        val x = c.x - u * 27f + k * u * 6f; val y = c.y - rr * 1.42f
        val thr = 3f + 5f * k / (n - 1)
        var col = if (k < 4) Color(0xFF2BFF6A) else if (k < 7) Color(0xFFFFD000) else Color(0xFFFF2A2A)
        if (redline && (t / 70f).toInt() % 2 == 0) col = Color(0xFF3A7BFF)
        drawCircle(Color(0xFF15161A).a(appear), u * 2.2f, Offset(x, y))
        if (rpm >= thr) {
            glow(Offset(x, y), u * 5.5f, col, 0.7f * appear)
            drawCircle(col.a(appear), u * 1.6f, Offset(x, y))
        }
    }
    // индикатор передачи: черточки
    val gear = if (t < peak + 300f) 2 else 3
    for (g in 0 until gear) drawRoundRect(Color(0xFFFF3B1F).a(appear), Offset(c.x - u * 6f + g * u * 4.5f, c.y + rr * 0.42f), Size(u * 3.2f, u * 1.6f), CornerRadius(u * 0.5f))
    // удар при переключении передачи
    shockRing(t, peak + 160f, c, w * 0.9f, WHITE, 650f, 0.8f)
    // в отсечке края кадра пульсируют красным
    if (redline) {
        val pulse = if ((t / 70f).toInt() % 2 == 0) 1f else 0.55f
        drawRect(Brush.radialGradient(listOf(CLEAR, Color(0xFFE60019).a(0.28f * pulse)), Offset(w / 2, h / 2), max(w, h) * 0.7f))
    }
    // линии скорости после пика
    val sp = span(t, peak, peak + 200f) * (1f - span(t, 2600f, 3100f))
    if (sp > 0f) {
        val rnd = Random(8)
        for (i in 0 until 26) {
            val y = rnd.nextFloat() * h; val len = w * (0.2f + rnd.nextFloat() * 0.5f)
            val x = fmod(rnd.nextFloat() + t / 1000f * (1.2f + rnd.nextFloat()), 1.5f) * w - w * 0.4f
            drawLine(WHITE.a(0.12f * sp), Offset(x, y), Offset(x + len, y), u * 0.35f)
        }
    }
    vignette(0.6f)
}

// ---------- 5. Night Rider: ночной аэродром, истребитель на форсаже уходит в небо ----------

/** Истребитель сзади-сверху; span — размах крыла, burner 0..1 — сила форсажа. */
private fun DrawScope.jetRear(cx: Float, cy: Float, sp: Float, burner: Float, t: Float) {
    val u = unit()
    val body = Color(0xFF1B2333); val edge = Color(0xFF55688A)
    // пламя форсажа: конусы к нам, с «бриллиантами» скачков уплотнения
    val flames = listOf(Color(0xFF1240FF) to 1f, Color(0xFF2E9BFF) to 0.7f, Color(0xFF9AE6FF) to 0.4f, WHITE to 0.18f)
    for (side in intArrayOf(-1, 1)) {
        val nx = cx + side * sp * 0.07f; val ny = cy + sp * 0.10f
        val len = sp * (0.15f + 0.75f * burner) * (1f + 0.05f * sin(t / 23f + side))
        if (burner > 0.02f) {
            glow(Offset(nx, ny + len * 0.3f), sp * 0.35f * (0.4f + burner), Color(0xFF1E6BFF), 0.45f * burner)
            flames.forEachIndexed { k, (col, wk) ->
                val fw = sp * 0.045f * wk
                drawPath(pathOf {
                    moveTo(nx - fw, ny)
                    cubicTo(nx - fw, ny + len * 0.5f, nx - fw * 0.3f, ny + len * (0.9f - 0.1f * k), nx, ny + len * (1f - 0.15f * k))
                    cubicTo(nx + fw * 0.3f, ny + len * (0.9f - 0.1f * k), nx + fw, ny + len * 0.5f, nx + fw, ny)
                    close()
                }, col.a((0.55f + 0.1f * k) * min(1f, burner * 1.6f)))
            }
            for (k in 0 until 4) {
                val dy = ny + len * (0.18f + 0.2f * k)
                drawOval(WHITE.a(0.55f * burner * (1f - k / 4f)), Offset(nx - sp * 0.018f, dy - sp * 0.01f), Size(sp * 0.036f, sp * 0.02f))
            }
        }
        drawCircle(mix(Color(0xFF9AE6FF), WHITE, burner), sp * 0.035f, Offset(nx, ny))
    }
    // крыло
    drawPath(pathOf {
        moveTo(cx - sp * 0.5f, cy + sp * 0.04f); lineTo(cx - sp * 0.12f, cy - sp * 0.08f); lineTo(cx + sp * 0.12f, cy - sp * 0.08f); lineTo(cx + sp * 0.5f, cy + sp * 0.04f)
        lineTo(cx + sp * 0.48f, cy + sp * 0.07f); lineTo(cx + sp * 0.1f, cy + sp * 0.03f); lineTo(cx - sp * 0.1f, cy + sp * 0.03f); lineTo(cx - sp * 0.48f, cy + sp * 0.07f); close()
    }, Brush.verticalGradient(listOf(edge, body), cy - sp * 0.08f, cy + sp * 0.07f))
    // кили, развалены в стороны
    for (side in intArrayOf(-1, 1)) {
        drawPath(pathOf {
            moveTo(cx + side * sp * 0.08f, cy - sp * 0.02f); lineTo(cx + side * sp * 0.16f, cy - sp * 0.24f)
            lineTo(cx + side * sp * 0.19f, cy - sp * 0.23f); lineTo(cx + side * sp * 0.13f, cy + sp * 0.02f); close()
        }, Brush.verticalGradient(listOf(edge, body), cy - sp * 0.24f, cy))
        drawLine(Color(0xFF3DDCFF).a(0.5f), Offset(cx + side * sp * 0.16f, cy - sp * 0.24f), Offset(cx + side * sp * 0.19f, cy - sp * 0.23f), u * 0.4f)
    }
    // фюзеляж и фонарь
    drawOval(Brush.verticalGradient(listOf(edge, body), cy - sp * 0.16f, cy + sp * 0.12f), Offset(cx - sp * 0.11f, cy - sp * 0.16f), Size(sp * 0.22f, sp * 0.28f))
    drawOval(Color(0xFF7FD8FF).a(0.35f), Offset(cx - sp * 0.04f, cy - sp * 0.15f), Size(sp * 0.08f, sp * 0.08f))
    // огни на законцовках крыла
    val blink = if ((t / 500f).toInt() % 2 == 0) 1f else 0.3f
    glow(Offset(cx - sp * 0.5f, cy + sp * 0.05f), sp * 0.06f, Color(0xFFFF3030), 0.8f * blink)
    glow(Offset(cx + sp * 0.5f, cy + sp * 0.05f), sp * 0.06f, Color(0xFF30FF7A), 0.8f * blink)
}

private fun DrawScope.drawNight(t: Float, peak: Float) {
    val w = size.width; val h = size.height; val u = unit()
    val hz = h * 0.56f
    drawRect(Brush.verticalGradient(listOf(Color(0xFF01030A), Color(0xFF061232), Color(0xFF0E2350)), 0f, hz))
    glow(Offset(w * 0.82f, h * 0.12f), w * 0.45f, Color(0xFF9DB8FF), 0.20f)
    drawCircle(Color(0xFFE6EEFF).a(0.85f), u * 5f, Offset(w * 0.82f, h * 0.12f))
    var rnd = Random(19)
    for (i in 0 until 90) {
        val sx = rnd.nextFloat() * w; val sy = rnd.nextFloat() * hz * 0.95f; val ph = rnd.nextFloat() * 6.28f; val r = u * (0.25f + rnd.nextFloat() * 0.35f)
        drawCircle(WHITE.a(0.35f + 0.35f * sin(t / 400f + ph)), r, Offset(sx, sy))
    }
    rnd = Random(23)
    for (i in 0 until 70) {
        val x = rnd.nextFloat() * w; val y = hz - rnd.nextFloat() * h * 0.02f
        drawCircle(Color(0xFFFFC66E).a(0.5f), u * 0.35f, Offset(x, y))
    }
    glow(Offset(w / 2, hz), w * 0.8f, Color(0xFF2E5BFF), 0.18f)
    drawRect(Brush.verticalGradient(listOf(Color(0xFF050A16), Color(0xFF02040A)), hz, h), Offset(0f, hz), Size(w, h - hz))
    val vx = w / 2
    fun rp(z: Float, side: Float): Offset = Offset(vx + side * (w * 0.015f + w * 0.6f * z * z), hz + (h - hz) * z * z)
    fun quad(a: Offset, b: Offset, c: Offset, d: Offset) = pathOf { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); lineTo(d.x, d.y); close() }
    drawPath(quad(rp(0f, -1f), rp(0f, 1f), rp(1.1f, 1f), rp(1.1f, -1f)), Brush.verticalGradient(listOf(Color(0xFF0C1424), Color(0xFF1A2438)), hz, h))
    // разметка: осевая и «зебра» порога
    for (k in 0 until 12) {
        val z0 = (k + 0.2f) / 12f; val z1 = (k + 0.65f) / 12f
        drawPath(quad(rp(z0, -0.012f), rp(z0, 0.012f), rp(z1, 0.012f), rp(z1, -0.012f)), WHITE.a(0.28f))
    }
    for (k in 0 until 8) {
        val sd = -0.85f + k * 0.24f + (if (k >= 4) 0.1f else 0f)
        drawPath(quad(rp(0.88f, sd), rp(0.88f, sd + 0.13f), rp(0.97f, sd + 0.13f), rp(0.97f, sd)), WHITE.a(0.3f))
    }
    // синие огни рулёжки
    for (k in 0 until 14) {
        val z = k / 13f
        for (side in intArrayOf(-1, 1)) drawCircle(Color(0xFF3D7BFF).a(0.55f * span(t, 300f + z * 600f, 500f + z * 600f)), u * (0.2f + 0.5f * z), rp(z, side * 1.9f))
    }
    // огни полосы зажигаются от горизонта к нам; по осевой бежит «волна»
    for (k in 0 until 28) {
        val z = k / 27f
        val on = span(t, 100f + z * 600f, 250f + z * 600f)
        if (on <= 0f) continue
        val chase = Math.floorMod((t / 60f).toInt() - k, 10) == 0
        for (side in intArrayOf(-1, 1)) {
            val p = rp(z, side * 1.06f); val r = u * (0.25f + z)
            glow(p, r * 3.5f, Color(0xFFBFD6FF), 0.35f * on)
            drawCircle(Color(0xFFE6F0FF).a(on), r, p)
        }
        if (k % 2 == 0) drawCircle((if (chase) Color(0xFF3DDCFF) else WHITE).a(0.6f * on), u * (0.2f + 0.6f * z), rp(z, 0f))
    }
    // самолёт: раскручивает двигатели, на пике форсаж, уходит вдаль и взлетает
    var burner = span(t, 900f, 1700f) * 0.45f + span(t, peak - 300f, peak) * 0.55f
    burner *= 1f - 0.6f * span(t, 4200f, 6500f)
    val go = span(t, peak - 100f, 4600f).pow(1.6f)
    val lift = span(t, 3300f, 5200f)
    val sp = w * (0.95f - 0.88f * go)
    val cy = h * 0.80f - (h * 0.80f - hz) * go - h * 0.30f * lift
    val cx = w / 2 + sin(t / 900f) * u * 1.5f * (1f - go) + w * 0.22f * lift.pow(1.3f)
    val q = if (t >= peak) (1f - lin(t, peak, peak + 600f)) * u * 1.2f else 0f
    if (sp > u * 2f) {
        translate(sin(t / 11f) * q, cos(t / 13f) * q) {
            if (lift > 0f) {
                drawPath(pathOf { moveTo(w / 2, hz); quadraticTo(w / 2 + (cx - w / 2) * 0.2f, hz - (hz - cy) * 0.6f, cx, cy) },
                    Color(0xFF3DA5FF).a(0.35f * (1f - span(t, 5200f, 7600f))), style = Stroke(u * 1.2f, cap = StrokeCap.Round))
            }
            glow(Offset(cx, cy + sp * 0.22f), sp * 0.7f, Color(0xFF1E6BFF), 0.22f * burner)
            jetRear(cx, cy, sp, burner, t)
            // кольцо скачка уплотнения на форсаже
            shockRing(t, peak, Offset(cx, cy), w * 0.95f, Color(0xFFA8DCFF), 800f, 0.9f)
        }
    }
    flareAt(Offset(cx, cy + sp * 0.1f), Color(0xFF6EB8FF), 0.6f * lift * (1f - span(t, 5600f, 7400f)))
    flareAt(Offset(w * 0.82f, h * 0.12f), Color(0xFF9DB8FF), 0.3f)
    vignette(0.6f)
}

// ---------- 6. Street Underground: янтарная ночная улица, фонари, машина уходит, мигалки, толпа ----------
private fun DrawScope.drawStreet(t: Float, peak: Float) {
    val w = size.width; val h = size.height; val u = unit()
    val amber = Color(0xFFFFB000); val haze = Color(0xFFD9A441)
    val q = if (t >= peak) (1f - lin(t, peak, peak + 450f)) * u * 2.2f else 0f
    val hz = h * 0.47f; val vx = w * 0.5f
    val lampsOn = span(t, 0f, 380f)
    fun gy(z: Float) = hz + (h - hz) * z * z
    fun rp(z: Float, sd: Float) = Offset(vx + sd * (w * 0.03f + w * 0.7f * z * z), hz + (h - hz) * z * z)
    fun quad(a: Offset, b: Offset, c: Offset, d: Offset) = pathOf { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); lineTo(d.x, d.y); close() }
    translate(sin(t / 14f) * q, cos(t / 17f) * q) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF0B0904), Color(0xFF2A2210), Color(0xFF4A3A16)), 0f, hz))
        glow(Offset(vx, hz), w * 0.9f, haze, 0.35f * lampsOn)
        // дома по сторонам в перспективе, с окнами
        for (side in intArrayOf(-1, 1)) {
            for (k in 0 until 7) {
                val z0 = k / 7f; val z1 = (k + 0.9f) / 7f
                fun px(z: Float) = vx + side * (w * 0.05f + w * 0.95f * z * z)
                val bh = h * (0.20f + 0.18f * ((k * 5 + (if (side > 0) 3 else 0)) % 4) / 3f)
                fun top(z: Float) = gy(z) - bh * (0.15f + 0.85f * z * z) * 2.2f
                drawPath(quad(Offset(px(z0), gy(z0)), Offset(px(z0), top(z0)), Offset(px(z1), top(z1)), Offset(px(z1), gy(z1))), mix(Color(0xFF0B0905), Color(0xFF1A140B), z0))
                val rnd = Random(k * 10 + (if (side > 0) 1 else 0))
                for (wv in 0 until 14) {
                    val zz = z0 + (z1 - z0) * rnd.nextFloat(); val fx = px(zz)
                    val fy = gy(zz) - (gy(zz) - top(zz)) * (0.12f + 0.8f * rnd.nextFloat())
                    val lit = rnd.nextFloat() < 0.55f; val br = rnd.nextFloat()
                    if (lit) drawRect(amber.a(0.2f + 0.45f * lampsOn * br), Offset(fx - u * 0.6f * zz, fy), Size(u * (0.4f + 2.2f * zz), u * (0.4f + 1.8f * zz)))
                }
            }
        }
        // мокрый асфальт
        drawRect(Brush.verticalGradient(listOf(Color(0xFF1A140A), Color(0xFF070604)), hz, h), Offset(0f, hz), Size(w, h - hz))
        drawPath(quad(rp(0f, -1f), rp(0f, 1f), rp(1.05f, 1f), rp(1.05f, -1f)), Color(0xFF14110B))
        // фонари по обе стороны, свет и отражения на мокрой дороге
        for (k in 0 until 9) {
            val z = (k + 1) / 9f
            for (side in intArrayOf(-1, 1)) {
                val b = rp(z, side * 1.08f)
                val top = Offset(b.x - side * u * 5f * z, b.y - h * 0.9f * z * z - u * 2f)
                drawLine(Color(0xFF2A2214), b, top, u * (0.3f + 0.9f * z))
                val fl = if (k % 4 != 1) lampsOn else lampsOn * (0.6f + if ((t / 90f + k).toInt() % 3 != 0) 0.4f else 0f)
                glow(top, u * (3f + 14f * z), amber, 0.8f * fl)
                drawCircle(Color(0xFFFFE2A0).a(fl), u * (0.4f + 1.1f * z), top)
                if (z > 0.55f) star4(top, u * (1.5f + 3f * z) * fl, Color(0xFFFFF0C8).a(0.7f * fl))
                val ry = b.y + (b.y - hz) * 0.08f
                drawOval(amber.a(0.16f * fl), Offset(b.x - u * (1f + 2f * z), ry), Size(u * (2f + 4f * z), u * (3f + 22f * z)))
            }
        }
        // разметка бежит к нам
        for (k in 0 until 10) {
            val z0 = (k + 0.1f) / 10f + fmod(t / 1000f * 0.9f, 0.1f); val z1 = z0 + 0.04f
            if (z1 > 1.05f) continue
            drawPath(quad(rp(z0, -0.015f), rp(z0, 0.015f), rp(z1, 0.015f), rp(z1, -0.015f)), Color(0xFFFFD27A).a(0.45f))
        }
        // машина уходит вдаль: стоп-сигналы и красные следы
        val go = span(t, peak - 120f, 2400f)
        val z = 0.8f - 0.74f * go
        if (z > 0.05f) {
            val c = rp(z, 0f); val cw = w * 0.36f * z * z + u * 2f
            for (k in 1 until 7) {
                val zz = min(1.05f, z + 0.05f * k * (0.3f + go))
                val tp = rp(zz, 0f); val tw = w * 0.36f * zz * zz
                for (sd in floatArrayOf(-0.38f, 0.38f)) {
                    drawLine(Color(0xFFFF2020).a(0.10f * (1f - k / 7f)), Offset(c.x + sd * cw, c.y), Offset(tp.x + sd * tw, tp.y), u * 0.6f + tw * 0.04f)
                }
            }
            drawPath(pathOf {
                moveTo(c.x - cw * 0.5f, c.y); lineTo(c.x - cw * 0.46f, c.y - cw * 0.26f); lineTo(c.x - cw * 0.3f, c.y - cw * 0.44f)
                lineTo(c.x + cw * 0.3f, c.y - cw * 0.44f); lineTo(c.x + cw * 0.46f, c.y - cw * 0.26f); lineTo(c.x + cw * 0.5f, c.y); close()
            }, Color(0xFF0B0B0D))
            drawRect(Color(0xFF2A2F3A).a(0.8f), Offset(c.x - cw * 0.26f, c.y - cw * 0.40f), Size(cw * 0.52f, cw * 0.12f))
            for (sd in intArrayOf(-1, 1)) {
                val tl = Offset(c.x + sd * cw * 0.36f, c.y - cw * 0.2f)
                glow(tl, cw * 0.35f, Color(0xFFFF1A1A), 0.8f)
                drawRoundRect(Color(0xFFFF4040), Offset(tl.x - cw * 0.08f, tl.y - cw * 0.03f), Size(cw * 0.16f, cw * 0.06f), CornerRadius(cw * 0.02f))
            }
        }
        // полицейские мигалки по краям после пика
        val pol = span(t, peak, peak + 150f) * (1f - span(t, 2700f, 3300f) * 0.7f)
        if (pol > 0f) {
            val ph = (t / 110f).toInt() % 4
            glow(Offset(-w * 0.05f, h * 0.62f), w * 0.75f, Color(0xFFFF1E2E), 0.55f * pol * (if (ph < 2) 1f else 0.15f))
            glow(Offset(w * 1.05f, h * 0.62f), w * 0.75f, Color(0xFF2E5BFF), 0.55f * pol * (if (ph >= 2) 1f else 0.15f))
            // отражения мигалок на мокром асфальте
            val redOn = if (ph < 2) 1f else 0.15f; val blueOn = if (ph >= 2) 1f else 0.15f
            drawRect(Brush.verticalGradient(listOf(CLEAR, Color(0xFFFF1E2E).a(0.20f * pol * redOn), CLEAR), hz, h), Offset(0f, hz), Size(w * 0.35f, h - hz))
            drawRect(Brush.verticalGradient(listOf(CLEAR, Color(0xFF2E5BFF).a(0.20f * pol * blueOn), CLEAR), hz, h), Offset(w * 0.65f, hz), Size(w * 0.35f, h - hz))
        }
        // толпа силуэтами по низу, подпрыгивает после пика
        for (row in 0..1) {
            val rnd = Random(21 + row)
            for (k in 0 until 14) {
                val bx = rnd.nextFloat(); val side = if (k % 2 == 0) -1 else 1
                val x = (if (side < 0) bx * 0.32f else 1f - bx * 0.32f) * w
                val hh = h * (0.08f + rnd.nextFloat() * 0.03f) * (1f + 0.4f * row)
                val jump = max(0f, sin(t / 160f + k)) * u * 1.5f * span(t, peak, peak + 200f)
                val y = h + h * 0.01f - jump
                val col = Color(0xFF050403).a(if (row == 1) 0.97f else 0.8f)
                drawRoundRect(col, Offset(x - hh * 0.28f, y - hh), Size(hh * 0.56f, hh), CornerRadius(hh * 0.2f))
                drawCircle(col, hh * 0.17f, Offset(x, y - hh * 1.1f))
                if ((k + row) % 3 == 0) {
                    val arm = Offset(x - side * hh * 0.1f, y - hh * 0.85f)
                    drawLine(col, arm, Offset(arm.x - side * hh * 0.25f, arm.y - hh * 0.55f - jump), hh * 0.12f, cap = StrokeCap.Round)
                }
            }
        }
    }
    rainStreaks(t, Color(0xFFFFD27A), 0.22f * lampsOn, 80, 12)
    drawRect(Color(0xFF6B5A1A).a(0.12f)) // зеленовато-янтарный фильтр
    vignette(0.65f)
}

// ---------- 7. Rally Gravel: лесной спецучасток на закате, машина с люстрой фар прыгает на нас, пыль ----------

private fun DrawScope.pine(x: Float, base: Float, hh: Float, col: Color) {
    val ww0 = hh * 0.32f
    val p = Path()
    p.moveTo(x, base - hh)
    for (k in 0 until 4) {
        val y = base - hh + hh * (k + 1) / 4.2f; val ww = ww0 * (0.35f + 0.65f * (k + 1) / 4f)
        p.lineTo(x + ww, y); p.lineTo(x + ww * 0.45f, y)
    }
    p.lineTo(x + ww0 * 0.12f, base); p.lineTo(x - ww0 * 0.12f, base)
    for (k in 3 downTo 0) {
        val y = base - hh + hh * (k + 1) / 4.2f; val ww = ww0 * (0.35f + 0.65f * (k + 1) / 4f)
        p.lineTo(x - ww * 0.45f, y); p.lineTo(x - ww, y)
    }
    p.close()
    drawPath(p, col)
}

/** Раллийная машина спереди; sw — ширина, lamps 0..1 — яркость люстры. */
private fun DrawScope.rallyCarFront(cx: Float, cy: Float, sw: Float, lamps: Float) {
    drawPath(pathOf {
        moveTo(cx - sw * 0.5f, cy); lineTo(cx - sw * 0.48f, cy - sw * 0.22f); lineTo(cx - sw * 0.32f, cy - sw * 0.30f)
        lineTo(cx - sw * 0.24f, cy - sw * 0.52f); lineTo(cx + sw * 0.24f, cy - sw * 0.52f); lineTo(cx + sw * 0.32f, cy - sw * 0.30f)
        lineTo(cx + sw * 0.48f, cy - sw * 0.22f); lineTo(cx + sw * 0.5f, cy); close()
    }, Brush.verticalGradient(listOf(Color(0xFFF2F2F2), Color(0xFF1E64C8), Color(0xFF0E2E66)), cy - sw * 0.52f, cy))
    drawPath(pathOf {
        moveTo(cx - sw * 0.21f, cy - sw * 0.33f); lineTo(cx - sw * 0.17f, cy - sw * 0.49f); lineTo(cx + sw * 0.17f, cy - sw * 0.49f); lineTo(cx + sw * 0.21f, cy - sw * 0.33f); close()
    }, Color(0xFF101820))
    drawRect(Color(0xFFFFD100), Offset(cx - sw * 0.5f, cy - sw * 0.16f), Size(sw, sw * 0.04f))
    drawRect(Color(0xFF0A0A0A), Offset(cx - sw * 0.2f, cy - sw * 0.12f), Size(sw * 0.4f, sw * 0.06f))
    drawRect(WHITE, Offset(cx - sw * 0.09f, cy - sw * 0.06f), Size(sw * 0.18f, sw * 0.05f))
    for (sd in intArrayOf(-1, 1)) {
        drawRect(Color(0xFF0A0A0A), Offset(cx + sd * sw * 0.42f - sw * 0.06f, cy - sw * 0.02f), Size(sw * 0.12f, sw * 0.12f))
        drawOval(Color(0xFFFFF4D0).a(0.9f), Offset(cx + sd * sw * 0.37f - sw * 0.06f, cy - sw * 0.24f), Size(sw * 0.12f, sw * 0.05f))
    }
    for (k in 0 until 4) {
        val l = Offset(cx - sw * 0.27f + k * sw * 0.18f, cy - sw * 0.26f)
        glow(l, sw * 0.5f, Color(0xFFFFF2C0), 0.5f * lamps)
        drawCircle(Color(0xFF111111), sw * 0.07f, l)
        drawCircle(Color(0xFFFFFBE6).a(0.3f + 0.7f * lamps), sw * 0.055f, l)
    }
}

private fun DrawScope.drawRally(t: Float, peak: Float) {
    val w = size.width; val h = size.height; val u = unit()
    fun quake(a: Float, at: Float) = if (t >= at && t < at + 600f) (1f - lin(t, at, at + 600f)) * a else 0f
    val q = quake(u * 2.5f, peak) + quake(u * 1.2f, peak + 700f)
    val hz = h * 0.5f; val vx = w * 0.5f
    val ground = hz + h * 0.03f
    fun rx(z: Float) = vx + sin(z * 3.2f + 0.6f) * w * 0.16f * (1f - z * 0.5f)
    fun ry(z: Float) = ground + (h - ground + h * 0.06f) * z * z
    fun rw(z: Float) = w * 0.012f + w * 0.55f * z * z
    translate(sin(t / 16f) * q, cos(t / 13f) * q) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF3A2A1C), Color(0xFFC98A4B), Color(0xFFF2C27A)), 0f, hz))
        val sun = Offset(w * 0.68f, h * 0.30f)
        glow(sun, w * 0.6f, Color(0xFFFFE2A0), 0.55f)
        drawCircle(Color(0xFFFFF4D6), u * 6f, sun)
        fun forest(sc: Float, col: Color, n: Int, seed: Int, base: Float) {
            val rnd = Random(seed)
            val zb = min(1f, ((base - ground) / (h - ground + h * 0.06f)).coerceAtLeast(0f).pow(0.5f))
            val gap = rw(zb) * 1.3f + w * 0.02f
            val cxr = rx(zb)
            for (k in 0 until n) {
                var x = (k + rnd.nextFloat() * 0.6f) / n * w
                if (abs(x - cxr) < gap) x = cxr + gap * (if (x > cxr) 1f else -1f) + (x - cxr) * 0.3f
                val th = h * (0.10f + 0.22f * sc) * (0.7f + 0.5f * rnd.nextFloat())
                pine(x, base, th, col)
            }
        }
        // дальний лес в дымке
        forest(0.15f, Color(0xFF8A6A48), 26, 3, ground + h * 0.005f)
        drawRect(Brush.verticalGradient(listOf(Color(0x00F2C27A), Color(0xFFE8B06A).a(0.35f)), hz - h * 0.08f, ground), Offset(0f, hz - h * 0.08f), Size(w, h * 0.11f))
        forest(0.30f, Color(0xFF4E3A26), 20, 7, ground + h * 0.02f)
        // лесная подстилка и гравийная дорога змейкой к нам
        drawRect(Brush.verticalGradient(listOf(Color(0xFF3A2814), Color(0xFF120C06)), ground + h * 0.02f, h), Offset(0f, ground + h * 0.02f), Size(w, h))
        val road = Path(); val nn = 28
        for (k in 0..nn) { val z = k / nn.toFloat(); if (k == 0) road.moveTo(rx(z) - rw(z), ry(z)) else road.lineTo(rx(z) - rw(z), ry(z)) }
        for (k in nn downTo 0) { val z = k / nn.toFloat(); road.lineTo(rx(z) + rw(z), ry(z)) }
        road.close()
        drawPath(road, Brush.verticalGradient(listOf(Color(0xFFB08A60), Color(0xFF6A4E30)), ground, h))
        var rnd = Random(55)
        for (k in 0 until 140) {
            val z = rnd.nextFloat().pow(0.7f); val x = rx(z) + (rnd.nextFloat() * 2f - 1f) * rw(z)
            drawCircle(Color(0xFF3A2A18).a(0.5f), u * (0.15f + 0.6f * z), Offset(x, ry(z)))
        }
        // ближний лес по краям
        forest(0.55f, Color(0xFF22180E), 12, 11, ground + h * 0.10f)
        forest(1.0f, Color(0xFF0E0A06), 8, 17, ground + h * 0.28f)
        // лучи солнца сквозь лес
        val rayLen = h * 0.9f
        val rayBrush = Brush.radialGradient(listOf(Color(0xFFFFE9B8).a(0.16f), CLEAR), sun, rayLen)
        for (k in 0 until 6) {
            val a = -0.9f + k * 0.32f + sin(t / 2000f + k) * 0.03f + 1.9f
            val ex = sun.x + cos(a) * rayLen; val ey = sun.y + sin(a) * rayLen
            drawPath(pathOf { moveTo(sun.x, sun.y); lineTo(ex - u * 6f, ey); lineTo(ex + u * 6f, ey); close() }, rayBrush)
        }
        // машина: из глубины к нам, прыжок на пике
        val cp = lin(t, peak - 1400f, peak + 150f)
        if (cp > 0f && cp < 1f) {
            val z = 0.05f + 0.95f * cp.pow(2.2f)
            val jump = sin(PI.toFloat() * lin(t, peak - 500f, peak + 150f)) * h * 0.08f * z
            val sw = rw(z) * 1.25f
            for (k in 0 until 10) glow(Offset(rx(z) + (k - 4.5f) * sw * 0.12f, ry(z) - sw * 0.05f * k), sw * (0.25f + 0.05f * k), Color(0xFFD8B28A), 0.25f)
            rallyCarFront(rx(z), ry(z) - jump, sw, span(t, peak - 1400f, peak - 1000f))
        }
        // кольцо пыли при приземлении
        scale(1f, 0.32f, Offset(rx(0.95f), h * 0.86f)) {
            shockRing(t, peak + 150f, Offset(rx(0.95f), h * 0.86f), w * 0.7f, Color(0xFFE8C9A0), 800f, 0.8f)
        }
        // облако пыли после пика, две волны
        for (burst in 0..1) {
            val t0 = if (burst == 0) peak - 60f else peak + 650f
            rnd = Random(30 + burst)
            for (i in 0 until (if (burst == 0) 60 else 35)) {
                val p = lin(t, t0 + rnd.nextFloat() * 200f, t0 + 2600f + rnd.nextFloat() * 1600f)
                val ang = rnd.nextFloat() * PI.toFloat() + PI.toFloat(); val spd = (0.25f + rnd.nextFloat() * 0.7f) * w
                val r = u * (8f + rnd.nextFloat() * 22f) * (0.6f + p * 2.2f)
                if (p <= 0f || p >= 1f) continue
                val d = spd * (1f - (1f - p).pow(3))
                drawCircle(Color(0xFFD9B48C).a((1f - p) * 0.30f), r, Offset(w / 2 + cos(ang) * d, h * 0.85f + sin(ang) * d * 0.5f))
            }
        }
        // летящий гравий
        rnd = Random(61)
        for (i in 0 until 45) {
            val st = peak - 50f + rnd.nextFloat() * 300f
            val ang = -PI.toFloat() / 2f + (rnd.nextFloat() - 0.5f) * 2.4f; val spd = u * (40f + rnd.nextFloat() * 70f); val rr = u * (0.6f + rnd.nextFloat() * 1.4f)
            val p = lin(t, st, st + 700f)
            if (p <= 0f || p >= 1f) continue
            drawCircle(Color(0xFF2A1E12).a(1f - p * 0.5f), rr * (1f + p), Offset(w / 2 + cos(ang) * spd * p, h * 0.86f + sin(ang) * spd * p + u * 90f * p * p))
        }
        // пылинки в лучах
        rnd = Random(70)
        for (i in 0 until 40) {
            val x = fmod(rnd.nextFloat() + t / 9000f * (0.5f + rnd.nextFloat()), 1f) * w
            val y = (rnd.nextFloat() + sin(t / 1500f + i) * 0.01f) * h * 0.8f
            drawCircle(Color(0xFFFFE7B0).a(0.35f * (0.5f + 0.5f * sin(t / 300f + i))), u * 0.35f, Offset(x, y))
        }
        flareAt(sun, Color(0xFFFFE2A0), 0.55f)
    }
    vignette(0.6f)
}

// ---------- 8. Drift Night: горный серпантин ночью, машина в заносе, световые следы, дым и туман ----------

private fun bez(p0: Offset, p1: Offset, p2: Offset, p3: Offset, q: Float): Offset {
    val m = 1f - q
    return Offset(
        m * m * m * p0.x + 3 * m * m * q * p1.x + 3 * m * q * q * p2.x + q * q * q * p3.x,
        m * m * m * p0.y + 3 * m * m * q * p1.y + 3 * m * q * q * p2.y + q * q * q * p3.y
    )
}

/** Точка горной дороги: s=0 вдали наверху, s=1 у нас внизу. */
private fun driftRoad(w: Float, h: Float, s: Float): Offset = when {
    s < 0.3f -> bez(Offset(w * 0.5f, h * 0.20f), Offset(w * 0.38f, h * 0.26f), Offset(w * 0.86f, h * 0.30f), Offset(w * 0.62f, h * 0.42f), s / 0.3f)
    s < 0.75f -> bez(Offset(w * 0.62f, h * 0.42f), Offset(w * 0.30f, h * 0.58f), Offset(-w * 0.05f, h * 0.62f), Offset(w * 0.42f, h * 0.78f), (s - 0.3f) / 0.45f)
    else -> bez(Offset(w * 0.42f, h * 0.78f), Offset(w * 0.75f, h * 0.88f), Offset(w * 0.70f, h * 0.95f), Offset(w * 0.55f, h * 1.08f), (s - 0.75f) / 0.25f)
}

private fun DrawScope.drawDrift(t: Float, peak: Float) {
    val w = size.width; val h = size.height; val u = unit()
    drawRect(Brush.verticalGradient(listOf(Color(0xFF04051A), Color(0xFF0F1440), Color(0xFF1C1F55)), 0f, h * 0.45f))
    var rnd = Random(4)
    for (i in 0 until 70) {
        val x = rnd.nextFloat() * w; val y = rnd.nextFloat() * h * 0.35f
        drawCircle(WHITE.a(0.25f + 0.3f * sin(t / 300f + i)), u * 0.3f, Offset(x, y))
    }
    glow(Offset(w * 0.22f, h * 0.10f), w * 0.4f, Color(0xFFB7C4FF), 0.25f)
    drawCircle(Color(0xFFEDF0FF), u * 4.5f, Offset(w * 0.22f, h * 0.10f))
    flareAt(Offset(w * 0.22f, h * 0.10f), Color(0xFFB7C4FF), 0.3f)
    // хребты гор слоями с туманом
    val ridges = listOf(Triple(0.24f, Color(0xFF232867), 0.06f), Triple(0.30f, Color(0xFF161A48), 0.09f), Triple(0.40f, Color(0xFF0B0D2A), 0.12f))
    ridges.forEachIndexed { idx, (base, col, amp) ->
        rnd = Random((idx + 1) * 31)
        val ridge = Path(); ridge.moveTo(0f, h)
        for (k in 0 until 9) ridge.lineTo(k / 8f * w, h * base - rnd.nextFloat() * h * amp)
        ridge.lineTo(w, h); ridge.close()
        drawPath(ridge, col)
        drawRect(Brush.verticalGradient(listOf(CLEAR, Color(0xFF7D86D9).a(0.10f)), h * (base - 0.05f), h * (base + 0.04f)), Offset(0f, h * (base - 0.05f)), Size(w, h * 0.09f))
    }
    drawRect(Brush.verticalGradient(listOf(Color(0xFF080A20), Color(0xFF04050F)), h * 0.40f, h), Offset(0f, h * 0.4f), Size(w, h * 0.6f))
    // дорога: лента, осевая, светоотражатели отбойника
    val nn = 60
    val pts = Array(nn + 1) { driftRoad(w, h, it / nn.toFloat()) }
    for (k in 0 until nn) drawLine(Color(0xFF1A1D33), pts[k], pts[k + 1], u * (2f + 16f * k / nn), cap = StrokeCap.Round)
    for (k in 0 until nn step 3) {
        val a = pts[k]; val b = pts[k + 1]
        drawLine(Color(0xFFE8E8F0).a(0.35f), a, Offset(a.x + (b.x - a.x) * 0.5f, a.y + (b.y - a.y) * 0.5f), u * (0.2f + 0.5f * k / nn))
    }
    for (k in 0 until nn step 2) {
        val z = k / nn.toFloat(); val a = pts[k]; val b = pts[min(nn, k + 1)]
        val dx = b.x - a.x; val dy = b.y - a.y; val len = hypot(dx, dy).let { if (it == 0f) 1f else it }
        val nx = -dy / len; val ny = dx / len; val off = u * (1.2f + 8.5f * z)
        for (sd in intArrayOf(-1, 1)) drawCircle(Color(0xFFFFB347).a(0.55f), u * (0.15f + 0.45f * z), Offset(a.x + nx * off * sd, a.y + ny * off * sd))
    }
    // машина едет к нам, в повороте идёт в занос
    val sp = lin(t, 150f, 3300f).pow(1.1f)
    fun head(s: Float): Float {
        val a = driftRoad(w, h, max(0f, s - 0.004f)); val b = driftRoad(w, h, min(1f, s + 0.004f))
        return atan2(b.y - a.y, b.x - a.x)
    }
    // световые следы стоп-сигналов
    val tail = max(0f, sp - 0.28f)
    for (k in 0 until 24) {
        val s0 = tail + (sp - tail) * k / 24f; val s1 = tail + (sp - tail) * (k + 1) / 24f
        val a = driftRoad(w, h, s0); val b = driftRoad(w, h, s1)
        val hh = head(s0); val nx = -sin(hh); val ny = cos(hh)
        for (sd in intArrayOf(-1, 1)) {
            val off = u * (0.6f + 2.6f * s1) * sd
            drawLine(Color(0xFFFF2244).a(0.55f * k / 24f), Offset(a.x + nx * off, a.y + ny * off), Offset(b.x + nx * off, b.y + ny * off), u * (0.25f + 0.7f * s1), cap = StrokeCap.Round)
        }
    }
    // дым из-под колёс
    val smokeOn = span(t, 500f, 900f)
    for (k in 0 until 16) {
        val s0 = sp - 0.012f * k
        if (s0 <= 0f) continue
        val p = driftRoad(w, h, s0); val age = k / 16f
        glow(Offset(p.x + sin(k * 1.7f) * u * 2f, p.y - age * u * 4f), u * (2f + 9f * s0) * (1f + age * 2.2f), Color(0xFFDDE2FF), 0.28f * (1f - age) * smokeOn)
    }
    // клуб дыма в апексе на пике
    val burst = span(t, peak - 80f, peak + 120f) * (1f - span(t, peak + 400f, peak + 2200f))
    if (burst > 0f) {
        val pc = driftRoad(w, h, lin(peak, 150f, 3300f).pow(1.1f))
        val grow = span(t, peak, peak + 1800f); val rise = span(t, peak, peak + 2000f)
        for (k in 0 until 14) {
            val ang = k * 0.45f; val d = u * (6f + 22f * grow) * (0.6f + 0.4f * (k % 3) / 2f)
            glow(Offset(pc.x + cos(ang) * d, pc.y + sin(ang) * d * 0.6f - u * 6f * rise), u * (7f + 2f * (k % 5)) * (1f + rise), Color(0xFFE6EAFF), 0.16f * burst)
        }
    }
    if (sp > 0f && sp < 1f) {
        val c = driftRoad(w, h, sp)
        val slip = (38.0 * PI / 180.0).toFloat() * span(t, 500f, peak) * (1f - span(t, peak + 500f, peak + 1300f))
        val ang = head(sp) + slip
        val len = u * (4f + 12f * sp); val wd = len * 0.5f
        val fx = cos(ang); val fy = sin(ang)
        // фары: два конуса света вперёд
        for (sd in intArrayOf(-1, 1)) {
            val o = Offset(c.x + fx * len * 0.5f - fy * wd * 0.32f * sd, c.y + fy * len * 0.5f + fx * wd * 0.32f * sd)
            val far = Offset(o.x + fx * len * 4f, o.y + fy * len * 4f)
            drawPath(pathOf {
                moveTo(o.x, o.y); lineTo(far.x - fy * len * 1.2f, far.y + fx * len * 1.2f); lineTo(far.x + fy * len * 1.2f, far.y - fx * len * 1.2f); close()
            }, Brush.radialGradient(listOf(Color(0xFFE8F0FF).a(0.35f), CLEAR), o, len * 4.2f))
            // фары слепят, когда машина близко
            val near = span(sp, 0.45f, 0.8f)
            if (near > 0f) {
                glow(o, len * 0.9f, Color(0xFFE8F0FF), 0.35f * near)
                star4(o, len * 0.3f * near, WHITE.a(0.75f * near))
            }
        }
        rotate((ang * 180f / PI.toFloat()), c) {
            drawRoundRect(Color(0xFF0E0F16), Offset(c.x - len / 2, c.y - wd / 2), Size(len, wd), CornerRadius(wd * 0.3f))
            drawRoundRect(Color(0xFFB4FF00).a(0.85f), Offset(c.x - len / 2, c.y - wd / 2), Size(len, wd), CornerRadius(wd * 0.3f), style = Stroke(u * 0.25f))
            drawRect(Color(0xFF2A3350), Offset(c.x + len * 0.05f, c.y - wd * 0.36f), Size(len * 0.18f, wd * 0.72f))
            for (sd in intArrayOf(-1, 1)) {
                drawCircle(Color(0xFFFF2244), wd * 0.11f, Offset(c.x - len * 0.48f, c.y + sd * wd * 0.3f))
                drawCircle(Color(0xFFF4F8FF), wd * 0.1f, Offset(c.x + len * 0.48f, c.y + sd * wd * 0.3f))
            }
            glow(Offset(c.x - len * 0.55f, c.y), wd * 1.2f, Color(0xFFFF2244), 0.5f)
        }
    }
    // туман: сначала закрывает, на пике расступается, потом ползёт полосами
    val fog = span(t, 0f, 600f) * (1f - span(t, 700f, 1800f))
    if (fog > 0f) {
        val origins = listOf(Offset(0f, h * 0.5f), Offset(w, h * 0.55f), Offset(w * 0.5f, h * 0.2f), Offset(w * 0.5f, h), Offset(0f, h * 0.15f), Offset(w, h * 0.9f))
        val m = Offset(w / 2, h / 2)
        origins.forEachIndexed { i, o ->
            val f = 0.7f * fog
            glow(Offset(o.x + (m.x - o.x) * f, o.y + (m.y - o.y) * f), w * (0.6f + 0.05f * (i % 3)), Color(0xFFC8CEF0), 0.45f * fog)
        }
    }
    for (i in 0 until 4) {
        val y = h * (0.35f + i * 0.16f)
        val x = (fmod(t / 1000f * (0.06f + 0.03f * i) + i * 0.37f, 1.6f) - 0.3f) * w
        scale(1f, 0.14f, Offset(x, y)) { glow(Offset(x, y), w * 0.55f, Color(0xFFB8C0F0), 0.16f) }
    }
    vignette(0.6f)
}
