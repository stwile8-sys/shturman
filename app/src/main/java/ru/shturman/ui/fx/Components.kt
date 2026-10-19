package ru.shturman.ui.fx

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.shturman.ui.theme.ShColors
import ru.shturman.ui.theme.ShText
import ru.shturman.ui.themes.Flavor
import ru.shturman.ui.themes.ThemeSound
import ru.shturman.ui.themes.ThemeSounds

/** Форма главной кнопки по характеру темы. */
fun glowButtonShape(): Shape = when (ShColors.L.flavor) {
    Flavor.PREMIUM -> RoundedCornerShape(10.dp)
    Flavor.TELEMETRY -> CutCornerShape(topEnd = 16.dp, bottomStart = 16.dp)
    Flavor.NEON -> RoundedCornerShape(15.dp)
    Flavor.APEX -> androidx.compose.ui.graphics.RectangleShape
    else -> RoundedCornerShape(22.dp)
}

/** Градиент акцентов темы для заливок. */
fun accentBrush(): Brush = Brush.linearGradient(listOf(ShColors.Accent, ShColors.Accent2), Offset.Zero, Offset.Infinite)

/**
 * Главная кнопка («Поехали», «Войти», «Синхронизировать»): ровный градиент двух цветов темы и мягкая
 * цветная тень, без бликов и полосок. Отклик на нажатие по теме и, по желанию, взрыв частиц из центра.
 */
@Composable
fun GlowButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    burst: Float = 1f,
    height: Dp = 58.dp
) {
    val fx = LocalFx.current
    val anchor = rememberFxAnchor()
    val l = ShColors.L
    val source = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .fxAnchor(anchor)
            .height(height)
            .pressFx()
            .accentButton(glowButtonShape(), elevation = 14.dp, enabled = enabled)
            .clickable(interactionSource = source, indication = null, enabled = enabled) {
                ThemeSounds.play(ThemeSound.TAP)
                if (burst > 0f && l.flavor != Flavor.PREMIUM) fx.burst(anchor.center, burst)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        if (l.flavor == Flavor.PREMIUM) {
            // «ПОЕХАЛИ»: капсом с широкой разрядкой, как на макете
            Text(
                text = text.uppercase(),
                color = if (enabled) ShColors.OnAccent else ShColors.TextSecondary,
                style = ShText.Button.copy(fontSize = 14.sp, letterSpacing = 5.sp)
            )
        } else {
            Text(
                text = text,
                color = if (enabled) ShColors.OnAccent else ShColors.TextSecondary,
                style = ShText.Button.copy(fontSize = 18.sp, letterSpacing = when (l.flavor) { Flavor.GLASS -> 0.3.sp; Flavor.APEX -> 4.sp; else -> 1.2.sp })
            )
        }
    }
}

/** Ручка-хваталка сверху шторок и панелей. */
@Composable
fun GrabHandle(modifier: Modifier = Modifier) {
    Box(modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (ShColors.premium) ShColors.Divider else Color.White.copy(alpha = 0.28f)))
}

/** Плитка с градиентом темы под иконку (поворот, разделы настроек). */
@Composable
fun AccentTile(modifier: Modifier = Modifier, size: Dp = 54.dp, content: @Composable () -> Unit) {
    val l = ShColors.L
    val shape = when (l.flavor) {
        Flavor.TELEMETRY -> CutCornerShape(topEnd = 12.dp, bottomStart = 12.dp)
        Flavor.APEX -> androidx.compose.ui.graphics.RectangleShape
        Flavor.PREMIUM -> RoundedCornerShape(10.dp)
        else -> RoundedCornerShape(size * 0.33f)
    }
    Box(
        modifier
            .size(size)
            .shadow(10.dp, shape, clip = false, ambientColor = l.glow, spotColor = l.glow)
            .clip(shape)
            .background(accentBrush()),
        contentAlignment = Alignment.Center
    ) { content() }
}

/** Пульсирующая точка «идёт загрузка». */
@Composable
fun PulseDot(color: Color = ShColors.Accent, modifier: Modifier = Modifier) {
    val tr = rememberInfiniteTransition(label = "dot")
    val a by tr.animateFloat(0.3f, 1f, infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    Box(modifier.size(8.dp).alpha(a).clip(CircleShape).background(color))
}
