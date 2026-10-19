package ru.shturman.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import ru.shturman.NavState
import ru.shturman.routing.LatLon
import ru.shturman.routing.OVERSPEED_TOLERANCE_KMH
import ru.shturman.routing.bearingDeg
import ru.shturman.ui.theme.NumberFont
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

private val HudWhite = Color(0xFFFFFFFF)
private val HudDim = Color(0xFFB8C2CC)
private val HudRed = Color(0xFFFF3B30)
private val HudLimitRed = Color(0xFFFF2D2D)
/** Линия маршрута и полоска до поворота — цветом темы (у «Спорт-премиум» — красным). */
private val HudRouteColor: Color get() = ru.shturman.ui.theme.ShColors.Accent

/** С этого расстояния до поворота полоска начинает укорачиваться, метры. */
private const val BAR_FROM_M = 600.0

/**
 * Проекция на лобовое стекло (HUD). Телефон кладут под стекло экраном вверх, верхом к себе,
 * и смотрят на отражение. На чёрном фоне только крупное и яркое: скорость, ближайший поворот,
 * расстояние до него с укорачивающейся полоской и ограничение скорости.
 * Картинка отражена зеркально (mirror), чтобы в стекле читалась правильно.
 * Нажатие по экрану показывает кнопки «Выйти» и «Зеркально» — они не отражаются.
 * Пока проекция включена, яркость на максимуме и поворот экрана закреплён.
 */
@Composable
fun WindshieldHud(s: NavState, mirror: Boolean, onMirror: (Boolean) -> Unit, onExit: () -> Unit) {
    val context = LocalContext.current
    BackHandler(onBack = onExit)
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        val window = activity?.window
        window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = 1f } }
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
        onDispose {
            window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE } }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    var controls by remember { mutableStateOf(false) }
    // каждое нажатие на кнопку заново отсчитывает 5 секунд до скрытия кнопок
    var touchKey by remember { mutableStateOf(0) }
    var hint by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(7000L)
        hint = false
    }
    LaunchedEffect(controls, touchKey) {
        if (controls) {
            delay(5000L)
            controls = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) { detectTapGestures(onTap = { controls = !controls; hint = false }) }
    ) {
        HudPicture(
            s = s,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { scaleX = if (mirror) -1f else 1f }
                .padding(horizontal = 20.dp, vertical = 28.dp)
        )

        // Подсказка и кнопки — не зеркальные: их читают, глядя прямо на телефон
        AnimatedVisibility(
            visible = hint && !controls,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp)
        ) {
            Text(
                text = "Положите телефон под лобовое стекло экраном вверх, верхом к себе. " +
                    "Нажмите на экран, чтобы выйти или выключить зеркало.",
                color = HudDim,
                fontSize = 14.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF16191D))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
        AnimatedVisibility(
            visible = controls,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HudButton(if (mirror) "Зеркально: да" else "Зеркально: нет") { onMirror(!mirror); touchKey++ }
                HudButton("Выйти", accent = true, onClick = onExit)
            }
        }
    }
}

@Composable
private fun HudButton(text: String, accent: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .height(56.dp)
            .clip(shape)
            .background(if (accent) Color(0xFF2A2F36) else Color(0xFF16191D))
            .border(1.dp, Color.White.copy(alpha = 0.18f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = HudWhite, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Сама картинка для отражения. Размеры — от ширины (вертикально) или высоты (горизонтально) экрана. */
@Composable
private fun HudPicture(s: NavState, modifier: Modifier) {
    BoxWithConstraints(modifier) {
        val landscape = maxWidth > maxHeight
        val u = if (landscape) minOf(maxHeight, maxWidth / 2) else minOf(maxWidth, maxHeight / 1.6f)
        val route = s.route
        val navigating = s.navigating && route != null
        val total = (route?.totalM ?: 0.0).coerceAtLeast(1.0)
        val traveled = (s.position?.traveledM ?: 0.0).coerceIn(0.0, total)
        // Внизу: время прибытия и сколько осталось, а без маршрута — просто часы
        val footer = route?.takeIf { navigating }?.let { r ->
            val left = (1.0 - traveled / total).coerceIn(0.0, 1.0)
            "${formatClock(System.currentTimeMillis() + (r.timeSec * left * 1000).toLong())} · ${formatKm(total - traveled)} КМ · " +
                formatDuration(r.timeSec * left).uppercase()
        } ?: formatClock(System.currentTimeMillis())
        if (landscape) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                if (navigating) {
                    // слева поворот и под ним линия маршрута впереди
                    Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                        TurnBlock(s, u)
                        HudRoute(s, u, Modifier.weight(1f).fillMaxWidth())
                    }
                    Spacer(Modifier.width(u * 0.08f))
                }
                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    SpeedBlock(s, u)
                    Spacer(Modifier.height(u * 0.05f))
                    HudText(footer, u * 0.05f, HudDim, caps = true)
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = if (navigating) Arrangement.Top else Arrangement.Center
            ) {
                if (navigating) {
                    TurnBlock(s, u)
                    // линия маршрута впереди занимает всё место между поворотом и скоростью
                    HudRoute(s, u, Modifier.weight(1f).fillMaxWidth())
                }
                SpeedBlock(s, u)
                Spacer(Modifier.height(u * 0.04f))
                HudText(footer, u * 0.04f, HudDim, caps = true)
            }
        }
    }
}

