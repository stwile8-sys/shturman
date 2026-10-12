package ru.shturman.ui.themes

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.shturman.ui.theme.ShColors
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** Форма панелей выбранной темы. */
fun themePanelShape(): Shape {
    val t = ShColors.T
    return if (t.btn == BtnKind.CHAMFER) CutCornerShape(topStart = t.panelRadius.dp, bottomEnd = t.panelRadius.dp)
    else RoundedCornerShape(t.panelRadius.dp)
}

/** Форма кнопок выбранной темы. */
fun themeButtonShape(): Shape {
    val t = ShColors.T
    return when (t.btn) {
        BtnKind.CHAMFER -> CutCornerShape(topStart = t.btnRadius.dp, bottomEnd = t.btnRadius.dp)
        BtnKind.SLIM -> RoundedCornerShape(50)
        BtnKind.ROUND -> RoundedCornerShape(t.btnRadius.dp)
        else -> RoundedCornerShape(t.btnRadius.dp)
    }
}

/** Материал кнопки: полосы, карбон, бетон, зерно, пламя по краям, блеск, размытие. Рисуется поверх фона кнопки. */
fun Modifier.themedButtonTexture(): Modifier = drawBehind {
    val t = ShColors.T
    val w = size.width
    val h = size.height
    when (t.btn) {
        BtnKind.STRIPES -> {
            val bandH = h * 0.09f
            val y0 = h * 0.70f
            drawRect(Color.White, Offset(0f, y0), Size(w, bandH))
            drawRect(t.second, Offset(0f, y0 + bandH), Size(w, bandH))
            drawRect(t.third, Offset(0f, y0 + bandH * 2), Size(w, bandH))
        }
        BtnKind.CARBON -> {
            var x = -h
            while (x < w) {
                drawLine(Color.White.copy(alpha = 0.07f), Offset(x, h), Offset(x + h, 0f), strokeWidth = 3f)
                x += 7f
            }
            drawRect(t.accent, Offset(0f, 0f), Size(w * 0.09f, h))
        }
        BtnKind.CONCRETE -> {
            val r = Random(11)
            repeat(70) {
                val c = if (r.nextBoolean()) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.22f)
                drawCircle(c, radius = 1f + r.nextFloat() * 1.6f, center = Offset(r.nextFloat() * w, r.nextFloat() * h))
            }
            drawLine(Color.White.copy(alpha = 0.10f), Offset(w * 0.1f, h * 0.9f), Offset(w * 0.55f, h * 0.15f), strokeWidth = 1.5f)
            drawLine(Color.Black.copy(alpha = 0.3f), Offset(w * 0.6f, h * 0.95f), Offset(w * 0.95f, h * 0.4f), strokeWidth = 1.5f)
        }
        BtnKind.GRAIN -> {
            val r = Random(5)
            repeat(90) {
                drawCircle(Color(0xFFE3C79A).copy(alpha = 0.13f), radius = 0.8f + r.nextFloat() * 1.2f, center = Offset(r.nextFloat() * w, r.nextFloat() * h))
            }
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, t.accent.copy(alpha = 0.18f))))
        }
        BtnKind.SLIM -> {
            drawRect(Brush.horizontalGradient(listOf(t.accent.copy(alpha = 0.55f), Color.Transparent), 0f, w * 0.35f))
            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, t.accent.copy(alpha = 0.55f)), w * 0.65f, w))
        }
        BtnKind.ROUND -> {
            drawOval(Color.White.copy(alpha = 0.16f), Offset(w * 0.12f, h * 0.06f), Size(w * 0.5f, h * 0.28f))
        }
        BtnKind.SOFT -> {
            drawRect(Brush.radialGradient(listOf(t.accent.copy(alpha = 0.22f), Color.Transparent), Offset(w / 2, h / 2), w * 0.8f))
        }
        BtnKind.CHAMFER -> Unit
    }
}

private val BounceOut = Easing { x ->
    val n = 7.5625f
    val d = 2.75f
    when {
        x < 1f / d -> n * x * x
        x < 2f / d -> { val y = x - 1.5f / d; n * y * y + 0.75f }
        x < 2.5f / d -> { val y = x - 2.25f / d; n * y * y + 0.9375f }
        else -> { val y = x - 2.625f / d; n * y * y + 0.984375f }
    }
}

