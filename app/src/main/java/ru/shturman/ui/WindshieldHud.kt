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
import ru.shturman.routing.OVERSPEED_TOLERANCE_KMH
import ru.shturman.ui.theme.NumberFont
import java.util.Locale

private val HudWhite = Color(0xFFFFFFFF)
private val HudDim = Color(0xFFB8C2CC)
private val HudRed = Color(0xFFFF3B30)
private val HudLimitRed = Color(0xFFFF2D2D)

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
        val footer = if (navigating && route != null) {
            val left = (1.0 - traveled / total).coerceIn(0.0, 1.0)
            "${formatClock(System.currentTimeMillis() + (route.timeSec * left * 1000).toLong())} · ${formatKm(total - traveled)} км"
        } else {
            formatClock(System.currentTimeMillis())
        }
        if (landscape) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                if (navigating) {
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) { TurnBlock(s, u) }
                    Spacer(Modifier.width(u * 0.08f))
                }
                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    SpeedBlock(s, u)
                    Spacer(Modifier.height(u * 0.05f))
                    HudText(footer, u * 0.075f, HudDim)
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = if (navigating) Arrangement.SpaceEvenly else Arrangement.Center
            ) {
                if (navigating) TurnBlock(s, u)
                SpeedBlock(s, u)
                HudText(footer, u * 0.075f, HudDim)
            }
        }
    }
}

/** Ближайший поворот: стрелка, расстояние, улица и полоска, которая укорачивается к повороту. */
@Composable
private fun TurnBlock(s: NavState, u: Dp) {
    val turn = s.turn
    val color = if (turn == null) HudWhite else signColor(turn.severity)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RallyArrow(severity = turn?.severity, isLeft = turn?.isLeft ?: false, boxSize = u * 0.38f, color = color)
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
                    if (turn.street.isNotEmpty()) HudText(turn.street, u * 0.065f, HudDim, maxWidth = u * 0.6f)
                }
            }
        }
        if (turn != null && turn.distanceM <= BAR_FROM_M) {
            Spacer(Modifier.height(u * 0.04f))
            val frac = (turn.distanceM / BAR_FROM_M).toFloat().coerceIn(0f, 1f)
            Box(
                Modifier
                    .width(u * 0.95f)
                    .height(u * 0.035f)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.16f))
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(frac)
                        .clip(RoundedCornerShape(50))
                        .background(color)
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
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            HudText(s.speedKmh.toString(), u * 0.48f, if (over) HudRed else HudWhite, bold = true, numbers = true)
            HudText("км/ч", u * 0.07f, HudDim)
        }
        if (limit != null) {
            Spacer(Modifier.width(u * 0.07f))
            Box(
                modifier = Modifier
                    .size(u * 0.26f)
                    .border(u * 0.03f, HudLimitRed, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                HudText(limit.toString(), if (limit >= 100) u * 0.085f else u * 0.105f, HudWhite, bold = true, numbers = true)
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
    maxWidth: Dp? = null
) {
    val fontSize: TextUnit = with(LocalDensity.current) { size.toSp() }
    Text(
        text = text,
        color = color,
        fontSize = fontSize,
        lineHeight = fontSize * 1.05f,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
        fontFamily = if (numbers) NumberFont else null,
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
