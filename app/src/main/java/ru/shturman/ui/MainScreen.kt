package ru.shturman.ui

import android.Manifest
import androidx.compose.ui.res.stringResource
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.ui.platform.LocalConfiguration
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
import ru.shturman.ui.themes.themedScreenEnter
import ru.shturman.ui.themes.themedEnter
import ru.shturman.ui.fx.panel
import ru.shturman.ui.fx.pressFx
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
    var showMusic by remember { mutableStateOf(false) }
    var showRadar by remember { mutableStateOf(false) }
    var recenterKey by remember { mutableStateOf(0) }
    // В поездке без живого положения подсказываем, что происходит: ищем спутники, нет точного разрешения или выключена геолокация.
    // Второе число — действие по нажатию: 1 — настройки приложения, 2 — настройки геолокации.
    var gpsWait by remember { mutableStateOf<Pair<String, Int>?>(null) }
    LaunchedEffect(s.navigating) {
        if (!s.navigating) { gpsWait = null; return@LaunchedEffect }
        while (true) {
            val d = ru.shturman.routing.Diag
            gpsWait = when {
                d.hasLiveFix(8000L) -> null
                d.permission == "только примерное" -> "Разрешено только примерное местоположение, стрелка стоит. Нажмите и разрешите точное" to 1
                d.permission == "нет" -> "Нет доступа к местоположению. Нажмите, чтобы разрешить" to 1
                !d.locationOn -> "Геолокация выключена. Нажмите, чтобы включить" to 2
                d.satSeen > 0 -> "Ищу спутники: видно ${d.satSeen}, в расчёте ${d.satUsed}. На открытом месте быстрее" to 0
                else -> "Ищу сигнал GPS…" to 0
            }
            kotlinx.coroutines.delay(1000L)
        }
    }
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
    val sysVoice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        vm.onSystemVoiceResult(r.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull())
    }
    androidx.compose.runtime.LaunchedEffect(s.voiceFallbackKey) {
        if (s.voiceFallbackKey > 0) {
            val i = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
                putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Скажите команду")
            }
            runCatching { sysVoice.launch(i) }.onFailure { vm.onSystemVoiceResult(null) }
        }
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

    val fx = remember { ru.shturman.ui.fx.FxController() }
    // Высоты нижних блоков и карточки поворота: по ним ставятся остальные элементы, чтобы ничего не перекрывалось
    val density = LocalDensity.current
    var navBottomH by remember { mutableStateOf(0.dp) }
    var pillH by remember { mutableStateOf(0.dp) }
    var panelH by remember { mutableStateOf(0.dp) }
    var skeletonH by remember { mutableStateOf(0.dp) }
    var paceH by remember { mutableStateOf(0.dp) }
    fun Modifier.measureH(set: (Dp) -> Unit) = this.onSizeChanged { set(with(density) { it.height.toDp() }) }
    // Карта на весь экран, а панели — над системными кнопками внизу: их высоту учитываем для стрелки
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // Режим «просто карта»: маршрута нет и он не строится — снизу строка «Куда едем?»
    val mapMode = !s.navigating && s.options.isEmpty() && !s.routeLoading && s.destination == null
    androidx.compose.runtime.CompositionLocalProvider(ru.shturman.ui.fx.LocalFx provides fx) {
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
            themeId = s.theme,
            map3d = s.map3d,
            // стрелка — на высоте кнопки «Где я»: над нижней строкой отступ 8 dp и половина кнопки 28 dp
            markerFromBottom = if (s.navigating) navBottomH + 36.dp + navBarBottom else 0.dp,
            onFix = { here, speed, accuracy, course, altitude -> vm.onFix(here, speed, accuracy, course, altitude) }
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MapCapsule(
                items = listOf(
                    CapsuleItem(if (s.theme == 1) Icons.Filled.Star else Icons.Filled.Menu, "Меню") { showSettings = true },
                    CapsuleItem(ImageVector.vectorResource(R.drawable.ic_music), "Музыка") { showMusic = true },
                    CapsuleItem(ImageVector.vectorResource(R.drawable.ic_radar), "Антирадар") { showRadar = true }
                ),
                index = 0
            )
            MapButton(
                icon = Icons.Filled.Search,
                description = "Куда едем?",
                onClick = { showSearch = true },
                index = 1,
                accent = true
            )
            if (s.destination != null) {
                MapButton(
                    icon = Icons.Filled.Close,
                    description = "Отменить маршрут",
                    onClick = { vm.clearRoute() },
                    index = 2
                )
            }
        }

        // Раллийный дорожник: форма дороги впереди
        if (s.navigating) {
            val turn = s.turn
            val firstNote = s.notes.firstOrNull()
            // Название улицы берём у ближайшего манёвра, если он рядом с первым изгибом
            val street = if (turn != null && (firstNote == null || kotlin.math.abs(turn.distanceM - firstNote.distanceM) < 80.0)) turn.street else ""
            PaceCard(
                notes = s.notes,
                street = street,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .measureH { paceH = it }
                    .padding(12.dp)
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
            pills += Triple("Слушаю…" + (s.heardText?.let { "\n«$it»" } ?: " скажите команду"), ShColors.Accent, null)
        } else if (s.voiceMessage != null || s.heardText != null) {
            pills += Triple((s.heardText?.let { "«$it»\n" } ?: "") + (s.voiceMessage ?: ""), ShColors.TextPrimary, null)
        }
        if (message != null && !s.navigating) {
            val err = s.routeError
            val openSettings: (() -> Unit)? = when {
                err != null && err.startsWith("Геолокация выключена") -> ({
                    runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                })
                err != null && err.startsWith("Нет доступа к местоположению") -> ({
                    runCatching {
                        context.startActivity(
                            android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + context.packageName))
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                })
                else -> null
            }
            pills += Triple(message.first, message.second, if (!s.dataReady && s.dataError != null) ({ vm.downloadDataIfNeeded() }) else openSettings)
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
        if (s.navigating && s.notice != null) {
            pills += Triple(s.notice!!, ShColors.Accent, null)
        }
        gpsWait?.let { (text, action) ->
            val click: (() -> Unit)? = when (action) {
                1 -> ({ runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + context.packageName)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } })
                2 -> ({ runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } })
                else -> null
            }
            pills += Triple(text, ShColors.Sign3, click)
        }
        if (pills.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 80.dp, top = if (s.navigating) paceH + 4.dp else 12.dp, end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                pills.forEach { (text, color, click) -> StatusPill(text = text, color = color, onClick = click) }
            }
        }

        // Высота занятого низа экрана в текущем режиме: над ним ставятся скорость, показания и «Где я»
        val bottomUi = when {
            s.navigating && route != null -> navBottomH
            s.options.isNotEmpty() -> panelH
            s.routeLoading -> skeletonH
            mapMode -> pillH
            else -> 0.dp
        }

        // Справа над нижним блоком: мелкие показания (компас, высота, GPS, батарея, сеть, погода) и «Где я»
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 12.dp, bottom = bottomUi + 8.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HudCorner(hud = s.hud)
            if (!mapMode) {
                MapButton(
                    icon = if (s.theme == 1) Icons.Filled.Favorite else Icons.Filled.LocationOn,
                    description = "Где я",
                    onClick = { recenterKey++ },
                    index = 3
                )
            }
        }

        // Скорость без маршрута — слева над нижним блоком
        if (!s.navigating && s.speedKmh >= 5) {
            SpeedReadout(
                kmh = s.speedKmh,
                over = false,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(start = 12.dp, bottom = bottomUi + 8.dp)
            )
        }

        if (mapMode) {
            SearchPill(
                onSearch = { showSearch = true },
                onLocate = { recenterKey++ },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .measureH { pillH = it }
                    .padding(start = 16.dp, end = 16.dp, bottom = 26.dp)
            )
        }

        if (s.navigating && route != null) {
            // Низ в поездке — одна строка: слева знак ограничения над скоростью, справа карточка пути.
            // Всё в одном ряду, поэтому ничего ни на что не налезает.
            val remainingSec = route.timeSec * (1.0 - progress)
            val limit = s.limitKmh
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .measureH { navBottomH = it }
                    .padding(12.dp)
                    .widthIn(max = 760.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (limit != null) SpeedLimitSign(limit = limit)
                    SpeedReadout(kmh = s.speedKmh, over = limit != null && s.speedKmh > limit + 2)
                }
                ProgressCard(
                    modifier = Modifier.weight(1f),
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
            }
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
                onClose = { vm.clearRoute() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .measureH { panelH = it }
                    .padding(12.dp)
            )
        } else if (s.routeLoading) {
            // Заглушка, пока маршрут считается
            RouteSkeleton(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .measureH { skeletonH = it }
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
            Box(Modifier.fillMaxSize().themedScreenEnter()) {
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
        }

        if (showAdmin) AdminCodesScreen(onClose = { showAdmin = false })
        if (showMusic) MusicSheet(onDismiss = { showMusic = false })
        if (showRadar) AntiRadarSheet(onDismiss = { showRadar = false })

        StartOverlay(trigger = startKey)
        ru.shturman.ui.fx.FxHost(fx)
    }
    }
}

@Composable
private fun StatusPill(
    text: String,
    color: Color,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val shape = ru.shturman.ui.fx.chipShape()
    Box(
        modifier = modifier
            .themedEnter()
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(if (onClick != null) Modifier.pressFx() else Modifier)
            .panel(shape, elevation = 8.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(text = text, color = color, style = ShText.Small.copy(fontSize = 13.sp), maxLines = 3)
    }
}

