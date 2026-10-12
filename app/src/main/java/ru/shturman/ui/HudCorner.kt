package ru.shturman.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.shturman.Hud
import ru.shturman.ui.theme.ShColors

private val SIDES = arrayOf("С", "СВ", "В", "ЮВ", "Ю", "ЮЗ", "З", "СЗ")

/** Сторона света по курсу: 0° — С, 90° — В. */
fun compassSide(deg: Int): String = SIDES[(((deg % 360) + 360) % 360 + 22) / 45 % 8]

/** Мелкие показания: компас, высота, точность GPS, батарея, сеть, погода, приветствие. */
@Composable
fun HudCorner(hud: Hud, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        hud.greeting?.let { Line(it, ShColors.Accent) }
        hud.weather?.let { Line("${it.label}, ${it.tempC}°", ShColors.TextSecondary) }
        if (hud.heading >= 0) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Line("${compassSide(hud.heading)} ${hud.heading}°", ShColors.TextSecondary)
                val color = ShColors.Accent
                Canvas(Modifier.size(14.dp)) {
                    rotate(hud.heading.toFloat(), pivot = center) {
                        val p = Path().apply {
                            moveTo(center.x, 0f); lineTo(size.width * 0.8f, size.height); lineTo(center.x, size.height * 0.7f)
                            lineTo(size.width * 0.2f, size.height); close()
                        }
                        drawPath(p, color)
                    }
                }
            }
        }
        hud.altitudeM?.let { Line("Высота $it м", ShColors.TextSecondary) }
        if (hud.accuracyM >= 0) {
            val (label, c) = when {
                hud.accuracyM <= 10 -> "GPS отличный" to ShColors.TextSecondary
                hud.accuracyM <= 30 -> "GPS средний" to ShColors.TextSecondary
                else -> "GPS слабый" to ShColors.Danger
            }
            Line("$label ±${hud.accuracyM} м", c)
        }
        val bat = if (hud.batteryPct >= 0) "Батарея ${hud.batteryPct}%" + (if (hud.charging) " ⚡" else "") else null
        bat?.let { Line(it, if (hud.batteryPct in 1..14 && !hud.charging) ShColors.Danger else ShColors.TextSecondary) }
        if (!hud.online) Line("Нет сети", ShColors.Danger)
    }
}

@Composable
private fun Line(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(text = text, color = color.copy(alpha = 0.85f), fontSize = 11.sp)
}
