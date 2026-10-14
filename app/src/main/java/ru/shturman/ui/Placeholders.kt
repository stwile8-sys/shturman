package ru.shturman.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import ru.shturman.ui.fx.panel
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.shturman.ui.theme.ShColors
import ru.shturman.ui.theme.ShText
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Какую картинку рисовать в заглушке. */
enum class EmptyKind { TRIPS, FUEL, CARAVAN, SEARCH, ERROR, OFFLINE, SERVICE }

/**
 * Заглушка «пока пусто» или «что-то пошло не так»: маленькая рисованная картинка в цветах темы,
 * заголовок и подсказка, что сделать. Картинка медленно «дышит», чтобы экран не выглядел зависшим.
 */
@Composable
fun EmptyState(kind: EmptyKind, title: String, hint: String? = null, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    val t = rememberInfiniteTransition(label = "empty")
    val breath by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Reverse), label = "b")
    val accent = ShColors.Accent
    val dim = ShColors.TextSecondary
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(Modifier.size(84.dp)) { drawEmpty(kind, breath, accent, dim) }
        Spacer(Modifier.height(10.dp))
        Text(text = title, color = ShColors.TextPrimary, style = ShText.Small, textAlign = TextAlign.Center)
        if (hint != null) {
            Spacer(Modifier.height(4.dp))
            Text(text = hint, color = ShColors.TextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 12.dp))
        }
        if (action != null && onAction != null) {
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onAction,
                modifier = Modifier.height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ShColors.Accent, contentColor = ShColors.Background)
            ) { Text(action, style = ShText.Button) }
        }
    }
}

