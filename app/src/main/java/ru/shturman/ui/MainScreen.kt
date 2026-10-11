package ru.shturman.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.vectorResource
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import ru.shturman.R
import ru.shturman.routing.fuelLiters
import ru.shturman.routing.fuelText
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
    var showAdmin by remember { mutableStateOf(false) }
    var recenterKey by remember { mutableStateOf(0) }
    var startKey by remember { mutableStateOf(0) }
    val context = LocalContext.current
    val view = LocalView.current

    // Полноэкранный режим: прячем системные панели Android (они выезжают свайпом от края)
    LaunchedEffect(s.fullscreen) {
        var c: Context = context
        while (c is ContextWrapper && c !is Activity) c = c.baseContext
        val window = (c as? Activity)?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, view)
        if (s.fullscreen) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Микрофон для голосовых команд
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.listen()
    }
    val onMic = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            vm.listen()
        } else {
            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val route = s.route
    val total = (route?.totalM ?: 0.0).coerceAtLeast(1.0)
    val traveled = (s.position?.traveledM ?: 0.0).coerceIn(0.0, total)
    val progress = (traveled / total).toFloat()
    val arrived = s.navigating && route != null && progress >= 0.985f

    // Приехали: убираем маршрут
    LaunchedEffect(arrived) {
        if (arrived) vm.arrived()
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
            bumps = s.bumps,
            works = s.works,
            danger = s.danger,
            members = s.members,
            satellite = s.satellite,
            princess = s.princess,
            onFix = { here, speed -> vm.onFix(here, speed) }
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MapButton(
                icon = if (s.princess) Icons.Filled.Star else Icons.Filled.Menu,
                description = "Меню",
                onClick = { showSettings = true }
            )
            MapButton(
                icon = Icons.Filled.Search,
                description = "Куда едем?",
                onClick = { showSearch = true }
            )
            MapButton(
                icon = ImageVector.vectorResource(R.drawable.ic_mic),
                description = "Голосовая команда",
                onClick = { onMic() }
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
        val weather = s.weather
        val pills = ArrayList<Triple<String, Color, (() -> Unit)?>>()
        if (s.listening) {
            pills += Triple("Слушаю… скажите команду", ShColors.Accent, null)
        } else if (s.voiceMessage != null) {
            pills += Triple(s.voiceMessage!!, ShColors.TextPrimary, null)
        }
        if (message != null && !s.navigating) {
            pills += Triple(message.first, message.second, if (!s.dataReady && s.dataError != null) ({ vm.downloadDataIfNeeded() }) else null)
        } else if (!s.navigating && s.options.isNotEmpty()) {
            if (weather != null) {
                val extra = ((1.0 / weather.speedFactor - 1.0) * 100).toInt()
                val text = "${weather.label}, ${weather.tempC}°" + (if (extra > 0) " · дольше на $extra%" else "") +
                    (s.seasonNote?.let { "\n$it" } ?: "")
                pills += Triple(text, if (extra > 0 || s.seasonNote != null) ShColors.Sign3 else ShColors.TextSecondary, null)
            } else if (s.seasonNote != null) {
                pills += Triple(s.seasonNote!!, ShColors.Sign3, null)
            }
            s.departAdvice?.let { pills += Triple(it, ShColors.Gold, null) }
        }
        if (!s.navigating && s.destination == null && s.leaderDest != null) {
            pills += Triple("Ведущий едет: ${s.leaderDest!!.title}. Нажмите, чтобы ехать туда же", ShColors.Gold, { vm.followLeader() })
        }
        if (s.navigating && s.hazard != null) {
            pills += Triple(s.hazard!!, ShColors.Sign3, null)
        }
        if (pills.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 72.dp, top = 12.dp, end = if (s.navigating) 190.dp else 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                pills.forEach { (text, color, click) -> StatusPill(text = text, color = color, onClick = click) }
            }
        }

        // Радар-круг: камера впереди на маршруте
        val cam = s.cameraAhead
        if (s.navigating && cam != null) {
            RadarCircle(
                distanceM = cam.distanceM,
                place = cam.place,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 12.dp, bottom = 180.dp)
            )
        }

        // Знак ограничения скорости
        val limit = s.limitKmh
        if (s.navigating && limit != null) {
            SpeedLimitSign(
                limit = limit,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(start = 12.dp, bottom = 112.dp)
            )
        }

        // Текущая скорость
        if (s.navigating || s.speedKmh >= 5) {
            val over = s.limitKmh != null && s.speedKmh > s.limitKmh!! + 2
            SpeedReadout(
                kmh = s.speedKmh,
                over = over,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(start = 12.dp, bottom = if (s.navigating) 180.dp else 24.dp)
            )
        }

        val bottomPadding = when {
            s.navigating -> 112.dp
            s.options.isNotEmpty() -> 300.dp
            else -> 24.dp
        }
        MapButton(
            icon = if (s.princess) Icons.Filled.Favorite else Icons.Filled.LocationOn,
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
                eta = formatClock(System.currentTimeMillis() + (remainingSec * 1000).toLong()),
                showPercent = s.cardPercent,
                showDistance = s.cardDistance,
                showEta = s.cardEta,
                remaining = if (s.cardRemaining) "осталось ${formatDuration(remainingSec)} · ${formatKm(total - traveled)} км" else null,
                fuel = if (s.cardFuel) fuelText(fuelLiters(route.lengthM, route.timeSec, s.consumption.toDouble()), s.fuelPrice.toDouble()) else null
            )
        } else if (s.options.isNotEmpty()) {
            RoutePanel(
                options = s.options,
                selected = s.selected,
                departOffsetMin = s.departOffsetMin,
                timeAware = s.timeAware,
                destinationTitle = s.destination?.title ?: "",
                consumption = s.consumption,
                fuelPrice = s.fuelPrice,
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
                s = s,
                vm = vm,
                isAdmin = user.email == ru.shturman.access.ADMIN_EMAIL,
                onOpenAdmin = { showSettings = false; showAdmin = true },
                onSignOut = {
                    showSettings = false
                    onSignOut()
                },
                onDismiss = { showSettings = false }
            )
        }

        if (showSearch) {
            SearchScreen(
                home = s.home,
                work = s.work,
                onSetFavorite = vm::setFavorite,
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

        if (showAdmin) AdminCodesScreen(onClose = { showAdmin = false })

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
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(ShColors.Panel.copy(alpha = 0.92f))
            .border(1.dp, ShBorder, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(text = text, color = color, style = ShText.Small.copy(fontSize = 13.sp), maxLines = 3)
    }
}

/** Текущая скорость: число и «км/ч». Краснеет, если быстрее ограничения. */
@Composable
private fun SpeedReadout(kmh: Int, over: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .width(60.dp)
            .clip(shape)
            .background(ShColors.Panel.copy(alpha = 0.92f))
            .border(1.dp, ShBorder, shape)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = kmh.toString(),
            color = if (over) ShColors.Danger else ShColors.TextPrimary,
            style = ShText.Distance.copy(fontSize = 24.sp)
        )
        Text(text = "км/ч", color = ShColors.TextSecondary, fontSize = 11.sp)
    }
}
