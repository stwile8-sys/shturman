package ru.shturman.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.shturman.ui.fx.LocalFx
import ru.shturman.ui.fx.apexScanlines
import ru.shturman.ui.fx.fxAnchor
import ru.shturman.ui.fx.rememberFxAnchor
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
 * Надпись «Поехали!» собирается по буквам (у каждой темы по-своему), в момент появления — вспышка,
 * лёгкая тряска и взрыв частиц темы; в конце буквы улетают. Камера карты в это время подлетает к стрелке.
 * trigger: каждый раз, когда число растёт, сцена проигрывается заново. Рисуется лёгкими средствами.
 */
@Composable
fun StartOverlay(trigger: Int) {
    val flavor = ShColors.L.flavor
    val motion = ShColors.M
    val dur = when (flavor) { Flavor.TELEMETRY -> 2300; Flavor.APEX -> 2100; Flavor.PREMIUM -> 1500; else -> 1900 }
    val progress = remember { Animatable(0f) }
    var visible by remember { mutableStateOf(false) }
    val fx = LocalFx.current
    val anchor = rememberFxAnchor()
    // момент, когда появляется надпись (у телеметрии — после зелёного)
    val textAt = when (flavor) { Flavor.TELEMETRY -> 0.6f; Flavor.APEX -> 0.34f; Flavor.PREMIUM -> 0.12f; else -> 0.1f }

    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        visible = true
        progress.snapTo(0f)
        launch {
            delay((dur * (textAt + 0.08f)).toLong())
            if (anchor.center.isSpecified) fx.burst(anchor.center, 2.2f)
        }
        progress.animateTo(1f, tween(durationMillis = dur, easing = LinearEasing))
        visible = false
    }
    if (!visible) return

    val accent = ShColors.Accent
    val accent2 = ShColors.Accent2
    val street = ShColors.themeId == 5
    val p = progress.value

    Box(
        modifier = Modifier
            .fillMaxSize()
            .fxAnchor(anchor)
            .graphicsLayer {
                // короткая тряска в момент старта
                val k = lin(p, textAt, textAt + 0.14f)
                if (k > 0f && k < 1f && flavor != Flavor.GLASS && flavor != Flavor.PREMIUM) {
                    val amp = 5f * density * (1f - k) * (1f - k)
                    translationX = sin(k * 47f) * amp
                    translationY = sin(k * 61f + 1f) * amp * 0.7f
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val out = 1f - lin(p, 0.8f, 1f)
            when (flavor) {
                Flavor.GLASS -> drawGlass(p, out, accent, accent2)
                Flavor.NEON -> drawNeon(p, out, accent, accent2, street)
                Flavor.TELEMETRY -> drawLights(p, out, accent)
                Flavor.DEPTH -> drawTunnel(p, out, accent, accent2)
                Flavor.APEX -> drawApexGate(p, out, textAt)
                Flavor.PREMIUM -> drawPremium(p, out, accent, motion)
            }
            // вспышка в момент появления надписи (в новом оформлении без вспышки)
            val f = lin(p, textAt, textAt + 0.16f)
            if (flavor != Flavor.PREMIUM && p >= textAt && f < 1f) drawRect(Color.White.copy(alpha = 0.24f * (1f - f) * (1f - f)))
        }
        KineticTitle(
            text = when (flavor) { Flavor.GLASS -> "Поехали!"; Flavor.APEX, Flavor.PREMIUM -> "ПОЕХАЛИ"; else -> "ПОЕХАЛИ!" },
            p = p, textAt = textAt, flavor = flavor, accent = accent, accent2 = accent2, motion = motion,
            modifier = Modifier.align(Alignment.Center)
        )
        if (flavor == Flavor.APEX) {
            ApexStopwatch(p = p, textAt = textAt, dur = dur, modifier = Modifier.align(Alignment.Center).padding(bottom = 150.dp))
        }
    }
}

/** Отскок с перелётом: буква чуть увеличивается больше нормы и возвращается. */
private fun backOut(x: Float): Float {
    val t = x.coerceIn(0f, 1f) - 1f
    return 1f + 2.70158f * t * t * t + 1.70158f * t * t
}

/**
 * Надпись по буквам. Появление: стекло — пружинный «поп» снизу; неон — мерцание с цветными двойниками;
 * телеметрия — въезд справа с наклоном; глубина — из камеры на экран. Уход: буквы улетают по характеру темы.
 */
