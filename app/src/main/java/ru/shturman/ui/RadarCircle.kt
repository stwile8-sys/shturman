package ru.shturman.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.shturman.ui.theme.ShColors
import ru.shturman.ui.theme.ShText

/**
 * Радар-круг: появляется, когда впереди на маршруте камера (ближе 600 м).
 * Внутри расстояние, под ним категория: «камера», «на мосту» или «в тоннеле».
 */
@Composable
fun RadarCircle(distanceM: Double, place: String, modifier: Modifier = Modifier) {
    val ring = when (place) {
        "tunnel" -> Color(0xFFB36BFF)
        "bridge" -> Color(0xFF3DDCFF)
        else -> Color(0xFFFF8A00)
    }
    val label = when (place) {
        "tunnel" -> "в тоннеле"
        "bridge" -> "на мосту"
        else -> "камера"
    }
    Column(
        modifier = modifier
            .size(72.dp)
            .background(ShColors.Panel.copy(alpha = 0.94f), CircleShape)
            .border(4.dp, ring, CircleShape),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        Text(
            text = "${distanceM.toInt()} м",
            color = ShColors.TextPrimary,
            style = ShText.Distance.copy(fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
        )
        Text(text = label, color = ring, fontSize = 10.sp)
    }
}
