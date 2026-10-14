package ru.shturman.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import ru.shturman.ui.theme.NumberFont
import ru.shturman.ui.theme.ShColors
import ru.shturman.ui.themes.Flavor
import kotlin.math.min
import kotlin.math.sin

private fun ease(x: Float): Float { val t = x.coerceIn(0f, 1f); return t * t * (3f - 2f * t) }
private fun lin(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)

/**
 * Старт поездки после «Поехали», у каждой темы свой:
 * стекло — мягкая вспышка и кольцо; неон — квадратная волна, скан и глитч надписи (у Street ещё мигалки);
 * телеметрия — пять стартовых огней, зелёный и полосы скорости; глубина — тоннель из глубины.
 * trigger: каждый раз, когда число растёт, сцена проигрывается заново. Рисуется лёгкими средствами.
 */
@Composable
fun StartOverlay(trigger: Int) {
    val flavor = ShColors.L.flavor
    val dur = if (flavor == Flavor.TELEMETRY) 2100 else 1700
    val progress = remember { Animatable(0f) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        visible = true
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = dur, easing = LinearEasing))
        visible = false
    }
    if (!visible) return

    val accent = ShColors.Accent
    val accent2 = ShColors.Accent2
    val street = ShColors.themeId == 5
    // момент, когда появляется надпись (у телеметрии — после зелёного)
    val textAt = if (flavor == Flavor.TELEMETRY) 0.62f else 0.08f

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val p = progress.value
            val out = 1f - lin(p, 0.78f, 1f)
            when (flavor) {
                Flavor.GLASS -> drawGlass(p, out, accent, accent2)
                Flavor.NEON -> drawNeon(p, out, accent, accent2, street)
                Flavor.TELEMETRY -> drawLights(p, out, accent)
                Flavor.DEPTH -> drawTunnel(p, out, accent, accent2)
            }
        }
        val p = progress.value
        val tA = lin(p, textAt, textAt + 0.12f) * (1f - lin(p, 0.8f, 1f))
        if (tA > 0f) {
            Text(
                text = if (flavor == Flavor.GLASS) "Поехали!" else "ПОЕХАЛИ!",
                color = Color.White,
                fontFamily = NumberFont,
                fontWeight = FontWeight.Bold,
                fontStyle = if (flavor == Flavor.GLASS) FontStyle.Normal else FontStyle.Italic,
                fontSize = 44.sp,
                letterSpacing = if (flavor == Flavor.GLASS) 1.sp else 4.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer {
                        val g = lin(p, textAt, textAt + 0.25f)
                        alpha = tA
                        when (flavor) {
                            Flavor.GLASS -> { val s = 0.7f + 0.3f * (1f + sin(g * 3.14f) * 0.15f) * ease(g); scaleX = s; scaleY = s }
                            Flavor.NEON -> { if (g < 1f) translationX = (if ((g * 12).toInt() % 2 == 0) 8f else -6f) * density * (1f - g) }
                            Flavor.TELEMETRY -> { translationX = (1f - ease(g)) * size.width * 0.6f; rotationY = (1f - ease(g)) * -30f }
                            Flavor.DEPTH -> { val s = 0.3f + 0.7f * ease(g); scaleX = s; scaleY = s; rotationX = (1f - ease(g)) * 50f; cameraDistance = 14f * density }
                        }
                        shadowElevation = 0f
                    }
            )
        }
    }
}

private fun DrawScope.drawGlass(p: Float, out: Float, a: Color, b: Color) {
    drawRect(Color(0xFF05060A).copy(alpha = 0.45f * lin(p, 0f, 0.1f) * out))
    val c = Offset(size.width / 2, size.height * 0.82f)
    val maxR = size.maxDimension
    val bloom = ease(lin(p, 0f, 0.6f))
    drawCircle(Brush.radialGradient(listOf(a.copy(alpha = 0.55f * out), b.copy(alpha = 0.18f * out), Color.Transparent), c, maxR * (0.2f + 0.8f * bloom)), maxR * (0.2f + 0.8f * bloom), c)
    for (k in 0 until 3) {
        val q = lin(p, k * 0.1f, 0.55f + k * 0.1f)
        if (q <= 0f || q >= 1f) continue
        drawCircle(Color.White.copy(alpha = (1f - q) * 0.8f), maxR * 0.9f * ease(q), c, style = Stroke((6f - k * 1.5f) * density))
    }
}

