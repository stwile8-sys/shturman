package ru.shturman.ui.fx

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import ru.shturman.ui.theme.ShColors
import ru.shturman.ui.themes.Particle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Живой фон для полноэкранных окон (поиск, вход, доступ): медленно плывущие пятна света в цветах темы
 * и редкие частицы темы, поднимающиеся вверх. Очень лёгкий: одна канва, несколько градиентов.
 */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier) {
    val tr = rememberInfiniteTransition(label = "aurora")
    val t by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(16000, easing = LinearEasing)), label = "t")
    val bg = ShColors.Background
    val a = ShColors.Accent
    val b = ShColors.Accent2
    val g = ShColors.L.glow
    val kind = ShColors.L.particle
    val cols = ShColors.L.particleColors
    val apex = ShColors.L.flavor == ru.shturman.ui.themes.Flavor.APEX
    Canvas(modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        val tau = (2 * PI).toFloat()
        drawRect(bg)
        if (apex) {
            // APEX: тёмный экран старого монитора — строки развёртки, медленная светлая полоса и редкие пиксели
            val band = h * 0.22f
            val y = -band + ((t * 2.3f) % 1f) * (h + band * 2)
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, a.copy(alpha = 0.05f), Color.Transparent), y, y + band), Offset(0f, y), Size(w, band))
            for (i in 0 until 18) {
                val seed = (i * 0.618034f) % 1f
                val blink = ((t * 40f + i * 7f).toInt() % 9) == 0
                if (blink) continue
                val px = w * seed
                val py = h * ((i * 0.37f + 0.11f) % 1f)
                val ps = 2f * density
                drawRect((if (i % 6 == 0) ApexRed else ApexChalk).copy(alpha = 0.22f), Offset(px, py), Size(ps, ps))
            }
            apexScanlines(0.22f)
            drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)), Offset(w / 2, h / 2), max(w, h) * 0.75f))
            return@Canvas
        }
        fun blob(cx: Float, cy: Float, r: Float, c: Color, alpha: Float) {
            drawCircle(Brush.radialGradient(listOf(c.copy(alpha = alpha), Color.Transparent), Offset(cx, cy), r), r, Offset(cx, cy))
        }
        blob(w * (0.25f + 0.2f * sin(tau * t)), h * (0.18f + 0.06f * cos(tau * t)), w * 0.9f, a, 0.20f)
        blob(w * (0.8f + 0.15f * cos(tau * t + 1.3f)), h * (0.45f + 0.08f * sin(tau * t + 0.7f)), w * 0.8f, b, 0.16f)
        blob(w * (0.4f + 0.25f * sin(tau * t + 2.4f)), h * (0.85f + 0.05f * cos(tau * t)), w * 0.9f, g, 0.12f)
        // частицы темы медленно поднимаются
        for (i in 0 until 26) {
            val seed = (i * 0.618034f) % 1f
            val x = w * ((seed + 0.07f * sin(tau * (t * 2 + seed))) % 1f)
            val y = h * (1.05f - ((t * (0.6f + seed) + seed) % 1.1f))
            val c = cols[i % cols.size]
            val s = (2f + 3f * ((i * 0.37f) % 1f)) * density
            when (kind) {
                Particle.PIXEL -> drawRect(c.copy(alpha = 0.35f), Offset(x, y), Size(s, s))
                Particle.EMBER, Particle.SPARK -> drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.5f), Color.Transparent), Offset(x, y), s * 2.5f), s * 2.5f, Offset(x, y))
                Particle.DUST, Particle.SMOKE -> drawCircle(c.copy(alpha = 0.10f), s * 4f, Offset(x, y))
                Particle.HEART -> drawCircle(c.copy(alpha = 0.35f), s, Offset(x, y))
            }
        }
        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)), Offset(w / 2, h / 2), max(w, h) * 0.8f))
    }
}
