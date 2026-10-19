package ru.shturman.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.shturman.routing.RoadStats
import ru.shturman.routing.SharedSync
import ru.shturman.routing.SpeedStore
import ru.shturman.routing.bestHourText
import ru.shturman.routing.computeRoadStats
import ru.shturman.ui.fx.GlowButton
import ru.shturman.ui.theme.ShColors
import ru.shturman.ui.theme.ShText
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/** Цвета скорости на «карте знаний»: красный — еле едут, зелёный — свободно. */
private val Slow = Color(0xFFE0313A)
private val Mid = Color(0xFFF28C28)
private val Fair = Color(0xFFF2C14E)
private val Free = Color(0xFF4CC38A)

private fun speedColor(kmh: Double): Color = when {
    kmh < 15 -> Slow
    kmh < 30 -> Mid
    kmh < 50 -> Fair
    else -> Free
}

/** Что показать про сбор и обмен (вне сводки базы). */
class CollectInfo(
    val lastSyncText: String,
    val lastResult: String?,
    val background: String,
    val dangerSpots: Int
)

/**
 * Окно только для владельца: насколько хорошо собраны данные о пробках и светофорах — оценкой, картинкой и словами.
 * Считается по базе на этом телефоне (в ней после синхронизации данные всех водителей).
 */
@Composable
fun RoadDataScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var stats by remember { mutableStateOf<RoadStats?>(null) }
    var info by remember { mutableStateOf<CollectInfo?>(null) }
    var syncing by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }
    BackHandler(onBack = onClose)

    LaunchedEffect(reloadKey) {
        val store = SpeedStore.shared(context)
        val now = System.currentTimeMillis()
        stats = withContext(Dispatchers.IO) { runCatching { computeRoadStats(store.allCells(), store.allStops(), now) }.getOrNull() }
        val sync = SharedSync(context, store)
        val last = sync.lastSyncAt
        info = CollectInfo(
            lastSyncText = if (last == 0L) "ещё не было" else agoText(now - last),
            lastResult = sync.lastResult,
            background = backgroundText(context),
            dangerSpots = withContext(Dispatchers.IO) { store.dangerCount(2) }
        )
    }

    RoadDataContent(
        stats = stats,
        info = info,
        syncing = syncing,
        onSync = {
            syncing = true
            scope.launch {
                // обмен идёт не в главном потоке (sync сам уходит в фон), экран не замирает
                runCatching { SharedSync(context, SpeedStore.shared(context)).sync() }
                syncing = false
                reloadKey++
            }
        },
        onClose = onClose
    )
}

/** Само окно по готовым цифрам (без базы) — так его можно снять в тестах. */
@Composable
fun RoadDataContent(stats: RoadStats?, info: CollectInfo?, syncing: Boolean, onSync: () -> Unit, onClose: () -> Unit) {
    // касания не проходят сквозь окно на карту под ним
    Box(Modifier.fillMaxSize().background(ShColors.Background).pointerInput(Unit) {}) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassButton(ApexIcons.Back, "Назад", onClose)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Данные о дорогах", color = ShColors.TextPrimary, style = ShText.Title)
                    Text("видно только вам", color = ShColors.TextSecondary, style = ShText.Secondary)
                }
            }
            if (stats == null) {
                LoadingLine("Считаю…", Modifier.padding(8.dp))
                return@Column
            }
            ScoreCard(stats)
            Header("КАРТА ЗНАНИЙ")
            Text(
                "Каждая точка — участок дороги около 130 м, где мы знаем скорость. Цвет — как обычно едут: " +
                    "красный — еле ползут, жёлтый — средне, зелёный — свободно. Белые точки — светофоры и места, где стоят.",
                color = ShColors.TextSecondary, style = ShText.Secondary
            )
            KnowledgeMap(stats)
            Legend()
            Header("КОГДА ЗНАЕМ ПРОБКИ")
            Text(
                "Строки — дни недели, столбцы — часы. Чем ярче клетка, тем больше участков знаем в этот час.",
                color = ShColors.TextSecondary, style = ShText.Secondary
            )
            HoursStrip(stats.hours)
            Header("ЦИФРЫ")
            Numbers(stats, info)
            Header("СБОР И ОБМЕН")
            if (info != null) {
                NumberRow("Последний обмен с общей базой", info.lastSyncText)
                info.lastResult?.let { NumberRow("Итог обмена", it) }
                Text(info.background, color = ShColors.TextSecondary, style = ShText.Secondary)
            }
            GlowButton(
                text = if (syncing) "Обмениваюсь…" else "Обменяться сейчас",
                onClick = onSync, enabled = !syncing, burst = 0f, height = 52.dp,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "Всё обезличено: участок карты, час недели и скорость или время ожидания. Имён, аккаунтов и маршрутов в базе нет.",
                color = ShColors.Muted, style = ShText.Secondary, modifier = Modifier.padding(bottom = 12.dp)
            )
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(text, color = ShColors.Muted, style = ShText.Caps, modifier = Modifier.padding(top = 8.dp))
}

