package ru.shturman.ui.themes

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.shturman.ui.theme.ShColors
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * Идёт ли заставка. Интерфейс под ней ждёт, пока она начнёт уходить, и только тогда въезжает каскадом
 * (см. themedEnter), чтобы появление было видно, а не проигрывалось под заставкой.
 */
object SplashGate {
    var covering by mutableStateOf(false)
}

/** Как заставка уходит, открывая приложение. */
enum class SplashExit {
    /** Круг раскрывается из точки (Классика — из самолёта). */
    IRIS,
    /** Раскрытие сердцем (Принцесса). */
    HEART,
    /** Кадр разрезается на полосы, они разъезжаются в стороны с цветными краями (Street). */
    GLITCH,
    /** Шахматная шторка по диагонали (NASCAR, GT, Rally). */
    CHECKER,
    /** Пролёт сквозь кадр: он приближается и тает (Night Rider, Drift). */
    WARP
}

/** Плёнка поверх кадра. */
enum class SplashTexture { NONE, GRAIN, SCAN, BOKEH }

/** Настройки «кинослоя» заставки для темы. */
class SplashLook(
    val exit: SplashExit,
    val texture: SplashTexture,
    /** Тряска камеры на пике, dp. */
    val shake: Float,
    /** Сила вспышки на пике, 0..1. */
    val flash: Float,
    /** Горизонтальный блик от объектива на пике. */
    val streak: Boolean,
    /** Чёрные полосы сверху и снизу, как в кино; при уходе разъезжаются. */
    val letterbox: Boolean,
    /** Откуда раскрывается круг/сердце, доли экрана. */
    val origin: Offset = Offset(0.5f, 0.46f),
    /** Цвет вспышки и блика, если не цвет темы (у Классики — тёплое солнце). */
    val tint: Color? = null
)

fun splashLookFor(themeId: Int): SplashLook = when (themeId) {
    0 -> SplashLook(SplashExit.IRIS, SplashTexture.GRAIN, shake = 0f, flash = 0.30f, streak = true, letterbox = false, origin = Offset(0.48f, 0.42f), tint = Color(0xFFFFE2A8))
    1 -> SplashLook(SplashExit.HEART, SplashTexture.BOKEH, shake = 2f, flash = 0.45f, streak = false, letterbox = false, origin = Offset(0.5f, 0.46f))
    2 -> SplashLook(SplashExit.CHECKER, SplashTexture.GRAIN, shake = 7f, flash = 0.50f, streak = true, letterbox = true)
    3 -> SplashLook(SplashExit.CHECKER, SplashTexture.SCAN, shake = 6f, flash = 0.50f, streak = true, letterbox = false)
    4 -> SplashLook(SplashExit.WARP, SplashTexture.GRAIN, shake = 8f, flash = 0.55f, streak = true, letterbox = true)
    5 -> SplashLook(SplashExit.GLITCH, SplashTexture.SCAN, shake = 5f, flash = 0.45f, streak = true, letterbox = false)
    6 -> SplashLook(SplashExit.CHECKER, SplashTexture.GRAIN, shake = 4f, flash = 0.35f, streak = false, letterbox = true)
    else -> SplashLook(SplashExit.WARP, SplashTexture.GRAIN, shake = 4f, flash = 0.40f, streak = true, letterbox = true)
}

private fun smooth(x: Float): Float { val t = x.coerceIn(0f, 1f); return t * t * (3f - 2f * t) }
private fun ramp(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)

/**
 * Общая сцена заставки: часы, звук, касание для пропуска, «камера» (медленный наезд, лёгкое покачивание,
 * тряска на пике), вспышка и блик на пике, плёнка, кинополосы и красивый уход в приложение.
 * Сам рисунок темы приходит в content(t), где t — миллисекунды от начала.
 *
 * Длительность та же, что была: holdMs чёрного экрана, затем totalMs сцены; звук — в soundAtMs.
 */
