package ru.shturman.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.ui.res.stringResource
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ru.shturman.ui.theme.NumberFont
import ru.shturman.ui.theme.ShColors

private const val SCRIM_MAX = 0.72f
private const val DURATION_MS = 1600

/**
 * Короткая стартовая заставка при нажатии «Поехали»: затемнение, летящие полосы скорости
 * и крупная надпись. Рисуется очень лёгкими средствами, чтобы не тормозить карту.
 * trigger: каждый раз, когда число растёт, заставка проигрывается заново.
 */
@Composable
fun StartOverlay(trigger: Int) {
    val progress = remember { Animatable(0f) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        visible = true
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = DURATION_MS, easing = LinearEasing))
        visible = false
    }

    if (!visible) return

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val p = progress.value
            val scrim = scrimAlpha(p)
            drawRect(Color(0xFF0A0C10).copy(alpha = scrim))

            val strength = (scrim / SCRIM_MAX).coerceIn(0f, 1f)
            val lines = 16
            for (i in 0 until lines) {
                val seed = (i * 0.61803f) % 1f
                val y = size.height * (0.10f + 0.80f * seed)
                val length = size.width * (0.35f + 0.45f * ((i * 0.37f) % 1f))
                val speed = 1.4f + (i % 5) * 0.45f
                // полосы летят справа налево, чем ближе к концу, тем быстрее уходят
                val x0 = size.width * 1.1f - p * speed * size.width * 1.4f
                drawLine(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            ShColors.Accent.copy(alpha = 0.95f * strength),
                            Color.Transparent
                        ),
                        startX = x0,
                        endX = x0 + length
                    ),
                    start = Offset(x0, y),
                    end = Offset(x0 + length, y),
                    strokeWidth = (2 + (i % 3)) * density
                )
            }
        }

        Text(text = stringResource(ru.shturman.R.string.s_5bc7124d),
            color = Color.White,
            fontFamily = NumberFont,
            fontWeight = FontWeight.Bold,
            fontStyle = FontStyle.Italic,
            fontSize = 46.sp,
            letterSpacing = 4.sp,
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer {
                    val p = progress.value
                    val grow = (p / 0.3f).coerceIn(0f, 1f)
                    val scale = 0.6f + 0.5f * (1f - (1f - grow) * (1f - grow))
                    scaleX = scale
                    scaleY = scale
                    alpha = (scrimAlpha(p) / SCRIM_MAX).coerceIn(0f, 1f)
                }
        )
    }
}

/** Затемнение: быстро нарастает, держится и плавно уходит. */
private fun scrimAlpha(p: Float): Float = when {
    p < 0.12f -> p / 0.12f * SCRIM_MAX
    p < 0.62f -> SCRIM_MAX
    else -> SCRIM_MAX * (1f - (p - 0.62f) / 0.38f)
}
