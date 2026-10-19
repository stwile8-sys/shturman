package ru.shturman.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.shturman.ui.fx.panel
import ru.shturman.ui.fx.panelShape
import ru.shturman.ui.theme.ShColors
import ru.shturman.ui.theme.ShText
import ru.shturman.ui.themes.Flavor
import ru.shturman.ui.themes.ThemedText
import ru.shturman.ui.themes.themedEnter

/**
 * Нижняя карточка поездки: сколько проехали. Полоса по характеру темы: стекло — светящаяся точка на конце;
 * неон — бегущие полосы; телеметрия — сектора, как на гоночной трассе; глубина — объёмная полоса в «желобе».
 */
@Composable
fun ProgressCard(
    modifier: Modifier = Modifier,
    progress: Float = 0.42f,
    traveledKm: String = "12,4",
    totalKm: String = "29,5",
    eta: String? = null,
    showPercent: Boolean = true,
    showDistance: Boolean = true,
    showEta: Boolean = true,
    remaining: String? = null,
    fuel: String? = null
) {
    val l = ShColors.L
    Column(
        modifier = modifier
            .themedEnter()
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .panel(panelShape(30.dp), elevation = 18.dp)
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 18.dp)
    ) {
        val parts = ArrayList<String>()
        if (showDistance) parts += "$traveledKm из $totalKm км"
        if (showEta && eta != null) parts += "прибытие $eta"
        if (remaining != null) parts += remaining
        if (fuel != null) parts += fuel
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            if (showPercent) {
                ThemedText(text = "${(progress * 100).toInt()}%", color = ShColors.TextPrimary, style = ShText.BigNumber)
            }
            Spacer(Modifier.weight(1f))
            if (parts.isNotEmpty()) {
                Text(
                    text = parts.joinToString(" · "),
                    color = ShColors.TextSecondary,
                    style = ShText.Small.copy(fontSize = 13.sp),
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(if (showPercent) 2f else 1f)
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        ProgressBar(progress = progress, flavor = l.flavor)
    }
}

@Composable
private fun ProgressBar(progress: Float, flavor: Flavor) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), spring(dampingRatio = 0.9f, stiffness = 60f), label = "prog")
    val tr = rememberInfiniteTransition(label = "bar")
    val run by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(700, easing = LinearEasing)), label = "run")
    val pulse by tr.animateFloat(0.35f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "pulse")
    val a = ShColors.Accent
    val b = ShColors.Accent2
    val h = when (flavor) { Flavor.TELEMETRY -> 10.dp; Flavor.DEPTH -> 12.dp; Flavor.NEON -> 6.dp; Flavor.GLASS -> 8.dp; Flavor.APEX -> 12.dp; Flavor.PREMIUM -> 2.dp }
    Canvas(Modifier.fillMaxWidth().height(h + 8.dp)) {
        val barH = h.toPx()
        val top = (size.height - barH) / 2
        val w = size.width
        val fw = w * p
        when (flavor) {
            Flavor.PREMIUM -> {
                // тонкая линия: серая дорожка и пройденное акцентом
                drawRect(ShColors.Divider, Offset(0f, top), Size(w, barH))
                if (fw > 0f) drawRect(a, Offset(0f, top), Size(fw, barH))
            }
            Flavor.TELEMETRY -> {
                val n = 12
                val gap = 3.dp.toPx()
                val sw = (w - gap * (n - 1)) / n
                val done = p * n
                for (i in 0 until n) {
                    val x = i * (sw + gap)
                    val col = when {
                        i + 1 <= done -> if (i % 4 == 1) Color(0xFFB54BFF) else a
                        i < done -> Color.White.copy(alpha = 0.35f + 0.5f * pulse)
                        else -> Color.White.copy(alpha = 0.10f)
                    }
                    drawRect(col, Offset(x, top), Size(sw, barH))
                }
            }
            Flavor.DEPTH -> {
                drawRoundRect(Color.Black.copy(alpha = 0.45f), Offset(0f, top), Size(w, barH), CornerRadius(barH / 2))
                drawRoundRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent), top, top + barH * 0.6f), Offset(0f, top), Size(w, barH), CornerRadius(barH / 2))
                if (fw > 0f) {
                    drawRoundRect(Brush.verticalGradient(listOf(a, b), top, top + barH), Offset(0f, top), Size(fw.coerceAtLeast(barH), barH), CornerRadius(barH / 2))
                    drawRoundRect(Color.White.copy(alpha = 0.35f), Offset(barH * 0.3f, top + 2f), Size((fw - barH * 0.6f).coerceAtLeast(0f), barH * 0.22f), CornerRadius(barH * 0.1f))
                }
            }
            Flavor.NEON -> {
                drawRoundRect(Color.White.copy(alpha = 0.08f), Offset(0f, top), Size(w, barH), CornerRadius(barH / 2))
                if (fw > 0f) {
                    drawRoundRect(a.copy(alpha = 0.35f), Offset(0f, top - barH), Size(fw, barH * 3), CornerRadius(barH * 1.5f))
                    clipRect(0f, top, fw, top + barH) {
                        val step = 14.dp.toPx()
                        var x = -step * 2 + run * step * 2
                        while (x < fw) {
                            drawRect(a, Offset(x, top), Size(step * 1.4f, barH))
                            drawRect(b, Offset(x + step * 1.4f, top), Size(step * 0.6f, barH))
                            x += step * 2
                        }
                    }
                }
            }
            Flavor.GLASS -> {
                drawRoundRect(Color.White.copy(alpha = 0.10f), Offset(0f, top), Size(w, barH), CornerRadius(barH / 2))
                if (fw > 0f) {
                    drawRoundRect(Brush.horizontalGradient(listOf(b, a), 0f, fw.coerceAtLeast(1f)), Offset(0f, top), Size(fw, barH), CornerRadius(barH / 2))
                    val c = Offset(fw, top + barH / 2)
                    val gr = barH * 2.6f
                    drawCircle(Brush.radialGradient(listOf(a.copy(alpha = 0.55f + 0.25f * pulse), Color.Transparent), c, gr), gr, c)
                    drawCircle(Color.White, barH * 0.95f, c)
                }
            }
            Flavor.APEX -> {
                // линия мелом по шву, 12 делений, как на линейке, и светящаяся головка
                val line = 2.dp.toPx()
                val cy = top + barH / 2
                drawRect(ru.shturman.ui.fx.ApexSeam, Offset(0f, cy - line / 2), Size(w, line))
                if (fw > 0f) drawRect(a, Offset(0f, cy - line / 2), Size(fw, line))
                for (i in 0 until 12) {
                    val x = w * i / 12f
                    drawRect(a.copy(alpha = if (x <= fw) 0.45f else 0.3f), Offset(x, top), Size(1.dp.toPx(), barH))
                }
                val c = Offset(fw.coerceIn(0f, w), cy)
                val gr = barH * (1.1f + 0.5f * pulse)
                drawCircle(Brush.radialGradient(listOf(a.copy(alpha = 0.55f), Color.Transparent), c, gr), gr, c)
                drawCircle(a, 4.dp.toPx(), c)
            }
        }
    }
}
