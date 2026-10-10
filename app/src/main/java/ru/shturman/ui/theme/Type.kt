package ru.shturman.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Rajdhani пока не подключён, поэтому для цифр используем Roboto Bold
// (на Android SansSerif — это и есть Roboto). Позже можно добавить Rajdhani
// в папку res/font и заменить одну строку ниже.
val NumberFont: FontFamily = FontFamily.SansSerif
val TextFont: FontFamily = FontFamily.SansSerif

object ShText {
    val BigNumber = TextStyle(fontFamily = NumberFont, fontWeight = FontWeight.Bold, fontSize = 28.sp)
    val Distance = TextStyle(fontFamily = NumberFont, fontWeight = FontWeight.Bold, fontSize = 24.sp)
    val Street = TextStyle(fontFamily = TextFont, fontWeight = FontWeight.Normal, fontSize = 16.sp)
    val Small = TextStyle(fontFamily = TextFont, fontWeight = FontWeight.Medium, fontSize = 16.sp)
    val Button = TextStyle(fontFamily = TextFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
}

val ShTypography = Typography()
