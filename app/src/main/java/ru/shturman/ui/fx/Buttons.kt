package ru.shturman.ui.fx

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.shturman.ui.theme.ShColors
import ru.shturman.ui.themes.Flavor

// Кнопки в стиле «Капсула» (выбор владельца, вариант 3): ровное стекло без текстур и бликов,
// тема видна по форме и цвету. Стекло — круг, неон — скруглённый квадрат, телеметрия — срезанные углы.

/** Отдельная кнопка на карте. */
fun mapButtonShape(): Shape = when (ShColors.L.flavor) {
    Flavor.PREMIUM -> RoundedCornerShape(10.dp)
    Flavor.TELEMETRY -> CutCornerShape(topEnd = 12.dp, bottomStart = 12.dp)
    Flavor.NEON -> RoundedCornerShape(17.dp)
    Flavor.APEX -> androidx.compose.ui.graphics.RectangleShape
    else -> CircleShape
}

/** Вертикальная капсула с несколькими кнопками. */
fun capsuleShape(): Shape = when (ShColors.L.flavor) {
    Flavor.PREMIUM -> RoundedCornerShape(10.dp)
    Flavor.TELEMETRY -> CutCornerShape(topEnd = 14.dp, bottomStart = 14.dp)
    Flavor.NEON -> RoundedCornerShape(18.dp)
    Flavor.APEX -> androidx.compose.ui.graphics.RectangleShape
    else -> RoundedCornerShape(50)
}

/** Строка поиска «Куда едем?». */
fun pillShape(): Shape = when (ShColors.L.flavor) {
    Flavor.PREMIUM -> RoundedCornerShape(14.dp)
    Flavor.TELEMETRY -> CutCornerShape(topEnd = 16.dp, bottomStart = 16.dp)
    Flavor.NEON -> RoundedCornerShape(17.dp)
    Flavor.APEX -> androidx.compose.ui.graphics.RectangleShape
    else -> RoundedCornerShape(50)
}

/** Маленькая яркая кнопка внутри строки (например, «Где я»). */
fun innerButtonShape(): Shape = when (ShColors.L.flavor) {
    Flavor.PREMIUM -> RoundedCornerShape(10.dp)
    Flavor.TELEMETRY -> CutCornerShape(topEnd = 9.dp, bottomStart = 9.dp)
    Flavor.NEON -> RoundedCornerShape(13.dp)
    Flavor.APEX -> androidx.compose.ui.graphics.RectangleShape
    else -> CircleShape
}

/** Плашки выбора (время выезда и т. п.). */
fun choiceChipShape(): Shape = when (ShColors.L.flavor) {
    Flavor.PREMIUM -> RoundedCornerShape(8.dp)
    Flavor.TELEMETRY -> RoundedCornerShape(2.dp)
    Flavor.NEON -> RoundedCornerShape(11.dp)
    Flavor.APEX -> androidx.compose.ui.graphics.RectangleShape
    else -> RoundedCornerShape(50)
}

/** Круглая кнопка-крестик в заголовках панелей. */
fun closeButtonShape(): Shape = when (ShColors.L.flavor) {
    Flavor.PREMIUM -> RoundedCornerShape(10.dp)
    Flavor.TELEMETRY -> RoundedCornerShape(2.dp)
    Flavor.APEX -> androidx.compose.ui.graphics.RectangleShape
    else -> CircleShape
}

/** Цвет иконок на стеклянных кнопках. */
val ButtonIcon: Color get() = ShColors.TextPrimary

/** Тонкий разделитель внутри капсулы. */
val CapsuleDivider: Color get() =
    when (ShColors.L.flavor) {
        Flavor.NEON -> ShColors.Accent.copy(alpha = 0.30f)
        Flavor.APEX -> ApexChalk.copy(alpha = 0.08f)
        Flavor.PREMIUM -> Color.White.copy(alpha = 0.08f)
        else -> Color.White.copy(alpha = 0.12f)
    }

