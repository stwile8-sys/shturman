package ru.shturman.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.shturman.routing.NextTurn
import ru.shturman.routing.PaceNote
import ru.shturman.ui.fx.accentButton
import ru.shturman.ui.fx.changeFx
import ru.shturman.ui.fx.glassButton
import ru.shturman.ui.fx.mapButtonShape
import ru.shturman.ui.fx.panel
import ru.shturman.ui.fx.pressFx
import ru.shturman.ui.theme.ShColors
import ru.shturman.ui.theme.ShText
import ru.shturman.ui.themes.ThemeSound
import ru.shturman.ui.themes.ThemeSounds
import ru.shturman.ui.themes.themedEnter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

// Элементы нового оформления «Спорт-премиум» (макеты: docs/design, холст «Спорт-премиум: все экраны»).

/** Сторона стеклянных кнопок на карте. */
val SportButtonSize = 52.dp

/** Красный знака ограничения: у всех тем одинаковый, как на дорожном знаке. */
private val LimitRed = Color(0xFFE0313A)

/** «320» + «м» или «1,2» + «км» — для крупных чисел, где подпись мельче. */
fun splitDistance(m: Double): Pair<String, String> =
    if (m < 1000) "${((m / 10).roundToInt() * 10).coerceAtLeast(10)}" to "м"
    else String.format(Locale.US, "%.1f", m / 1000).replace('.', ',') to "км"

/** Цвет кружка с резкостью изгиба: 1–2 — красный, 3 — янтарный, плавные — серый. */
fun severityColor(severity: Int): Color = when {
    severity <= 2 -> ShColors.Danger
    severity == 3 -> ShColors.Warning
    else -> ShColors.Muted
}