@Composable
fun SplashStage(
    totalMs: Float,
    peakMs: Float,
    holdMs: Long,
    soundAtMs: Float,
    look: SplashLook,
    onPlaySound: () -> Unit,
    onFinished: () -> Unit,
    content: @Composable BoxScope.(t: Float) -> Unit
) {
    val clock = remember { Animatable(0f) }
    val out = remember { Animatable(0f) }
    val flags = remember { booleanArrayOf(false, false) } // [0] уход начат, [1] заставка закрыта
    val scope = rememberCoroutineScope()
    val accent = look.tint ?: ShColors.Accent
    val accent2 = ShColors.Accent2

    fun leave(fast: Boolean) {
        if (flags[0]) return
        flags[0] = true
        SplashGate.covering = false
        scope.launch {
            out.animateTo(1f, tween(if (fast) 420 else 640, easing = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)))
            if (!flags[1]) { flags[1] = true; onFinished() }
        }
    }

    DisposableEffect(Unit) {
        SplashGate.covering = true
        onDispose { SplashGate.covering = false }
    }
    LaunchedEffect(Unit) {
        delay(holdMs)
        var played = false
        if (soundAtMs <= 0f) { played = true; onPlaySound() }
        clock.animateTo(totalMs, tween(totalMs.toInt(), easing = LinearEasing)) {
            if (!played && !flags[0] && value >= soundAtMs) { played = true; onPlaySound() }
        }
        leave(false)
    }

    val t = clock.value
    val o = out.value
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { leave(true) } }
            .graphicsLayer {
                if (look.exit == SplashExit.WARP && o > 0f) {
                    val s = 1f + 1.5f * o * o
                    scaleX = s; scaleY = s
                    alpha = 1f - smooth(ramp(o, 0.25f, 1f))
                }
            }
            .drawWithContent {
                if (o <= 0f || look.exit == SplashExit.WARP) { drawContent(); return@drawWithContent }
                drawExit(look, o, accent, accent2) { this@drawWithContent.drawContent() }
            }
            .background(Color.Black)
    ) {
        // кадр темы под «камерой»
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = (t / totalMs).coerceIn(0f, 1f)
                    val push = 1.05f + 0.05f * smooth(p) + (if (look.exit == SplashExit.WARP) 0.25f * o * o else 0f)
                    scaleX = push; scaleY = push
                    val d = density
                    var tx = sin(t / 1900f) * 3f * d
                    var ty = cos(t / 2300f) * 2.5f * d
                    val since = t - peakMs
                    if (look.shake > 0f && since >= 0f && since < 560f) {
                        val k = 1f - since / 560f
                        val amp = look.shake * d * k * k
                        tx += sin(since * 0.11f) * amp
                        ty += cos(since * 0.137f) * amp * 0.8f
                    }
                    translationX = tx; translationY = ty
                    rotationZ = sin(t / 2700f) * 0.35f
                }
        ) { content(t) }
        // плёнка, вспышка, кинополосы — не качаются вместе с камерой
        Canvas(Modifier.fillMaxSize()) { drawPost(t, peakMs, o, look, accent) }
    }
}

/** Вспышка и блик на пике, плёнка, затемнение в начале, кинополосы и полосы скорости при пролёте. */
private fun DrawScope.drawPost(t: Float, peak: Float, o: Float, look: SplashLook, accent: Color) {
    val w = size.width; val h = size.height
    // плавное проявление из темноты
    val dark = 1f - ramp(t, 0f, 380f)
    if (dark > 0f) drawRect(Color.Black.copy(alpha = dark))

    // плёнка
    when (look.texture) {
        SplashTexture.GRAIN -> {
            val r = Random((t / 42f).toInt())
            val dot = 1.1f * density
            repeat(150) {
                val c = if (r.nextBoolean()) Color.White.copy(alpha = 0.055f) else Color.Black.copy(alpha = 0.09f)
                drawRect(c, Offset(r.nextFloat() * w, r.nextFloat() * h), Size(dot, dot))
            }
        }
        SplashTexture.SCAN -> {
            val step = 3f * density
            var y = 0f
            val line = Color.Black.copy(alpha = 0.13f)
            while (y < h) { drawRect(line, Offset(0f, y), Size(w, step * 0.4f)); y += step }
            val by = ((t * 0.32f) % (h * 1.3f)) - h * 0.15f
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.05f), Color.Transparent), by - 40f * density, by + 40f * density), Offset(0f, by - 40f * density), Size(w, 80f * density))
        }
        SplashTexture.BOKEH -> {
            for (i in 0 until 9) {
                val seed = (i * 0.618034f) % 1f
                val x = w * ((seed + 0.08f * sin(t / 2100f + i)) % 1f)
                val y = h * (1.1f - ((t / 9000f * (0.6f + seed) + seed) % 1.2f))
                val r = (26f + 30f * ((i * 0.37f) % 1f)) * density
                drawCircle(Brush.radialGradient(listOf((if (i % 2 == 0) accent else Color.White).copy(alpha = 0.10f), Color.Transparent), Offset(x, y), r), r, Offset(x, y))
            }
        }
        SplashTexture.NONE -> Unit
    }

    // вспышка и блик на пике
    val k = ramp(t, peak, peak + 460f)
    if (t >= peak && k < 1f && look.flash > 0f) {
        val f = (1f - k) * (1f - k) * look.flash
        val c = Offset(w / 2, h * 0.46f)
        val r = max(w, h) * 0.75f
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.55f * f), accent.copy(alpha = 0.35f * f), Color.Transparent), c, r), r, c)
        if (look.streak) {
            val half = 2.2f * density
            drawRect(
                Brush.horizontalGradient(listOf(Color.Transparent, accent.copy(alpha = f), Color.White.copy(alpha = f), accent.copy(alpha = f), Color.Transparent)),
                Offset(0f, c.y - half), Size(w, half * 2)
            )
            val band = 34f * density
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, accent.copy(alpha = 0.22f * f), Color.Transparent), c.y - band, c.y + band),
                Offset(0f, c.y - band), Size(w, band * 2)
            )
        }
    }

    // полосы скорости при пролёте сквозь кадр
    if (look.exit == SplashExit.WARP && o > 0f) {
        val c = Offset(w / 2, h / 2)
        val rnd = Random(9)
        val reach = hypot(w, h) * 0.6f
        repeat(48) {
            val a = rnd.nextFloat() * 2f * PI.toFloat()
            val start = reach * (0.15f + 0.5f * rnd.nextFloat()) * (0.4f + o)
            val len = reach * (0.2f + 0.5f * o) * (0.5f + rnd.nextFloat())
            val dir = Offset(cos(a), sin(a))
            drawLine(
                Brush.linearGradient(listOf(Color.Transparent, accent.copy(alpha = 0.55f * (1f - o)), Color.White.copy(alpha = 0.7f * (1f - o))), c + dir * start, c + dir * (start + len)),
                c + dir * start, c + dir * (start + len), strokeWidth = (1.2f + 1.8f * rnd.nextFloat()) * density
            )
        }
    }

    // кинополосы: при уходе разъезжаются
    if (look.letterbox) {
        val bar = h * 0.065f * (1f - smooth(o * 1.6f))
        if (bar > 0.5f) {
            drawRect(Color.Black, Offset.Zero, Size(w, bar))
            drawRect(Color.Black, Offset(0f, h - bar), Size(w, bar))
        }
    }
}

