package ru.shturman.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.shturman.NavState
import ru.shturman.routing.Place
import ru.shturman.ui.fx.glassButton
import ru.shturman.ui.fx.mapButtonShape
import ru.shturman.ui.fx.panel
import ru.shturman.ui.fx.pressFx
import ru.shturman.ui.theme.ShColors
import ru.shturman.ui.theme.ShText
import ru.shturman.ui.themes.ThemeSound
import ru.shturman.ui.themes.ThemeSounds

/** Сообщение поверх карты. warn — янтарное предупреждение. */
class Pill(val text: String, val warn: Boolean, val onClick: (() -> Unit)? = null)

/** Где держать стрелку и какие поля оставлять, когда маршрут показан целиком. */
class MapInsets(
    val markerFromBottom: Dp = 0.dp,
    val markerFromLeft: Dp = 0.dp,
    val previewTop: Dp = 130.dp,
    val previewBottom: Dp = 330.dp,
    val previewLeft: Dp = 32.dp
)

/** Что делают кнопки главного экрана. */
class MainActions(
    val openSettings: () -> Unit,
    /** null — обычный поиск; "home" / "work" — задать адрес. */
    val openSearch: (String?) -> Unit,
    val voiceSearch: () -> Unit,
    val pickPlace: (Place) -> Unit,
    val clearRoute: () -> Unit,
    val toggleVoice: () -> Unit,
    val overview: () -> Unit,
    val recenter: () -> Unit,
    val setSatellite: (Boolean) -> Unit,
    val setMap3d: (Boolean) -> Unit,
    val openMusic: () -> Unit,
    val selectOption: (Int) -> Unit,
    val setDeparture: (Int) -> Unit,
    val start: () -> Unit
)

/**
 * Раскладка главного экрана в новом оформлении (макеты P01, P05, P06, P09). Только рисует по состоянию,
 * поэтому её можно снимать на поддельной карте. Высоты блоков меряются: ничто ни на что не налезает,
 * а стрелка на карте стоит над нижним блоком.
 */