private fun DrawScope.drawEmpty(kind: EmptyKind, b: Float, accent: Color, dim: Color) {
    val w = size.width
    val h = size.height
    val c = Offset(w / 2, h / 2)
    val stroke = w * 0.045f
    // мягкое свечение под картинкой
    drawCircle(
        Brush.radialGradient(listOf(accent.copy(alpha = 0.10f + 0.08f * b), Color.Transparent), center = c, radius = w * 0.5f),
        radius = w * 0.5f, center = c
    )
    when (kind) {
        EmptyKind.TRIPS -> {
            // дорога уходит вдаль, на горизонте флажок
            val road = Path().apply {
                moveTo(w * 0.22f, h * 0.92f); lineTo(w * 0.44f, h * 0.36f); lineTo(w * 0.56f, h * 0.36f); lineTo(w * 0.78f, h * 0.92f); close()
            }
            drawPath(road, dim.copy(alpha = 0.25f))
            drawLine(
                accent, Offset(w * 0.5f, h * 0.9f), Offset(w * 0.5f, h * 0.4f), strokeWidth = stroke * 0.6f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(w * 0.06f, w * 0.05f), phase = -b * w * 0.11f)
            )
            drawLine(dim, Offset(w * 0.56f, h * 0.36f), Offset(w * 0.56f, h * 0.08f), strokeWidth = stroke * 0.5f, cap = StrokeCap.Round)
            val flag = Path().apply {
                val wave = (b - 0.5f) * h * 0.03f
                moveTo(w * 0.56f, h * 0.08f); lineTo(w * 0.78f, h * 0.13f + wave); lineTo(w * 0.56f, h * 0.2f); close()
            }
            drawPath(flag, accent)
        }
        EmptyKind.FUEL -> {
            // канистра и капля
            drawRoundRect(dim.copy(alpha = 0.3f), topLeft = Offset(w * 0.22f, h * 0.3f), size = Size(w * 0.42f, h * 0.56f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.06f))
            drawRoundRect(dim, topLeft = Offset(w * 0.22f, h * 0.3f), size = Size(w * 0.42f, h * 0.56f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.06f), style = Stroke(stroke * 0.6f))
            drawLine(dim, Offset(w * 0.3f, h * 0.42f), Offset(w * 0.56f, h * 0.74f), strokeWidth = stroke * 0.5f)
            drawLine(dim, Offset(w * 0.56f, h * 0.42f), Offset(w * 0.3f, h * 0.74f), strokeWidth = stroke * 0.5f)
            val dy = h * 0.06f * b
            val drop = Path().apply {
                moveTo(w * 0.78f, h * 0.14f + dy)
                cubicTo(w * 0.9f, h * 0.32f + dy, w * 0.86f, h * 0.44f + dy, w * 0.78f, h * 0.44f + dy)
                cubicTo(w * 0.7f, h * 0.44f + dy, w * 0.66f, h * 0.32f + dy, w * 0.78f, h * 0.14f + dy)
                close()
            }
            drawPath(drop, accent)
        }
        EmptyKind.CARAVAN -> {
            // три машины-точки, связанные пунктиром; первая ведёт
            val pts = listOf(Offset(w * 0.2f, h * 0.7f), Offset(w * 0.5f, h * 0.5f), Offset(w * 0.8f, h * 0.3f))
            for (i in 0 until 2) {
                drawLine(dim, pts[i], pts[i + 1], strokeWidth = stroke * 0.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(w * 0.04f, w * 0.04f), phase = -b * w * 0.08f))
            }
            pts.forEachIndexed { i, p ->
                val lead = i == 2
                drawCircle(if (lead) accent else dim.copy(alpha = 0.5f + 0.3f * b), radius = w * (if (lead) 0.09f else 0.07f), center = p)
                if (lead) drawCircle(accent.copy(alpha = 0.25f * (1 - b)), radius = w * (0.12f + 0.08f * b), center = p)
            }
        }
        EmptyKind.SEARCH -> {
            val r = w * 0.22f
            val cc = Offset(w * 0.44f, h * 0.44f)
            drawCircle(dim, radius = r, center = cc, style = Stroke(stroke))
            drawLine(dim, Offset(cc.x + r * 0.7f, cc.y + r * 0.7f), Offset(w * 0.82f, h * 0.82f), strokeWidth = stroke * 1.4f, cap = StrokeCap.Round)
            val a = b * 2 * PI.toFloat()
            drawCircle(accent, radius = w * 0.04f, center = Offset(cc.x + cos(a) * r * 0.45f, cc.y + sin(a) * r * 0.45f))
        }
        EmptyKind.ERROR, EmptyKind.OFFLINE -> {
            val tri = Path().apply {
                moveTo(w * 0.5f, h * 0.14f); lineTo(w * 0.88f, h * 0.82f); lineTo(w * 0.12f, h * 0.82f); close()
            }
            drawPath(tri, accent.copy(alpha = 0.18f + 0.1f * b))
            drawPath(tri, accent, style = Stroke(stroke * 0.7f))
            drawLine(accent, Offset(w * 0.5f, h * 0.38f), Offset(w * 0.5f, h * 0.6f), strokeWidth = stroke, cap = StrokeCap.Round)
            drawCircle(accent, radius = stroke * 0.7f, center = Offset(w * 0.5f, h * 0.71f))
            if (kind == EmptyKind.OFFLINE) {
                drawLine(dim, Offset(w * 0.1f, h * 0.1f), Offset(w * 0.9f, h * 0.9f), strokeWidth = stroke * 0.5f, cap = StrokeCap.Round)
            }
        }
        EmptyKind.SERVICE -> {
            // ключ, который покачивается
            val ang = (b - 0.5f) * 30f
            rotate(ang, pivot = c) {
                drawLine(dim, Offset(w * 0.3f, h * 0.7f), Offset(w * 0.62f, h * 0.38f), strokeWidth = stroke * 1.4f, cap = StrokeCap.Round)
                drawCircle(accent, radius = w * 0.13f, center = Offset(w * 0.68f, h * 0.32f), style = Stroke(stroke))
            }
        }
    }
}

/**
 * Заглушка карты, пока не загрузился её стиль: тёмная «сетка улиц», по которой бежит световая линия,
 * и пульсирующая точка в центре. Если карта не загрузилась, показываем ошибку и кнопку «Повторить».
 */
