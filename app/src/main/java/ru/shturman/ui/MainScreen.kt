package ru.shturman.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.shturman.NavViewModel
import ru.shturman.auth.UserInfo
import ru.shturman.ui.theme.ShColors
import ru.shturman.ui.themes.themedScreenEnter

/**
 * Главный экран: карта на весь экран и всё поверх неё (раскладка — в MainLayout.kt).
 * Здесь — состояние, системные окна (разрешения, распознавание речи) и шторки.
 */
@Composable
fun MainScreen(user: UserInfo, onSignOut: () -> Unit, vm: NavViewModel = viewModel()) {
    val s by vm.state.collectAsState()
    var showSettings by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    // Поиск открыт, чтобы задать «Дом» или «Работу» (иначе null)
    var searchSaving by remember { mutableStateOf<String?>(null) }
    // Текст, продиктованный голосом: поиск откроется сразу с ним
    var searchText by remember { mutableStateOf("") }
    var showAdmin by remember { mutableStateOf(false) }
    var showMusic by remember { mutableStateOf(false) }
    var showRadar by remember { mutableStateOf(false) }
    // Проекция на лобовое (HUD): остаётся включённой и после поворота экрана
    var hudOn by rememberSaveable { mutableStateOf(false) }
    var recenterKey by remember { mutableStateOf(0) }
    var overviewKey by remember { mutableStateOf(0) }
    // Камера едет за стрелкой; если её сдвинули или показан весь путь — вместо «Обзора» кнопка «Где я»
    var following by remember { mutableStateOf(true) }
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
                d.satSeen > 0 -> "Ищу спутники: ${d.satUsed} из ${d.satSeen}" to 0
                else -> "Ищу сигнал GPS…" to 0
            }
            kotlinx.coroutines.delay(1000L)
        }
    }
    var startKey by remember { mutableStateOf(0) }
    val context = LocalContext.current
    val view = LocalView.current

    // Полноэкранный режим и проекция: прячем системные панели Android (они выезжают свайпом от края)
    LaunchedEffect(s.fullscreen, hudOn) {
        var c: Context = context
        while (c is ContextWrapper && c !is Activity) c = c.baseContext
        val window = (c as? Activity)?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, view)
        if (s.fullscreen || hudOn) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Запасной путь голосовых команд: системное окно распознавания (если встроенная служба не отвечает)
    val sysVoice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        vm.onSystemVoiceResult(r.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull())
    }
    LaunchedEffect(s.voiceFallbackKey) {
        if (s.voiceFallbackKey > 0) {
            val i = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
                putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Скажите команду")
            }
            runCatching { sysVoice.launch(i) }.onFailure { vm.onSystemVoiceResult(null) }
        }
    }

    // Голосовой поиск: системное окно распознаёт адрес, и поиск открывается уже с ним.
    // Навигатор при этом ничего не говорит. Если распознавания на телефоне нет — просто открывается поиск.
    val voiceSearch = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        searchText = r.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
        searchSaving = null
        showSearch = true
    }
    val onVoiceSearch: () -> Unit = {
        val i = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Куда едем?")
        }
        runCatching { voiceSearch.launch(i) }.onFailure { searchText = ""; searchSaving = null; showSearch = true }
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

    // «Поехать» из приложения «Заказы»: как только дорожные данные готовы — маршрут и ведение
    val external by ru.shturman.api.ExternalTrip.pending.collectAsState()
    LaunchedEffect(external, s.dataReady) {
        val r = external ?: return@LaunchedEffect
        if (!s.dataReady) return@LaunchedEffect
        ru.shturman.api.ExternalTrip.pending.value = null
        vm.startExternalTrip(r)
    }

    val pills = buildPills(s, gpsWait, context, vm)

    val actions = MainActions(
        openSettings = { showSettings = true },
        openSearch = { saving -> searchSaving = saving; searchText = ""; showSearch = true },
        voiceSearch = onVoiceSearch,
        pickPlace = { place -> Recents.add(context, place); vm.chooseDestination(place) },
        clearRoute = { vm.clearRoute() },
        toggleVoice = { vm.setVoiceOn(!s.voiceOn) },
        overview = { overviewKey++ },
        recenter = { recenterKey++ },
        setSatellite = vm::setSatellite,
        setMap3d = vm::setMap3d,
        openMusic = { showMusic = true },
        selectOption = vm::selectOption,
        setDeparture = vm::setDeparture,
        start = { startKey++; vm.startNavigation() }
    )

    val fx = remember { ru.shturman.ui.fx.FxController() }
    androidx.compose.runtime.CompositionLocalProvider(ru.shturman.ui.fx.LocalFx provides fx) {
        Box(Modifier.fillMaxSize().background(ShColors.Background)) {
            MainLayout(s = s, pills = pills, following = following, a = actions) { insets ->
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
                    markerFromBottom = insets.markerFromBottom,
                    markerFromLeft = insets.markerFromLeft,
                    previewTop = insets.previewTop,
                    previewBottom = insets.previewBottom,
                    previewLeft = insets.previewLeft,
                    overviewKey = overviewKey,
                    onFollowChanged = { following = it },
                    speedKmh = s.speedKmh,
                    turnAheadM = s.turn?.distanceM ?: Double.MAX_VALUE,
                    onFix = { here, speed, accuracy, course, altitude -> vm.onFix(here, speed, accuracy, course, altitude) }
                )
            }

            NextOrderButton(orderId = s.nextOrderId, onDismiss = vm::dismissNextOrder)

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
                    onDismiss = { showSettings = false },
                    onOpenHud = { showSettings = false; hudOn = true },
                    onOpenMusic = { showSettings = false; showMusic = true },
                    onOpenRadar = { showSettings = false; showRadar = true }
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
                            actions.pickPlace(it)
                        },
                        onClose = { showSearch = false },
                        initialQuery = searchText,
                        initialSaving = searchSaving,
                        here = s.hereForSearch()
                    )
                }
            }

            if (showAdmin) AdminCodesScreen(onClose = { showAdmin = false })
            if (showMusic) MusicSheet(onDismiss = { showMusic = false })
            if (showRadar) AntiRadarSheet(onDismiss = { showRadar = false })

            StartOverlay(trigger = startKey)
            ru.shturman.ui.fx.FxHost(fx)

            // Проекция на лобовое — поверх всего
            if (hudOn) {
                WindshieldHud(s = s, mirror = s.hudMirror, onMirror = vm::setHudMirror, onExit = { hudOn = false })
            }
        }
    }
}

