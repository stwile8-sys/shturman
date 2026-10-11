package ru.shturman.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import ru.shturman.ui.theme.ShColors
import ru.shturman.ui.theme.ShText

/** Нижняя карточка: только сколько проехали относительно всего пути. */
@Composable
fun ProgressCard(
    modifier: Modifier = Modifier,
    progress: Float = 0.42f,
    traveledKm: String = "12,4",
    totalKm: String = "29,5",
    eta: String? = null,
    showPercent: Boolean = true,
    showDistance: Boolean = true,
    showEta: Boolean = true,
    remaining: String? = null,
    fuel: String? = null
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ShColors.Panel.copy(alpha = 0.92f))
            .border(1.dp, ShBorder, shape)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        val parts = ArrayList<String>()
        if (showDistance) parts += "$traveledKm из $totalKm км"
        if (showEta && eta != null) parts += "прибытие $eta"
        if (remaining != null) parts += remaining
        if (fuel != null) parts += fuel
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            if (showPercent) {
                Text(
                    text = "${(progress * 100).toInt()}%",
                    color = ShColors.TextPrimary,
                    style = ShText.BigNumber
                )
            }
            Spacer(Modifier.weight(1f))
            if (parts.isNotEmpty()) {
                Text(
                    text = parts.joinToString(" · "),
                    color = ShColors.TextSecondary,
                    style = ShText.Small,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    modifier = Modifier.weight(if (showPercent) 2f else 1f)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(ShColors.Divider)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .background(
                        Brush.horizontalGradient(listOf(ShColors.Accent, ShColors.Success))
                    )
            )
        }
    }
}