/**
 * Линия маршрута впереди, «по ходу движения вверх», как схема: видно, куда уходит дорога и где поворот.
 * Без карты и плиток — только несколько десятков точек линии, поэтому почти не тратит батарею.
 * Сколько видно вперёд — от скорости: 15 секунд пути, от 220 до 600 м.
 */
@Composable
private fun HudRoute(s: NavState, u: Dp, modifier: Modifier) {
    val route = s.route
    val pos = s.position
    val stroke = with(LocalDensity.current) { (u * 0.05f).toPx() }
    Canvas(modifier.clipToBounds()) {
        if (route == null || pos == null || route.points.size < 2 || route.cumM.size != route.points.size) return@Canvas
        val pts = route.points
        val cum = route.cumM
        val aheadM = (s.speedKmh / 3.6 * 15.0).coerceIn(220.0, 600.0)
        val t0 = pos.traveledM.coerceIn(0.0, cum.last())
        val here = pointOnRoute(pts, cum, t0, pos.index)
        // направление дороги в нашей точке: на 20 м вперёд (в самом конце — с 20 м назад)
        val h = Math.toRadians(
            if (t0 + 20.0 <= cum.last()) bearingDeg(here, pointOnRoute(pts, cum, t0 + 20.0, pos.index))
            else bearingDeg(pointOnRoute(pts, cum, t0 - 20.0, pos.index), here)
        )
        val sinH = sin(h)
        val cosH = cos(h)
        val kx = 111_320.0 * cos(Math.toRadians(here.lat))
        val scale = (size.height * 0.86f) / aheadM.toFloat()
        val cx = size.width / 2f
        val baseY = size.height - stroke * 1.6f
        fun screen(p: LatLon): Offset {
            val x = (p.lon - here.lon) * kx
            val y = (p.lat - here.lat) * 110_540.0
            val forward = x * sinH + y * cosH
            val right = x * cosH - y * sinH
            return Offset(cx + (right * scale).toFloat(), baseY - (forward * scale).toFloat())
        }
        val line = Path()
        val from = (t0 - 25.0).coerceAtLeast(0.0)
        val to = (t0 + aheadM).coerceAtMost(cum.last())
        val a = screen(pointOnRoute(pts, cum, from, pos.index))
        line.moveTo(a.x, a.y)
        var i = (pos.index - 30).coerceAtLeast(0)
        while (i < pts.size && cum[i] <= from) i++
        while (i < pts.size && cum[i] < to) {
            val p = screen(pts[i])
            line.lineTo(p.x, p.y)
            i++
        }
        val b = screen(pointOnRoute(pts, cum, to, pos.index))
        line.lineTo(b.x, b.y)
        drawPath(line, HudRouteColor, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        // финиш, если он уже виден
        if (to >= cum.last() - 1.0) drawCircle(HudWhite, stroke * 0.9f, b)
        // наша машина — белый наконечник внизу по центру
        val w = stroke * 1.2f
        val car = Path().apply {
            moveTo(cx, baseY - w * 1.5f)
            lineTo(cx + w, baseY + w)
            lineTo(cx, baseY + w * 0.35f)
            lineTo(cx - w, baseY + w)
            close()
        }
        drawPath(car, HudWhite)
    }
}

/** Точка на линии маршрута в d метрах от начала. Ищет рядом с подсказкой index, чтобы не перебирать весь маршрут. */
private fun pointOnRoute(pts: List<LatLon>, cum: DoubleArray, d: Double, index: Int): LatLon {
    if (d <= 0.0) return pts.first()
    if (d >= cum.last()) return pts.last()
    var i = index.coerceIn(0, pts.size - 2)
    while (i > 0 && cum[i] > d) i--
    while (i < pts.size - 2 && cum[i + 1] < d) i++
    val seg = cum[i + 1] - cum[i]
    val t = if (seg < 1e-6) 0.0 else ((d - cum[i]) / seg).coerceIn(0.0, 1.0)
    return LatLon(pts[i].lat + (pts[i + 1].lat - pts[i].lat) * t, pts[i].lon + (pts[i + 1].lon - pts[i].lon) * t)
}

/** Ближайший поворот: стрелка, расстояние, улица и полоска, которая укорачивается к повороту. */
@Composable
private fun TurnBlock(s: NavState, u: Dp) {
    val turn = s.turn
    // знак белый, как на макете: цвет — только у линии маршрута и полоски
    val color = HudWhite
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (turn != null && turn.isRoundabout) {
                RoundaboutSign(turn.roundaboutExit, turn.exitAngle, u * 0.38f, color)
            } else {
                RallyArrow(severity = turn?.severity, isLeft = turn?.isLeft ?: false, boxSize = u * 0.38f, color = color, stroke = 0.08f)
            }
            Spacer(Modifier.width(u * 0.04f))
            Column {
                if (turn == null) {
                    HudText("Прямо", u * 0.15f, HudWhite, bold = true)
                } else {
                    val (num, unit) = distanceParts(turn.distanceM)
                    Row(verticalAlignment = Alignment.Bottom) {
                        HudText(num, u * 0.22f, HudWhite, bold = true, numbers = true)
                        Spacer(Modifier.width(u * 0.015f))
                        HudText(unit, u * 0.09f, HudWhite, bold = true, modifier = Modifier.padding(bottom = u * 0.03f))
                    }
                    val sub = if (turn.isRoundabout) "${turn.roundaboutExit}-й съезд" else turn.street
                    if (sub.isNotEmpty()) HudText(sub.uppercase(), u * 0.045f, HudDim, maxWidth = u * 0.6f, caps = true)
                }
            }
        }
        if (turn != null && turn.distanceM <= BAR_FROM_M) {
            Spacer(Modifier.height(u * 0.04f))
            val frac = (turn.distanceM / BAR_FROM_M).toFloat().coerceIn(0f, 1f)
            Box(
                Modifier
                    .width(u * 0.8f)
                    .height(u * 0.012f)
                    .background(Color(0xFF262626))
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(frac)
                        .background(HudRouteColor)
                )
            }
        }
    }
}