private fun DrawScope.drawNeon(p: Float, out: Float, a: Color, b: Color, police: Boolean) {
    drawRect(Color(0xFF020308).copy(alpha = 0.62f * lin(p, 0f, 0.08f) * out))
    val c = Offset(size.width / 2, size.height / 2)
    for (k in 0 until 3) {
        val q = lin(p, k * 0.08f, 0.5f + k * 0.08f)
        if (q <= 0f || q >= 1f) continue
        val r = size.maxDimension * 0.7f * ease(q)
        rotate(45f + 120f * q, c) {
            drawRect((if (k % 2 == 0) a else b).copy(alpha = (1f - q)), Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(3f * density))
        }
    }
    // скан-линия сверху вниз
    val sy = size.height * lin(p, 0.05f, 0.55f)
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, a.copy(alpha = 0.35f * out), Color.Transparent), sy - 80f * density, sy + 20f * density), Offset(0f, sy - 80f * density), Size(size.width, 100f * density))
    drawLine(a.copy(alpha = 0.9f * out), Offset(0f, sy), Offset(size.width, sy), 2f * density)
    // мелкие строки «развёртки»
    var y = 0f
    while (y < size.height) { drawLine(Color.White.copy(alpha = 0.04f * out), Offset(0f, y), Offset(size.width, y), 1f); y += 4f * density }
    if (police) {
        val ph = ((p * 1700f) / 110f).toInt() % 4
        val red = Color(0xFFFF1E2E); val blue = Color(0xFF2E5BFF)
        val r = size.width * 0.9f
        drawCircle(Brush.radialGradient(listOf(red.copy(alpha = (if (ph < 2) 0.55f else 0.1f) * out), Color.Transparent), Offset(-size.width * 0.05f, size.height * 0.6f), r), r, Offset(-size.width * 0.05f, size.height * 0.6f))
        drawCircle(Brush.radialGradient(listOf(blue.copy(alpha = (if (ph >= 2) 0.55f else 0.1f) * out), Color.Transparent), Offset(size.width * 1.05f, size.height * 0.6f), r), r, Offset(size.width * 1.05f, size.height * 0.6f))
    }
}

private fun DrawScope.drawLights(p: Float, out: Float, a: Color) {
    drawRect(Color(0xFF050505).copy(alpha = 0.66f * lin(p, 0f, 0.06f) * out))
    val green = p >= 0.58f
    val n = 5
    val r = min(size.width / (n * 3.2f), 26f * density)
    val gap = r * 0.9f
    val total = n * r * 2 + (n - 1) * gap
    val x0 = size.width / 2 - total / 2 + r
    val y = size.height * 0.36f
    // панель-стойка огней
    drawRoundRect(Color(0xFF111114).copy(alpha = out), Offset(x0 - r * 1.6f, y - r * 1.7f), Size(total + r * 1.2f, r * 3.4f), CornerRadius(r * 0.6f))
    for (i in 0 until n) {
        val on = p >= 0.06f + i * 0.1f
        val c = Offset(x0 + i * (r * 2 + gap), y)
        val col = when { green -> Color(0xFF00E04A); on -> Color(0xFFFF1A1A); else -> Color(0xFF2A0A0A) }
        if (on || green) drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.6f * out), Color.Transparent), c, r * 2.6f), r * 2.6f, c)
        drawCircle(col.copy(alpha = out), r, c)
        drawCircle(Color.White.copy(alpha = 0.25f * out), r * 0.35f, Offset(c.x - r * 0.3f, c.y - r * 0.3f))
    }
    if (green) {
        val q = lin(p, 0.58f, 0.7f)
        drawRect(Color(0xFF00E04A).copy(alpha = 0.18f * (1f - q)))
        // полосы скорости после зелёного
        val k = lin(p, 0.6f, 1f)
        for (i in 0 until 18) {
            val yy = size.height * ((i * 0.618f) % 1f)
            val len = size.width * (0.3f + 0.4f * ((i * 0.37f) % 1f))
            val x = size.width * (1.1f - k * (1.6f + (i % 4) * 0.35f))
            drawLine(Brush.horizontalGradient(listOf(a.copy(alpha = 0.9f * out), Color.Transparent), x, x + len), Offset(x, yy), Offset(x + len, yy), (2f + i % 3) * density)
        }
    }
}

private fun DrawScope.drawTunnel(p: Float, out: Float, a: Color, b: Color) {
    drawRect(Color(0xFF03040A).copy(alpha = 0.6f * lin(p, 0f, 0.1f) * out))
    val c = Offset(size.width / 2, size.height / 2)
    // рамки летят из глубины на зрителя
    for (k in 0 until 9) {
        val z = ((p * 1.6f + k / 9f) % 1f)
        val s = 0.05f + z * z * 1.6f
        val w = size.width * 0.6f * s
        val h = size.height * 0.45f * s
        val alpha = (z * (1f - z) * 3.2f).coerceIn(0f, 1f) * out
        drawRoundRect(
            (if (k % 2 == 0) a else b).copy(alpha = alpha),
            Offset(c.x - w / 2, c.y - h / 2), Size(w, h), CornerRadius(24f * density * s),
            style = Stroke((1.5f + 3f * z) * density)
        )
    }
    drawCircle(Brush.radialGradient(listOf(a.copy(alpha = 0.45f * out), Color.Transparent), c, size.minDimension * 0.6f), size.minDimension * 0.6f, c)
}
