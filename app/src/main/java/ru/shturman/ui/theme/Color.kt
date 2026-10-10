package ru.shturman.ui.theme

import androidx.compose.ui.graphics.Color

// Все цвета приложения собраны здесь. Хочешь поменять цвет — меняй только тут.
object ShColors {
    val Background = Color(0xFF0A0C10)      // фон приложения
    val Panel = Color(0xFF12161C)           // панели
    val Accent = Color(0xFF3DDCFF)          // кнопки, маршрут
    val Danger = Color(0xFFFF2E2E)          // камеры
    val Success = Color(0xFF7CFF6B)         // успех
    val TextPrimary = Color(0xFFFFFFFF)     // основной текст
    val TextSecondary = Color(0xFF8A9099)   // второстепенный текст
    val Divider = Color(0xFF2A2F38)         // разделитель и фон полосы
    val SearchField = Color(0xFF1B2028)     // поле поиска внутри панели

    // Цвета раллийного знака по резкости поворота
    val Sign1to2 = Color(0xFFFF2E2E)
    val Sign3 = Color(0xFFFF8A00)
    val Sign4 = Color(0xFFFFD400)
    val Sign5 = Color(0xFF7CFF6B)
    val Sign6 = Color(0xFF3DDCFF)
}
