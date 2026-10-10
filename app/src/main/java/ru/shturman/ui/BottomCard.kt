package ru.shturman.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import ru.shturman.ui.theme.NumberFont
import ru.shturman.ui.theme.ShColors
import ru.shturman.ui.theme.ShText

/**
 * Нижняя карточка с маршрутом. Все значения пока заглушки.
 * minutesAhead: положительное — приезжаем раньше (зелёным), отрицательное — позже (красным).
 */
@Composable
fun BottomCard(
    modifier: Modifier = Modifier,
    severity: Int = 3,
    isLeft: Boolean = true,
    distance: String = "320 м",
    street: String = "ул. Ленина",
    eta: String = "18:42",
    minutesAhead: Int = 3,
    speed: String = "62 км/ч",
    progress: Float = 0.42f
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(ShColors.Panel.copy(alpha = 0.95f))
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp)
    ) {
        // а) знак + дистанция + улица
        Row(verticalAlignment = Alignment.CenterVertically) {
            RallySign(severity = severity, isLeft = isLeft)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(text = distance, color = ShColors.TextPrimary, style = ShText.Distance)
                Text(text = street, color = ShColors.TextSecondary, style = ShText.Street)
            }
        }

        // б) разделитель
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(ShColors.Divider)
        )
        Spacer(Modifier.height(12.dp))

        // в) ETA, разница во времени, скорость
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = eta, color = ShColors.TextPrimary, style = ShText.BigNumber)
            Spacer(Modifier.width(12.dp))
            val ahead = minutesAhead >= 0
            Text(
                text = if (ahead) "+$minutesAhead мин" else "−${-minutesAhead} мин",
                color = if (ahead) ShColors.Success else ShColors.Danger,
                style = ShText.Small
            )
            Spacer(Modifier.weight(1f))
            Text(text = speed, color = ShColors.TextPrimary, style = ShText.BigNumber)
        }

        // г) прогресс-полоса поездки
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(ShColors.Divider)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress)
                        .background(
                            Brush.horizontalGradient(
                                listOf(ShColors.Accent, ShColors.Success)
                            )
                        )
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = "${(progress * 100).toInt()}%",
                color = ShColors.TextSecondary,
                fontFamily = NumberFont,
                style = ShText.Small
            )
        }

        // д) кнопка «Синхронизировать»
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ShColors.Accent,
                contentColor = ShColors.Background
            )
        ) {
            Text(text = "Синхронизировать", style = ShText.Button)
        }
    }
}
