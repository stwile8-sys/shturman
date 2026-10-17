package ru.shturman.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import ru.shturman.ui.themes.ThemeSpec
import ru.shturman.ui.themes.Themes

// Все цвета приложения берутся из выбранной темы (ui/themes/AppTheme.kt). Все темы тёмные.
object ShColors {
    /** Номер выбранной темы. Меняется из настроек, и весь экран перекрашивается сам. */
    var themeId by mutableStateOf(0)

    val T: ThemeSpec get() = Themes.all[themeId.coerceIn(0, Themes.all.size - 1)]

    /** Характер оформления темы: стекло, неон, телеметрия или глубина (ui/themes/Look.kt). */
    val L: ru.shturman.ui.themes.ThemeLook get() = ru.shturman.ui.themes.Looks.of(themeId)

    /** Второй цвет градиентов темы. */
    val Accent2: Color get() = L.acc2

    /** Цвет текста на заливке акцентом. */
    val OnAccent: Color get() = T.bg

    /** Старое имя: «принцесса» — это тема №2. */
    val princess: Boolean get() = themeId == 1

    val Background: Color get() = T.bg
    val Panel: Color get() = T.panel
    val Accent: Color get() = T.accent
    val Danger: Color get() = T.danger
    val Success: Color get() = T.third
    val Gold: Color get() = when {
        themeId == 1 -> T.third
        L.flavor == ru.shturman.ui.themes.Flavor.APEX -> T.text
        else -> Color(0xFFFFD400)
    }
    val TextPrimary: Color get() = T.text
    val TextSecondary: Color get() = T.textSec
    val Divider: Color get() = T.divider
    val SearchField: Color get() = T.search

    // Цвета раллийного знака по резкости поворота
    val Sign1to2: Color get() = T.signs[0]
    val Sign3: Color get() = T.signs[1]
    val Sign4: Color get() = T.signs[2]
    val Sign5: Color get() = T.signs[3]
    val Sign6: Color get() = T.signs[4]
}
