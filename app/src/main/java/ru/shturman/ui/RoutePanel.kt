package ru.shturman.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.shturman.routing.RouteOption
import ru.shturman.routing.fuelLiters
import ru.shturman.routing.fuelText
import ru.shturman.ui.fx.GlowButton
import ru.shturman.ui.fx.GrabHandle
import ru.shturman.ui.fx.chipShape
import ru.shturman.ui.fx.choiceChipShape
import ru.shturman.ui.fx.closeButtonShape
import ru.shturman.ui.fx.panel
import ru.shturman.ui.fx.panelShape
import ru.shturman.ui.fx.pressFx
import ru.shturman.ui.theme.ShColors
import ru.shturman.ui.theme.ShText
import ru.shturman.ui.themes.Flavor
import ru.shturman.ui.themes.ThemeSound
import ru.shturman.ui.themes.ThemeSounds
import ru.shturman.ui.themes.themedEnter

/** Цвета полос «таблицы позиций» у тем с телеметрией: фиолетовый — лучший, зелёный, жёлтый. */
private val TowerColors = listOf(Color(0xFFB54BFF), Color(0xFF00E04A), Color(0xFFFFD400), Color(0xFF3AA0FF))

/**
 * Панель выбора маршрута: стеклянная шторка снизу, варианты карточками (у телеметрии — таблица позиций
 * P1/P2/P3), время выезда, кнопка «Поехали» со вспышкой частиц. В альбомной ориентации не растягивается на всю ширину.
 */
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
    onClose: (() -> Unit)? = null
) {
    val l = ShColors.L
    val departMillis = System.currentTimeMillis() + departOffsetMin * 60_000L
    val screenH = LocalConfiguration.current.screenHeightDp
    val fastest = options.minOfOrNull { it.timeSec } ?: 0.0

    Column(
        modifier = modifier
            .themedEnter()
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .panel(panelShape(34.dp), elevation = 22.dp)
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 16.dp)
    ) {
        GrabHandle(Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(text = destinationTitle, color = ShColors.TextPrimary, style = ShText.Button.copy(fontSize = 18.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = "${options.size} ${if (options.size == 1) "вариант" else if (options.size in 2..4) "варианта" else "вариантов"} · с учётом светофоров",
                    color = ShColors.TextSecondary, fontSize = 13.sp
                )
            }
            if (onClose != null) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .pressFx()
                        .clip(closeButtonShape())
                        .background(Color.White.copy(alpha = 0.09f))
                        .clickable { ThemeSounds.play(ThemeSound.TAP); onClose() },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Close, contentDescription = "Отменить маршрут", tint = ShColors.TextPrimary, modifier = Modifier.size(20.dp)) }
            }
        }
        Spacer(Modifier.height(12.dp))

        // Варианты маршрута
        Column(
            modifier = Modifier
                .heightIn(max = (screenH * 0.36f).dp.coerceAtLeast(130.dp))
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(if (l.flavor == Flavor.TELEMETRY) 5.dp else 8.dp)
        ) {
            options.forEachIndexed { i, option ->
                val arrival = formatClock(departMillis + (option.timeSec * 1000).toLong())
                val fuel = fuelText(fuelLiters(option.lengthM, option.timeSec, consumption.toDouble()), fuelPrice.toDouble())
                val name = option.label.ifEmpty { "Вариант ${i + 1}" }
                val meta = "${formatDistance(option.totalM)} · прибытие $arrival · $fuel"
                if (l.flavor == Flavor.TELEMETRY) {
                    val diffMin = ((option.timeSec - fastest) / 60).toInt()
                    TowerRow(
                        index = i, pos = "P${i + 1}", name = name, meta = meta,
                        time = if (diffMin <= 0) formatDuration(option.timeSec) else "+$diffMin мин",
                        selected = i == selected, onClick = { onSelect(i) }
                    )
                } else {
                    OptionCard(index = i, time = formatDuration(option.timeSec), name = name, meta = meta, selected = i == selected, onClick = { onSelect(i) })
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Время выезда
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(0 to "Сейчас", 15 to "+15 мин", 30 to "+30 мин", 60 to "+1 час", 120 to "+2 часа").forEach { (offset, label) ->
                DepartChip(label = label, selected = departOffsetMin == offset, onClick = { onDepart(offset) })
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (timeAware) "Маршрут рассчитан на выбранное время выезда." else "Время выезда движок не учёл, маршрут построен по обычным скоростям.",
            color = ShColors.TextSecondary,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(12.dp))
        GlowButton(text = "Поехали", onClick = onStart, burst = 2.6f, modifier = Modifier.fillMaxWidth())
    }
}

/** Карточка варианта: крупное время, название, подробности и «радиокнопка». */
@Composable
private fun OptionCard(index: Int, time: String, name: String, meta: String, selected: Boolean, onClick: () -> Unit) {
    val shape = chipShape()
    val border by animateColorAsState(if (selected) ShColors.Accent else Color.White.copy(alpha = 0.08f), label = "b")
    val bg by animateColorAsState(if (selected) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.05f), label = "bg")
    Row(
        modifier = Modifier
            .themedEnter(index + 1)
            .fillMaxWidth()
            .pressFx()
            .shadow(if (selected) 12.dp else 0.dp, shape, clip = false, ambientColor = ShColors.L.glow, spotColor = ShColors.L.glow)
            .clip(shape)
            .background(bg)
            .border(if (selected) 1.5.dp else 1.dp, border, shape)
            .clickable { ThemeSounds.play(ThemeSound.TAP); onClick() }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = time,
            color = if (selected) ShColors.Accent else ShColors.TextPrimary,
            style = ShText.Distance.copy(fontSize = 20.sp),
            modifier = Modifier.widthIn(min = 84.dp)
        )
        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Text(text = name, color = ShColors.TextPrimary, style = ShText.Small.copy(fontSize = 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = meta, color = ShColors.TextSecondary, fontSize = 12.sp, maxLines = 2)
        }
        Box(
            Modifier.size(20.dp).clip(CircleShape).border(2.dp, if (selected) ShColors.Accent else Color.White.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(ShColors.Accent))
        }
    }
}

