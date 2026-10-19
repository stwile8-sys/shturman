package ru.shturman.ui.themes

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.shturman.ui.fx.pressFx

/**
 * Раздел «Тема оформления»: живые карточки тем (APEX — только после открытия «Штурман 2»). Слева — маленький макет экрана в стиле темы
 * (карта, маршрут, стеклянная панель, её частицы), справа — название и характер. Выбор применяется сразу.
 */
@Composable
fun ThemePicker(selected: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        for (th in Themes.visible()) {
            val look = Looks.of(th.id)
            val on = th.id == selected
            val lift by animateFloatAsState(if (on) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = 300f), label = "lift")
            val shape = when (look.flavor) {
                Flavor.PREMIUM -> RoundedCornerShape(12.dp)
                Flavor.TELEMETRY -> CutCornerShape(topEnd = 16.dp, bottomStart = 16.dp)
                Flavor.APEX -> androidx.compose.ui.graphics.RectangleShape
                else -> RoundedCornerShape(18.dp)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .themedEnter(th.id)
                    .fillMaxWidth()
                    .graphicsLayer { translationY = -4f * density * lift; val s = 1f + 0.02f * lift; scaleX = s; scaleY = s }
                    .pressFx()
                    .shadow((12f * lift).dp, shape, clip = false, ambientColor = look.glow, spotColor = look.glow)
                    .clip(shape)
                    .background(Brush.linearGradient(listOf(th.bg, androidx.compose.ui.graphics.lerp(th.panel, th.accent, if (look.flavor == Flavor.APEX) 0.02f else 0.08f))))
                    .border(if (on) 2.dp else 1.dp, if (on) th.accent else Color.White.copy(alpha = 0.08f), shape)
                    .clickable { onSelect(th.id) }
                    .padding(10.dp)
            ) {
                Canvas(Modifier.size(width = 74.dp, height = 74.dp).clip(RoundedCornerShape(12.dp))) { miniScene(th, look) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    // в новом оформлении шрифт у всех один — Montserrat; у APEX — свой
                    val apexLook = look.flavor == Flavor.APEX
                    Text(text = th.name, color = th.text, fontFamily = if (apexLook) th.numFont else ru.shturman.ui.theme.Montserrat, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, fontSize = 17.sp)
                    Spacer(Modifier.height(2.dp))
                    Text(text = th.vibe, color = th.textSec, fontFamily = if (apexLook) th.textFont else ru.shturman.ui.theme.Montserrat, fontSize = 12.sp, maxLines = 2)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (c in listOf(th.accent, look.acc2, th.third)) {
                            Box(Modifier.size(12.dp).clip(CircleShape).background(c))
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .graphicsLayer { val s = 0.6f + 0.4f * lift; scaleX = s; scaleY = s; alpha = lift }
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(th.accent, look.acc2))),
                    contentAlignment = Alignment.Center
                ) { Text("✓", color = th.bg, fontSize = 14.sp) }
            }
        }
    }
}

/** Маленький макет экрана темы: тёмная карта, маршрут цветом темы, стеклянная плашка и метка характера. */
private fun DrawScope.miniScene(th: ThemeSpec, look: ThemeLook) {
    val w = size.width; val h = size.height
    drawRect(th.bg)
    // улицы
    val road = androidx.compose.ui.graphics.lerp(th.bg, Color.White, 0.12f)
    drawLine(road, Offset(0f, h * 0.7f), Offset(w, h * 0.55f), 5f)
    drawLine(road, Offset(w * 0.25f, 0f), Offset(w * 0.4f, h), 4f)
    drawLine(road, Offset(w * 0.7f, 0f), Offset(w * 0.85f, h), 4f)
    // маршрут с подсветкой
    val route = Path().apply { moveTo(w * 0.5f, h); lineTo(w * 0.52f, h * 0.62f); lineTo(w * 0.36f, h * 0.45f); lineTo(w * 0.72f, h * 0.22f) }
    drawPath(route, look.glow.copy(alpha = 0.35f), style = Stroke(10f))
    drawPath(route, th.accent, style = Stroke(4f))
    drawCircle(look.dot ?: th.accent, 5f, Offset(w * 0.72f, h * 0.22f))
    // стеклянная плашка сверху
    val pw = w * 0.62f; val ph = h * 0.2f
    val tl = Offset(w * 0.06f, h * 0.06f)
    drawRoundRect(Color.White.copy(alpha = 0.16f), tl, Size(pw, ph), CornerRadius(if (look.flavor == Flavor.TELEMETRY || look.flavor == Flavor.APEX) 0f else 10f))
    when (look.flavor) {
        Flavor.NEON -> drawRoundRect(Brush.linearGradient(listOf(th.accent, look.acc2)), tl, Size(pw, ph), CornerRadius(10f), style = Stroke(2.5f))
        Flavor.TELEMETRY -> drawRect(th.accent, tl, Size(5f, ph))
        Flavor.DEPTH -> drawRoundRect(Color.Black.copy(alpha = 0.35f), Offset(tl.x + 3f, tl.y + ph), Size(pw, 5f), CornerRadius(3f))
        Flavor.GLASS -> drawRoundRect(Color.White.copy(alpha = 0.35f), tl, Size(pw, ph), CornerRadius(10f), style = Stroke(1.2f))
        Flavor.PREMIUM -> drawRoundRect(Color.White.copy(alpha = 0.18f), tl, Size(pw, ph), CornerRadius(8f), style = Stroke(1f))
        Flavor.APEX -> {
            // метки по углам плашки
            val k = ph * 0.35f
            for ((cx, cy, sx, sy) in listOf(
                floatArrayOf(tl.x, tl.y, 1f, 1f), floatArrayOf(tl.x + pw, tl.y, -1f, 1f),
                floatArrayOf(tl.x, tl.y + ph, 1f, -1f), floatArrayOf(tl.x + pw, tl.y + ph, -1f, -1f)
            )) {
                drawLine(th.text, Offset(cx, cy), Offset(cx + sx * k, cy), 1.5f)
                drawLine(th.text, Offset(cx, cy), Offset(cx, cy + sy * k), 1.5f)
            }
        }
    }
    drawRoundRect(Brush.linearGradient(listOf(th.accent, look.acc2), tl, Offset(tl.x + ph, tl.y + ph)), Offset(tl.x + 5f, tl.y + 5f), Size(ph - 10f, ph - 10f), CornerRadius(6f))
    // частицы темы в углу
    val c = Offset(w * 0.8f, h * 0.78f)
    for (i in 0 until 5) {
        val a = i * 1.256f
        val p = Offset(c.x + kotlin.math.cos(a) * w * 0.1f, c.y + kotlin.math.sin(a) * h * 0.1f)
        val col = look.particleColors[i % look.particleColors.size]
        when (look.particle) {
            Particle.PIXEL -> drawRect(col, Offset(p.x - 3f, p.y - 3f), Size(6f, 6f))
            Particle.HEART -> drawCircle(col, 4f, p)
            Particle.SPARK -> drawLine(col, c, p, 2.5f)
            Particle.EMBER -> drawCircle(Brush.radialGradient(listOf(col, Color.Transparent), p, 8f), 8f, p)
            Particle.DUST, Particle.SMOKE -> drawCircle(col.copy(alpha = 0.45f), 7f, p)
        }
    }
}