/** Скорость крупно; если превышение больше допустимого — красная. Рядом знак ограничения. */
@Composable
private fun SpeedBlock(s: NavState, u: Dp) {
    val limit = s.limitKmh
    val over = limit != null && s.speedKmh > limit + OVERSPEED_TOLERANCE_KMH
    Row(verticalAlignment = Alignment.CenterVertically) {
        // кольцо скорости, как в поездке, только крупнее; знак ограничения — справа
        SpeedRing(s.speedKmh, null, over, size = u * 0.56f)
        if (limit != null) {
            Spacer(Modifier.width(u * 0.05f))
            Box(
                modifier = Modifier
                    .size(u * 0.16f)
                    .border(u * 0.009f, HudLimitRed, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                HudText(limit.toString(), if (limit >= 100) u * 0.045f else u * 0.055f, HudWhite, numbers = true)
            }
        }
    }
}

@Composable
private fun HudText(
    text: String,
    size: Dp,
    color: Color,
    modifier: Modifier = Modifier,
    bold: Boolean = false,
    numbers: Boolean = false,
    maxWidth: Dp? = null,
    caps: Boolean = false
) {
    val fontSize: TextUnit = with(LocalDensity.current) { size.toSp() }
    Text(
        text = text,
        color = color,
        fontSize = fontSize,
        lineHeight = fontSize * 1.05f,
        // крупные цифры тонкие, как на макете (300 — чтобы читались в отражении), подписи капсом — плотные
        fontWeight = when { numbers -> FontWeight.Light; caps -> FontWeight.SemiBold; bold -> FontWeight.Medium; else -> FontWeight.Normal },
        letterSpacing = if (caps) fontSize * 0.3f else androidx.compose.ui.unit.TextUnit.Unspecified,
        fontFamily = NumberFont,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = if (maxWidth != null) modifier.then(Modifier.widthIn(max = maxWidth)) else modifier
    )
}

/** «320» + «м» или «1,2» + «км». */
private fun distanceParts(m: Double): Pair<String, String> {
    val rounded = (Math.round(m / 10.0) * 10).toInt().coerceAtLeast(10)
    return if (rounded < 1000) {
        rounded.toString() to "м"
    } else {
        String.format(Locale.US, "%.1f", m / 1000).replace('.', ',') to "км"
    }
}

private fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