@Composable
private fun KineticTitle(text: String, p: Float, textAt: Float, flavor: Flavor, accent: Color, accent2: Color, modifier: Modifier, motion: ru.shturman.ui.themes.Motion = ru.shturman.ui.themes.Motion.PRECISE) {
    val italic = flavor != Flavor.GLASS && flavor != Flavor.APEX && flavor != Flavor.PREMIUM
    Row(modifier = modifier) {
        text.forEachIndexed { i, ch ->
            val a0 = textAt + i * 0.022f
            val g = lin(p, a0, a0 + 0.17f)
            val o = lin(p, 0.78f + i * 0.012f, 0.93f + i * 0.012f)
            if (g <= 0f) return@forEachIndexed
            Box {
                // цветные двойники у неона — сбой сигнала
                if (flavor == Flavor.NEON && (g < 1f || o > 0f)) {
                    val k = if (g < 1f) 1f - g else o
                    for ((col, dx) in listOf(accent to -3f, accent2 to 3f)) {
                        Text(
                            text = ch.toString(), color = col.copy(alpha = 0.8f * k), fontFamily = NumberFont, fontWeight = FontWeight.Bold,
                            fontStyle = FontStyle.Italic, fontSize = 46.sp, letterSpacing = 3.sp,
                            modifier = Modifier.graphicsLayer { translationX = dx * density * (1f + 2f * k) }
                        )
                    }
                }
                Text(
                    text = ch.toString(),
                    color = Color.White,
                    fontFamily = NumberFont,
                    fontWeight = if (flavor == Flavor.PREMIUM) FontWeight.Light else FontWeight.Bold,
                    fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
                    fontSize = if (flavor == Flavor.PREMIUM) 34.sp else 46.sp,
                    letterSpacing = when { flavor == Flavor.PREMIUM -> 8.sp; flavor == Flavor.APEX -> 6.sp; italic -> 3.sp; else -> 1.sp },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.graphicsLayer {
                        cameraDistance = 14f * density
                        var al = g.coerceIn(0f, 1f)
                        when (flavor) {
                            Flavor.GLASS -> {
                                val s = 0.3f + 0.7f * backOut(g)
                                scaleX = s; scaleY = s
                                translationY = (1f - ease(g)) * 30f * density - o * 24f * density
                                val so = 1f + 0.4f * ease(o); scaleX *= so; scaleY *= so
                            }
                            Flavor.NEON -> {
                                al = if (g >= 1f) 1f else ((g * 6f).toInt() / 6f).coerceAtLeast(0.2f)
                                if (g < 1f) translationX = (if ((g * 14).toInt() % 2 == 0) 7f else -5f) * density * (1f - g)
                                if (o > 0f) {
                                    translationX = (if ((o * 16).toInt() % 2 == 0) 9f else -9f) * density * o
                                    scaleY = 1f - 0.85f * ease(o)
                                }
                            }
                            Flavor.TELEMETRY -> {
                                translationX = (1f - ease(g)) * 260f * density - ease(o) * 700f * density
                                rotationY = (1f - ease(g)) * -40f
                            }
                            Flavor.DEPTH -> {
                                val s = (3f - 2f * ease(g)) * (1f + 2.2f * o * o)
                                scaleX = s; scaleY = s
                                rotationX = (1f - ease(g)) * 50f
                            }
                            Flavor.PREMIUM -> {
                                // буквы проявляются по очереди, у каждой темы — своим движением, и спокойно уходят
                                val e = ease(g)
                                when (motion) {
                                    ru.shturman.ui.themes.Motion.PRECISE -> translationY = (1f - e) * 10f * density - ease(o) * 8f * density
                                    ru.shturman.ui.themes.Motion.POWER_ON -> al = ru.shturman.ui.themes.Motions.flicker(g)
                                    ru.shturman.ui.themes.Motion.FLOAT -> { translationY = (1f - e) * 24f * density - ease(o) * 16f * density; val sc = 0.9f + 0.1f * e; scaleX = sc; scaleY = sc }
                                    ru.shturman.ui.themes.Motion.SIDE_DASH -> translationX = -(1f - e) * 60f * density + ease(o) * 90f * density
                                    ru.shturman.ui.themes.Motion.WIPE -> translationX = (1f - e) * 30f * density - ease(o) * 30f * density
                                    ru.shturman.ui.themes.Motion.EMERGE -> { val sc = 1.25f - 0.25f * e + 0.15f * ease(o); scaleX = sc; scaleY = sc }
                                    ru.shturman.ui.themes.Motion.DROP -> translationY = -(1f - e) * 26f * density + ease(o) * 10f * density
                                    ru.shturman.ui.themes.Motion.DUST -> { translationX = (1f - e) * 40f * density; rotationZ = (1f - e) * 6f }
                                    ru.shturman.ui.themes.Motion.FOG -> { al = g * g; translationY = (1f - e) * 6f * density }
                                }
                            }
                            Flavor.APEX -> {
                                // буквы загораются, как лампы табло, каждая со своим сбоем; гаснут вразнобой
                                al = ru.shturman.ui.fx.apexIgnite(g)
                                if (o > 0f && ((o * 14f).toInt() + i) % 3 == 0) al *= 0.15f
                            }
                        }
                        alpha = al * (1f - ease(o))
                    }
                )
            }
        }
    }
}