/** Уход заставки, открывающий приложение под ней. content() рисует кадр заставки целиком. */
private inline fun DrawScope.drawExit(look: SplashLook, o: Float, accent: Color, accent2: Color, content: DrawScope.() -> Unit) {
    val w = size.width; val h = size.height
    when (look.exit) {
        SplashExit.IRIS, SplashExit.HEART -> {
            val c = Offset(w * look.origin.x, h * look.origin.y)
            val far = hypot(max(c.x, w - c.x), max(c.y, h - c.y))
            val e = o * o * (3f - 2f * o)
            val hole = Path()
            if (look.exit == SplashExit.IRIS) {
                val r = far * 1.02f * e
                hole.addOval(Rect(c, r))
                clipPath(hole, ClipOp.Difference) { content() }
                if (r > 1f) drawCircle(accent.copy(alpha = 0.85f * (1f - o)), r, c, style = Stroke((10f * (1f - o) + 1.5f) * density))
            } else {
                val s = far * 1.9f * e
                heart(hole, c.x, c.y + s * 0.15f, s)
                clipPath(hole, ClipOp.Difference) { content() }
                if (s > 1f) drawPath(hole, Color(0xFFFFD1EC).copy(alpha = 0.9f * (1f - o)), style = Stroke((12f * (1f - o) + 1.5f) * density))
            }
        }
        SplashExit.GLITCH -> {
            val bands = 14
            val bh = h / bands
            val rnd = Random(21)
            for (i in 0 until bands) {
                val lag = rnd.nextFloat() * 0.45f
                val p = ((o - lag) / (1f - 0.45f)).coerceIn(0f, 1f)
                val dir = if (i % 2 == 0) 1f else -1f
                val dx = dir * w * (p * p) * 1.05f
                val top = i * bh
                if (p >= 1f) continue
                clipRect(0f, top, w, top + bh + 1f) {
                    translate(left = dx) { this@drawExit.content() }
                }
                // цветные края разреза, как сбой сигнала
                if (p > 0f) {
                    val edgeX = if (dir > 0f) dx else w + dx
                    drawRect((if (i % 3 == 0) accent2 else accent).copy(alpha = 0.75f * (1f - p)), Offset(edgeX - 3f * density, top), Size(6f * density, bh))
                }
            }
        }
        SplashExit.CHECKER -> {
            val cols = 7
            val cell = w / cols
            val rows = ceil(h / cell).toInt()
            val span = (cols + rows).toFloat()
            val keep = Path()
            for (r in 0 until rows) for (cI in 0 until cols) {
                val d = (cI + r) / span
                val p = ((o - d * 0.55f) / 0.45f).coerceIn(0f, 1f)
                val s = 1f - p * p * (3f - 2f * p)
                if (s <= 0f) continue
                val cx = cI * cell + cell / 2; val cy = r * cell + cell / 2
                val half = cell / 2 * s + 0.5f
                keep.addRect(Rect(cx - half, cy - half, cx + half, cy + half))
            }
            clipPath(keep) { content() }
        }
        SplashExit.WARP -> content()
    }
}

/** Сердце для раскрытия: s — половина ширины. */
private fun heart(p: Path, cx: Float, cy: Float, s: Float) {
    p.moveTo(cx, cy + s * 0.9f)
    p.cubicTo(cx - s * 1.5f, cy - s * 0.1f, cx - s * 0.7f, cy - s * 1.2f, cx, cy - s * 0.45f)
    p.cubicTo(cx + s * 0.7f, cy - s * 1.2f, cx + s * 1.5f, cy - s * 0.1f, cx, cy + s * 0.9f)
    p.close()
}