/** Строка «таблицы позиций» для тем с телеметрией. */
@Composable
private fun TowerRow(index: Int, pos: String, name: String, meta: String, time: String, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) Color.White.copy(alpha = 0.13f) else Color.White.copy(alpha = 0.05f), label = "bg")
    Row(
        modifier = Modifier
            .themedEnter(index + 1)
            .fillMaxWidth()
            .height(60.dp)
            .pressFx()
            .clip(CutCornerShape(topEnd = 10.dp))
            .background(bg)
            .clickable { ThemeSounds.play(ThemeSound.TAP); onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.width(46.dp).fillMaxHeight().background(if (selected) ShColors.Accent else Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) { Text(pos, color = if (selected) ShColors.OnAccent else ShColors.TextPrimary, style = ShText.Distance.copy(fontSize = 18.sp)) }
        Box(Modifier.width(5.dp).fillMaxHeight().background(TowerColors[index % TowerColors.size]))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(text = name.uppercase(), color = ShColors.TextPrimary, style = ShText.Small.copy(fontSize = 14.sp, letterSpacing = 0.6.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = meta, color = ShColors.TextSecondary, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(time, color = if (selected) ShColors.Accent else ShColors.TextPrimary, style = ShText.Distance.copy(fontSize = 18.sp), modifier = Modifier.padding(end = 14.dp))
    }
}

/** Плашка времени выезда: тонкая обводка, выбранная подсвечена цветом темы. */
@Composable
private fun DepartChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = choiceChipShape()
    val acc = ShColors.Accent
    val bg by animateColorAsState(if (selected) acc.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.06f), label = "chipBg")
    val edge by animateColorAsState(if (selected) acc else Color.White.copy(alpha = 0.12f), label = "chipEdge")
    Box(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .pressFx()
            .clip(shape)
            .background(bg)
            .border(1.dp, edge, shape)
            .clickable { ThemeSounds.play(ThemeSound.TAP); onClick() }
            .padding(horizontal = 15.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) acc else ShColors.TextSecondary, style = ShText.Small.copy(fontSize = 13.sp))
    }
}
