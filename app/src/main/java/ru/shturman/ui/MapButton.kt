package ru.shturman.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import ru.shturman.ui.fx.ButtonIcon
import ru.shturman.ui.fx.CapsuleDivider
import ru.shturman.ui.fx.accentButton
import ru.shturman.ui.fx.capsuleShape
import ru.shturman.ui.fx.glassButton
import ru.shturman.ui.fx.mapButtonShape
import ru.shturman.ui.fx.pressFx
import ru.shturman.ui.theme.ShColors
import ru.shturman.ui.themes.ThemeSound
import ru.shturman.ui.themes.ThemeSounds
import ru.shturman.ui.themes.themedEnter

/** Обводка для элементов, которые ещё не переведены на стекло: цвет берётся из темы. */
val ShBorder: Color get() = ShColors.T.border

/** Сторона кнопки на карте: крупная, чтобы попадать пальцем на ходу. */
val MapButtonSize = 56.dp

/**
 * Отдельная кнопка поверх карты: ровное стекло в форме темы (круг, скруглённый квадрат или срезанные углы),
 * появляется каскадом (index) и откликается на нажатие по характеру темы.
 * accent — главная кнопка (поиск): заливка градиентом цветов темы.
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
    val shape = mapButtonShape()
    val source = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current
    val vibrate = ShColors.T.vibrate
    Box(
        modifier = modifier
            .themedEnter(index)
            .size(MapButtonSize)
            .pressFx()
            .then(if (accent) Modifier.accentButton(shape, elevation = 12.dp) else Modifier.glassButton(shape))
            .clickable(interactionSource = source, indication = null) {
                ThemeSounds.play(ThemeSound.TAP)
                if (vibrate) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (accent) ShColors.OnAccent else ButtonIcon,
            modifier = Modifier.size(24.dp)
        )
    }
}

/** Кнопка внутри капсулы. */
class CapsuleItem(val icon: ImageVector, val description: String, val onClick: () -> Unit)

/**
 * Капсула: несколько кнопок одной стеклянной полосой с тонкими разделителями (меню, музыка, антирадар).
 * На карте меньше отдельных плиток, а каждая кнопка остаётся крупной.
 */
@Composable
fun MapCapsule(items: List<CapsuleItem>, modifier: Modifier = Modifier, index: Int = 0) {
    val haptic = LocalHapticFeedback.current
    val vibrate = ShColors.T.vibrate
    val divider = CapsuleDivider
    Column(
        modifier = modifier
            .themedEnter(index)
            .width(MapButtonSize)
            .glassButton(capsuleShape(), elevation = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        items.forEachIndexed { i, item ->
            if (i > 0) Box(Modifier.padding(horizontal = 12.dp).fillMaxWidth().height(1.dp).background(divider))
            val source = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .size(MapButtonSize)
                    .pressFx()
                    .clickable(interactionSource = source, indication = null) {
                        ThemeSounds.play(ThemeSound.TAP)
                        if (vibrate) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        item.onClick()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = item.icon, contentDescription = item.description, tint = ButtonIcon, modifier = Modifier.size(24.dp))
            }
        }
    }
}
