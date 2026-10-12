package ru.shturman.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import ru.shturman.ui.theme.ShColors
import ru.shturman.ui.themes.ThemeSound
import ru.shturman.ui.themes.ThemeSounds
import ru.shturman.ui.themes.themeButtonShape
import ru.shturman.ui.themes.themedButtonTexture

/** Обводка для всех плавающих элементов: цвет и толщина берутся из темы. */
val ShBorder: Color get() = ShColors.T.border

/** Плавающая кнопка поверх карты. Форма, материал, размер, вспышка и звук нажатия зависят от темы. */
@Composable
fun MapButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val t = ShColors.T
    val shape = themeButtonShape()
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) t.pressScale else 1f, tween(t.pressMs), label = "press")
    val flash = remember { Animatable(0f) }
    LaunchedEffect(pressed) {
        if (pressed) {
            flash.snapTo(1f)
            flash.animateTo(0f, tween(t.pressMs + 120))
        }
    }
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = modifier
            .size(t.btnW.dp, t.btnH.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(ShColors.Panel.copy(alpha = 0.92f))
            .themedButtonTexture()
            .drawBehind { drawRect(t.flash.copy(alpha = flash.value * 0.55f)) }
            .border(t.borderW.dp, ShBorder, shape)
            .clickable(interactionSource = source, indication = null) {
                ThemeSounds.play(ThemeSound.TAP)
                if (t.vibrate) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = t.iconTint,
            modifier = Modifier.size(24.dp)
        )
    }
}
