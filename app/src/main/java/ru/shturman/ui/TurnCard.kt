package ru.shturman.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.shturman.ui.theme.ShColors
import ru.shturman.ui.theme.ShText
import ru.shturman.ui.themes.ThemedText
import ru.shturman.ui.themes.themedEnter
import ru.shturman.ui.fx.changeFx
import ru.shturman.ui.fx.panel
import androidx.compose.foundation.layout.Box

/** Ближайший поворот: раллийный знак, расстояние и улица. Живёт в правом верхнем углу. */
@Composable
fun TurnCard(
    modifier: Modifier = Modifier,
    severity: Int = 3,
    isLeft: Boolean = true,
    distance: String = "320 м",
    street: String = "ул. Ленина"
) {
    val shape = ru.shturman.ui.themes.themePanelShape()
    Row(
        modifier = modifier.themedEnter()
            .widthIn(max = 250.dp)
            .panel(shape, glowing = true)
            .padding(start = 10.dp, end = 18.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.changeFx(severity to isLeft)) { RallyPacenote(severity = severity, isLeft = isLeft) }
        Spacer(Modifier.width(12.dp))
        Column {
            ThemedText(text = distance, color = ShColors.TextPrimary, style = ShText.Distance)
            Text(
                text = street,
                color = ShColors.TextSecondary,
                style = ShText.Street,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Раллийный дорожник: ближайший изгиб дороги крупно и два следующих мельче, как записи пилота.
 * Если впереди прямо, показывается прямая стрелка. Кольцо — своим знаком с номером съезда.
 * Под знаком: полоска до поворота (последние 600 м), полосы перед манёвром (за 800 м, если размечены)
 * и совет сбавить скорость перед крутым изгибом.
 */
@Composable
fun PaceCard(
    notes: List<ru.shturman.routing.PaceNote>,
    street: String,
    modifier: Modifier = Modifier,
    turn: ru.shturman.routing.NextTurn? = null,
    adviceKmh: Int? = null
) {
    val shape = ru.shturman.ui.themes.themePanelShape()
    val first = notes.firstOrNull()
    // Кольцо показываем, когда оно ближайшее (или мы уже на нём)
    val ring = turn?.takeIf {
        it.isRoundabout && it.distanceM <= 1500.0 && (it.onRoundabout || first == null || it.distanceM <= first.distanceM + 80.0)
    }
    Column(
        modifier = modifier.themedEnter()
            .widthIn(max = 210.dp)
            .width(IntrinsicSize.Max)
            // компактнее и на 30% прозрачнее: дорожник не закрывает карту, но читается
            .panel(shape, elevation = 4.dp, glowing = true, alpha = 0.7f)
            .padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)
    ) {
        val key: Any? = ring?.let { "ring" to it.roundaboutExit } ?: first?.let { it.severity to it.isLeft }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.changeFx(key)) {
            when {
                ring != null -> RoundaboutSign(ring.roundaboutExit, ring.exitAngle, 44.dp, ShColors.Accent)
                first == null -> RallyArrow(severity = null, isLeft = false, boxSize = 44.dp)
                else -> RallyPacenote(severity = first.severity, isLeft = first.isLeft, boxSize = 44.dp)
            }
            Spacer(Modifier.width(8.dp))
            Column {
                ThemedText(
                    text = when {
                        ring != null -> formatDistance(ring.distanceM)
                        first == null -> "Прямо"
                        else -> formatDistance(first.distanceM)
                    },
                    color = ShColors.TextPrimary,
                    style = ShText.Distance.copy(fontSize = 20.sp)
                )
                val sub = if (ring != null) {
                    "${ring.roundaboutExit}-й съезд" + (ring.street.takeIf { it.isNotEmpty() }?.let { " · $it" } ?: "")
                } else street
                if (sub.isNotEmpty()) {
                    Text(
                        text = sub,
                        color = ShColors.TextSecondary,
                        style = ShText.Street.copy(fontSize = 13.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        // Полоска до поворота: за 600 м полная, к повороту укорачивается
        val mainDist = ring?.distanceM ?: first?.distanceM
        if (mainDist != null && mainDist <= 600.0) {
            TurnDistanceBar(
                distanceM = mainDist,
                color = if (ring != null || first == null) ShColors.Accent else signColor(first.severity),
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        // Полосы перед манёвром — где они размечены в данных
        if (turn != null && turn.lanes.size > 1 && !turn.onRoundabout && turn.distanceM <= 800.0) {
            LaneStrip(turn.lanes, Modifier.padding(top = 6.dp))
        }
        if (adviceKmh != null) {
            CurveAdvicePlate(adviceKmh, Modifier.padding(top = 6.dp))
        }
        if (ring == null && notes.size > 1) {
            Row(
                modifier = Modifier.padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                for (j in 1 until notes.size) {
                    val n = notes[j]
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        RallyPacenote(severity = n.severity, isLeft = n.isLeft, boxSize = 28.dp)
                        Text(
                            text = formatDistance((n.distanceM - notes[j - 1].distanceM).coerceAtLeast(0.0)),
                            color = ShColors.TextSecondary,
                            style = ShText.Street.copy(fontSize = 12.sp)
                        )
                    }
                }
            }
        }
    }
}