/** Оценка готовности прогноза пробок: кольцо 0–100, слово и одна-две фразы. */
@Composable
private fun ScoreCard(s: RoadStats) {
    val shape = RoundedCornerShape(14.dp)
    val color = when {
        s.score >= 70 -> Free
        s.score >= 40 -> Fair
        else -> Slow
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ShColors.SearchField)
            .border(1.dp, ShColors.GlassEdge, shape)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
            val track = ShColors.Divider
            Canvas(Modifier.size(96.dp)) {
                val r = size.minDimension / 2f - 4.dp.toPx()
                drawCircle(track, r, center, style = Stroke(3.dp.toPx()))
                drawArc(
                    color, -90f, 360f * s.score / 100f, false,
                    Offset(center.x - r, center.y - r), Size(2 * r, 2 * r), style = Stroke(4.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${s.score}", color = ShColors.TextPrimary, style = ShText.Hero.copy(fontSize = 34.sp, lineHeight = 36.sp, letterSpacing = 0.sp))
                Text("ИЗ 100", color = ShColors.TextSecondary, style = ShText.Caps.copy(fontSize = 8.sp))
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(s.verdict.uppercase(), color = color, style = ShText.Caps.copy(fontSize = 13.sp, letterSpacing = 2.sp))
            Spacer(Modifier.height(4.dp))
            Text(summary(s), color = ShColors.TextPrimary, style = ShText.Body.copy(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 19.sp))
        }
    }
}

/** Пара фраз простыми словами: сколько знаем и насколько этому можно верить. */
private fun summary(s: RoadStats): String {
    if (s.places == 0) return "Данных о дорогах пока нет. Они появятся после первых поездок и обмена с общей базой."
    val usePct = if (s.records == 0) 0 else s.usable * 100 / s.records
    return "Знаем скорость на ${s.places} участках (≈${s.roadKm} км дорог). " +
        "Для прогноза пробок годится $usePct% записей — там проехали хотя бы дважды за последние 4 недели."
}

/** Картинка «карта знаний»: точки участков по координатам, цвет — обычная скорость, белые — остановки. */
@Composable
private fun KnowledgeMap(s: RoadStats) {
    val shape = RoundedCornerShape(14.dp)
    val bg = ShColors.SearchField
    Box(
        Modifier
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(shape)
            .background(bg)
            .border(1.dp, ShColors.GlassEdge, shape),
        contentAlignment = Alignment.Center
    ) {
        if (s.spots.isEmpty()) {
            Text("Пока пусто", color = ShColors.TextSecondary, style = ShText.Secondary)
            return@Box
        }
        Canvas(Modifier.fillMaxSize().padding(12.dp)) {
            // рамка по 2-му и 98-му процентилю, чтобы одиночные дальние точки не сжимали город в пятнышко
            val lats = s.spots.map { it.position.lat }.sorted()
            val lons = s.spots.map { it.position.lon }.sorted()
            fun q(list: List<Double>, p: Double) = list[((list.size - 1) * p).toInt().coerceIn(0, list.size - 1)]
            val minLat = q(lats, 0.02) - 0.002
            val maxLat = q(lats, 0.98) + 0.002
            val minLon = q(lons, 0.02) - 0.004
            val maxLon = q(lons, 0.98) + 0.004
            val k = cos(Math.toRadians((minLat + maxLat) / 2))
            val wDeg = (maxLon - minLon) * k
            val hDeg = maxLat - minLat
            val scale = min(size.width / max(wDeg, 1e-6), size.height / max(hDeg, 1e-6)).toFloat()
            val ox = (size.width - (wDeg * scale).toFloat()) / 2f
            val oy = (size.height - (hDeg * scale).toFloat()) / 2f
            fun p(lat: Double, lon: Double) = Offset(
                ox + ((lon - minLon) * k * scale).toFloat(),
                oy + ((maxLat - lat) * scale).toFloat()
            )
            // размер точки — примерно размер участка на картинке, но не меньше 2 dp
            val cell = max((0.0012 * scale).toFloat() * 0.9f, 2.dp.toPx())
            for (spot in s.spots) {
                val c = p(spot.position.lat, spot.position.lon)
                if (c.x < -cell || c.y < -cell || c.x > size.width + cell || c.y > size.height + cell) continue
                val alpha = (0.45f + 0.55f * (spot.passes / 10f)).coerceIn(0.45f, 1f)
                drawRoundRect(
                    speedColor(spot.kmh).copy(alpha = alpha),
                    Offset(c.x - cell / 2, c.y - cell / 2), Size(cell, cell), CornerRadius(cell * 0.25f)
                )
            }
            val dot = max(cell * 0.35f, 1.5.dp.toPx())
            for (st in s.stops) drawCircle(Color.White.copy(alpha = 0.85f), dot, p(st.lat, st.lon))
        }
    }
}

@Composable
private fun Legend() {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        for ((c, t) in listOf(Slow to "до 15", Mid to "15–30", Fair to "30–50", Free to "50+")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(c))
                Spacer(Modifier.width(5.dp))
                Text(t, color = ShColors.TextSecondary, style = ShText.Secondary)
            }
        }
        Text("км/ч", color = ShColors.Muted, style = ShText.Secondary)
    }
}

