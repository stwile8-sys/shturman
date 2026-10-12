package ru.shturman.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Шрифты берутся из выбранной темы (ui/themes/AppTheme.kt).
val NumberFont: FontFamily get() = ShColors.T.numFont
val TextFont: FontFamily get() = ShColors.T.textFont

object ShText {
    val BigNumber: TextStyle get() = TextStyle(fontFamily = NumberFont, fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = ShColors.T.letterSp.sp)
    val Distance: TextStyle get() = TextStyle(fontFamily = NumberFont, fontWeight = FontWeight.Bold, fontSize = 24.sp, letterSpacing = ShColors.T.letterSp.sp)
    val Street: TextStyle get() = TextStyle(fontFamily = TextFont, fontWeight = FontWeight.Normal, fontSize = 16.sp, letterSpacing = ShColors.T.letterSp.sp)
    val Small: TextStyle get() = TextStyle(fontFamily = TextFont, fontWeight = FontWeight.Medium, fontSize = 16.sp, letterSpacing = ShColors.T.letterSp.sp)
    val Button: TextStyle get() = TextStyle(fontFamily = TextFont, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = ShColors.T.letterSp.sp)
}

val ShTypography = Typography()
