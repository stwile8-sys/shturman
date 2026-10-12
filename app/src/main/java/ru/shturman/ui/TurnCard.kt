package ru.shturman.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
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
import ru.shturman.ui.theme.ShColors
import ru.shturman.ui.theme.ShText
import ru.shturman.ui.themes.ThemedText
import ru.shturman.ui.themes.themedEnter

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
            .clip(shape)
            .background(ShColors.Panel.copy(alpha = 0.92f))
            .border(ShColors.T.borderW.dp, ShBorder, shape)
            .padding(start = 10.dp, end = 18.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RallyPacenote(severity = severity, isLeft = isLeft)
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
 * Если впереди прямо, показывается прямая стрелка.
 */
@Composable
fun PaceCard(
    notes: List<ru.shturman.routing.PaceNote>,
    street: String,
    modifier: Modifier = Modifier
) {
    val shape = ru.shturman.ui.themes.themePanelShape()
    Column(
        modifier = modifier.themedEnter()
            .widthIn(max = 260.dp)
            .clip(shape)
            .background(ShColors.Panel.copy(alpha = 0.92f))
            .border(ShColors.T.borderW.dp, ShBorder, shape)
            .padding(start = 10.dp, end = 18.dp, top = 10.dp, bottom = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val first = notes.firstOrNull()
            if (first == null) {
                RallyArrow(severity = null, isLeft = false, boxSize = 60.dp)
            } else {
                RallyPacenote(severity = first.severity, isLeft = first.isLeft)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                ThemedText(
                    text = if (first == null) "Прямо" else formatDistance(first.distanceM),
                    color = ShColors.TextPrimary,
                    style = ShText.Distance
                )
                if (street.isNotEmpty()) {
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
        if (notes.size > 1) {
            Row(
                modifier = Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                for (j in 1 until notes.size) {
                    val n = notes[j]
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        RallyPacenote(severity = n.severity, isLeft = n.isLeft, boxSize = 38.dp)
                        Text(
                            text = formatDistance((n.distanceM - notes[j - 1].distanceM).coerceAtLeast(0.0)),
                            color = ShColors.TextSecondary,
                            style = ShText.Street
                        )
                    }
                }
            }
        }
    }
}