/** Квадратная стеклянная кнопка на карте: 52 dp, контурный значок 20 dp. */
@Composable
fun GlassButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = ShColors.TextPrimary,
    index: Int = 0
) {
    val source = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .themedEnter(index)
            .size(SportButtonSize)
            .pressFx()
            .glassButton(mapButtonShape(), elevation = 8.dp)
            .clickable(interactionSource = source, indication = null) {
                ThemeSounds.play(ThemeSound.TAP)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/**
 * Панель манёвра сверху экрана поездки: знак (изгиб с резкостью, кольцо с номером съезда или «прямо»),
 * расстояние крупно, полосы справа, улица капсом и тонкая линия до поворота (полная за 600 м).
 */
@Composable
fun ManeuverPanel(turn: NextTurn?, notes: List<PaceNote>, modifier: Modifier = Modifier, compact: Boolean = false, framed: Boolean = true) {
    val first = notes.firstOrNull()
    // Кольцо показываем, когда оно ближайшее (или мы уже на нём)
    val ring = turn?.takeIf {
        it.isRoundabout && it.distanceM <= 1500.0 && (it.onRoundabout || first == null || it.distanceM <= first.distanceM + 80.0)
    }
    val mainDist = ring?.distanceM ?: first?.distanceM
    val street = when {
        ring != null -> "${ring.roundaboutExit}-й съезд" + (ring.street.takeIf { it.isNotEmpty() }?.let { " · $it" } ?: "")
        turn != null && (first == null || abs(turn.distanceM - first.distanceM) < 80.0) -> turn.street
        else -> ""
    }
    val lanes = turn?.takeIf { it.lanes.size > 1 && !it.onRoundabout && it.distanceM <= 800.0 }?.lanes
    val glyph = if (compact) 46.dp else 50.dp
    Column(
        if (framed) modifier
            .themedEnter()
            .panel(RoundedCornerShape(14.dp), elevation = 8.dp)
            .padding(start = 14.dp, end = 16.dp, top = 14.dp, bottom = 16.dp)
        else modifier
    ) {
        val key: Any? = ring?.let { "ring" to it.roundaboutExit } ?: first?.let { it.severity to it.isLeft }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.changeFx(key)) {
            Box(Modifier.size(glyph)) {
                when {
                    ring != null -> RoundaboutSign(ring.roundaboutExit, ring.exitAngle, glyph, ShColors.TextPrimary)
                    first == null -> RallyArrow(severity = null, isLeft = false, boxSize = glyph, color = ShColors.TextPrimary, stroke = 0.09f)
                    else -> RallyArrow(severity = first.severity, isLeft = first.isLeft, boxSize = glyph, color = ShColors.TextPrimary, stroke = 0.09f)
                }
                if (ring == null && first != null) SeverityDot(first.severity, Modifier.align(Alignment.BottomEnd))
            }
            Spacer(Modifier.width(14.dp))
            if (mainDist == null) {
                Text("Прямо", color = ShColors.TextPrimary, style = ShText.Hero.copy(fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = 0.sp))
            } else {
                val (num, unit) = splitDistance(mainDist)
                Text(
                    buildAnnotatedString {
                        append(num)
                        withStyle(SpanStyle(fontSize = 20.sp, fontWeight = FontWeight(300), letterSpacing = 0.sp)) { append(" $unit") }
                    },
                    color = ShColors.TextPrimary,
                    style = ShText.Hero.copy(fontSize = if (compact) 48.sp else 54.sp),
                    maxLines = 1
                )
            }
            Spacer(Modifier.weight(1f))
            if (lanes != null) {
                LaneStrip(lanes, Modifier.align(Alignment.Bottom).padding(start = 8.dp, bottom = 2.dp), laneWidth = 18.dp, laneHeight = 26.dp)
            }
        }
        if (street.isNotEmpty()) {
            Text(
                street.uppercase(),
                color = ShColors.TextSecondary,
                style = ShText.Caps.copy(fontSize = 12.sp, letterSpacing = 3.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        val frac = if (mainDist == null) 0f else (mainDist / 600.0).toFloat().coerceIn(0f, 1f)
        Box(Modifier.padding(top = 12.dp).fillMaxWidth().height(2.dp).background(ShColors.Divider)) {
            Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(ShColors.Accent))
        }
    }
}

/** Кружок с цифрой резкости изгиба в углу знака. */
@Composable
private fun SeverityDot(severity: Int, modifier: Modifier) {
    Box(
        modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(ShColors.Background)
            .border(1.5.dp, severityColor(severity), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(severity.toString(), color = ShColors.TextPrimary, fontSize = 10.sp, lineHeight = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** «ЗАТЕМ» и следующие изгибы с расстоянием между ними. */
@Composable
fun ThenPill(notes: List<PaceNote>, modifier: Modifier = Modifier, framed: Boolean = true) {
    if (notes.size < 2) return
    Row(
        if (framed) modifier
            .themedEnter(1)
            .panel(RoundedCornerShape(10.dp), elevation = 6.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp)
        else modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("ЗАТЕМ", color = ShColors.TextSecondary, style = ShText.Caps)
        for (j in 1 until minOf(notes.size, 3)) {
            val n = notes[j]
            RallyArrow(severity = n.severity, isLeft = n.isLeft, boxSize = 16.dp, color = ShColors.TextPrimary, stroke = 0.12f)
            Text(
                formatDistance((n.distanceM - notes[j - 1].distanceM).coerceAtLeast(0.0)),
                color = ShColors.TextPrimary, fontSize = 14.sp
            )
        }
    }
}

/** Совет сбавить скорость перед крутым изгибом: янтарный круг с числом. */
@Composable
fun AdvicePill(kmh: Int, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier
            .themedEnter(1)
            .panel(shape, elevation = 6.dp)
            .border(1.dp, ShColors.WarningEdge, shape)
            .padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.size(30.dp).border(2.dp, ShColors.WarningEdge, CircleShape), contentAlignment = Alignment.Center) {
            Text(kmh.toString(), color = ShColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Text("СБАВЬТЕ ДО $kmh", color = ShColors.Warning, style = ShText.Caps.copy(fontSize = 11.sp, letterSpacing = 1.5.sp))
    }
}

/** Сообщение на карте: янтарное (предупреждение) или спокойное серое. Короткие — капсом. */
@Composable
fun WarnChip(text: String, warn: Boolean, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(10.dp)
    val caps = text.length <= 36 && !text.contains('\n')
    Box(
        modifier
            .themedEnter(2)
            .then(if (onClick != null) Modifier.pressFx() else Modifier)
            .panel(shape, elevation = 6.dp)
            .border(1.dp, if (warn) ShColors.WarningEdge else ShColors.Divider, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            if (caps) text.uppercase() else text,
            color = if (warn) ShColors.Warning else ShColors.TextSecondary,
            style = if (caps) ShText.Caps.copy(fontSize = 11.sp, letterSpacing = 1.sp) else ShText.Secondary.copy(fontSize = 13.sp, lineHeight = 17.sp),
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Кольцо скорости: тонкая шкала, дуга от 135° (полная — 160 км/ч), число крупно и тонко, снизу — знак ограничения.
 * Превышение больше допустимого: число и дуга красные.
 */
@Composable
fun SpeedRing(kmh: Int, limit: Int?, over: Boolean, modifier: Modifier = Modifier, size: Dp = 150.dp) {
    val k = size.value / 150f
    val arc = if (over) ShColors.Danger else ShColors.Accent
    val frac = (kmh / 160f).coerceIn(0f, 1f)
    val track = ShColors.Divider
    val fill = ShColors.Background.copy(alpha = 0.55f)
    Box(modifier.width(size).height(size + 18.dp * k)) {
        Canvas(Modifier.size(size)) {
            val r = this.size.minDimension / 2f - 3.dp.toPx() * k
            val c = center
            drawCircle(fill, r, c)
            drawCircle(track, r, c, style = Stroke(2.dp.toPx() * k))
            if (frac > 0f) {
                drawArc(
                    arc, 135f, 270f * frac, false, Offset(c.x - r, c.y - r), Size(2 * r, 2 * r),
                    style = Stroke(3.dp.toPx() * k, cap = StrokeCap.Round)
                )
            }
        }
        Column(Modifier.size(size), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(
                kmh.toString(),
                color = if (over) ShColors.Danger else ShColors.TextPrimary,
                style = ShText.Hero.copy(fontSize = (56 * k).sp, lineHeight = (60 * k).sp, letterSpacing = 0.sp)
            )
            Text("КМ/Ч", color = ShColors.TextSecondary, style = ShText.Caps.copy(fontSize = (10 * k).sp, letterSpacing = (3 * k).sp))
        }
        if (limit != null) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .size(36.dp * k)
                    .clip(CircleShape)
                    .background(ShColors.Background)
                    .border(2.dp * k, LimitRed, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(limit.toString(), color = ShColors.TextPrimary, fontSize = (if (limit >= 100) 11 * k else 12 * k).sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Значение и подпись капсом под ним: «23:47 / ПРИБЫТИЕ». */
@Composable
fun TripValue(value: String, label: String, modifier: Modifier = Modifier, alignEnd: Boolean = false) {
    Column(modifier, horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(value, color = ShColors.TextPrimary, style = ShText.Value)
        Text(label, color = ShColors.TextSecondary, style = ShText.Caps.copy(letterSpacing = 3.sp))
    }
}

/** Скорость на карте без маршрута: стеклянный круг 76 dp. */
@Composable
fun SpeedBubble(kmh: Int, modifier: Modifier = Modifier) {
    Column(
        modifier.themedEnter().size(76.dp).panel(CircleShape, elevation = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(kmh.toString(), color = ShColors.TextPrimary, style = ShText.Hero.copy(fontSize = 28.sp, lineHeight = 30.sp, letterSpacing = 0.sp))
        Text("КМ/Ч", color = ShColors.TextSecondary, style = ShText.Caps.copy(fontSize = 8.sp))
    }
}

/** Карточка «Дом» / «Работа» на карте. Долгое нажатие — задать адрес заново. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaceCard(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    index: Int = 0
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .themedEnter(index)
            .height(76.dp)
            .pressFx()
            .panel(shape, elevation = 8.dp)
            .combinedClickable(onClick = { ThemeSounds.play(ThemeSound.TAP); onClick() }, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = ShColors.TextPrimary, modifier = Modifier.size(16.dp))
            Text(label, color = ShColors.TextSecondary, style = ShText.Caps)
        }
        Text(value, color = ShColors.TextPrimary, style = ShText.Body.copy(fontWeight = FontWeight(300), fontSize = 16.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Строка «Куда едем?» внизу карты; справа — кнопка голосового поиска. */
@Composable
fun SearchBar(onSearch: () -> Unit, onMic: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    val src = remember { MutableInteractionSource() }
    Row(
        modifier
            .themedEnter(3)
            .fillMaxWidth()
            .height(64.dp)
            .panel(shape, elevation = 10.dp)
            .clickable(interactionSource = src, indication = null) { ThemeSounds.play(ThemeSound.TAP); onSearch() }
            .padding(start = 18.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(ApexIcons.Search, contentDescription = null, tint = ShColors.TextPrimary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text("Куда едем?", color = ShColors.TextSecondary, fontSize = 17.sp, modifier = Modifier.weight(1f))
        Box(
            Modifier
                .size(48.dp)
                .pressFx()
                .accentButton(RoundedCornerShape(10.dp), elevation = 0.dp)
                .clickable { ThemeSounds.play(ThemeSound.TAP); onMic() },
            contentAlignment = Alignment.Center
        ) {
            Icon(ApexIcons.Mic, contentDescription = "Голосовой поиск", tint = ShColors.OnAccent, modifier = Modifier.size(20.dp))
        }
    }
}

/** Погода в правом верхнем углу карты: «−2° ЯСНО». */
@Composable
fun WeatherChip(tempC: Int, label: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .themedEnter(1)
            .height(48.dp)
            .panel(RoundedCornerShape(10.dp), elevation = 6.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text((if (tempC < 0) "−" else "") + "${abs(tempC)}°", color = ShColors.TextPrimary, style = ShText.Value.copy(fontSize = 18.sp, lineHeight = 22.sp))
        Text(label.uppercase(), color = ShColors.TextSecondary, style = ShText.Caps, maxLines = 1)
    }
}
