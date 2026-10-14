package ru.shturman.ui.themes

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

// Общие «киношные» детали для заставок: ударная волна, лучи, блик объектива, конфетти, дождь.

private fun Color.al(x: Float): Color = copy(alpha = x.coerceIn(0f, 1f))
private fun DrawScope.u1(): Float = min(size.width, size.height) / 100f
private fun prog(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)

/** Ударная волна: два кольца со светлым ореолом расходятся из точки c в момент at. */
internal fun DrawScope.shockRing(t: Float, at: Float, c: Offset, maxR: Float, col: Color, dur: Float = 700f, strength: Float = 1f) {
    val u = u1()
    for (k in 0..1) {
        val p = prog(t, at + k * 130f, at + k * 130f + dur)
        if (p <= 0f || p >= 1f) continue
        val e = 1f - (1f - p).pow(3)
        val r = maxR * e * (1f - 0.18f * k)
        val a = (1f - p) * strength * (1f - 0.35f * k)
        if (r < 1f) continue
        drawCircle(
            Brush.radialGradient(0.78f to Color.Transparent, 0.95f to col.al(0.22f * a), 1f to Color.Transparent, center = c, radius = r * 1.08f),
            r * 1.08f, c
        )
        drawCircle(col.al(0.75f * a), r, c, style = Stroke(u * (1.8f - 1.2f * p)))
    }
}

/** Мягкие лучи из точки c, медленно поворачиваются (rot — угол в радианах). */
internal fun DrawScope.raysFrom(c: Offset, n: Int, len: Float, col: Color, a: Float, rot: Float, spread: Float = 0.07f) {
    if (a <= 0.003f) return
    val brush = Brush.radialGradient(listOf(col.al(a), Color.Transparent), c, len)
    for (k in 0 until n) {
        val ang = rot + k * 2f * PI.toFloat() / n
        val ex = c.x + cos(ang) * len; val ey = c.y + sin(ang) * len
        val nx = -sin(ang) * len * spread; val ny = cos(ang) * len * spread
        drawPath(Path().apply { moveTo(c.x, c.y); lineTo(ex + nx, ey + ny); lineTo(ex - nx, ey - ny); close() }, brush)
    }
}

/** Блик объектива: звезда в источнике, горизонтальная черта и «призраки» по линии через центр кадра. */
internal fun DrawScope.flareAt(src: Offset, col: Color, a: Float) {
    if (a <= 0.01f) return
    val u = u1()
    val c = Offset(size.width / 2, size.height / 2)
    drawCircle(Brush.radialGradient(listOf(col.al(0.45f * a), Color.Transparent), src, u * 16f), u * 16f, src)
    val len = u * 34f
    drawLine(
        Brush.horizontalGradient(listOf(Color.Transparent, col.al(0.6f * a), Color.White.al(0.8f * a), col.al(0.6f * a), Color.Transparent), src.x - len, src.x + len),
        Offset(src.x - len, src.y), Offset(src.x + len, src.y), u * 0.45f
    )
    val r = u * 5f * a
    drawPath(Path().apply {
        moveTo(src.x, src.y - r * 1.6f)
        quadraticTo(src.x, src.y, src.x + r, src.y); quadraticTo(src.x, src.y, src.x, src.y + r * 1.6f)
        quadraticTo(src.x, src.y, src.x - r, src.y); quadraticTo(src.x, src.y, src.x, src.y - r * 1.6f); close()
    }, Color.White.al(0.85f * a))
    val ghosts = floatArrayOf(0.55f, 0.85f, 1.35f, 1.7f)
    val radii = floatArrayOf(2.5f, 5.5f, 3.5f, 8f)
    for (i in ghosts.indices) {
        val p = Offset(src.x + (c.x - src.x) * ghosts[i], src.y + (c.y - src.y) * ghosts[i])
        val gr = u * radii[i]
        drawCircle(col.al(0.07f * a), gr, p)
        drawCircle(col.al(0.12f * a), gr, p, style = Stroke(u * 0.3f))
    }
}

/** Конфетти сыплется сверху, начиная с момента at. */
internal fun DrawScope.confettiRain(t: Float, at: Float, cols: List<Color>, n: Int = 70, seed: Int = 5) {
    if (t < at) return
    val w = size.width; val h = size.height; val u = u1()
    val rnd = Random(seed)
    for (i in 0 until n) {
        val x0 = rnd.nextFloat() * w
        val dl = rnd.nextFloat() * 700f
        val spd = h * (0.22f + 0.3f * rnd.nextFloat())
        val spin = 0.5f + rnd.nextFloat()
        val p = (t - at - dl) / 1000f
        if (p < 0f) continue
        val y = -u * 4f + spd * p
        if (y > h + u * 4f) continue
        val x = x0 + sin(p * 3f + i) * u * 5f
        val flip = cos(p * 9f * spin + i)
        val sw = u * 1.7f; val sh = u * 1.0f * (0.25f + 0.75f * abs(flip))
        rotate(p * 300f * spin + i * 37f, Offset(x, y)) {
            drawRect(cols[i % cols.size].al(0.95f), Offset(x - sw / 2, y - sh / 2), Size(sw, sh))
        }
    }
}

/** Косой дождь тонкими штрихами. */
internal fun DrawScope.rainStreaks(t: Float, col: Color, a: Float, n: Int = 70, seed: Int = 12) {
    if (a <= 0.01f) return
    val w = size.width; val h = size.height; val u = u1()
    val rnd = Random(seed)
    for (i in 0 until n) {
        val x0 = rnd.nextFloat() * w * 1.2f
        val spd = 1.6f + rnd.nextFloat() * 0.9f
        val ph = rnd.nextFloat()
        val p = ((t / 1000f * spd + ph) % 1f)
        val y = -h * 0.1f + p * h * 1.2f
        val x = x0 - p * w * 0.12f
        val len = u * (4f + 5f * rnd.nextFloat())
        drawLine(col.al(a * (0.5f + 0.5f * rnd.nextFloat())), Offset(x, y), Offset(x - len * 0.25f, y + len), u * 0.18f)
    }
}
