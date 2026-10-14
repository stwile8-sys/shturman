package ru.shturman.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.shturman.ui.fx.panel
import ru.shturman.ui.theme.ShColors
import ru.shturman.ui.theme.ShText
import ru.shturman.ui.themes.Flavor
import ru.shturman.ui.themes.ThemedText
import ru.shturman.ui.themes.themedEnter
import kotlin.math.cos
import kotlin.math.sin

/**
 * Текущая скорость. Стекло — круглая капсула; неон — светящееся кольцо-шкала;
 * телеметрия — тахометр со шкалой и стрелкой; глубина — объёмная плитка со шкалой.
 * Быстрее ограничения — краснеет и пульсирует.
 */
@Composable
fun SpeedReadout(kmh: Int, over: Boolean, modifier: Modifier = Modifier) {
    val l = ShColors.L
    val shape: Shape = when (l.flavor) {
        Flavor.DEPTH -> RoundedCornerShape(26.dp)
        Flavor.TELEMETRY -> CutCornerShape(topEnd = 14.dp, bottomStart = 14.dp)
        else -> CircleShape
    }
    val frac by animateFloatAsState((kmh / 140f).coerceIn(0f, 1f), spring(dampingRatio = 0.7f, stiffness = 120f), label = "spd")
    val tr = rememberInfiniteTransition(label = "over")
    val pulse by tr.animateFloat(0.3f, 1f, infiniteRepeatable(tween(420), RepeatMode.Reverse), label = "p")
    val accent = if (over) ShColors.Danger else ShColors.Accent
    val accent2 = if (over) ShColors.Danger else ShColors.Accent2
    val boxSize = if (l.flavor == Flavor.TELEMETRY) 92.dp else 80.dp
    Box(
        modifier = modifier
            .themedEnter()
            .size(boxSize)
            .panel(shape, elevation = 10.dp, glowing = over)
            .drawWithContent {
                drawContent()
                if (l.flavor == Flavor.GLASS) {
                    if (over) drawCircle(accent.copy(alpha = 0.55f * pulse), this.size.minDimension / 2 - 2f, style = Stroke(3.dp.toPx()))
                    return@drawWithContent
                }
                // шкала 270°: от левого нижнего угла по часовой
                val stroke = (if (l.flavor == Flavor.TELEMETRY) 5.dp else 3.5.dp).toPx()
                val inset = stroke + 4.dp.toPx()
                val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
                val tl = Offset(inset, inset)
                drawArc(Color.White.copy(alpha = 0.10f), 135f, 270f, false, tl, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                drawArc(
                    Brush.sweepGradient(listOf(accent2, accent, accent2), center),
                    135f, 270f * frac, false, tl, arcSize, style = Stroke(stroke, cap = StrokeCap.Round)
                )
                if (l.flavor == Flavor.TELEMETRY) {
                    val c = center
                    val r = arcSize.width / 2
                    for (i in 0..7) {
                        val a = Math.toRadians((135.0 + 270.0 * i / 7)).toFloat()
                        val col = if (i >= 6) ShColors.Danger else Color.White.copy(alpha = 0.5f)
                        drawLine(col, Offset(c.x + cos(a) * (r - stroke * 2.2f), c.y + sin(a) * (r - stroke * 2.2f)), Offset(c.x + cos(a) * (r - stroke * 1.2f), c.y + sin(a) * (r - stroke * 1.2f)), 1.5.dp.toPx())
                    }
                    val a = Math.toRadians((135.0 + 270.0 * frac)).toFloat()
                    drawLine(Color.White, c, Offset(c.x + cos(a) * r * 0.78f, c.y + sin(a) * r * 0.78f), 2.dp.toPx(), cap = StrokeCap.Round)
                    drawCircle(accent, 3.dp.toPx(), c)
                }
                if (over) drawCircle(accent.copy(alpha = 0.25f * pulse), this.size.minDimension / 2)
            },
        contentAlignment = if (l.flavor == Flavor.TELEMETRY) Alignment.BottomCenter else Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ThemedText(
                text = kmh.toString(),
                color = if (over) ShColors.Danger else ShColors.TextPrimary,
                style = ShText.Distance.copy(fontSize = if (l.flavor == Flavor.TELEMETRY) 20.sp else 24.sp)
            )
            Text(text = stringResource(ru.shturman.R.string.s_fee7a82e), color = ShColors.TextSecondary, fontSize = 10.sp)
        }
    }
}
