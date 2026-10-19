package ru.shturman.ui.fx

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SweepGradientShader
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.shturman.ui.theme.ShColors
import ru.shturman.ui.themes.Flavor

// ---------- формы ----------

/** Форма больших панелей (карточки, шторки) с учётом характера темы. */
fun panelShape(radius: Dp = ShColors.T.panelRadius.dp.coerceAtLeast(18.dp)): Shape = when (ShColors.L.flavor) {
    Flavor.TELEMETRY -> CutCornerShape(topEnd = 16.dp, bottomStart = 16.dp)
    Flavor.NEON -> RoundedCornerShape(radius.coerceAtMost(18.dp))
    Flavor.GLASS -> RoundedCornerShape(radius.coerceAtLeast(26.dp))
    Flavor.DEPTH -> RoundedCornerShape(radius.coerceIn(20.dp, 24.dp))
    Flavor.APEX -> RectangleShape
    Flavor.PREMIUM -> RoundedCornerShape(14.dp)
}

/** Форма кнопок и небольших плашек. */
fun chipShape(): Shape = when (ShColors.L.flavor) {
    Flavor.TELEMETRY -> CutCornerShape(topEnd = 12.dp, bottomStart = 12.dp)
    Flavor.NEON -> RoundedCornerShape(14.dp)
    Flavor.GLASS -> RoundedCornerShape(18.dp)
    Flavor.DEPTH -> RoundedCornerShape(18.dp)
    Flavor.APEX -> RectangleShape
    Flavor.PREMIUM -> RoundedCornerShape(10.dp)
}

// ---------- стеклянная панель ----------

private fun glassTop(): Color = lerp(ShColors.Panel, Color.White, 0.10f).copy(alpha = 0.88f)
private fun glassBottom(): Color = lerp(ShColors.Panel, ShColors.Accent, 0.05f).copy(alpha = 0.82f)

/**
 * Стеклянная панель: полупрозрачная заливка с бликом, светлая кромка сверху, мягкая тень.
 * Поверх — особенности темы: неоновая бегущая обводка, полоса телеметрии или объёмная тень.
 * Реального размытия карты под панелью Android на всех машинах не умеет, поэтому стекло нарисовано.
 */
fun Modifier.panel(
    shape: Shape = panelShape(),
    elevation: Dp = 14.dp,
    glowing: Boolean = false,
    alpha: Float = 1f
): Modifier = composed {
    val l = ShColors.L
    val accent = ShColors.Accent
    if (l.flavor == Flavor.APEX) return@composed this.apexPanel(shape, elevation, glowing, alpha)
    if (l.flavor == Flavor.PREMIUM) return@composed this.premiumGlass(shape, elevation, alpha)
    val elev = if (l.flavor == Flavor.DEPTH) elevation * 1.6f else elevation
    var m = this.shadow(elev, shape, clip = false, ambientColor = Color.Black, spotColor = if (glowing) l.glow else Color.Black)
    if (l.flavor == Flavor.NEON) m = m.neonEdge(shape)
    m = m.clip(shape)
        .background(Brush.linearGradient(listOf(glassTop().copy(alpha = glassTop().alpha * alpha), glassBottom().copy(alpha = glassBottom().alpha * alpha)), Offset.Zero, Offset.Infinite))
        .drawBehind {
            // блик стекла по диагонали и светлая линия сверху
            drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.13f), Color.Transparent, Color.White.copy(alpha = 0.05f)), Offset.Zero, Offset(size.width, size.height)))
            drawLine(
                Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.40f), Color.Transparent)),
                Offset(0f, 1f), Offset(size.width, 1f), strokeWidth = 1.2f
            )
            when (l.flavor) {
                Flavor.TELEMETRY -> drawRect(accent, size = Size(4.dp.toPx(), size.height))
                Flavor.DEPTH -> drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.22f)), size.height * 0.6f, size.height))
                else -> Unit
            }
        }
    if (l.flavor != Flavor.NEON) {
        m = m.border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.06f))), shape)
    }
    m
}

/**
 * Панель APEX: плоский асфальт почти без прозрачности, волосяная рамка, метки по углам и строки развёртки.
 * Никакого стекла и бликов. У «горящей» панели (glowing) метки светятся мелом.
 */
private fun Modifier.apexPanel(shape: Shape, elevation: Dp, glowing: Boolean, alpha: Float): Modifier =
    this.shadow(elevation * 0.6f, shape, clip = false, ambientColor = Color.Black, spotColor = Color.Black)
        .clip(shape)
        .background(Color(0xFF0B0B0A).copy(alpha = 0.92f * alpha))
        .border(1.dp, ApexChalk.copy(alpha = 0.08f), shape)
        .drawWithContent {
            drawContent()
            apexScanlines(0.14f)
            apexTicks(if (glowing) ApexChalk.copy(alpha = 0.85f) else ApexTick)
        }

/**
 * Стекло «Спорт-премиум»: ровная тёмная заливка цветом панели темы (настоящего размытия карты Android
 * под панелью не умеет, поэтому стекло плотнее, чем на макете), тонкая светлая рамка и мягкая тень.
 */
