package ru.shturman.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.shturman.NavViewModel
import ru.shturman.auth.UserInfo
import ru.shturman.ui.theme.ShColors
import ru.shturman.ui.theme.ShText

/**
 * Главный экран: карта на весь экран.
 * Слева сверху — кнопки (меню, поиск), справа сверху — ближайший поворот,
 * снизу — выбор маршрута или сколько проехали.
 */
@Composable
fun MainScreen(user: UserInfo, onSignOut: () -> Unit, vm: NavViewModel = viewModel()) {
    val s by vm.state.collectAsState()
    var showSettings by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var recenterKey by remember { mutableStateOf(0) }
    var startKey by remember { mutableStateOf(0) }

    val route = s.route
    val total = (route?.totalM ?: 0.0).coerceAtLeast(1.0)
    val traveled = (s.position?.traveledM ?: 0.0).coerceIn(0.0, total)
    val progress = (traveled / total).toFloat()
    val arrived = s.navigating && route != null && progress >= 0.985f

    // Приехали: убираем маршрут
    LaunchedEffect(arrived) {
        if (arrived) vm.clearRoute()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ShColors.Background)
    ) {
        DarkMap(
            modifier = Modifier.fillMaxSize(),
            recenterKey = recenterKey,
            startKey = startKey,
            navigating = s.navigating,
            route = route?.points,
            destination = s.destination?.position,
            onFix = { here, _ -> vm.onFix(here) }
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MapButton(
                icon = Icons.Filled.Menu,
                description = "Меню",
                onClick = { showSettings = true }
            )
            MapButton(
                icon = Icons.Filled.Search,
                description = "Куда едем?",
                onClick = { showSearch = true }
            )
            if (s.destination != null) {
                MapButton(
                    icon = Icons.Filled.Close,
                    description = "Отменить маршрут",
                    onClick = { vm.clearRoute() }
                )
            }
        }

        // Ближайший поворот
        val turn = s.turn
        if (s.navigating && turn != null) {
            TurnCard(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(12.dp),
                severity = turn.severity,
                isLeft = turn.isLeft,
                distance = formatDistance(turn.distanceM),
                street = turn.street
            )
        }

        // Сообщения: загрузка данных, построение маршрута, ошибки
        val message: Pair<String, Color>? = when {
            s.routeError != null -> s.routeError!! to ShColors.Danger
            s.routeLoading -> "Строю маршрут…" to ShColors.TextPrimary
            !s.dataReady && s.dataError != null -> (s.dataError!! + " Нажмите, чтобы повторить.") to ShColors.Danger
            !s.dataReady -> "Загружаю дорожные данные: ${(s.dataProgress * 100).toInt()}%" to ShColors.TextPrimary
            else -> null
        }
        if (message != null && !s.navigating) {
            StatusPill(
                text = message.first,
                color = message.second,
                onClick = if (!s.dataReady && s.dataError != null) ({ vm.downloadDataIfNeeded() }) else null,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 72.dp, top = 12.dp, end = 12.dp)
            )
        }

        val bottomPadding = when {
            s.navigating -> 112.dp
            s.options.isNotEmpty() -> 300.dp
            else -> 24.dp
        }
        MapButton(
            icon = Icons.Filled.LocationOn,
            description = "Где я",
            onClick = { recenterKey++ },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 12.dp, bottom = bottomPadding)
        )

        if (s.navigating && route != null) {
            val remainingSec = route.timeSec * (1.0 - progress)
            ProgressCard(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(12.dp),
                progress = progress,
                traveledKm = formatKm(traveled),
                totalKm = formatKm(total),
                eta = formatClock(System.currentTimeMillis() + (remainingSec * 1000).toLong())
            )
        } else if (s.options.isNotEmpty()) {
            RoutePanel(
                options = s.options,
                selected = s.selected,
                departOffsetMin = s.departOffsetMin,
                timeAware = s.timeAware,
                destinationTitle = s.destination?.title ?: "",
                onSelect = vm::selectOption,
                onDepart = vm::setDeparture,
                onStart = {
                    startKey++
                    vm.startNavigation()
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(12.dp)
            )
        }

        if (showSettings) {
            SettingsSheet(
                user = user,
                onSignOut = {
                    showSettings = false
                    onSignOut()
                },
                onDismiss = { showSettings = false }
            )
        }

        if (showSearch) {
            SearchScreen(
                results = s.results,
                searching = s.searching,
                error = s.searchError,
                onQuery = vm::search,
                onPick = {
                    showSearch = false
                    vm.chooseDestination(it)
                },
                onClose = { showSearch = false }
            )
        }

        StartOverlay(trigger = startKey)
    }
}

@Composable
private fun StatusPill(
    text: String,
    color: Color,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(shape)
            .background(ShColors.Panel.copy(alpha = 0.92f))
            .border(1.dp, ShBorder, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(text = text, color = color, style = ShText.Small.copy(fontSize = 13.sp), maxLines = 2)
    }
}
