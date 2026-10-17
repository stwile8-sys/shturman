package ru.shturman.ui.fx

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import ru.shturman.ui.theme.ShColors
import ru.shturman.ui.themes.Flavor

/**
 * Смена содержимого карточки (новый поворот, новая цифра): глубина — переворот по вертикальной оси,
 * телеметрия — подъезд сбоку, неон — мерцание, стекло — мягкий «вдох». Первый показ без анимации.
 */
@Composable
fun Modifier.changeFx(key: Any?): Modifier {
    val flavor = ShColors.L.flavor
    val r = remember { Animatable(0f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(key) {
        if (first) { first = false; return@LaunchedEffect }
        r.snapTo(1f)
        if (flavor == Flavor.NEON || flavor == Flavor.APEX) r.animateTo(0f, tween(if (flavor == Flavor.APEX) 300 else 260, easing = LinearEasing))
        else r.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 320f))
    }
    return this.graphicsLayer {
        val v = r.value
        if (v == 0f) return@graphicsLayer
        cameraDistance = 14f * density
        when (flavor) {
            Flavor.DEPTH -> { rotationY = 90f * v; alpha = 1f - 0.5f * v.coerceIn(0f, 1f) }
            Flavor.TELEMETRY -> { translationX = v * size.width * 0.35f; alpha = 1f - 0.8f * v.coerceIn(0f, 1f) }
            Flavor.NEON -> alpha = if (((v * 8).toInt() % 2) == 0) 0.35f else 1f
            Flavor.GLASS -> { val s = 1f - 0.08f * v; scaleX = s; scaleY = s; alpha = 1f - 0.4f * v.coerceIn(0f, 1f) }
            // APEX: новое показание загорается, как лампа на приборной панели
            Flavor.APEX -> alpha = apexIgnite(1f - v).coerceAtLeast(0.15f)
        }
    }
}

/** Появление «пружиной» при смене значения (знак ограничения скорости и т.п.). */
@Composable
fun Modifier.popOnChange(key: Any?): Modifier {
    val r = remember { Animatable(0f) }
    LaunchedEffect(key) {
        r.snapTo(0f)
        r.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 420f))
    }
    return this.graphicsLayer { val s = r.value; scaleX = s; scaleY = s; alpha = s.coerceIn(0f, 1f) }
}