/**
 * Новое оформление: лёгкое затемнение и под надписью — тонкий рисунок в характере темы:
 * линия от центра, неоновая развёртка, мягкое свечение, полосы, шторка, след, луч фар, пыль или туман.
 */
private fun DrawScope.drawPremium(p: Float, out: Float, a: Color, m: ru.shturman.ui.themes.Motion) {
    drawRect(Color.Black.copy(alpha = 0.45f * lin(p, 0f, 0.12f) * out))
    val w = size.width
    val h = size.height
    val cy = h / 2f
    val y = cy + 34f * density
    val line = 2f * density
    when (m) {
        ru.shturman.ui.themes.Motion.PRECISE -> {
            val half = w * 0.3f * ease(lin(p, 0.08f, 0.55f))
            if (half > 0f) drawRect(a.copy(alpha = out), Offset(w / 2f - half, y), Size(half * 2f, line))
        }
        ru.shturman.ui.themes.Motion.POWER_ON -> {
            // развёртка сверху вниз и неоновая линия, которая «включается» с мерцанием
            val sy = h * lin(p, 0.02f, 0.5f)
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, a.copy(alpha = 0.22f * out), Color.Transparent), sy - 60f * density, sy + 10f * density), Offset(0f, sy - 60f * density), Size(w, 70f * density))
            val f = ru.shturman.ui.themes.Motions.flicker(lin(p, 0.1f, 0.5f))
            drawRect(a.copy(alpha = out * f), Offset(w * 0.2f, y), Size(w * 0.6f, line))
        }
        ru.shturman.ui.themes.Motion.FLOAT -> {
            val r = w * (0.25f + 0.45f * ease(lin(p, 0f, 0.6f)))
            drawCircle(Brush.radialGradient(listOf(a.copy(alpha = 0.28f * out), Color.Transparent), Offset(w / 2f, cy), r), r, Offset(w / 2f, cy))
        }
        ru.shturman.ui.themes.Motion.SIDE_DASH -> {
            // две белые полосы проносятся слева направо
            for (i in 0..1) {
                val k = lin(p, 0.05f + i * 0.08f, 0.45f + i * 0.08f)
                val x = -w * 0.6f + k * w * 1.8f
                drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.7f * out)), x, x + w * 0.5f), Offset(x, y + i * 8f * density), Size(w * 0.5f, line))
            }
        }
        ru.shturman.ui.themes.Motion.WIPE -> {
            // полоса цветом темы проходит слева направо, оставляя тонкую линию
            val k = ease(lin(p, 0.04f, 0.5f))
            drawRect(a.copy(alpha = 0.18f * out * (1f - k)), Offset(0f, cy - 40f * density), Size(w * k, 80f * density))
            drawRect(a.copy(alpha = out), Offset(0f, y), Size(w * k, line))
        }
        ru.shturman.ui.themes.Motion.EMERGE -> {
            // синий след: три линии расходятся от центра в стороны
            val k = ease(lin(p, 0.05f, 0.6f))
            for (i in -1..1) {
                val yy = y + i * 10f * density
                val half = w * (0.12f + 0.3f * k) * (1f - 0.25f * kotlin.math.abs(i))
                drawRect(Brush.horizontalGradient(listOf(Color.Transparent, a.copy(alpha = 0.8f * out), Color.Transparent), w / 2f - half, w / 2f + half), Offset(w / 2f - half, yy), Size(half * 2f, line * 0.8f))
            }
        }
        ru.shturman.ui.themes.Motion.DROP -> {
            // луч фар по диагонали
            val k = lin(p, 0.02f, 0.55f)
            val x = -w * 0.5f + k * w * 2f
            drawRect(Brush.linearGradient(listOf(Color.Transparent, a.copy(alpha = 0.3f * out), Color.Transparent), Offset(x - w * 0.25f, 0f), Offset(x + w * 0.25f, h)))
        }
        ru.shturman.ui.themes.Motion.DUST -> {
            // мягкие клубы пыли снизу
            for (i in 0 until 6) {
                val k = lin(p, i * 0.04f, 0.7f + i * 0.04f)
                if (k <= 0f || k >= 1f) continue
                val c = Offset(w * (0.15f + i * 0.14f), h * (0.82f - 0.25f * k))
                val r = (24f + 40f * k) * density
                drawCircle(Brush.radialGradient(listOf(a.copy(alpha = 0.22f * (1f - k) * out), Color.Transparent), c, r), r, c)
            }
        }
        ru.shturman.ui.themes.Motion.FOG -> {
            // две полосы тумана медленно плывут навстречу
            for (i in 0..1) {
                val k = lin(p, 0f, 1f)
                val x = if (i == 0) -w * 0.4f + k * w * 0.5f else w * 0.4f - k * w * 0.5f
                drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.10f * out), Color.Transparent), x, x + w), Offset(x, cy - (40f - i * 70f) * density), Size(w, 60f * density))
            }
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