/**
 * Стеклянная кнопка: ровная заливка цветом панели сверху чуть светлее, тонкая кромка и мягкая тень.
 * Неон — кромка цветом акцента и цветное свечение; телеметрия — короткая полоска акцента сверху слева;
 * глубина — объёмная заливка и тень глубже. Никаких текстур, бегущих бликов и боковых полос.
 */
fun Modifier.glassButton(shape: Shape, elevation: Dp = 10.dp): Modifier = composed {
    val flavor = ShColors.L.flavor
    val panel = ShColors.Panel
    val acc = ShColors.Accent
    if (flavor == Flavor.PREMIUM) return@composed this.premiumGlass(shape, elevation)
    if (flavor == Flavor.APEX) {
        // APEX: плоский асфальт, волосяная рамка и метки по углам
        return@composed this
            .shadow(elevation * 0.6f, shape, clip = false, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(Color(0xFF0B0B0A).copy(alpha = 0.92f))
            .border(1.dp, ApexChalk.copy(alpha = 0.08f), shape)
            .drawBehind { apexTicks(len = 8.dp.toPx()) }
    }
    val fill = if (flavor == Flavor.DEPTH) {
        Brush.verticalGradient(
            listOf(lerp(panel, Color.White, 0.28f).copy(alpha = 0.95f), panel.copy(alpha = 0.93f), lerp(panel, Color.Black, 0.22f).copy(alpha = 0.93f))
        )
    } else {
        Brush.verticalGradient(listOf(lerp(panel, Color.White, 0.17f).copy(alpha = 0.93f), panel.copy(alpha = 0.91f)))
    }
    val shadowColor = if (flavor == Flavor.NEON) acc else Color.Black
    var m = this
        .shadow(if (flavor == Flavor.DEPTH) elevation * 1.5f else elevation, shape, clip = false, ambientColor = shadowColor, spotColor = shadowColor)
        .clip(shape)
        .background(fill)
        .border(1.dp, if (flavor == Flavor.NEON) acc.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.13f), shape)
    if (flavor == Flavor.TELEMETRY) {
        m = m.drawBehind { drawRect(acc, Offset(9.dp.toPx(), 0f), Size(18.dp.toPx(), 3.dp.toPx())) }
    }
    m
}

/** Яркая кнопка: градиент двух цветов темы и цветная тень (поиск, «Где я», «Поехали»). */
fun Modifier.accentButton(shape: Shape, elevation: Dp = 10.dp, enabled: Boolean = true): Modifier = composed {
    val glow = ShColors.L.glow
    val flavor = ShColors.L.flavor
    if (flavor == Flavor.PREMIUM) {
        // новое оформление: ровная заливка акцентом, без градиента и цветного свечения
        return@composed this
            .shadow(if (enabled) (elevation * 0.4f) else 0.dp, shape, clip = false, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(if (enabled) ShColors.Accent else ShColors.Divider)
    }
    this
        .shadow(if (enabled) elevation else 0.dp, shape, clip = false, ambientColor = if (flavor == Flavor.TELEMETRY) Color.Black else glow, spotColor = if (flavor == Flavor.TELEMETRY) Color.Black else glow)
        .clip(shape)
        .background(if (enabled) accentBrush() else Brush.linearGradient(listOf(ShColors.Divider, ShColors.SearchField)))
        .then(if (enabled && flavor == Flavor.APEX) Modifier.headlights() else Modifier)
}

/**
 * APEX: по главной кнопке раз в 4 секунды проходит полоса света — как фары встречной машины по стене гаража.
 */
private fun Modifier.headlights(): Modifier = composed {
    // Полоса бежит 1,2 с, потом 2,8 с покоя: в покое кнопка не перерисовывается
    val k0 = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            k0.snapTo(0f)
            k0.animateTo(1f, tween(1200, easing = LinearEasing))
            delay(2800L)
        }
    }
    this.drawWithContent {
        drawContent()
        val k = k0.value
        if (k <= 0f || k >= 1f) return@drawWithContent
        val w = size.width
        val x = -w * 0.4f + k * w * 1.8f
        drawRect(
            Brush.linearGradient(
                listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent),
                Offset(x - w * 0.18f, 0f), Offset(x + w * 0.18f, size.height)
            )
        )
    }
}
