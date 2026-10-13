package ru.shturman.ui.fx

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import ru.shturman.ui.theme.ShColors
import ru.shturman.ui.themes.Particle
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Одна частица: положение и скорость в пикселях, время жизни в секундах. */
private class Bit(
    var x: Float, var y: Float, var vx: Float, var vy: Float,
    val life: Float, val size: Float, val color: Color, val kind: Particle,
    var rot: Float, val vr: Float, var age: Float = 0f
)

/** Расходящееся кольцо-ударная волна. */
private class Ring(val x: Float, val y: Float, val color: Color, val maxR: Float, val life: Float, var age: Float = 0f, val square: Boolean = false)

/**
 * Частицы поверх всего экрана: искры, сердечки, пиксели, угли, пыль, дым. Рисуются только пока
 * что-то летит, в остальное время ничего не считается.
 */
class FxController {
    private val bits = ArrayList<Bit>()
    private val rings = ArrayList<Ring>()
    internal var frame by mutableLongStateOf(0L)
    internal var running by mutableStateOf(false)
    private val rnd = Random(System.nanoTime())

    /** Взрыв частиц темы в точке экрана. power: 1 — нажатие кнопки, 2–3 — старт поездки. */
    fun burst(at: Offset, power: Float = 1f, kind: Particle = ShColors.L.particle, colors: List<Color> = ShColors.L.particleColors) {
        if (at == Offset.Unspecified) return
        val n = (10 * power).toInt().coerceIn(6, 60)
        val d = density
        for (i in 0 until n) {
            val ang = rnd.nextFloat() * 6.2832f
            val sp = (90f + rnd.nextFloat() * 220f) * d * (0.6f + 0.4f * power)
            val c = colors[i % colors.size]
            when (kind) {
                Particle.SMOKE, Particle.DUST -> bits += Bit(at.x, at.y, cos(ang) * sp * 0.35f, sin(ang) * sp * 0.35f - 20f * d, 0.9f + rnd.nextFloat() * 0.7f, (10f + rnd.nextFloat() * 14f) * d, c, kind, 0f, 0f)
                Particle.EMBER -> bits += Bit(at.x, at.y, cos(ang) * sp * 0.6f, sin(ang) * sp * 0.6f - 60f * d, 0.7f + rnd.nextFloat() * 0.6f, (2.5f + rnd.nextFloat() * 3f) * d, c, kind, 0f, 0f)
                else -> bits += Bit(at.x, at.y, cos(ang) * sp, sin(ang) * sp, 0.55f + rnd.nextFloat() * 0.45f, (3f + rnd.nextFloat() * 4f) * d, c, kind, rnd.nextFloat() * 360f, (rnd.nextFloat() - 0.5f) * 720f)
            }
        }
        rings += Ring(at.x, at.y, colors.first(), (60f + 50f * power) * d, 0.55f + 0.15f * power, square = kind == Particle.PIXEL)
        running = true
    }

    /** Только кольцо, без частиц. */
    fun ring(at: Offset, color: Color = ShColors.Accent, radiusDp: Float = 90f) {
        if (at == Offset.Unspecified) return
        rings += Ring(at.x, at.y, color, radiusDp * density, 0.6f)
        running = true
    }

    internal var density = 3f

    internal fun step(dt: Float): Boolean {
        val g = 520f * density
        val it = bits.iterator()
        while (it.hasNext()) {
            val b = it.next()
            b.age += dt
            if (b.age >= b.life) { it.remove(); continue }
            val drag = when (b.kind) { Particle.SMOKE, Particle.DUST -> 2.2f; Particle.EMBER -> 1.2f; else -> 2.6f }
            b.vx -= b.vx * drag * dt
            b.vy -= b.vy * drag * dt
            when (b.kind) {
                Particle.SPARK -> b.vy += g * dt
                Particle.EMBER -> b.vy -= 40f * density * dt
                Particle.HEART -> b.vy -= 30f * density * dt
                Particle.SMOKE, Particle.DUST -> b.vy -= 8f * density * dt
                Particle.PIXEL -> Unit
            }
            b.x += b.vx * dt
            b.y += b.vy * dt
            b.rot += b.vr * dt
        }
        val ir = rings.iterator()
        while (ir.hasNext()) { val r = ir.next(); r.age += dt; if (r.age >= r.life) ir.remove() }
        return bits.isNotEmpty() || rings.isNotEmpty()
    }