/** Появление панели: у каждой темы свой стиль и длительность. */
@Composable
fun Modifier.themedEnter(): Modifier {
    val t = ShColors.T
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        val easing: Easing = when (t.enter) {
            EnterKind.DROP -> BounceOut
            EnterKind.SIDE_FAST, EnterKind.SIDE_DUST -> LinearEasing
            else -> androidx.compose.animation.core.FastOutSlowInEasing
        }
        p.animateTo(1f, tween(t.enterMs, easing = easing))
    }
    val kind = t.enter
    return this.graphicsLayer {
        val v = p.value
        val q = 1f - v
        when (kind) {
            EnterKind.RISE -> { translationY = q * 20.dp.toPx(); alpha = v }
            EnterKind.FLOAT -> {
                translationY = q * 30.dp.toPx()
                rotationZ = sin(v * PI.toFloat() * 3f) * q * 3f
                scaleX = 0.9f + 0.1f * v; scaleY = 0.9f + 0.1f * v
                alpha = v
            }
            EnterKind.SIDE_FAST -> { translationX = -q * size.width * 0.7f; alpha = 0.3f + 0.7f * v }
            EnterKind.STICK_DOWN -> { transformOrigin = TransformOrigin(0.5f, 0f); scaleY = v.coerceAtLeast(0.02f); alpha = 0.3f + 0.7f * v }
            EnterKind.EMERGE -> { translationX = q * size.width * 0.4f; alpha = v; scaleX = 1f + q * 0.25f }
            EnterKind.DROP -> { translationY = -(1f - v) * 110.dp.toPx(); alpha = v.coerceIn(0.2f, 1f) }
            EnterKind.SIDE_DUST -> { translationX = q * size.width * 0.5f; alpha = 0.2f + 0.8f * v }
            EnterKind.FOG -> { alpha = v; scaleX = 1.05f - 0.05f * v; scaleY = 1.05f - 0.05f * v; translationY = q * 10.dp.toPx() }
        }
    }
}

/** Появление целого экрана (настройки, поиск): длительность перехода из темы. */
@Composable
fun Modifier.themedScreenEnter(): Modifier {
    val t = ShColors.T
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, tween(t.transMs, easing = androidx.compose.animation.core.FastOutSlowInEasing)) }
    val jerky = t.routeJerky
    return this.graphicsLayer {
        val v = if (jerky) (p.value * 4f).toInt() / 4f else p.value
        alpha = v
        translationY = (1f - v) * 24.dp.toPx()
    }
}

/** Текст с анимацией смены цифр: каждая тема по-своему. */
@Composable
fun ThemedText(
    text: String,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip
) {
    val kind = ShColors.T.digit
    val p = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(text) {
        if (first) { first = false; return@LaunchedEffect }
        p.snapTo(0f)
        p.animateTo(1f, tween(if (kind == DigitKind.GLITCH) 180 else 260, easing = LinearEasing))
    }
    Text(
        text = text,
        color = color,
        style = style,
        maxLines = maxLines,
        overflow = overflow,
        modifier = modifier.graphicsLayer {
            val v = p.value
            val q = 1f - v
            cameraDistance = 12f * density
            when (kind) {
                DigitKind.GLITCH -> {
                    if (v < 1f) {
                        val xs = floatArrayOf(7f, -6f, 3f)
                        translationX = xs[(v * 3f).toInt().coerceIn(0, 2)] * density
                        alpha = 0.75f
                    }
                }
                DigitKind.ROLL -> { translationY = q * 14f * density; rotationX = q * 50f; alpha = 0.3f + 0.7f * v }
                DigitKind.FLY -> { transformOrigin = TransformOrigin(1f, 0.5f); translationX = -q * 36f * density; scaleX = 1f + q * 0.35f; alpha = 0.4f + 0.6f * v }
                DigitKind.PRINT -> { transformOrigin = TransformOrigin(0.5f, 0f); scaleY = 0.15f + 0.85f * v; alpha = 0.5f + 0.5f * v }
                DigitKind.BLUR -> { scaleX = 1f + q * 0.6f; alpha = 0.35f + 0.65f * v }
                DigitKind.BOARD -> { rotationX = -q * 90f; alpha = v.coerceAtLeast(0.2f) }
                DigitKind.KNOCK -> {
                    translationY = sin(v * PI.toFloat() * 5f) * q * 5f * density
                    translationX = sin(v * PI.toFloat() * 7f) * q * 3f * density
                }
                DigitKind.HAZE -> { alpha = 0.3f + 0.7f * v; scaleX = 1f + q * 0.08f; scaleY = 1f + q * 0.08f }
            }
        }
    )
}