@Composable
fun MainLayout(
    s: NavState,
    pills: List<Pill>,
    following: Boolean,
    a: MainActions,
    map: @Composable (MapInsets) -> Unit
) {
    val density = LocalDensity.current
    var navBottomH by remember { mutableStateOf(0.dp) }
    var maneuverH by remember { mutableStateOf(0.dp) }
    var panelH by remember { mutableStateOf(0.dp) }
    var mapBottomH by remember { mutableStateOf(0.dp) }
    var sideW by remember { mutableStateOf(0.dp) }
    fun Modifier.measureH(set: (Dp) -> Unit) = this.onSizeChanged { set(with(density) { it.height.toDp() }) }
    fun Modifier.measureW(set: (Dp) -> Unit) = this.onSizeChanged { set(with(density) { it.width.toDp() }) }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val route = s.route
    val navigating = s.navigating && route != null

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenH = maxHeight
        val landscape = maxWidth > maxHeight && maxWidth >= 560.dp
        val insets = when {
            navigating && landscape -> MapInsets(
                markerFromBottom = 96.dp + navBar, markerFromLeft = sideW + 12.dp,
                previewTop = statusTop + 24.dp, previewBottom = 24.dp + navBar, previewLeft = sideW + 36.dp
            )
            navigating -> MapInsets(
                markerFromBottom = navBottomH + navBar + 36.dp,
                previewTop = statusTop + maneuverH + 32.dp, previewBottom = navBottomH + navBar + 24.dp
            )
            s.options.isNotEmpty() -> MapInsets(previewTop = statusTop + 80.dp, previewBottom = panelH + 16.dp)
            else -> MapInsets()
        }
        map(insets)
        // Ночной экран: после заката карта темнее, чтобы не слепила (кнопки и панели — как были)
        if (s.night) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))

        if (navigating && route != null) {
            val total = route.totalM.coerceAtLeast(1.0)
            val traveled = (s.position?.traveledM ?: 0.0).coerceIn(0.0, total)
            val remainingSec = route.timeSec * (1.0 - traveled / total)
            val eta = formatClock(System.currentTimeMillis() + (remainingSec * 1000).toLong())
            val kmLeft = formatKm(total - traveled)
            val minLeft = formatDuration(remainingSec).uppercase()
            val over = s.limitKmh != null && s.speedKmh > s.limitKmh + ru.shturman.routing.OVERSPEED_TOLERANCE_KMH
            val buttons = navButtons(s, following, a)
            if (landscape) {
                NavLandscape(s, pills, buttons, eta, kmLeft, minLeft, over, a, Modifier.measureW { sideW = it })
            } else {
                // затемнение снизу: кольцо скорости и цифры читаются поверх любой карты
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(navBottomH + navBar + 90.dp)
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.5f to ShColors.Background.copy(alpha = 0.9f),
                                1f to ShColors.Background
                            )
                        )
                )
                Column(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = statusTop)
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    ManeuverPanel(s.turn, s.notes, Modifier.fillMaxWidth().measureH { maneuverH = it })
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ThenPill(s.notes)
                            s.curveAdviceKmh?.let { AdvicePill(it) }
                            pills.forEach { WarnChip(it.text, it.warn, onClick = it.onClick) }
                        }
                        Spacer(Modifier.width(8.dp))
                        // кнопок столько, сколько влезает между панелью поворота и нижним блоком
                        val avail = screenH - statusTop - 12.dp - maneuverH - 8.dp - navBar - navBottomH - 20.dp
                        val fit = ((avail + 8.dp) / (SportButtonSize + 8.dp)).toInt().coerceIn(1, buttons.size)
                        val keep = buttons.sortedBy { it.priority }.take(fit).toSet()
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            buttons.filter { it in keep }.forEachIndexed { i, b ->
                                GlassButton(b.icon, b.description, b.onClick, tint = if (b.accent) ShColors.Accent else ShColors.TextPrimary, index = i)
                            }
                        }
                    }
                }
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = navBar)
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                        .measureH { navBottomH = it }
                        .padding(start = 12.dp, end = 12.dp, bottom = 16.dp)
                ) {
                    MiniPlayer(onOpen = a.openMusic, modifier = Modifier.padding(bottom = 10.dp))
                    Box(Modifier.fillMaxWidth()) {
                        TripValue(eta, "ПРИБЫТИЕ", Modifier.align(Alignment.BottomStart).padding(start = 10.dp, bottom = 40.dp))
                        SpeedRing(s.speedKmh, s.limitKmh, over, Modifier.align(Alignment.BottomCenter))
                        TripValue(kmLeft, "КМ · $minLeft", Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 40.dp), alignEnd = true)
                    }
                }
            }
        } else if (s.options.isNotEmpty() || s.routeLoading || s.destination != null) {
            // Выбор маршрута (P06): «Назад» и шторка с вариантами
            GlassButton(
                ApexIcons.Back, "Назад", a.clearRoute,
                Modifier.align(Alignment.TopStart).padding(top = statusTop).padding(12.dp)
            )
            if (pills.isNotEmpty()) {
                Column(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(top = statusTop + 12.dp, start = 12.dp + SportButtonSize + 8.dp, end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) { pills.forEach { WarnChip(it.text, it.warn, onClick = it.onClick) } }
            }
            if (s.options.isNotEmpty()) {
                val weather = s.weather
                val chips = ArrayList<Pill>()
                if (weather != null) {
                    val extra = ((1.0 / weather.speedFactor - 1.0) * 100).toInt()
                    val ice = weather.tempC <= 3 && weather.label != "Гололёд"
                    val t = (if (weather.tempC < 0) "−" else "+") + "${kotlin.math.abs(weather.tempC)}°"
                    chips += Pill(
                        "$t · " + (if (ice) "возможен гололёд" else weather.label) + (if (extra > 0) " · дольше на $extra%" else ""),
                        warn = ice || extra > 0 || weather.label == "Гололёд"
                    )
                }
                s.seasonNote?.let { chips += Pill(it, warn = true) }
                s.departAdvice?.let { chips += Pill(it, warn = false) }
                RoutePanel(
                    options = s.options,
                    selected = s.selected,
                    departOffsetMin = s.departOffsetMin,
                    timeAware = s.timeAware,
                    destinationTitle = s.destination?.title ?: "",
                    destinationSubtitle = s.destination?.subtitle ?: "",
                    consumption = s.consumption,
                    fuelPrice = s.fuelPrice,
                    onSelect = a.selectOption,
                    onDepart = a.setDeparture,
                    onStart = a.start,
                    onClose = a.clearRoute,
                    chips = chips,
                    modifier = Modifier.align(Alignment.BottomCenter).measureH { panelH = it }
                )
            } else if (s.routeLoading) {
                RouteSkeleton(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = navBar)
                        .padding(12.dp)
                )
            }
        } else {
            MapModeLayout(s, pills, a, statusTop, navBar, mapBottomH, Modifier.measureH { mapBottomH = it })
        }
    }
}

/** Кнопка колонки в поездке. priority: чем меньше, тем важнее (на маленьком экране остаются важные). */
private class NavButton(
    val icon: ImageVector,
    val description: String,
    val priority: Int,
    val accent: Boolean = false,
    val onClick: () -> Unit
)

private fun navButtons(s: NavState, following: Boolean, a: MainActions): List<NavButton> = listOf(
    NavButton(ApexIcons.Menu, "Меню", 3, onClick = a.openSettings),
    NavButton(if (s.voiceOn) ApexIcons.Sound else ApexIcons.SoundOff, if (s.voiceOn) "Выключить голос" else "Включить голос", 1, onClick = a.toggleVoice),
    if (following) NavButton(ApexIcons.Overview, "Весь маршрут", 2, onClick = a.overview)
    else NavButton(ApexIcons.Locate, "Где я", 0, onClick = a.recenter),
    NavButton(ApexIcons.Close, "Завершить маршрут", 1, accent = true, onClick = a.clearRoute)
)