/** Неделя по часам: 7 строк (пн–вс) × 24 столбца; яркость — сколько участков знаем в этот час. */
@Composable
private fun HoursStrip(hours: IntArray) {
    val names = listOf("ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ", "ВС")
    // в базе неделя начинается с воскресенья (0); показываем с понедельника
    val order = listOf(1, 2, 3, 4, 5, 6, 0)
    val maxV = (hours.maxOrNull() ?: 0).coerceAtLeast(1)
    val accent = ShColors.Accent
    val empty = Color.White.copy(alpha = 0.05f)
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        for ((row, day) in order.withIndex()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(names[row], color = ShColors.TextSecondary, style = ShText.Caps.copy(fontSize = 9.sp, letterSpacing = 1.sp), modifier = Modifier.width(26.dp))
                Canvas(Modifier.weight(1f).height(14.dp)) {
                    val gap = 2.dp.toPx()
                    val w = (size.width - gap * 23) / 24f
                    for (h in 0 until 24) {
                        val v = hours[day * 24 + h]
                        val k = v.toFloat() / maxV
                        val col = if (v == 0) empty else accent.copy(alpha = 0.18f + 0.82f * k)
                        drawRoundRect(col, Offset(h * (w + gap), 0f), Size(w, size.height), CornerRadius(2.dp.toPx()))
                    }
                }
            }
        }
        Row {
            Spacer(Modifier.width(26.dp))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
                for (t in listOf("0", "6", "12", "18", "24")) Text(t, color = ShColors.Muted, style = ShText.Secondary.copy(fontSize = 10.sp))
            }
        }
    }
}

@Composable
private fun Numbers(s: RoadStats, info: CollectInfo?) {
    val usePct = if (s.records == 0) 0 else s.usable * 100 / s.records
    NumberRow("Участков дорог со скоростью", "${s.places} (≈${s.roadKm} км)")
    NumberRow("Записей «участок + час недели»", "${s.records}")
    NumberRow("Годится для прогноза", "$usePct%")
    NumberRow("Обновлено за сутки / неделю", "${s.fresh24h} / ${s.fresh7d} участков")
    bestHourText(s.hours)?.let { NumberRow("Лучше всего знаем", it) }
    NumberRow("Светофоров и мест, где стоят", "${s.stopPlaces}")
    if (s.stopPlaces > 0) NumberRow("Обычно стоят", "${s.avgWaitSec} с")
    info?.let { NumberRow("Мест резкого торможения", "${it.dangerSpots}") }
}

@Composable
private fun NumberRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = ShColors.TextSecondary, style = ShText.Secondary.copy(fontSize = 13.sp), modifier = Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        Text(value, color = ShColors.TextPrimary, style = ShText.Body.copy(fontSize = 14.sp))
    }
}

private fun agoText(ms: Long): String {
    val min = ms / 60_000
    return when {
        min < 1 -> "только что"
        min < 60 -> "$min мин назад"
        min < 48 * 60 -> "${min / 60} ч назад"
        else -> "${min / (24 * 60)} дн назад"
    }
}

/** Состояние фонового сбора на этом телефоне простыми словами. */
private fun backgroundText(ctx: android.content.Context): String {
    val dw = ru.shturman.service.DriveWatch
    val state = if (dw.running) "Фоновый сбор на этом телефоне: ${dw.mode}" else "Фоновый сбор на этом телефоне выключен"
    val last = if (dw.lastDriveAt == 0L) "" else ", последняя поездка ${agoText(System.currentTimeMillis() - dw.lastDriveAt)}"
    val perm = if (dw.hasBackground(ctx)) "" else ". Нет разрешения на местоположение «всегда»"
    return "$state$last$perm. Обмен с общей базой — сам раз в 2 часа, когда есть интернет."
}