@Composable
fun MapPlaceholder(failed: Boolean, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "mapph")
    val run by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "run")
    val pulse by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "pulse")
    val bg = ShColors.Background
    val line = ShColors.Divider
    val accent = ShColors.Accent
    Box(modifier = modifier.fillMaxSize().background(bg), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val step = min(size.width, size.height) / 7f
            // «кварталы»: редкая сетка с парой диагональных проспектов
            var x = step * 0.5f
            while (x < size.width) { drawLine(line.copy(alpha = 0.5f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 2f); x += step }
            var y = step * 0.3f
            while (y < size.height) { drawLine(line.copy(alpha = 0.5f), Offset(0f, y), Offset(size.width, y), strokeWidth = 2f); y += step * 1.3f }
            drawLine(line, Offset(0f, size.height * 0.8f), Offset(size.width, size.height * 0.25f), strokeWidth = 6f)
            drawLine(line, Offset(size.width * 0.1f, 0f), Offset(size.width * 0.75f, size.height), strokeWidth = 4f)
            if (!failed) {
                // световая полоса бежит по проспекту, как будто карта прорисовывается
                val sx = size.width * run
                val sy = size.height * 0.8f - (size.height * 0.55f) * run
                drawCircle(
                    Brush.radialGradient(listOf(accent.copy(alpha = 0.55f), Color.Transparent), center = Offset(sx, sy), radius = step * 1.4f),
                    radius = step * 1.4f, center = Offset(sx, sy)
                )
                val cc = Offset(size.width / 2, size.height / 2)
                drawCircle(accent.copy(alpha = 0.35f * (1 - pulse)), radius = step * (0.25f + 0.9f * pulse), center = cc)
                drawCircle(accent, radius = step * 0.16f, center = cc)
            }
        }
        if (failed) {
            Box(
                Modifier.padding(24.dp).clip(RoundedCornerShape(20.dp)).background(ShColors.Panel.copy(alpha = 0.94f)).padding(horizontal = 12.dp)
            ) {
                EmptyState(
                    kind = EmptyKind.OFFLINE,
                    title = "Карта не загрузилась",
                    hint = "Проверьте интернет. Маршруты считаются на телефоне, но подложке карты нужна связь при первом запуске.",
                    action = "Повторить",
                    onAction = onRetry
                )
            }
        } else {
            Text(
                text = "Загружаю карту…",
                color = ShColors.TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.align(Alignment.Center).padding(top = 120.dp)
            )
        }
    }
}

/** Заглушка панели маршрутов, пока маршрут считается: три «карточки» с бегущим бликом. */
@Composable
fun RouteSkeleton(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "skel")
    val shine by t.animateFloat(-0.4f, 1.4f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "shine")
    val base = ShColors.SearchField
    val glow = ShColors.TextSecondary.copy(alpha = 0.18f)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 520.dp)
            .panel(ru.shturman.ui.fx.panelShape(34.dp), elevation = 18.dp)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Считаю варианты маршрута…", color = ShColors.TextSecondary, fontSize = 13.sp)
        repeat(3) { i ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Bar(shine, base, glow, Modifier.width(56.dp).height(36.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Bar(shine, base, glow, Modifier.fillMaxWidth(if (i == 0) 0.7f else 0.55f).height(14.dp))
                    Bar(shine, base, glow, Modifier.fillMaxWidth(0.4f).height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun Bar(shine: Float, base: Color, glow: Color, modifier: Modifier) {
    Canvas(modifier.clip(RoundedCornerShape(8.dp))) {
        drawRect(base)
        val x = size.width * shine
        drawRect(
            Brush.horizontalGradient(listOf(Color.Transparent, glow, Color.Transparent), startX = x - size.width * 0.3f, endX = x + size.width * 0.3f)
        )
    }
}

/** Небольшая подпись-заглушка внутри списков, когда грузится что-то короткое. */
@Composable
fun LoadingLine(text: String, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "dots")
    val a by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "a")
    Text(text = text, color = ShColors.TextSecondary, fontSize = 13.sp, modifier = modifier.alpha(a))
}