    internal fun draw(scope: DrawScope) = with(scope) {
        for (r in rings) {
            val p = r.age / r.life
            val e = 1f - (1f - p) * (1f - p)
            val rad = r.maxR * e
            val a = (1f - p).coerceIn(0f, 1f)
            if (r.square) {
                rotate(45f + 90f * p, Offset(r.x, r.y)) {
                    drawRect(r.color.copy(alpha = a), Offset(r.x - rad, r.y - rad), Size(rad * 2, rad * 2), style = Stroke(2.5f * density))
                }
            } else {
                drawCircle(r.color.copy(alpha = a), rad, Offset(r.x, r.y), style = Stroke(3f * density * (1f - p) + 1f))
            }
        }
        for (b in bits) {
            val p = b.age / b.life
            val a = (1f - p).coerceIn(0f, 1f)
            val pos = Offset(b.x, b.y)
            when (b.kind) {
                Particle.PIXEL -> {
                    val flick = if (((b.age * 30).toInt() % 3) == 0) 0.5f else 1f
                    drawRect(b.color.copy(alpha = a * flick), Offset(b.x - b.size / 2, b.y - b.size / 2), Size(b.size, b.size))
                }
                Particle.SPARK -> {
                    val tail = Offset(b.x - b.vx * 0.035f, b.y - b.vy * 0.035f)
                    drawLine(b.color.copy(alpha = a), tail, pos, strokeWidth = b.size * 0.45f, cap = StrokeCap.Round)
                }
                Particle.HEART -> rotate(b.rot * 0.15f, pos) { drawPath(heart(b.x, b.y, b.size * 0.9f * (1f - 0.3f * p)), b.color.copy(alpha = a)) }
                Particle.EMBER -> {
                    val r = b.size * 3f
                    drawCircle(Brush.radialGradient(listOf(b.color.copy(alpha = a * 0.8f), Color.Transparent), pos, r), r, pos)
                    drawCircle(Color.White.copy(alpha = a), b.size * 0.5f, pos)
                }
                Particle.DUST, Particle.SMOKE -> {
                    val r = b.size * (1f + p * 2.2f)
                    val k = if (b.kind == Particle.DUST) 0.35f else 0.28f
                    drawCircle(Brush.radialGradient(listOf(b.color.copy(alpha = a * k), Color.Transparent), pos, r), r, pos)
                }
            }
        }
    }

    private fun heart(cx: Float, cy: Float, s: Float): Path = Path().apply {
        moveTo(cx, cy + s * 0.9f)
        cubicTo(cx - s * 1.5f, cy - s * 0.1f, cx - s * 0.7f, cy - s * 1.2f, cx, cy - s * 0.45f)
        cubicTo(cx + s * 0.7f, cy - s * 1.2f, cx + s * 1.5f, cy - s * 0.1f, cx, cy + s * 0.9f)
        close()
    }
}

val LocalFx = staticCompositionLocalOf { FxController() }

/** Слой частиц на весь экран. Ставится один раз поверх всего интерфейса. */
@Composable
fun FxHost(controller: FxController) {
    LaunchedEffect(controller.running) {
        if (!controller.running) return@LaunchedEffect
        var last = 0L
        while (true) {
            val more = withFrameNanos { now ->
                val dt = if (last == 0L) 0.016f else ((now - last) / 1e9f).coerceIn(0f, 0.05f)
                last = now
                val m = controller.step(dt)
                controller.frame = now
                m
            }
            if (!more) break
        }
        controller.running = false
    }
    Canvas(Modifier.fillMaxSize()) {
        controller.density = density
        controller.frame // перерисовка на каждом кадре, пока что-то летит
        if (controller.running) controller.draw(this)
    }
}

/** Запоминает, где на экране находится элемент, чтобы из его центра вылетали частицы. */
class FxAnchor { var center: Offset = Offset.Unspecified }

@Composable
fun rememberFxAnchor(): FxAnchor = remember { FxAnchor() }

fun Modifier.fxAnchor(a: FxAnchor): Modifier = onGloballyPositioned { a.center = it.boundsInRoot().center }
