package ru.shturman.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
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

/** Ближайший поворот: раллийный знак, расстояние и улица. Живёт в правом верхнем углу. */
@Composable
fun TurnCard(
    modifier: Modifier = Modifier,
    severity: Int = 3,
    isLeft: Boolean = true,
    distance: String = "320 м",
    street: String = "ул. Ленина"
) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier = modifier
            .widthIn(max = 250.dp)
            .clip(shape)
            .background(ShColors.Panel.copy(alpha = 0.92f))
            .border(1.dp, ShBorder, shape)
            .padding(start = 10.dp, end = 18.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RallySign(severity = severity, isLeft = isLeft)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(text = distance, color = ShColors.TextPrimary, style = ShText.Distance)
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
