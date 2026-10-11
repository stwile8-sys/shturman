package ru.shturman.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

// Все цвета приложения собраны здесь. Хочешь поменять цвет — меняй только тут.
// Есть две палитры: обычная тёмная и «принцесса» (пастельная: розовый, лавандовый, золотой).
object ShColors {
    /** Режим принцессы. Меняется из настроек, и весь экран перекрашивается сам. */
    var princess by mutableStateOf(false)

    val Background: Color get() = if (princess) Color(0xFFF8EEF8) else Color(0xFF0A0C10)   // фон приложения
    val Panel: Color get() = if (princess) Color(0xFFFFF0F7) else Color(0xFF12161C)        // панели
    val Accent: Color get() = if (princess) Color(0xFFF2709F) else Color(0xFF3DDCFF)       // кнопки, маршрут
    val Danger: Color get() = if (princess) Color(0xFFE0457B) else Color(0xFFFF2E2E)
    val Success: Color get() = if (princess) Color(0xFFB79CF2) else Color(0xFF7CFF6B)      // успех (у принцессы — лаванда)
    val Gold: Color get() = if (princess) Color(0xFFE6B84F) else Color(0xFFFFD400)         // золотой акцент
    val TextPrimary: Color get() = if (princess) Color(0xFF4A2A4C) else Color(0xFFFFFFFF)  // основной текст
    val TextSecondary: Color get() = if (princess) Color(0xFF9A7B9E) else Color(0xFF8A9099)
    val Divider: Color get() = if (princess) Color(0xFFEBD3EA) else Color(0xFF2A2F38)      // разделитель и фон полосы
    val SearchField: Color get() = if (princess) Color(0xFFF6DDF0) else Color(0xFF1B2028)  // поле поиска внутри панели

    // Цвета раллийного знака по резкости поворота
    val Sign1to2 = Color(0xFFFF2E2E)
    val Sign3 = Color(0xFFFF8A00)
    val Sign4 = Color(0xFFFFD400)
    val Sign5 = Color(0xFF7CFF6B)
    val Sign6 = Color(0xFF3DDCFF)
}