fun Modifier.premiumGlass(shape: Shape, elevation: Dp = 8.dp, alpha: Float = 1f): Modifier =
    this.shadow((elevation * 0.5f).coerceAtMost(8.dp), shape, clip = false, ambientColor = Color.Black, spotColor = Color.Black)
        .clip(shape)
        .background(ShColors.T.panel.copy(alpha = (0.80f * alpha).coerceIn(0f, 1f)))
        .border(1.dp, ShColors.GlassEdge, shape)

/** Неоновая обводка, по которой бежит градиент из двух цветов темы. Снаружи — мягкое свечение. */
fun Modifier.neonEdge(shape: Shape, width: Dp = 1.5.dp): Modifier = composed {
    val tr = rememberInfiniteTransition(label = "neon")
    val angle by tr.animateFloat(0f, 360f, infiniteRepeatable(tween(3600, easing = LinearEasing)), label = "ang")
    val a = ShColors.Accent
    val b = ShColors.Accent2
    this.drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val path = Path().apply { addOutline(outline) }
        val c = Offset(size.width / 2, size.height / 2)
        val shader = SweepGradientShader(c, listOf(a, b, Color.Transparent, Color.Transparent, a, b, a))
        val brush = ShaderBrush(shader)
        val matrix = android.graphics.Matrix()
        val w = width.toPx()
        onDrawWithContent {
            drawContent()
            matrix.setRotate(angle, c.x, c.y)
            shader.setLocalMatrix(matrix)
            drawPath(path, a.copy(alpha = 0.18f), style = Stroke(w * 5f))
            drawPath(path, brush, style = Stroke(w * 2f))
        }
    }
}

// ---------- нажатие ----------

/**
 * Отклик на нажатие по характеру темы: стекло — пружинное сжатие; неон — короткий глитч;
 * телеметрия — резкий сдвиг вбок; глубина — наклон к пальцу в 3D. Касания не перехватывает.
 */
fun Modifier.pressFx(): Modifier = composed {
    val flavor = ShColors.L.flavor
    var pressed by remember { mutableStateOf(false) }
    var tiltX by remember { mutableStateOf(0f) }
    var tiltY by remember { mutableStateOf(0f) }
    val p by animateFloatAsState(
        if (pressed) 1f else 0f,
        spring(dampingRatio = if (flavor == Flavor.TELEMETRY || flavor == Flavor.APEX || flavor == Flavor.PREMIUM) 0.85f else 0.42f, stiffness = if (pressed) 900f else 420f),
        label = "press"
    )
    val glitch = remember { Animatable(0f) }
    LaunchedEffect(pressed) {
        if (pressed && (flavor == Flavor.NEON || flavor == Flavor.APEX)) {
            glitch.snapTo(1f); glitch.animateTo(0f, tween(220, easing = LinearEasing))
        }
    }
    this
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                pressed = true
                val w = size.width.toFloat().coerceAtLeast(1f)
                val h = size.height.toFloat().coerceAtLeast(1f)
                tiltY = ((down.position.x / w) - 0.5f) * 18f
                tiltX = -((down.position.y / h) - 0.5f) * 18f
                do {
                    val e = awaitPointerEvent(PointerEventPass.Initial)
                } while (e.changes.any { it.pressed })
                pressed = false
            }
        }
        .graphicsLayer {
            cameraDistance = 14f * density
            when (flavor) {
                Flavor.GLASS -> { val s = 1f - 0.11f * p; scaleX = s; scaleY = s }
                Flavor.NEON -> {
                    val s = 1f - 0.08f * p; scaleX = s; scaleY = s
                    val g = glitch.value
                    if (g > 0f) translationX = (if ((g * 6).toInt() % 2 == 0) 3f else -3f) * density * g
                }
                Flavor.TELEMETRY -> { translationX = 4f * density * p; val s = 1f - 0.05f * p; scaleX = s; scaleY = s }
                Flavor.DEPTH -> { rotationX = tiltX * p; rotationY = tiltY * p; val s = 1f - 0.05f * p; scaleX = s; scaleY = s; translationY = 2f * density * p }
                // новое оформление: лёгкое ровное сжатие, без наклонов и мигания
                Flavor.PREMIUM -> { val s = 1f - 0.04f * p; scaleX = s; scaleY = s }
                Flavor.APEX -> {
                    // механический щелчок: короткое сжатие и мигание, как реле
                    val s = 1f - 0.035f * p; scaleX = s; scaleY = s
                    val g = glitch.value
                    if (g > 0f) alpha = if ((g * 5).toInt() % 2 == 0) 0.55f else 1f
                }
            }
        }
}

/** Мягкое «дыхание» свечения: для главных кнопок. */
fun Modifier.breathingGlow(color: Color, radius: Dp = 18.dp): Modifier = composed {
    val tr = rememberInfiniteTransition(label = "breath")
    val k by tr.animateFloat(0.25f, 0.6f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "k")
    this.drawBehind {
        val r = size.minDimension / 2 + radius.toPx()
        drawCircle(Brush.radialGradient(listOf(color.copy(alpha = k * 0.6f), Color.Transparent), center, r), r, center)
    }
}