/** APEX: секундомер заезда стартует вместе с надписью. Погасшие сегменты видны под цифрами. */
@Composable
private fun ApexStopwatch(p: Float, textAt: Float, dur: Int, modifier: Modifier) {
    if (p < textAt) return
    val ms = ((p - textAt) * dur).toInt().coerceAtLeast(0)
    val text = String.format(java.util.Locale.ROOT, "00:%02d.%02d", ms / 1000, (ms % 1000) / 10)
    val a = ru.shturman.ui.fx.apexIgnite(lin(p, textAt, textAt + 0.12f)) * (1f - lin(p, 0.86f, 1f))
    Box(modifier.graphicsLayer { alpha = a }) {
        Text("88:88.88", color = Color.White.copy(alpha = 0.08f), fontFamily = NumberFont, fontWeight = FontWeight.Bold, fontSize = 34.sp)
        Text(text, color = ru.shturman.ui.fx.ApexChalk, fontFamily = NumberFont, fontWeight = FontWeight.Bold, fontSize = 34.sp)
    }
}

/**
 * APEX: створ хронометража. По краям загораются стойки, между ними — красный луч; красная точка
 * бежит по лучу, и в момент старта луч вспыхивает мелом и гаснет. Поверх — строки развёртки.
 */
private fun DrawScope.drawApexGate(p: Float, out: Float, textAt: Float) {
    val chalk = ru.shturman.ui.fx.ApexChalk
    val red = ru.shturman.ui.fx.ApexRed
    drawRect(Color(0xFF060605).copy(alpha = 0.72f * lin(p, 0f, 0.06f) * out))
    val y = size.height * 0.68f
    val post = 30f * density
    val pw = 3f * density
    val m = 18f * density
    val postA = ru.shturman.ui.fx.apexIgnite(lin(p, 0.02f, 0.12f)) * out
    drawRect(chalk.copy(alpha = postA), Offset(m, y - post / 2), Size(pw, post))
    drawRect(chalk.copy(alpha = postA), Offset(size.width - m - pw, y - post / 2), Size(pw, post))
    val x0 = m + pw
    val x1 = size.width - m - pw
    val beamOn = ru.shturman.ui.fx.apexIgnite(lin(p, 0.08f, 0.2f))
    val fire = lin(p, textAt, textAt + 0.14f)
    if (beamOn > 0f && out > 0f) {
        val col = if (p >= textAt) androidx.compose.ui.graphics.lerp(chalk, red, fire) else red
        val a = beamOn * (if (p >= textAt) 1f - 0.85f * fire else 1f) * out
        val gh = 16f * density
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, col.copy(alpha = 0.35f * a), Color.Transparent), y - gh, y + gh),
            Offset(x0, y - gh), Size(x1 - x0, gh * 2)
        )
        drawRect(col.copy(alpha = a), Offset(x0, y - density), Size(x1 - x0, 2f * density))
    }
    if (p < textAt && out > 0f) {
        val k = lin(p, 0.14f, textAt)
        val c = Offset(x0 + (x1 - x0) * k, y)
        val gr = 22f * density
        drawCircle(Brush.radialGradient(listOf(red.copy(alpha = 0.5f), Color.Transparent), c, gr), gr, c)
        drawCircle(red, 5f * density, c)
    }
    apexScanlines(0.22f * out)
}