/** Поездка горизонтально (P09): слева стеклянная колонка, справа снизу — кольцо скорости. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.NavLandscape(
    s: NavState,
    pills: List<Pill>,
    buttons: List<NavButton>,
    eta: String,
    kmLeft: String,
    minLeft: String,
    over: Boolean,
    a: MainActions,
    measure: Modifier
) {
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Column(
            Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .then(measure)
                .width(300.dp)
                .fillMaxHeight()
                .panel(RoundedCornerShape(14.dp), elevation = 8.dp)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ManeuverPanel(s.turn, s.notes, framed = false, compact = true)
            ThenPill(s.notes, framed = false)
            s.curveAdviceKmh?.let { AdvicePill(it) }
            pills.take(2).forEach { WarnChip(it.text, it.warn, onClick = it.onClick) }
            Box(Modifier.fillMaxWidth().height(1.dp).background(ShColors.GlassEdge))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                TripValue(eta, "ПРИБЫТИЕ", Modifier.weight(1f))
                TripValue(kmLeft, "КМ · $minLeft", alignEnd = true)
            }
            Spacer(Modifier.weight(1f))
            MiniPlayer(onOpen = a.openMusic)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                buttons.forEach { b ->
                    GlassKey(b.icon, b.description, b.onClick, Modifier.weight(1f), tint = if (b.accent) ShColors.Accent else ShColors.TextPrimary)
                }
            }
        }
        SpeedRing(s.speedKmh, s.limitKmh, over, Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 16.dp), size = 124.dp)
    }
}

/** Плоская стеклянная клавиша во всю ширину ячейки (кнопки внизу колонки в горизонтальном экране). */
@Composable
private fun GlassKey(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier, tint: Color) {
    val source = remember { MutableInteractionSource() }
    Box(
        modifier
            .height(48.dp)
            .pressFx()
            .glassButton(mapButtonShape(), elevation = 0.dp)
            .clickable(interactionSource = source, indication = null) { ThemeSounds.play(ThemeSound.TAP); onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/** Карта без маршрута (P05): меню, погода, слои и «Где я», скорость, «Дом»/«Работа» и строка поиска. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.MapModeLayout(
    s: NavState,
    pills: List<Pill>,
    a: MainActions,
    statusTop: Dp,
    navBar: Dp,
    bottomH: Dp,
    measure: Modifier
) {
    GlassButton(ApexIcons.Menu, "Меню", a.openSettings, Modifier.align(Alignment.TopStart).padding(top = statusTop).padding(12.dp))
    s.hud.weather?.let { w ->
        WeatherChip(w.tempC, w.label, Modifier.align(Alignment.TopEnd).padding(top = statusTop).padding(12.dp))
    }
    if (pills.isNotEmpty()) {
        Column(
            Modifier
                .align(Alignment.TopStart)
                .padding(top = statusTop + 12.dp + SportButtonSize + 8.dp, start = 12.dp, end = 12.dp)
                .widthIn(max = 520.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) { pills.forEach { WarnChip(it.text, it.warn, onClick = it.onClick) } }
    }
    // справа над карточками: слои карты и «Где я»
    var layers by remember { mutableStateOf(false) }
    Column(
        Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = navBar + bottomH + 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box {
            GlassButton(ApexIcons.Layers, "Слои карты", { layers = true }, index = 4)
            DropdownMenu(
                expanded = layers,
                onDismissRequest = { layers = false },
                containerColor = ShColors.T.panel
            ) {
                LayerRow("Спутник", s.satellite) { a.setSatellite(it) }
                LayerRow("3D-карта", s.map3d) { a.setMap3d(it) }
            }
        }
        GlassButton(ApexIcons.Locate, "Где я", a.recenter, index = 5)
    }
    if (s.speedKmh >= 5) {
        SpeedBubble(s.speedKmh, Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = navBar + bottomH + 12.dp))
    }
    Column(
        Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = navBar)
            .then(measure)
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val home = s.home
            val work = s.work
            PlaceCard(
                ApexIcons.Home, "ДОМ", home?.title ?: "Задать",
                onClick = { if (home != null) a.pickPlace(home) else a.openSearch("home") },
                onLongClick = { a.openSearch("home") },
                modifier = Modifier.weight(1f), index = 1
            )
            PlaceCard(
                ApexIcons.Work, "РАБОТА", work?.title ?: "Задать",
                onClick = { if (work != null) a.pickPlace(work) else a.openSearch("work") },
                onLongClick = { a.openSearch("work") },
                modifier = Modifier.weight(1f), index = 2
            )
        }
        SearchBar(onSearch = { a.openSearch(null) }, onMic = a.voiceSearch)
    }
}

/** Строка меню «Слои»: название и переключатель. */
@Composable
private fun LayerRow(title: String, on: Boolean, onChange: (Boolean) -> Unit) {
    DropdownMenuItem(
        text = { Text(title, color = ShColors.TextPrimary, style = ShText.Body) },
        trailingIcon = {
            Switch(
                checked = on,
                onCheckedChange = onChange,
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ShColors.Accent)
            )
        },
        onClick = { onChange(!on) }
    )
}
