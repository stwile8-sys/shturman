package ru.shturman.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.shturman.routing.RouteOption
import ru.shturman.ui.theme.ShColors
import ru.shturman.ui.theme.ShText

/** Панель выбора маршрута: варианты с временем, время выезда и кнопка «Поехали». */
@Composable
fun RoutePanel(
    options: List<RouteOption>,
    selected: Int,
    departOffsetMin: Int,
    timeAware: Boolean,
    destinationTitle: String,
    onSelect: (Int) -> Unit,
    onDepart: (Int) -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(24.dp)
    val departMillis = System.currentTimeMillis() + departOffsetMin * 60_000L

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ShColors.Panel.copy(alpha = 0.95f))
            .border(1.dp, ShBorder, shape)
            .padding(16.dp)
    ) {
        Text(
            text = destinationTitle,
            color = ShColors.TextPrimary,
            style = ShText.Small,
            maxLines = 1
        )
        Spacer(Modifier.height(10.dp))

        // Варианты маршрута
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEachIndexed { i, option ->
                val arrival = formatClock(departMillis + (option.timeSec * 1000).toLong())
                Chip(
                    tag = option.label.ifEmpty { null },
                    title = formatDuration(option.timeSec),
                    subtitle = "${formatDistance(option.totalM)} · $arrival",
                    selected = i == selected,
                    onClick = { onSelect(i) }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Время выезда
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(0 to "Сейчас", 15 to "+15 мин", 30 to "+30 мин", 60 to "+1 час", 120 to "+2 часа").forEach { (offset, label) ->
                Chip(
                    title = label,
                    subtitle = null,
                    selected = departOffsetMin == offset,
                    onClick = { onDepart(offset) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = if (timeAware) {
                "Маршрут рассчитан на выбранное время выезда. Реальные пробки появятся, когда накопятся поездки."
            } else {
                "Время выезда движок не учёл, маршрут построен по обычным скоростям."
            },
            color = ShColors.TextSecondary,
            fontSize = 12.sp
        )

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ShColors.Accent,
                contentColor = ShColors.Background
            )
        ) {
            Text(text = "Поехали", style = ShText.Button)
        }
    }
}

@Composable
private fun Chip(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit, tag: String? = null) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) ShColors.Accent.copy(alpha = 0.16f) else ShColors.SearchField)
            .border(1.dp, if (selected) ShColors.Accent else ShBorder, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        if (tag != null) {
            Text(text = tag, color = if (selected) ShColors.Accent else ShColors.TextSecondary, fontSize = 11.sp)
        }
        Text(
            text = title,
            color = if (selected) ShColors.Accent else ShColors.TextPrimary,
            style = ShText.Small
        )
        if (subtitle != null) {
            Text(text = subtitle, color = ShColors.TextSecondary, fontSize = 12.sp)
        }
    }
}
