package ru.shturman.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.shturman.ui.theme.NumberFont
import ru.shturman.ui.theme.ShColors

/** Цвет знака по резкости поворота (1 — самый резкий, 6 — самый плавный). */
fun signColor(severity: Int): Color = when (severity) {
    1, 2 -> ShColors.Sign1to2
    3 -> ShColors.Sign3
    4 -> ShColors.Sign4
    5 -> ShColors.Sign5
    else -> ShColors.Sign6
}

/** Круглый раллийный знак: цифра резкости и буква L (влево) или R (вправо). */
@Composable
fun RallySign(
    severity: Int,
    isLeft: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(50.dp)
            .clip(CircleShape)
            .background(signColor(severity)),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = severity.toString(),
                color = ShColors.TextPrimary,
                fontFamily = NumberFont,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp
            )
            Text(
                text = if (isLeft) "L" else "R",
                color = ShColors.TextPrimary,
                fontFamily = NumberFont,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }
}
