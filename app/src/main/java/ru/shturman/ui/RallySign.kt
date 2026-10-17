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

/** Форма раллийного знака зависит от темы: круг, скошенный угол, прямоугольник со скруглением. Логика 1–6 + L/R та же. */
fun signShape(): androidx.compose.ui.graphics.Shape = when (ShColors.T.btn) {
    ru.shturman.ui.themes.BtnKind.CHAMFER -> androidx.compose.foundation.shape.CutCornerShape(30)
    ru.shturman.ui.themes.BtnKind.ROUND, ru.shturman.ui.themes.BtnKind.SOFT -> CircleShape
    ru.shturman.ui.themes.BtnKind.SLIM -> androidx.compose.foundation.shape.RoundedCornerShape(50)
    ru.shturman.ui.themes.BtnKind.GRAIN -> androidx.compose.foundation.shape.RoundedCornerShape(22)
    ru.shturman.ui.themes.BtnKind.TICKS -> androidx.compose.ui.graphics.RectangleShape
    else -> androidx.compose.foundation.shape.RoundedCornerShape(12)
}

/** Раллийный знак: цифра резкости и буква L (влево) или R (вправо). */
@Composable
fun RallySign(
    severity: Int,
    isLeft: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(50.dp)
            .clip(signShape())
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
