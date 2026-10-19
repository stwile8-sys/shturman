package ru.shturman.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.shturman.routing.RouteOption
import ru.shturman.routing.fuelLiters
import ru.shturman.routing.fuelText
import ru.shturman.ui.fx.GlowButton
import ru.shturman.ui.fx.GrabHandle
import ru.shturman.ui.fx.pressFx
import ru.shturman.ui.theme.ShColors
import ru.shturman.ui.theme.ShText
import ru.shturman.ui.themes.ThemeSound
import ru.shturman.ui.themes.ThemeSounds
import ru.shturman.ui.themes.themedEnter

/** Варианты времени выезда: минуты от «сейчас» и подпись. */
private val DEPARTS = listOf(0 to "Сейчас", 15 to "+15 мин", 30 to "+30 мин", 60 to "+1 час", 120 to "+2 часа")

/**
 * Выбор маршрута (макет P06): шторка снизу — адрес, время выезда и погода, варианты строками
 * (выбранный — в рамке цветом темы), справа км, прибытие и бензин, внизу кнопка «Поехали».
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoutePanel(
    options: List<RouteOption>,
    selected: Int,
    departOffsetMin: Int,
    timeAware: Boolean,
    destinationTitle: String,
    consumption: Float,
    fuelPrice: Float,
    onSelect: (Int) -> Unit,
    onDepart: (Int) -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
    destinationSubtitle: String = "",
    chips: List<Pill> = emptyList()
) {
    val departMillis = System.currentTimeMillis() + departOffsetMin * 60_000L
    val screenH = LocalConfiguration.current.screenHeightDp
    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val sheet = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)

    Column(
        modifier = modifier
            .themedEnter()
            .widthIn(max = 600.dp)
            .fillMaxWidth()
            .clip(sheet)
            .background(lerp(ShColors.T.panel, Color.White, 0.012f))
            .border(1.dp, ShColors.GlassEdge, sheet)
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp + navBar)
    ) {
        GrabHandle(Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(destinationTitle, color = ShColors.TextPrimary, style = ShText.Title.copy(fontSize = 20.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    destinationSubtitle.ifEmpty {
                        "${options.size} ${if (options.size == 1) "вариант" else if (options.size in 2..4) "варианта" else "вариантов"} · с учётом светофоров"
                    },
                    color = ShColors.TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            if (onClose != null) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .pressFx()
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, ShColors.Divider, RoundedCornerShape(10.dp))
                        .clickable { ThemeSounds.play(ThemeSound.TAP); onClose() },
                    contentAlignment = Alignment.Center
                ) { Icon(ApexIcons.Close, contentDescription = "Отменить маршрут", tint = ShColors.TextPrimary, modifier = Modifier.size(16.dp)) }
            }
        }
        Spacer(Modifier.height(12.dp))

        // Время выезда (меню) и погода / советы
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box {
                var open by remember { mutableStateOf(false) }
                val label = DEPARTS.firstOrNull { it.first == departOffsetMin }?.second ?: "Сейчас"
                Row(
                    Modifier
                        .height(34.dp)
                        .pressFx()
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, ShColors.Divider, RoundedCornerShape(8.dp))
                        .clickable { ThemeSounds.play(ThemeSound.TAP); open = true }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("ВЫЕЗД: ${label.uppercase()}", color = ShColors.TextPrimary, style = ShText.Caps.copy(fontSize = 11.sp, letterSpacing = 1.5.sp))
                    Spacer(Modifier.size(6.dp))
                    Icon(ApexIcons.ChevronDown, contentDescription = null, tint = ShColors.TextPrimary, modifier = Modifier.size(12.dp))
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = ShColors.T.panel) {
                    DEPARTS.forEach { (offset, text) ->
                        DropdownMenuItem(
                            text = { Text(text, color = if (offset == departOffsetMin) ShColors.Accent else ShColors.TextPrimary, style = ShText.Body) },
                            onClick = { open = false; onDepart(offset) }
                        )
                    }
                }
            }
            chips.forEach { c ->
                Box(
                    Modifier
                        .heightIn(min = 34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, if (c.warn) ShColors.WarningEdge else ShColors.Divider, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        if (c.text.length <= 40) c.text.uppercase() else c.text,
                        color = if (c.warn) ShColors.Warning else ShColors.TextSecondary,
                        style = if (c.text.length <= 40) ShText.Caps.copy(fontSize = 11.sp, letterSpacing = 1.sp) else ShText.Secondary
                    )
                }
            }
        }
        if (departOffsetMin > 0 && !timeAware) {
            Text(
                "Время выезда движок не учёл, маршрут построен по обычным скоростям.",
                color = ShColors.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)
            )
        }
        Spacer(Modifier.height(12.dp))

        // Варианты маршрута
        Column(
            modifier = Modifier
                .heightIn(max = (screenH * 0.34f).dp.coerceAtLeast(120.dp))
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            options.forEachIndexed { i, option ->
                val arrival = formatClock(departMillis + (option.timeSec * 1000).toLong())
                val fuel = fuelText(fuelLiters(option.lengthM, option.timeSec, consumption.toDouble()), fuelPrice.toDouble())
                OptionRow(
                    index = i,
                    name = option.label.ifEmpty { "Вариант ${i + 1}" },
                    time = formatDuration(option.timeSec),
                    meta1 = "${formatDistance(option.totalM)} · ${fuel.substringAfter("· ")}",
                    meta2 = arrival,
                    selected = i == selected,
                    onClick = { onSelect(i) }
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        GlowButton(text = "Поехали", onClick = onStart, burst = 2.6f, height = 56.dp, modifier = Modifier.fillMaxWidth())
    }
}

/** Строка варианта: кружок выбора, название капсом, время крупно, справа расстояние, бензин и прибытие. */
@Composable
private fun OptionRow(index: Int, name: String, time: String, meta1: String, meta2: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    val edge by animateColorAsState(if (selected) ShColors.Accent else ShColors.Divider, label = "edge")
    val bg by animateColorAsState(if (selected) ShColors.Accent.copy(alpha = 0.08f) else Color.Transparent, label = "bg")
    Row(
        modifier = Modifier
            .themedEnter(index + 1)
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .pressFx()
            .clip(shape)
            .background(bg)
            .border(1.dp, edge, shape)
            .clickable { ThemeSounds.play(ThemeSound.TAP); onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(14.dp)
                .border(if (selected) 4.dp else 1.5.dp, if (selected) ShColors.Accent else Color(0xFF5A5A5E), CircleShape)
        )
        Text(
            name.uppercase(),
            color = if (selected) ShColors.TextPrimary else Color(0xFFD0D0D4),
            style = ShText.Caps.copy(letterSpacing = 2.sp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(time, color = ShColors.TextPrimary, style = ShText.Value.copy(fontSize = 22.sp, lineHeight = 26.sp), maxLines = 1)
        Column(horizontalAlignment = Alignment.End) {
            Text(meta1, color = ShColors.TextSecondary, fontSize = 11.sp, maxLines = 1)
            Text(meta2, color = ShColors.TextSecondary, fontSize = 11.sp, maxLines = 1)
        }
    }
}
