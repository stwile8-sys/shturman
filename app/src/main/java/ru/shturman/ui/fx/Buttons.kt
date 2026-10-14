package ru.shturman.ui.fx

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
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
    Flavor.TELEMETRY -> CutCornerShape(topEnd = 12.dp, bottomStart = 12.dp)
    Flavor.NEON -> RoundedCornerShape(17.dp)
    else -> CircleShape
}

/** Вертикальная капсула с несколькими кнопками. */
fun capsuleShape(): Shape = when (ShColors.L.flavor) {
    Flavor.TELEMETRY -> CutCornerShape(topEnd = 14.dp, bottomStart = 14.dp)
    Flavor.NEON -> RoundedCornerShape(18.dp)
    else -> RoundedCornerShape(50)
}

/** Строка поиска «Куда едем?». */
fun pillShape(): Shape = when (ShColors.L.flavor) {
    Flavor.TELEMETRY -> CutCornerShape(topEnd = 16.dp, bottomStart = 16.dp)
    Flavor.NEON -> RoundedCornerShape(17.dp)
    else -> RoundedCornerShape(50)
}

/** Маленькая яркая кнопка внутри строки (например, «Где я»). */
fun innerButtonShape(): Shape = when (ShColors.L.flavor) {
    Flavor.TELEMETRY -> CutCornerShape(topEnd = 9.dp, bottomStart = 9.dp)
    Flavor.NEON -> RoundedCornerShape(13.dp)
    else -> CircleShape
}

/** Плашки выбора (время выезда и т. п.). */
fun choiceChipShape(): Shape = when (ShColors.L.flavor) {
    Flavor.TELEMETRY -> RoundedCornerShape(2.dp)
    Flavor.NEON -> RoundedCornerShape(11.dp)
    else -> RoundedCornerShape(50)
}

/** Круглая кнопка-крестик в заголовках панелей. */
fun closeButtonShape(): Shape = if (ShColors.L.flavor == Flavor.TELEMETRY) RoundedCornerShape(2.dp) else CircleShape

/** Цвет иконок на стеклянных кнопках. */
val ButtonIcon: Color get() = ShColors.TextPrimary

/** Тонкий разделитель внутри капсулы. */
val CapsuleDivider: Color get() =
    if (ShColors.L.flavor == Flavor.NEON) ShColors.Accent.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.12f)

/**
 * Стеклянная кнопка: ровная заливка цветом панели сверху чуть светлее, тонкая кромка и мягкая тень.
 * Неон — кромка цветом акцента и цветное свечение; телеметрия — короткая полоска акцента сверху слева;
 * глубина — объёмная заливка и тень глубже. Никаких текстур, бегущих бликов и боковых полос.
 */
fun Modifier.glassButton(shape: Shape, elevation: Dp = 10.dp): Modifier = composed {
    val flavor = ShColors.L.flavor
    val panel = ShColors.Panel
    val acc = ShColors.Accent
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
    this
        .shadow(if (enabled) elevation else 0.dp, shape, clip = false, ambientColor = if (flavor == Flavor.TELEMETRY) Color.Black else glow, spotColor = if (flavor == Flavor.TELEMETRY) Color.Black else glow)
        .clip(shape)
        .background(if (enabled) accentBrush() else Brush.linearGradient(listOf(ShColors.Divider, ShColors.SearchField)))
}
