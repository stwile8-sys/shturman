@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package ru.shturman.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ru.shturman.R

private fun mw(weight: Int) = Font(R.font.montserrat, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

/**
 * Montserrat — шрифт нового оформления у всех тем. Один переменный файл, веса от тонкого до жирного:
 * 200 — крупные цифры, 300 — средние, 400–500 — текст, 600 — подписи капсом с разрядкой.
 */
val Montserrat: FontFamily = FontFamily(mw(200), mw(300), mw(400), mw(500), mw(600), mw(700))

// Шрифт один на всё приложение; цвета берутся из выбранной темы. Заставки тем рисуют свои надписи сами.
val NumberFont: FontFamily get() = Montserrat
val TextFont: FontFamily get() = Montserrat

object ShText {
    /** Крупное число в панели поворота: «320». */
    val Hero: TextStyle get() = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight(200), fontSize = 54.sp, letterSpacing = (-1).sp, lineHeight = 56.sp)
    /** Средние значения: время прибытия, остаток пути. */
    val Value: TextStyle get() = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight(300), fontSize = 26.sp, lineHeight = 30.sp)
    /** Подписи капсом с разрядкой: «ПРИБЫТИЕ», «УЛ. ГОГОЛЯ». */
    val Caps: TextStyle get() = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, letterSpacing = 2.sp, lineHeight = 14.sp)
    val Title: TextStyle get() = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 24.sp)
    val Body: TextStyle get() = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp)
    val Secondary: TextStyle get() = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp)

    // Прежние имена — для экранов, которые пользуются ими
    val BigNumber: TextStyle get() = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight(300), fontSize = 28.sp)
    val Distance: TextStyle get() = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight(300), fontSize = 24.sp)
    val Street: TextStyle get() = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Normal, fontSize = 15.sp)
    val Small: TextStyle get() = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Medium, fontSize = 15.sp)
    val Button: TextStyle get() = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, letterSpacing = 0.5.sp)
}

/** Вся типографика Material — на Montserrat, чтобы и простые подписи были в одном стиле. */
val ShTypography: Typography = Typography().let { t ->
    fun TextStyle.m() = copy(fontFamily = Montserrat)
    Typography(
        displayLarge = t.displayLarge.m(), displayMedium = t.displayMedium.m(), displaySmall = t.displaySmall.m(),
        headlineLarge = t.headlineLarge.m(), headlineMedium = t.headlineMedium.m(), headlineSmall = t.headlineSmall.m(),
        titleLarge = t.titleLarge.m(), titleMedium = t.titleMedium.m(), titleSmall = t.titleSmall.m(),
        bodyLarge = t.bodyLarge.m(), bodyMedium = t.bodyMedium.m(), bodySmall = t.bodySmall.m(),
        labelLarge = t.labelLarge.m(), labelMedium = t.labelMedium.m(), labelSmall = t.labelSmall.m()
    )
}
