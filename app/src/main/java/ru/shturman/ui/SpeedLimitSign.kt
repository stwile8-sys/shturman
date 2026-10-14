package ru.shturman.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.shturman.ui.fx.popOnChange

/** Круглый знак «ограничение скорости»: белый круг, красное кольцо, число по центру. */
@Composable
fun SpeedLimitSign(limit: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .popOnChange(limit)
            .size(60.dp)
            .shadow(8.dp, CircleShape)
            .background(Color.White, CircleShape)
            .border(6.dp, Color(0xFFE5202A), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = limit.toString(),
            color = Color(0xFF111111),
            style = TextStyle(
                fontSize = if (limit >= 100) 21.sp else 26.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp
            )
        )
    }
}
