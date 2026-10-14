package ru.shturman.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import ru.shturman.ui.fx.LocalFx
import ru.shturman.ui.fx.fxAnchor
import ru.shturman.ui.fx.panel
import ru.shturman.ui.fx.pressFx
import ru.shturman.ui.fx.rememberFxAnchor
import ru.shturman.ui.theme.ShColors
import ru.shturman.ui.themes.ThemeSound
import ru.shturman.ui.themes.ThemeSounds
import ru.shturman.ui.themes.themeButtonShape
import ru.shturman.ui.themes.themedButtonTexture
import ru.shturman.ui.themes.themedEnter

/** Обводка для элементов, которые ещё не переведены на стекло: цвет берётся из темы. */
val ShBorder: Color get() = ShColors.T.border

/**
 * Плавающая кнопка поверх карты: стеклянная плитка в стиле темы, появляется каскадом (index),
 * откликается на нажатие по характеру темы и выпускает частицы из центра.
 * accent — главная кнопка (поиск): иконка цвета акцента и цветное свечение.
 */
@Composable
fun MapButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    index: Int = 0,
    accent: Boolean = false
) {
    val t = ShColors.T
    val shape = themeButtonShape()
    val fx = LocalFx.current
    val anchor = rememberFxAnchor()
    val source = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = modifier
            .themedEnter(index)
            .fxAnchor(anchor)
            .size(t.btnW.dp.coerceAtLeast(48.dp), t.btnH.dp.coerceAtLeast(48.dp))
            .pressFx()
            .panel(shape, elevation = 10.dp, glowing = accent)
            .themedButtonTexture()
            .clickable(interactionSource = source, indication = null) {
                ThemeSounds.play(ThemeSound.TAP)
                if (t.vibrate) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                fx.burst(anchor.center, 0.6f)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (accent) ShColors.Accent else t.iconTint,
            modifier = Modifier.size(24.dp)
        )
    }
}
