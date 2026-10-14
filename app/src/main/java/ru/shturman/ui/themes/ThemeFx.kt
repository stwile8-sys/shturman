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

/** Форма панелей выбранной темы (стекло, неон, телеметрия или глубина). */
fun themePanelShape(): Shape = ru.shturman.ui.fx.panelShape()

/** Форма кнопок выбранной темы. */
fun themeButtonShape(): Shape = ru.shturman.ui.fx.chipShape()

/** Материал кнопки: полосы, карбон, бетон, зерно, пламя по краям, блеск, размытие. Рисуется поверх фона кнопки. */
fun Modifier.themedButtonTexture(): Modifier = drawBehind {
    val t = ShColors.T
    val w = size.width
    val h = size.height
    when (t.btn) {
        BtnKind.STRIPES -> {
            val bandH = h * 0.09f
            val y0 = h * 0.70f
            drawRect(Color.White.copy(alpha = 0.85f), Offset(0f, y0), Size(w, bandH))
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
                val c = if (r.nextBoolean()) Color.White.copy(alpha = 0.07f) else Color.Black.copy(alpha = 0.16f)
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
            drawRect(Brush.horizontalGradient(listOf(t.accent.copy(alpha = 0.35f), Color.Transparent), 0f, w * 0.35f))
            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, t.accent.copy(alpha = 0.35f)), w * 0.65f, w))
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

/**
 * Появление панели по характеру темы: стекло — мягкая пружина снизу; неон — «загрузка» рывками с глитчем;
 * телеметрия — резкий въезд сбоку; глубина — выезд из глубины с поворотом. index — задержка для каскада.
 */
@Composable
fun Modifier.themedEnter(index: Int = 0): Modifier {
    val flavor = ShColors.L.flavor
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(index * 70L)
        when (flavor) {
            Flavor.GLASS -> p.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.62f, stiffness = 220f))
            Flavor.NEON -> p.animateTo(1f, tween(420, easing = LinearEasing))
            Flavor.TELEMETRY -> p.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.72f, stiffness = 520f))
            Flavor.DEPTH -> p.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.58f, stiffness = 180f))
        }
    }
    val dust = ShColors.T.enter == EnterKind.SIDE_DUST
    return this.drawBehind {
        // Rally: за въезжающей панелью остаётся облако пыли
        if (dust) {
            val q = (1f - p.value).coerceIn(0f, 1f)
            if (q > 0.02f) {
                for (i in 0 until 6) {
                    val x = size.width * (1.02f + 0.14f * i) * (0.5f + 0.5f * q)
                    val y = size.height * (0.2f + 0.12f * i)
                    drawCircle(Color(0xFFC79A66).copy(alpha = 0.35f * q), size.height * (0.16f + 0.03f * i), Offset(x, y))
                }
            }
        }
    }.graphicsLayer {
        val v = p.value
        val q = 1f - v
        cameraDistance = 14f * density
        when (flavor) {
            Flavor.GLASS -> {
                alpha = v.coerceIn(0f, 1f)
                translationY = q * 28.dp.toPx()
                val sc = 0.94f + 0.06f * v; scaleX = sc; scaleY = sc
            }
            Flavor.NEON -> {
                val st = (v * 5f).toInt() / 5f
                alpha = if (v >= 1f) 1f else st.coerceAtLeast(0.15f)
                if (v < 1f) translationX = (if ((v * 10f).toInt() % 2 == 0) 6f else -4f) * density * q
            }
            Flavor.TELEMETRY -> {
                alpha = (v * 1.6f).coerceIn(0f, 1f)
                translationX = q * size.width * 0.55f
                rotationY = -q * 28f
            }
            Flavor.DEPTH -> {
                transformOrigin = TransformOrigin(0.5f, 1f)
                alpha = (v * 1.4f).coerceIn(0f, 1f)
                rotationX = q * 70f
                val sc = 0.9f + 0.1f * v; scaleX = sc; scaleY = sc
            }
        }
    }
}

/** Появление целого экрана (поиск, вход): глубина и пружина по характеру темы. */
@Composable
fun Modifier.themedScreenEnter(): Modifier {
    val flavor = ShColors.L.flavor
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        p.animateTo(1f, if (flavor == Flavor.NEON) tween(360, easing = LinearEasing) else androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 260f))
    }
    return this.graphicsLayer {
        val v = p.value
        cameraDistance = 14f * density
        alpha = v.coerceIn(0f, 1f)
        when (flavor) {
            Flavor.DEPTH -> { rotationX = (1f - v) * -18f; val sc = 0.92f + 0.08f * v; scaleX = sc; scaleY = sc }
            Flavor.TELEMETRY -> translationX = (1f - v) * size.width * 0.3f
            else -> { val sc = 0.94f + 0.06f * v; scaleX = sc; scaleY = sc; translationY = (1f - v) * 24.dp.toPx() }
        }
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
