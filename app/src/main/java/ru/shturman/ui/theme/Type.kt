package ru.shturman.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Rajdhani пока не подключён, поэтому для цифр используем Roboto Bold
// (на Android SansSerif — это и есть Roboto). Позже можно добавить Rajdhani
// в папку res/font и заменить одну строку ниже.
val NumberFont: FontFamily = FontFamily.SansSerif
val TextFont: FontFamily = FontFamily.SansSerif

/** Шрифт режима принцессы: изящный курсив Lora с кириллицей (лежит в res/font/lora_italic.ttf). */
val PrincessFont: FontFamily = FontFamily(Font(ru.shturman.R.font.lora_italic, FontWeight.Normal, FontStyle.Italic))

private val num: FontFamily get() = if (ShColors.princess) PrincessFont else NumberFont
private val txt: FontFamily get() = if (ShColors.princess) PrincessFont else TextFont

object ShText {
    val BigNumber: TextStyle get() = TextStyle(fontFamily = num, fontWeight = FontWeight.Bold, fontSize = 28.sp)
    val Distance: TextStyle get() = TextStyle(fontFamily = num, fontWeight = FontWeight.Bold, fontSize = 24.sp)
    val Street: TextStyle get() = TextStyle(fontFamily = txt, fontWeight = FontWeight.Normal, fontSize = 16.sp)
    val Small: TextStyle get() = TextStyle(fontFamily = txt, fontWeight = FontWeight.Medium, fontSize = 16.sp)
    val Button: TextStyle get() = TextStyle(fontFamily = txt, fontWeight = FontWeight.Bold, fontSize = 16.sp)
}

val ShTypography = Typography()
