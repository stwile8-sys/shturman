package ru.shturman.ui.fx

import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.ui.draw.drawWithContent
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
    Flavor.TELEMETRY -> CutCornerShape(topEnd = 18.dp, bottomStart = 18.dp)
    Flavor.NEON -> RoundedCornerShape(14.dp)
    else -> RoundedCornerShape(20.dp)
}

/** Градиент акцентов темы для заливок. */
fun accentBrush(): Brush = Brush.linearGradient(listOf(ShColors.Accent, ShColors.Accent2), Offset.Zero, Offset.Infinite)

/**
 * Главная кнопка («Поехали», «Войти», «Синхронизировать»): градиент двух цветов темы, бегущий блик,
 * цветное свечение, отклик на нажатие и, по желанию, взрыв частиц из её центра.
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
    val shape = glowButtonShape()
    val tr = rememberInfiniteTransition(label = "sheen")
    val sheen by tr.animateFloat(-0.5f, 1.8f, infiniteRepeatable(tween(2800, easing = FastOutSlowInEasing)), label = "s")
    val pulse by tr.animateFloat(0.6f, 1f, infiniteRepeatable(tween(1500), RepeatMode.Reverse), label = "p")
    val source = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .fxAnchor(anchor)
            .height(height)
            .pressFx()
            .shadow(if (l.flavor == Flavor.NEON) 18.dp * pulse else 14.dp, shape, clip = false, ambientColor = l.glow, spotColor = l.glow)
            .clip(shape)
            .background(if (enabled) accentBrush() else Brush.linearGradient(listOf(ShColors.Divider, ShColors.SearchField)))
            .drawWithContent {
                drawContent()
                if (!enabled) return@drawWithContent
                // бегущий блик
                val x = size.width * sheen
                drawRect(
                    Brush.linearGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.45f), Color.Transparent),
                        Offset(x - size.height, 0f), Offset(x + size.height * 0.2f, size.height)
                    )
                )
                // клетка финиша у телеметрии
                if (l.flavor == Flavor.TELEMETRY) {
                    val c = size.height / 4f
                    for (r in 0 until 4) for (k in 0 until 3) {
                        if ((r + k) % 2 == 0) drawRect(ShColors.OnAccent.copy(alpha = 0.28f), Offset(size.width - c * (k + 1) - 6f, r * c), androidx.compose.ui.geometry.Size(c, c))
                    }
                }
            }
            .clickable(interactionSource = source, indication = null, enabled = enabled) {
                ThemeSounds.play(ThemeSound.TAP)
                if (burst > 0f) fx.burst(anchor.center, burst)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (enabled) ShColors.OnAccent else ShColors.TextSecondary,
            style = ShText.Button.copy(fontSize = 18.sp, letterSpacing = if (l.flavor == Flavor.GLASS) 0.3.sp else 1.5.sp)
        )
    }
}

/** Ручка-хваталка сверху шторок и панелей. */
@Composable
fun GrabHandle(modifier: Modifier = Modifier) {
    Box(modifier.width(40.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.28f)))
}

/** Плитка с градиентом темы под иконку (поворот, разделы настроек). */
@Composable
fun AccentTile(modifier: Modifier = Modifier, size: Dp = 54.dp, content: @Composable () -> Unit) {
    val l = ShColors.L
    val shape = if (l.flavor == Flavor.TELEMETRY) CutCornerShape(topEnd = 12.dp, bottomStart = 12.dp) else RoundedCornerShape(size * 0.33f)
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