/** Где мы сейчас (для расстояний в поиске): точка на маршруте или последнее известное положение. */
private fun ru.shturman.NavState.hereForSearch(): ru.shturman.routing.LatLon? =
    ru.shturman.routing.Diag.lastPos ?: route?.points?.getOrNull(position?.index ?: -1)

/**
 * Сообщения поверх карты: загрузка данных, построение маршрута, ошибки, голос, опасности, GPS.
 * Погода и советы по выезду живут в шторке маршрута, а не здесь.
 */
private fun buildPills(
    s: ru.shturman.NavState,
    gpsWait: Pair<String, Int>?,
    context: Context,
    vm: NavViewModel
): List<Pill> {
    val pills = ArrayList<Pill>()
    fun open(action: String, appDetails: Boolean): () -> Unit = {
        runCatching {
            val i = if (appDetails) android.content.Intent(action, android.net.Uri.parse("package:" + context.packageName))
                else android.content.Intent(action)
            context.startActivity(i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
    if (s.listening) {
        pills += Pill("Слушаю…" + (s.heardText?.let { " «$it»" } ?: " скажите команду"), warn = false)
    } else if (s.voiceMessage != null || s.heardText != null) {
        pills += Pill((s.heardText?.let { "«$it» " } ?: "") + (s.voiceMessage ?: ""), warn = false)
    }
    if (!s.navigating) {
        val err = s.routeError
        when {
            err != null -> pills += Pill(
                err, warn = true,
                onClick = when {
                    err.startsWith("Геолокация выключена") -> open(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS, false)
                    err.startsWith("Нет доступа к местоположению") -> open(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, true)
                    else -> null
                }
            )
            s.routeLoading -> pills += Pill("Строю маршрут…", warn = false)
            !s.dataReady && s.dataError != null -> pills += Pill(s.dataError + " Нажмите, чтобы повторить.", warn = true, onClick = { vm.downloadDataIfNeeded() })
            !s.dataReady -> pills += Pill("Загружаю дорожные данные: ${(s.dataProgress * 100).toInt()}%", warn = false)
        }
    }
    if (!s.navigating && s.destination == null && s.leaderDest != null) {
        pills += Pill("Ведущий едет: ${s.leaderDest.title}. Нажмите, чтобы ехать туда же", warn = false, onClick = { vm.followLeader() })
    }
    if (!s.navigating && s.destination == null && s.options.isEmpty()) {
        s.hud.greeting?.let { pills += Pill(it, warn = false) }
        if (s.hud.batteryPct in 1..14 && !s.hud.charging) pills += Pill("Батарея ${s.hud.batteryPct}%", warn = true)
    }
    if (s.navigating) {
        s.hazard?.let { pills += Pill(it, warn = true) }
        s.notice?.let { pills += Pill(it, warn = false) }
        if (s.hud.batteryPct in 1..9 && !s.hud.charging) pills += Pill("Батарея ${s.hud.batteryPct}%", warn = true)
    }
    gpsWait?.let { (text, action) ->
        pills += Pill(
            text, warn = action != 0,
            onClick = when (action) {
                1 -> open(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, true)
                2 -> open(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS, false)
                else -> null
            }
        )
    }
    return pills
}
