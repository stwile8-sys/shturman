package ru.shturman

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.shturman.routing.Camera
import ru.shturman.routing.Cameras
import ru.shturman.routing.Caravan
import ru.shturman.routing.CaravanDest
import ru.shturman.routing.CaravanMember
import ru.shturman.routing.DeviationDetector
import ru.shturman.routing.DeviationEvent
import ru.shturman.routing.DeviationLevel
import ru.shturman.routing.OsmPoints
import ru.shturman.ui.themes.ThemeSound
import ru.shturman.ui.themes.ThemeSounds
import ru.shturman.routing.VoiceCommand
import ru.shturman.routing.VoiceListener
import ru.shturman.routing.alongRoute
import ru.shturman.routing.friendlyNetError
import ru.shturman.routing.withRetry
import kotlinx.coroutines.tasks.await
import ru.shturman.routing.fuelLiters
import ru.shturman.routing.parseVoiceCommand
import ru.shturman.routing.seasonEffect
import ru.shturman.ui.theme.ShColors
import ru.shturman.routing.Geocoder
import ru.shturman.routing.LatLon
import ru.shturman.routing.NextTurn
import ru.shturman.routing.Place
import ru.shturman.routing.RoadData
import ru.shturman.routing.RouteOption
import ru.shturman.routing.RoutePosition
import ru.shturman.routing.Router
import ru.shturman.routing.LimitSeg
import ru.shturman.routing.SharedSync
import ru.shturman.routing.Snap
import ru.shturman.routing.Voice
import ru.shturman.routing.turnPhrase
import ru.shturman.routing.Weather
import ru.shturman.routing.fetchWeather
import ru.shturman.routing.SpeedRecorder
import ru.shturman.routing.StopRecorder
import ru.shturman.routing.SpeedStore
import ru.shturman.routing.forecastTimeSec
import ru.shturman.routing.lastKnownPosition
import ru.shturman.routing.locateOnRoute
import ru.shturman.routing.nextTurn
import ru.shturman.routing.snapToRoute
import java.util.Date

private data class Quad(val a: List<Camera>, val b: List<LatLon>, val c: List<LatLon>, val d: List<LatLon>)

/** Дальше этого расстояния от линии маршрута стрелка уже не прилипает к дороге. */
private const val SNAP_MAX_M = 40.0

/** Столько подряд положений вдали от маршрута нужно, чтобы решить, что мы свернули, и построить новый путь. */

private fun loadPlace(app: Application, kind: String): Place? = runCatching {
    val text = app.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE).getString("fav_$kind", null)
        ?: return null
    val o = org.json.JSONObject(text)
    Place(o.getString("title"), o.optString("subtitle"), LatLon(o.getDouble("lat"), o.getDouble("lon")))
}.getOrNull()

/** Камера впереди на маршруте: сколько метров и где стоит ("" — обычная, "bridge" — на мосту, "tunnel" — в тоннеле). */
class CameraAhead(val distanceM: Double, val place: String)

/** Всё, что показывает экран: состояние данных, поиск, маршрут и поездка. */
/** Мелкие показания в углу карты. */
data class Hud(
    val heading: Int = -1,
    val altitudeM: Int? = null,
    val accuracyM: Int = -1,
    val batteryPct: Int = -1,
    val charging: Boolean = false,
    val online: Boolean = true,
    val weather: Weather? = null,
    val greeting: String? = null
)

data class NavState(
    val dataReady: Boolean = false,
    /** Приехали на заказ из приложения «Заказы», и у него есть следующий: показать кнопку «Следующий заказ». */
    val nextOrderId: Long? = null,
    val dataProgress: Float = 0f,
    val dataError: String? = null,

    val searching: Boolean = false,
    val results: List<Place> = emptyList(),
    val searchError: String? = null,

    val destination: Place? = null,
    val routeLoading: Boolean = false,
    val routeError: String? = null,
    val options: List<RouteOption> = emptyList(),
    val selected: Int = 0,
    val departOffsetMin: Int = 0,
    val timeAware: Boolean = true,

    val navigating: Boolean = false,
    val position: RoutePosition? = null,
    val turn: NextTurn? = null,
    val notes: List<ru.shturman.routing.PaceNote> = emptyList(),

    /** Ограничение скорости на участке, где мы сейчас едем, км/ч (если оно известно). */
    val limitKmh: Int? = null,
    /** Впереди крутой изгиб, а едем быстро: до скольких км/ч советуем сбавить. */
    val curveAdviceKmh: Int? = null,
    /** Сейчас темно (после заката) и включён ночной экран: карта темнее. */
    val night: Boolean = false,
    /** Настройка «Ночной экран». */
    val nightScreen: Boolean = true,

    val weather: Weather? = null,

    val shareData: Boolean = true,
    val voiceOn: Boolean = true,
    val home: Place? = null,
    val work: Place? = null,
    val speedKmh: Int = 0,
    val syncing: Boolean = false,
    val syncMessage: String? = null,

    // Настройки
    val fullscreen: Boolean = false,
    val satellite: Boolean = false,
    val theme: Int = 0,
    /** Короткое сообщение о маршруте («Перестраиваю маршрут»), пропадает само. */
    val notice: String? = null,
    /** Чувствительность отклонения: 0 — низкая, 1 — средняя, 2 — высокая. */
    val devLevel: Int = 1,
    val hud: Hud = Hud(),
    val turnVibro: Boolean = true,
    val map3d: Boolean = true,
    val overspeedSound: Boolean = true,
    /** Проекция на лобовое: картинка зеркальная (для отражения в стекле). */
    val hudMirror: Boolean = true,
    val nightVoice: Boolean = true,
    val radarOn: Boolean = true,
    val cardPercent: Boolean = true,
    val cardDistance: Boolean = true,
    val cardEta: Boolean = true,
    val cardRemaining: Boolean = false,
    val cardFuel: Boolean = false,
    val consumption: Float = 8.0f,
    val fuelPrice: Float = 60.0f,

    // Подсказки и предупреждения
    val departAdvice: String? = null,
    val seasonNote: String? = null,
    val bumps: List<LatLon> = emptyList(),
    val works: List<LatLon> = emptyList(),
    val danger: List<LatLon> = emptyList(),
    val hazard: String? = null,
    val cameraAhead: CameraAhead? = null,

    // Караван
    val caravanCode: String? = null,
    val caravanLeader: Boolean = false,
    val caravanMessage: String? = null,
    val members: List<CaravanMember> = emptyList(),
    val leaderDest: CaravanDest? = null,

    // Голосовое управление
    val listening: Boolean = false,
    val voiceMessage: String? = null,
    val voiceStatus: String? = null,
    val heardText: String? = null,
    val voiceFallbackKey: Int = 0
) {
    val route: RouteOption? get() = options.getOrNull(selected)
}

class NavViewModel(app: Application) : AndroidViewModel(app) {

    private val roadData = RoadData(app)
    private val router = Router(roadData)

    private var limits: List<LimitSeg> = emptyList()
    private val overspeed = ru.shturman.routing.OverspeedAlarm()
    private val cameraRepo = Cameras(app)
    private var cameraList: List<Camera> = emptyList()
    private val bumpRepo = OsmPoints.bumps(app)
    private val worksRepo = OsmPoints.roadworks(app)
    private var routeBumps: List<Pair<Double, Int>> = emptyList()
    private var routeWorks: List<Pair<Double, Int>> = emptyList()
    private var routeCams: List<Pair<Double, Int>> = emptyList()
    private val spoken = HashSet<String>()
    private var prevSpeed = 0f
    private var prevSpeedAt = 0L
    private val caravan = Caravan(
        onMembers = { m -> onCaravanMembers(m) },
        onDest = { d -> _state.update { it.copy(leaderDest = d) } }
    )
    private val listener = VoiceListener(
        app,
        onText = { text -> handleVoice(text) },
        onState = { on -> _state.update { it.copy(listening = on) } },
        onPartial = { t -> _state.update { it.copy(heardText = t) } },
        onProblem = { m -> _state.update { it.copy(voiceMessage = m) } },
        onFallback = { _state.update { it.copy(voiceFallbackKey = it.voiceFallbackKey + 1) } }
    )
    private val speedStore = SpeedStore.shared(app)
    private val recorder = SpeedRecorder(speedStore)
    private val stopRecorder = StopRecorder(speedStore)
    private val sharedSync = SharedSync(app, speedStore)
    private val voice = Voice(app)
    private val settings = app.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
    private val tripLog = ru.shturman.stats.TripLog(app.getSharedPreferences("trips", android.content.Context.MODE_PRIVATE))
    private var tripStartMs = 0L
    private var tripMaxKmh = 0
    private var spokenLevel = -1
    private var lastTurnKey = -1
    /** Где на маршруте (метры от начала) уже советовали сбавить скорость перед изгибом. */
    private val spokenCurves = ArrayList<Double>()
    private var iceWarned = false
    private var lastTurnDistance = Double.MAX_VALUE
    private var rerouting = false
    /** Номер текущего маршрута: ответ движка на устаревший запрос (сменили цель, отменили) отбрасывается. */
    private var routeGen = 0
    private var limitsJob: kotlinx.coroutines.Job? = null
    /** Сколько проехали в этой поездке до последнего перестроения (перестроение начинает счёт маршрута с нуля). */
    private var tripDoneM = 0.0
    private var vibeKey = -1
    private var lastRerouteAt = 0L

    private val _state = MutableStateFlow(NavState(dataReady = roadData.isReady(), shareData = sharedSync.share, voiceOn = settings.getBoolean("voice", true),
        home = loadPlace(app, "home"), work = loadPlace(app, "work"),
        fullscreen = settings.getBoolean("fullscreen", false),
        satellite = settings.getBoolean("satellite", false),
        theme = ru.shturman.ui.themes.Themes.load(settings),
        devLevel = settings.getInt("dev_level", 1).coerceIn(0, 2),
        turnVibro = settings.getBoolean("turn_vibro", true),
        map3d = settings.getBoolean("map3d", true),
        overspeedSound = settings.getBoolean("overspeed", true),
        hudMirror = settings.getBoolean("hud_mirror", true),
        nightScreen = settings.getBoolean("night_screen", true),
        nightVoice = settings.getBoolean("night_voice", true),
        radarOn = settings.getBoolean("radar", true),
        cardPercent = settings.getBoolean("card_percent", true),
        cardDistance = settings.getBoolean("card_distance", true),
        cardEta = settings.getBoolean("card_eta", true),
        cardRemaining = settings.getBoolean("card_remaining", false),
        cardFuel = settings.getBoolean("card_fuel", false),
        consumption = settings.getFloat("consumption", 8.0f),
        fuelPrice = settings.getFloat("fuel_price", 60.0f)))
    val state: StateFlow<NavState> = _state

    init {
        voice.onStatus = { m -> _state.update { it.copy(voiceStatus = m) } }
        downloadDataIfNeeded()
        startHudLoop()
        showGreeting()
        ru.shturman.ui.themes.Shturman2.load(settings)
        ShColors.themeId = ru.shturman.ui.themes.Themes.load(settings)
        voice.princess = ShColors.themeId == 1
        ThemeSounds.enabled = settings.getBoolean("theme_sounds", true)
        ThemeSounds.setTheme(app, ShColors.themeId)
        voice.nightMode = settings.getBoolean("night_voice", true)
        // Данные OpenStreetMap: камеры (на карте не рисуются, нужны для маршрута и радар-круга),
        // лежачие полицейские и ремонт дорог (рисуются на карте). Обновляются раз в неделю.
        viewModelScope.launch {
            val (cams, bumps, works, danger) = withContext(Dispatchers.IO) {
                listOf(cameraRepo.load(), bumpRepo.load(), worksRepo.load(), speedStore.dangerSpots(2))
            }.let { Quad(it[0] as List<Camera>, it[1] as List<LatLon>, it[2] as List<LatLon>, it[3] as List<LatLon>) }
            cameraList = cams
            _state.update { it.copy(bumps = bumps, works = works, danger = danger) }
            if (cameraRepo.updateIfStale()) cameraList = withContext(Dispatchers.IO) { cameraRepo.load() }
            if (bumpRepo.updateIfStale()) {
                val fresh = withContext(Dispatchers.IO) { bumpRepo.load() }
                _state.update { it.copy(bumps = fresh) }
            }
            if (worksRepo.updateIfStale()) {
                val fresh = withContext(Dispatchers.IO) { worksRepo.load() }
                _state.update { it.copy(works = fresh) }
            }
        }
        // Раз в неделю пересчитываем время ожидания на светофорах: берём самое частое за неделю
        viewModelScope.launch(Dispatchers.IO) {
            val prefs = app.getSharedPreferences("sync", android.content.Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            if (now - prefs.getLong("stops_recomputed", 0L) > 7L * 24 * 3600 * 1000) {
                speedStore.recomputeStops(now)
                prefs.edit().putLong("stops_recomputed", now).apply()
            }
        }
        // Раз в сутки сами обмениваемся данными с общей базой, тихо
        viewModelScope.launch {
            if (System.currentTimeMillis() - sharedSync.lastSyncAt > 24L * 3600 * 1000) {
                runCatching { sharedSync.sync() }
            }
        }
        // Раз в неделю подтягиваем свежие дорожные данные, чтобы старые значения не оставались
        viewModelScope.launch {
            if (roadData.updateIfStale()) router.close()
        }
    }

    /** Объясняет, почему нет положения: разрешение не дано, геолокация выключена или просто слабый сигнал. */
    private fun gpsProblemText(): String {
        val app = getApplication<Application>()
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            app, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) return "Нет доступа к местоположению. Нажмите здесь, чтобы открыть настройки приложения и разрешить его."
        val lm = app.getSystemService(android.content.Context.LOCATION_SERVICE) as? android.location.LocationManager
        val on = runCatching { lm?.isLocationEnabled ?: true }.getOrDefault(true)
        if (!on) return "Геолокация выключена. Нажмите здесь, чтобы открыть настройки и включить её."
        return "Нет сигнала GPS: выйдите на открытое место и подождите"
    }

    fun downloadDataIfNeeded() {
        if (roadData.isReady()) {
            _state.update { it.copy(dataReady = true, dataError = null) }
            return
        }
        _state.update { it.copy(dataError = null, dataProgress = 0f) }
        viewModelScope.launch {
            try {
                roadData.download { p -> _state.update { it.copy(dataProgress = p) } }
                _state.update { it.copy(dataReady = true) }
            } catch (e: Exception) {
                ru.shturman.Errors.log("roaddata", e)
                _state.update { it.copy(dataError = "Не удалось скачать дорожные данные. ${friendlyNetError(e)}") }
            }
        }
    }

    private var searchJob: kotlinx.coroutines.Job? = null

    fun search(query: String) {
        if (query.trim().length < 3) {
            _state.update { it.copy(results = emptyList(), searching = false, searchError = null) }
            return
        }
        _state.update { it.copy(searching = true, searchError = null) }
        // Старый запрос отменяем: ответ на прежний текст не должен затереть новый
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            try {
                val found = Geocoder.search(query)
                _state.update { it.copy(results = found, searching = false) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                ru.shturman.Errors.log("search", e)
                _state.update { it.copy(searching = false, searchError = friendlyNetError(e)) }
            }
        }
    }

    fun chooseDestination(place: Place) {
        ru.shturman.api.ExternalTrip.active = null
        autoStart = false
        // новая цель посреди поездки: прежнее ведение заканчиваем (поездка записывается), маршрут строится заново
        if (_state.value.navigating) {
            finishTrip()
            stopNavService()
            _state.update { it.copy(navigating = false, position = null, turn = null, notes = emptyList(), limitKmh = null, hazard = null, cameraAhead = null) }
        }
        _state.update { it.copy(destination = place, results = emptyList(), searchError = null) }
        buildRoute()
    }

    /** Запрос «Поехать» из «Заказов»: строим маршрут и сами запускаем ведение, как кнопкой «Поехали». */
    private var autoStart = false

    fun startExternalTrip(r: ru.shturman.api.ExternalTrip.Request) {
        chooseDestination(Place(r.name, "", LatLon(r.lat, r.lon)))
        ru.shturman.api.ExternalTrip.active = r
        autoStart = _state.value.routeError == null
        _state.update { it.copy(nextOrderId = null) }
    }

    fun dismissNextOrder() {
        _state.update { it.copy(nextOrderId = null) }
    }

    fun setDeparture(offsetMin: Int) {
        _state.update { it.copy(departOffsetMin = offsetMin) }
        if (_state.value.destination != null) buildRoute()
    }

    fun selectOption(index: Int) {
        _state.update { it.copy(selected = index) }
    }

    private fun buildRoute() {
        val s = _state.value
        val destination = s.destination ?: return
        if (!s.dataReady) {
            ThemeSounds.play(ThemeSound.ERROR)
            _state.update { it.copy(routeError = "Дорожные данные ещё загружаются, ${(s.dataProgress * 100).toInt()}%") }
            return
        }
        // Берём свежее положение из своего приёма GPS; системный «последний известный» адрес может быть старым
        val fresh = lastFix?.takeIf { System.currentTimeMillis() - lastFixAt < 30_000L }
        val from = fresh ?: lastKnownPosition(getApplication())
        if (from == null) {
            ThemeSounds.play(ThemeSound.ERROR)
            _state.update { it.copy(routeError = gpsProblemText()) }
            return
        }
        _state.update { it.copy(routeLoading = true, routeError = null) }
        val gen = ++routeGen
        viewModelScope.launch {
            try {
                val departAt = Date(System.currentTimeMillis() + s.departOffsetMin * 60_000L)
                val result = router.route(from, destination.position, departAt, cameraList.map { it.position })
                // Тяжёлый подсчёт идёт не в главном потоке; при любой ошибке остаётся время движка
                val forecast = withContext(Dispatchers.Default) {
                    runCatching {
                        result.options.map { o ->
                            RouteOption(forecastTimeSec(o, speedStore, departAt.time), o.lengthM, o.points, o.maneuvers, o.label)
                        }
                    }.getOrDefault(result.options)
                }
                // Погода на час выезда: дождь, снег и гололёд замедляют прогноз
                val weather = fetchWeather(from, departAt.time)
                val weatherFactor = weatherSlowdown(weather, departAt.time)
                // Сезонность (зима, дачный поток, час пик, каникулы) считается по каждому варианту
                val adjusted = forecast.map { o ->
                    val season = seasonEffect(departAt.time, o.lengthM)
                    RouteOption(o.timeSec * weatherFactor * season.slowdown, o.lengthM, o.points, o.maneuvers, o.label)
                }
                val fastest = adjusted.minByOrNull { it.timeSec }
                val note = fastest?.let { seasonNote(departAt.time, it.lengthM, weather) }

                // Маршрут с наименьшим расходом получает пометку «Экономия бензина»
                val litersOf = { o: RouteOption -> fuelLiters(o.lengthM, o.timeSec, s.consumption.toDouble()) }
                val minFuel = adjusted.minByOrNull(litersOf)
                val labeled = adjusted.map { o ->
                    if (o === minFuel && o !== fastest && litersOf(o) < litersOf(fastest!!) * 0.98) {
                        RouteOption(o.timeSec, o.lengthM, o.points, o.maneuvers, if (o.label.isEmpty()) "Экономия бензина" else o.label + " + экономия")
                    } else o
                }.sortedBy { it.timeSec }

                // Лучшее время выезда: смотрим на ближайшие 3 часа с шагом 15 минут
                val advice = fastest?.let { f ->
                    withContext(Dispatchers.Default) { runCatching { departureAdvice(f, departAt.time, weather) }.getOrNull() }
                }
                // пока считали, цель сменили или маршрут отменили — этот ответ уже не нужен
                if (gen != routeGen || _state.value.destination != destination) return@launch
                _state.update {
                    it.copy(
                        weather = weather,
                        routeLoading = false,
                        options = labeled,
                        selected = 0,
                        timeAware = result.timeAware,
                        departAdvice = advice,
                        seasonNote = note
                    )
                }
                if (autoStart) {
                    autoStart = false
                    if (_state.value.route != null) startNavigation()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (gen != routeGen) return@launch
                autoStart = false
                ru.shturman.Errors.log("route", e)
                ThemeSounds.play(ThemeSound.ERROR)
                _state.update {
                    it.copy(
                        routeLoading = false,
                        options = emptyList(),
                        routeError = "Не удалось построить маршрут. Попробуйте другую точку назначения или повторите чуть позже."
                    )
                }
            }
        }
    }

    /** Во сколько раз погода замедляет движение: дождь и снег (из прогноза) плюс возможный гололёд при около нуля. */
    private fun weatherSlowdown(w: Weather?, whenMs: Long): Double {
        if (w == null) return 1.0
        var f = 1.0 / w.speedFactor
        if (iceRisk(w, whenMs)) f *= 1.06
        return f
    }

    private fun iceRisk(w: Weather, whenMs: Long): Boolean {
        val month = java.util.Calendar.getInstance().apply { timeInMillis = whenMs }.get(java.util.Calendar.MONTH) + 1
        val coldSeason = month >= 10 || month <= 4
        return coldSeason && w.tempC in -3..1
    }

    private fun seasonNote(whenMs: Long, lengthM: Double, w: Weather?): String? {
        val parts = ArrayList<String>()
        seasonEffect(whenMs, lengthM).note?.let { parts += it }
        if (w != null && iceRisk(w, whenMs)) parts += "возможен гололёд"
        return parts.takeIf { it.isNotEmpty() }?.joinToString(", ")?.replaceFirstChar { it.uppercase() }
    }

    /** Ищет, когда выехать выгоднее всего. Возвращает фразу, если выигрыш не меньше 5 минут. */
    private fun departureAdvice(route: RouteOption, baseMs: Long, weather: Weather?): String? {
        fun total(offsetMin: Int): Double {
            val at = baseMs + offsetMin * 60_000L
            val t = forecastTimeSec(route, speedStore, at) * weatherSlowdown(weather, at) * seasonEffect(at, route.lengthM).slowdown
            return offsetMin * 60.0 + t
        }
        val now = total(0)
        var bestOffset = 0
        var best = now
        for (off in 15..180 step 15) {
            val t = total(off)
            if (t < best) { best = t; bestOffset = off }
        }
        val gainMin = ((now - best) / 60.0).toInt()
        if (bestOffset == 0 || gainMin < 5) return null
        val clock = java.text.SimpleDateFormat("H:mm", java.util.Locale.forLanguageTag("ru")).format(Date(baseMs + bestOffset * 60_000L))
        return "Выедь в $clock — приедешь на $gainMin мин раньше"
    }

    fun startNavigation() {
        val route = _state.value.route ?: return
        deviation.reset()
        lastTurnDistOnRoute = Double.MAX_VALUE
        spokenLevel = -1
        lastTurnKey = -1
        lastTurnDistance = Double.MAX_VALUE
        spokenCurves.clear()
        iceWarned = false
        stopRecorder.reset()
        overspeed.reset()
        loadLimits(route)
        prepareHazards(route)
        _state.value.destination?.let { caravan.setDestination(it.title, it.position) }
        val position = RoutePosition(0, 0.0)
        ThemeSounds.play(ThemeSound.NAV)
        _state.update { it.copy(navigating = true, position = position, turn = nextTurn(route, position)) }
        lowResourcesNotice()
        ru.shturman.service.NavService.onCancel = { clearRoute() }
        ru.shturman.service.NavService.start(getApplication(), "APEX: поездка", "Маршрут запущен")
        startGpsWatch()
        tripStartMs = System.currentTimeMillis()
        tripDoneM = 0.0
        tripMaxKmh = 0
        if (tripLog.serviceDue()) {
            viewModelScope.launch {
                kotlinx.coroutines.delay(7_000)
                showNotice("Пора на техобслуживание: пробег с прошлого ТО достиг ${tripLog.serviceIntervalKm} км")
            }
        }
    }

    /** Заранее находит на маршруте лежачие полицейские, ремонт и камеры. */
    private fun prepareHazards(route: RouteOption) {
        spoken.clear()
        val st = _state.value
        routeBumps = alongRoute(route, st.bumps, 25.0)
        routeWorks = alongRoute(route, st.works, 40.0)
        routeCams = alongRoute(route, cameraList.map { it.position }, 35.0)
    }

    /**
     * Вызывается на каждое новое положение GPS. Во время поездки привязывает его к линии маршрута,
     * обновляет пройденный путь и ближайший поворот и возвращает точку, где рисовать стрелку.
     * Если телефон ушёл от маршрута дальше SNAP_MAX_M, стрелка остаётся на настоящем GPS-положении.
     */
    private var lastFix: LatLon? = null
    private var lastFixAt = 0L
    private var lastCarSpeedAt = 0L

    fun onFix(here: LatLon, speedMps: Float = 0f, accuracyM: Float = 0f, courseDeg: Float = -1f, altitudeM: Float = Float.NaN): Snap? {
        lastFix = here
        lastFixAt = System.currentTimeMillis()
        val s = _state.value
        // округляем, а не отбрасываем дробную часть: 59,8 км/ч — это 60 (скорость уже сглажена в GpsTracker)
        val kmh = ru.shturman.routing.SpeedMeter.kmh(speedMps.toDouble())
        if (kmh != s.speedKmh) _state.update { it.copy(speedKmh = kmh) }
        updateHud(courseDeg, altitudeM, accuracyM)
        // Запоминаем, как быстро здесь едут: всегда в поездке, а вне её только на скорости
        if (s.navigating || speedMps > 3f) {
            recorder.add(here.lat, here.lon, speedMps.toDouble(), System.currentTimeMillis())
        }
        // Остановки (светофоры) считаем в навигации и в любой поездке на машине: недавно ехали быстрее ~22 км/ч
        if (speedMps >= 6f) lastCarSpeedAt = System.currentTimeMillis()
        if (s.navigating || System.currentTimeMillis() - lastCarSpeedAt < 5 * 60_000L) {
            stopRecorder.add(here, speedMps.toDouble(), System.currentTimeMillis())
        }
        if (s.navigating && kmh > tripMaxKmh && kmh < 300) tripMaxKmh = kmh
        if (s.navigating) {
            if (speedMps > 1.5f) {
                lastMovingAt = System.currentTimeMillis()
                if (navPaused) resumeNav()
            }
        }
        detectHardBraking(here, speedMps)
        caravan.sendPosition(here, selfOff)
        val route = s.route ?: return null
        if (!s.navigating) return null

        val fromIndex = s.position?.index ?: 0
        val near = snapToRoute(route, here, fromIndex)
        var snap = near?.takeIf { it.offsetM <= SNAP_MAX_M }
        var closest = near
        if (snap == null) {
            // Не нашли рядом с прошлым местом: ищем дальше вперёд по всему маршруту, чтобы не застрять
            // ищем дальше, но не дальше 2 км вперёд: иначе на дороге «туда-обратно» стрелку может унести к концу
            val fromM = route.cumM.getOrElse(fromIndex.coerceIn(0, route.cumM.size - 1)) { 0.0 }
            var ahead = fromIndex.coerceAtLeast(0)
            while (ahead < route.cumM.size - 1 && route.cumM[ahead] - fromM < 2000.0) ahead++
            val wide = snapToRoute(route, here, fromIndex, window = (ahead - fromIndex).coerceAtLeast(150))
            closest = wide ?: near
            snap = wide?.takeIf { it.offsetM <= SNAP_MAX_M }
            if (snap == null) ru.shturman.routing.Diag.snapMisses++
        }
        ru.shturman.routing.Diag.snapOffset = snap?.offsetM ?: -1.0
        ru.shturman.routing.Diag.snapIndex = snap?.index ?: -1
        val position = if (snap != null) {
            RoutePosition(snap.index, snap.traveledM)
        } else {
            locateOnRoute(route, here, fromIndex)
        }
        val turn = nextTurn(route, position)
        announceTurn(turn, speedMps)
        vibrateForTurn(turn?.distanceM)
        val limit = limits.firstOrNull { position.traveledM >= it.startM && position.traveledM < it.endM }?.kmh
        // Один сигнал на одно превышение больше чем на 15 км/ч (routing/Overspeed.kt)
        if (overspeed.update(kmh, limit) && s.overspeedSound && limit != null) ThemeSounds.speeding(kmh - limit)
        val hazards = checkHazards(position.traveledM, s)
        val notes = runCatching { ru.shturman.routing.paceNotes(route, position.traveledM) }.getOrDefault(emptyList())
        // Крутой изгиб дороги впереди, а едем быстро: «Сбавьте до N» на дорожнике и голосом один раз
        val advice = ru.shturman.routing.curveAdvice(notes, turn?.distanceM, kmh)
        if (advice != null && s.voiceOn) {
            val at = position.traveledM + advice.second.distanceM
            if (spokenCurves.none { kotlin.math.abs(it - at) < 80.0 }) {
                spokenCurves += at
                voice.say(ru.shturman.routing.curvePhrase(advice.first), flush = false)
            }
        }
        warnIce(s)
        _state.update {
            it.copy(
                position = position, turn = turn, limitKmh = limit,
                notes = notes, curveAdviceKmh = advice?.first,
                hazard = hazards.first, cameraAhead = hazards.second
            )
        }

        updateNavNotification(kmh, nextTurn(route, position), route.totalM - position.traveledM)

        // Съезд с маршрута, разворот, пропущенный поворот, движение назад: решает детектор (routing/Deviation.kt)
        if (snap != null) lastTurnDistOnRoute = nextTurn(route, position)?.distanceM ?: Double.MAX_VALUE
        if (snap != null) selfOff = false
        val event = deviation.update(
            System.currentTimeMillis(), closest?.offsetM ?: Double.MAX_VALUE, accuracyM, speedMps,
            courseDeg, closest?.bearing ?: -1.0, lastTurnDistOnRoute
        )
        onDeviation(event, here)
        return snap
    }

    private val deviation = DeviationDetector(DeviationLevel.of(settings.getInt("dev_level", 1)))
    private var lastTurnDistOnRoute = Double.MAX_VALUE

    /** Я сейчас вне маршрута (видно остальным в караване). */
    private var selfOff = false
    private val offNotified = HashSet<String>()

    /** Ведущий узнаёт, что кто-то из каравана отклонился от маршрута. */
    private fun onCaravanMembers(m: List<ru.shturman.routing.CaravanMember>) {
        _state.update { it.copy(members = m) }
        if (!caravan.isLeader) return
        val offNow = m.filter { it.off }
        offNotified.retainAll(offNow.map { it.uid }.toSet())
        for (member in offNow) {
            if (offNotified.add(member.uid)) {
                showNotice("${member.name} отклонился от маршрута")
                if (_state.value.voiceOn) voice.say("Один из участников каравана съехал с маршрута", flush = false)
            }
        }
    }

    private fun onDeviation(event: DeviationEvent, here: LatLon) {
        if (event == DeviationEvent.OFF_ROUTE || event == DeviationEvent.U_TURN || event == DeviationEvent.MISSED_TURN) {
            selfOff = true
            // В караване ведомый не пересчитывает маршрут, если ведущий тоже съехал: ждём, куда поедет ведущий
            if (caravan.currentCode != null && !caravan.isLeader &&
                _state.value.members.any { it.leader && it.off }
            ) {
                showNotice("Ведущий свернул, ждём его")
                return
            }
        }
        when (event) {
            DeviationEvent.NONE -> Unit
            DeviationEvent.OFF_ROUTE -> reroute(here, "Перестраиваю маршрут")
            DeviationEvent.U_TURN -> reroute(here, "Разворот, строю новый маршрут")
            DeviationEvent.MISSED_TURN -> reroute(here, "Вы пропустили поворот, перестраиваю маршрут")
            DeviationEvent.REVERSE -> {
                showNotice("Вы едете в обратную сторону")
                if (_state.value.voiceOn) voice.say("Вы едете в обратную сторону", flush = false)
            }
            DeviationEvent.STOPPED_OFF -> {
                val phrase = "Мы стоим не на маршруте. Тронетесь, и я перестрою маршрут"
                showNotice("Стоим вне маршрута. Тронетесь, и маршрут перестроится, либо отмените его крестиком")
                if (_state.value.voiceOn) voice.say(phrase, flush = false)
            }
        }
    }

    /** Короткое сообщение на экране; через несколько секунд пропадает само. */
    /** Перед поездкой предупреждает о севшей батарее или почти полной памяти; ничего не блокирует. */
    private fun lowResourcesNotice() {
        runCatching {
            val app = getApplication<Application>()
            val bm = app.getSystemService(android.content.Context.BATTERY_SERVICE) as android.os.BatteryManager
            val pct = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val charging = bm.isCharging
            if (pct in 1..14 && !charging) {
                showNotice("Батарея $pct%. Подключите зарядку, навигация быстро её тратит")
                return
            }
            val free = android.os.StatFs(app.filesDir.path).availableBytes
            if (free < 100L * 1024 * 1024) showNotice("Мало памяти в телефоне: освободите место")
        }
    }

    private var vibeLevel = -1

    /** Вибро-отклик: за 300 м лёгкий, за 150 м средний, за 50 м сильный. Каждый раз один раз на поворот. */
    private fun vibrateForTurn(distM: Double?) {
        if (distM == null || !_state.value.turnVibro) return
        val level = when {
            distM <= 50.0 -> 2
            distM <= 150.0 -> 1
            distM <= 300.0 -> 0
            else -> -1
        }
        // новый поворот — вибрации заново, даже если он близко за прошлым
        val key = _state.value.turn?.key ?: -1
        if (key != vibeKey) { vibeKey = key; vibeLevel = -1 }
        if (level < 0) { vibeLevel = -1; return }
        if (level <= vibeLevel) return
        vibeLevel = level
        runCatching {
            @Suppress("DEPRECATION")
            val v = getApplication<Application>().getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            val ms = longArrayOf(40L, 90L, 180L)[level]
            if (v?.hasVibrator() == true) v.vibrate(android.os.VibrationEffect.createOneShot(ms, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }


    private var lastMovingAt = System.currentTimeMillis()
    private var navPaused = false
    private var lowBatteryWarned = false

    /** Простой больше 5 минут: отпускаем блокировку процессора, чтобы не тратить батарею. */
    private fun pauseNav() {
        navPaused = true
        ru.shturman.service.NavService.setWake(getApplication(), false)
        ru.shturman.service.NavService.update(getApplication(), "APEX · пауза", "Стоите больше 5 минут. Тронетесь, и навигация продолжится")
        showNotice("Навигация на паузе: стоим больше 5 минут")
    }

    private fun resumeNav() {
        navPaused = false
        ru.shturman.service.NavService.setWake(getApplication(), true)
    }

    /** Батарея меньше 15% во время поездки: один раз предупреждаем. */
    private fun checkBatteryDuringTrip() {
        runCatching {
            val bm = getApplication<Application>().getSystemService(android.content.Context.BATTERY_SERVICE) as android.os.BatteryManager
            val pct = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (pct in 1..14 && !bm.isCharging) {
                if (!lowBatteryWarned) {
                    lowBatteryWarned = true
                    showNotice("Батарея $pct%. Подключите зарядку")
                }
            } else if (pct > 20) lowBatteryWarned = false
        }
    }

    private var lastNotifAt = 0L
    private var gpsWatch: kotlinx.coroutines.Job? = null
    private var gpsLostShown = false

    /** Текст постоянного уведомления: скорость, ближайший поворот и сколько осталось ехать. Не чаще раза в 2 секунды. */
    private fun updateNavNotification(kmh: Int, turn: NextTurn?, leftM: Double) {
        if (navPaused) return
        val now = System.currentTimeMillis()
        if (now - lastNotifAt < 2_000L) return
        lastNotifAt = now
        gpsLostShown = false
        val title = "APEX · $kmh км/ч"
        val left = ru.shturman.ui.formatDistance(leftM.coerceAtLeast(0.0))
        val text = if (turn != null) {
            val side = if (turn.isLeft) "налево" else "направо"
            val street = if (turn.street.isNotBlank()) ", ${turn.street}" else ""
            "Через ${ru.shturman.ui.formatDistance(turn.distanceM)}: $side$street · осталось $left"
        } else "Осталось $left"
        ru.shturman.service.NavService.update(getApplication(), title, text)
    }

    /** Если положение не приходит больше 30 секунд, предупреждает и пишет это в уведомлении. */
    private fun startGpsWatch() {
        gpsWatch?.cancel()
        lastMovingAt = System.currentTimeMillis(); navPaused = false
        lastFixAt = maxOf(lastFixAt, System.currentTimeMillis())
        gpsWatch = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(5_000)
                if (!_state.value.navigating) break
                checkBatteryDuringTrip()
                if (!navPaused && System.currentTimeMillis() - lastMovingAt > 5 * 60_000L) pauseNav()
                if (System.currentTimeMillis() - lastFixAt > 30_000L && !gpsLostShown) {
                    gpsLostShown = true
                    showNotice("Потерян сигнал GPS")
                    if (_state.value.voiceOn) voice.say("Потерян сигнал GPS", flush = false)
                    ru.shturman.service.NavService.update(getApplication(), "APEX · нет сигнала GPS", "Выедите на открытое место")
                }
            }
        }
    }

    private fun stopNavService() {
        gpsWatch?.cancel(); gpsWatch = null
        ru.shturman.service.NavService.stop(getApplication())
    }


    private fun showNotice(text: String) {
        _state.update { it.copy(notice = text) }
        viewModelScope.launch {
            kotlinx.coroutines.delay(6_000)
            _state.update { if (it.notice == text) it.copy(notice = null) else it }
        }
    }

    fun setDevLevel(index: Int) {
        val i = index.coerceIn(0, 2)
        settings.edit().putInt("dev_level", i).apply()
        deviation.level = DeviationLevel.of(i)
        _state.update { it.copy(devLevel = i) }
    }

    private fun reroute(here: LatLon, phrase: String? = null) {
        val destination = _state.value.destination ?: return
        val now = System.currentTimeMillis()
        if (rerouting || now - lastRerouteAt < deviation.level.pauseMs) return
        rerouting = true
        lastRerouteAt = now
        if (phrase != null) {
            showNotice(phrase)
            if (_state.value.voiceOn) voice.say(phrase)
        }
        val gen = routeGen
        // выбранный вариант сохраняем: после съезда с «Без камер» новый путь тоже объезжает камеры
        val label = _state.value.route?.label ?: ""
        val cams = if (label.startsWith("Без камер")) cameraList.map { it.position } else emptyList()
        viewModelScope.launch {
            try {
                val result = router.route(here, destination.position, Date(), cams)
                if (result.options.isNotEmpty() && _state.value.navigating && gen == routeGen && _state.value.destination == destination) {
                    val fresh = result.options.firstOrNull { label.isNotEmpty() && it.label == label.substringBefore(" +") } ?: result.options[0]
                    val position = RoutePosition(0, 0.0)
                    tripDoneM += _state.value.position?.traveledM ?: 0.0
                    loadLimits(fresh)
                    prepareHazards(fresh)
                    _state.update {
                        it.copy(
                            options = listOf(fresh), selected = 0,
                            position = position, turn = nextTurn(fresh, position)
                        )
                    }
                    deviation.reset()
                    // новый маршрут — подсказки о поворотах и изгибах заново
                    spokenLevel = -1
                    lastTurnKey = -1
                    lastTurnDistance = Double.MAX_VALUE
                    spokenCurves.clear()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // Не вышло, попробуем снова через паузу
                ru.shturman.Errors.log("reroute", e)
            } finally {
                rerouting = false
            }
        }
    }

    /** Узнаёт у движка ограничения скорости вдоль маршрута (в фоне). */
    private fun loadLimits(route: RouteOption) {
        limits = emptyList()
        limitsJob?.cancel()
        limitsJob = viewModelScope.launch {
            val found = runCatching { router.speedLimits(route.points) }.getOrDefault(emptyList())
            // ответ для прежнего маршрута не подставляем к новому
            if (_state.value.route === route) limits = found
        }
    }

    /** Голосовая подсказка о ближайшем повороте: заранее, близко и в момент поворота, по одному разу. */
    /**
     * Голосовая подсказка о ближайшем повороте: заранее, близко и в момент поворота, по одному разу.
     * На скорости подсказки раньше: «сейчас» — за 4 секунды пути (не ближе 60 м), «близко» — за 12 секунд
     * (не ближе 250 м), а от 70 км/ч ещё и «через километр». По кольцу до своего съезда едем молча.
     */
    private fun announceTurn(turn: NextTurn?, speedMps: Float) {
        if (turn == null || !_state.value.voiceOn) return
        val d = turn.distanceM
        // начался следующий поворот (или маршрут перестроен и расстояние выросло)
        if (turn.key != lastTurnKey || d > lastTurnDistance + 150.0) spokenLevel = -1
        lastTurnKey = turn.key
        lastTurnDistance = d
        if (turn.onRoundabout) {
            spokenLevel = 9
            return
        }
        val v = speedMps.toDouble().coerceAtLeast(0.0)
        val nowM = maxOf(60.0, v * 4.0)
        val nearM = maxOf(250.0, v * 12.0).coerceAtMost(550.0)
        val level = when {
            d <= nowM -> 3
            d <= nearM -> 2
            d <= 600.0 -> 1
            d in 700.0..1100.0 && v >= 19.0 -> 0
            else -> -1
        }
        if (level > spokenLevel) {
            spokenLevel = level
            voice.say(turnPhrase(level, d, turn.isLeft, turn.severity, turn.roundaboutExit))
        }
    }

    /** Лежачие полицейские (за 100 м), ремонт (за 500 м) и камеры (за 600 м): текст, голос один раз и радар-круг. */
    private fun checkHazards(traveled: Double, st: NavState): Pair<String?, CameraAhead?> {
        var hazard: String? = null
        routeBumps.firstOrNull { it.first > traveled }?.let { (at, idx) ->
            val d = at - traveled
            if (d <= 100.0) {
                hazard = "Лежачий полицейский через ${d.toInt()} м"
                if (st.voiceOn && spoken.add("bump$idx")) voice.say("Впереди лежачий полицейский", flush = false)
            }
        }
        if (hazard == null) {
            routeWorks.firstOrNull { it.first > traveled }?.let { (at, idx) ->
                val d = at - traveled
                if (d <= 500.0) {
                    hazard = "Ремонт дороги через ${d.toInt()} м"
                    if (st.voiceOn && spoken.add("work$idx")) voice.say("Впереди ремонт дороги", flush = false)
                }
            }
        }
        // Камеры на карте и голосом отключены по решению владельца; они учитываются только в варианте маршрута «Без камер»
        val camera: CameraAhead? = null
        return hazard to camera
    }

    /** Резкое торможение (быстрее 4 м/с за секунду со скорости от 30 км/ч) запоминается как опасное место. */
    private fun detectHardBraking(here: LatLon, speedMps: Float) {
        val now = System.currentTimeMillis()
        val dt = (now - prevSpeedAt) / 1000.0
        if (prevSpeedAt != 0L && dt in 0.4..2.5 && prevSpeed >= 8f && (prevSpeed - speedMps) / dt >= 4.0) {
            viewModelScope.launch(Dispatchers.IO) {
                speedStore.recordDanger(here.lat, here.lon, now)
                val spots = speedStore.dangerSpots(2)
                _state.update { it.copy(danger = spots) }
            }
        }
        prevSpeed = speedMps
        prevSpeedAt = now
    }

    // ---------- настройки ----------

    private fun putBool(key: String, value: Boolean) = settings.edit().putBoolean(key, value).apply()

    fun setFullscreen(on: Boolean) { putBool("fullscreen", on); _state.update { it.copy(fullscreen = on) } }
    fun setSatellite(on: Boolean) { putBool("satellite", on); _state.update { it.copy(satellite = on) } }
    fun setHudMirror(on: Boolean) {
        putBool("hud_mirror", on)
        _state.update { it.copy(hudMirror = on) }
    }

    fun setNightScreen(on: Boolean) {
        putBool("night_screen", on)
        _state.update { it.copy(nightScreen = on, night = on && darkNow()) }
    }

    /** Темно ли сейчас там, где мы (по солнцу); без положения — по часам: с 20 до 6. */
    private fun darkNow(): Boolean {
        val now = System.currentTimeMillis()
        val here = lastFix ?: lastKnownPosition(getApplication())
        if (here != null) return ru.shturman.routing.isDark(here, now)
        val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return h >= 20 || h < 6
    }

    /** Холодно (до +3°): возможен гололёд — один раз за поездку голосом и коротким сообщением. */
    private fun warnIce(s: NavState) {
        if (iceWarned || !s.navigating) return
        if (System.currentTimeMillis() - tripStartMs < 8_000L) return // сначала звук старта и первая подсказка
        val t = (s.hud.weather ?: s.weather)?.tempC ?: return
        if (t > 3) return
        iceWarned = true
        showNotice("Возможен гололёд: ${if (t > 0) "+$t" else "$t"}°")
        if (s.voiceOn) voice.say("Осторожно, возможен гололёд", flush = false)
    }

    fun setOverspeedSound(on: Boolean) {
        putBool("overspeed", on)
        _state.update { it.copy(overspeedSound = on) }
    }
    fun setNightVoice(on: Boolean) { putBool("night_voice", on); voice.nightMode = on; _state.update { it.copy(nightVoice = on) } }
    fun setMap3d(on: Boolean) { putBool("map3d", on); _state.update { it.copy(map3d = on) } }
    fun setTurnVibro(on: Boolean) { putBool("turn_vibro", on); _state.update { it.copy(turnVibro = on) } }
    fun setRadar(on: Boolean) { putBool("radar", on); _state.update { it.copy(radarOn = on) } }

    /** Тема оформления: цвета, шрифты, формы, анимации и звуки. Функции приложения те же. Голос навигатора не меняется. */
    fun setTheme(id: Int) {
        settings.edit().putInt(ru.shturman.ui.themes.Themes.PREF, id).apply()
        ShColors.themeId = id
        voice.princess = id == 1
        ThemeSounds.setTheme(getApplication(), id)
        _state.update { it.copy(theme = id) }
    }

    /** «Штурман 2»: открывает скрытый режим навсегда и сразу включает тему APEX. */
    fun unlockShturman2() {
        ru.shturman.ui.themes.Shturman2.unlock(settings)
        setTheme(ru.shturman.ui.themes.Themes.APEX)
    }

    fun setThemeSounds(on: Boolean) {
        putBool("theme_sounds", on)
        ThemeSounds.enabled = on
    }

    /** Что показывать на нижней карточке: key — percent, distance, eta, remaining, fuel. */
    fun setCardOption(key: String, on: Boolean) {
        putBool("card_$key", on)
        _state.update {
            when (key) {
                "percent" -> it.copy(cardPercent = on)
                "distance" -> it.copy(cardDistance = on)
                "eta" -> it.copy(cardEta = on)
                "remaining" -> it.copy(cardRemaining = on)
                else -> it.copy(cardFuel = on)
            }
        }
    }

    fun setConsumption(v: Float) { settings.edit().putFloat("consumption", v).apply(); _state.update { it.copy(consumption = v) } }
    fun setFuelPrice(v: Float) { settings.edit().putFloat("fuel_price", v).apply(); _state.update { it.copy(fuelPrice = v) } }

    // ---------- поездки и статистика ----------

    /** Записывает поездку в журнал и показывает итог в уведомлении. Совсем короткие (до 300 м) не записываются. */
    private fun finishTrip() {
        val s = _state.value
        if (!s.navigating || tripStartMs == 0L) return
        val traveled = tripDoneM + (s.position?.traveledM ?: 0.0)
        tripDoneM = 0.0
        if (traveled < 300.0) { tripStartMs = 0L; return }
        val dur = ((System.currentTimeMillis() - tripStartMs) / 1000).coerceAtLeast(1)
        val km = traveled / 1000.0
        val cost = Math.round(fuelLiters(traveled, dur.toDouble(), s.consumption.toDouble()) * s.fuelPrice).toInt()
        val avg = ru.shturman.stats.TripMath.avgKmh(km, dur)
        runCatching {
            tripLog.add(ru.shturman.stats.Trip(tripStartMs, km, dur, tripMaxKmh, avg, cost))
            ru.shturman.service.NavService.notifyTripEnd(
                getApplication(),
                "${ru.shturman.ui.formatKm(traveled)} км · ${ru.shturman.ui.formatDuration(dur.toDouble())} · средняя $avg км/ч · макс. $tripMaxKmh · $cost ₽"
            )
        }.onFailure { ru.shturman.Errors.log("finishTrip", it) }
        tripStartMs = 0L
    }

    /** Сводка для настроек: сегодня, неделя, месяц, последняя поездка, пробег. */
    fun statsText(): String {
        val now = System.currentTimeMillis()
        val trips = tripLog.trips()
        val day = ru.shturman.stats.TripMath.DAY_MS
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0)
        }
        val today = cal.timeInMillis
        fun km(from: Long) = String.format(java.util.Locale.US, "%.1f", ru.shturman.stats.TripMath.km(trips, from)).replace('.', ',')
        val last = trips.lastOrNull()
        return buildString {
            append("Сегодня: ${km(today)} км\n")
            append("За неделю: ${ru.shturman.stats.TripMath.count(trips, now - 7 * day)} поездок, ${km(now - 7 * day)} км\n")
            append("За месяц: ${ru.shturman.stats.TripMath.cost(trips, now - 30 * day)} ₽ бензина\n")
            if (last != null) append("Последняя: ${String.format(java.util.Locale.US, "%.1f", last.km).replace('.', ',')} км, средняя ${last.avgKmh}, макс. ${last.maxKmh} км/ч, ${last.costRub} ₽\n")
            append("Общий пробег в приложении: ${tripLog.odometerKm.toInt()} км")
        }
    }

    fun tripCount(): Int = runCatching { tripLog.trips().size }.getOrDefault(0)

    fun exportText(): String =
        ru.shturman.stats.TripMath.csv(tripLog.trips()) + "\n" + ru.shturman.stats.TripMath.refuelCsv(tripLog.refuels())

    fun clearTrips() = tripLog.clearTrips()
    fun refuelsText(): String = tripLog.refuels().takeLast(3).reversed().joinToString("\n") {
        "${java.text.SimpleDateFormat("dd.MM", java.util.Locale.US).format(java.util.Date(it.atMs))} ${it.place}: ${String.format(java.util.Locale.US, "%.1f", it.liters)} л по ${String.format(java.util.Locale.US, "%.1f", it.pricePerLiter)} ₽"
    }
    fun addRefuel(place: String, liters: Double, price: Double) {
        if (liters <= 0 || price <= 0) return
        tripLog.addRefuel(ru.shturman.stats.Refuel(System.currentTimeMillis(), place.trim().take(40), liters, price))
    }
    var serviceIntervalKm: Int
        get() = tripLog.serviceIntervalKm
        set(v) { tripLog.serviceIntervalKm = v }
    fun markServiceDone() = tripLog.markServiceDone()

    // ---------- показания в углу карты ----------

    private var lastHudAt = 0L
    private var lastWeatherAt = 0L

    private fun updateHud(courseDeg: Float, altitudeM: Float, accuracyM: Float) {
        val now = System.currentTimeMillis()
        if (now - lastHudAt < 1_000L) return
        lastHudAt = now
        _state.update {
            val h = it.hud
            it.copy(
                hud = h.copy(
                    heading = if (courseDeg >= 0f) courseDeg.toInt() else h.heading,
                    altitudeM = if (altitudeM.isNaN()) h.altitudeM else altitudeM.toInt(),
                    accuracyM = accuracyM.toInt()
                )
            )
        }
    }

    /** Раз в 30 секунд: батарея и сеть; раз в 30 минут: погода рядом. */
    private fun startHudLoop() {
        viewModelScope.launch {
            val app = getApplication<Application>()
            while (true) {
                runCatching {
                    val bm = app.getSystemService(android.content.Context.BATTERY_SERVICE) as android.os.BatteryManager
                    val cm = app.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                    val online = cm.getNetworkCapabilities(cm.activeNetwork)
                        ?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                    _state.update {
                        it.copy(hud = it.hud.copy(
                            batteryPct = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY),
                            charging = bm.isCharging, online = online
                        ))
                    }
                }
                // Ночной экран: после заката карта темнее (проверка по солнцу там, где мы)
                val dark = _state.value.nightScreen && darkNow()
                if (dark != _state.value.night) _state.update { it.copy(night = dark) }
                val here = lastFix
                val now = System.currentTimeMillis()
                if (here != null && now - lastWeatherAt > 30 * 60_000L) {
                    lastWeatherAt = now
                    val w = runCatching { fetchWeather(here, now) }.getOrNull()
                    if (w != null) _state.update { it.copy(hud = it.hud.copy(weather = w)) }
                }
                kotlinx.coroutines.delay(30_000)
            }
        }
    }

    /** Приветствие по времени суток на несколько секунд после запуска. */
    private fun showGreeting() {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val hello = when (hour) {
            in 5..11 -> "Доброе утро"
            in 12..17 -> "Добрый день"
            in 18..22 -> "Добрый вечер"
            else -> "Доброй ночи"
        }
        val first = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.displayName?.substringBefore(' ')
        val text = if (first.isNullOrBlank()) hello else "$hello, $first"
        _state.update { it.copy(hud = it.hud.copy(greeting = text)) }
        viewModelScope.launch {
            kotlinx.coroutines.delay(8_000)
            _state.update { it.copy(hud = it.hud.copy(greeting = null)) }
        }
    }

    /** Ссылка на моё положение для отправки другим людям. */
    fun locationLink(): String? {
        val p = lastFix ?: lastKnownPosition(getApplication()) ?: return null
        return String.format(java.util.Locale.US, "https://maps.google.com/?q=%.6f,%.6f", p.lat, p.lon)
    }

    // ---------- резервная копия настроек ----------

    /** Сохраняет настройки в аккаунт (Firestore), зашифровав на телефоне. Возвращает короткий итог для показа. */
    suspend fun backupSettings(): String {
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return "Нужен вход в аккаунт"
        return try {
            val json = org.json.JSONObject()
            for ((k, v) in settings.all) {
                if (k.startsWith("map_") || v == null) continue
                json.put(k, v)
            }
            val blob = ru.shturman.Crypto.encrypt(json.toString())
            withRetry { com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("backups").document(uid)
                .set(mapOf("data" to blob, "at" to System.currentTimeMillis())).await() }
            ru.shturman.service.NavService.notifyInfo(getApplication(), "Резервная копия сохранена", "Настройки записаны в ваш аккаунт")
            "Копия сохранена"
        } catch (e: kotlinx.coroutines.CancellationException) { throw e
        } catch (e: Exception) { ru.shturman.Errors.log("backup", e); friendlyNetError(e) }
    }

    /** Восстанавливает настройки из копии. После восстановления приложение лучше перезапустить. */
    suspend fun restoreSettings(): String {
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return "Нужен вход в аккаунт"
        return try {
            val doc = withRetry { com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("backups").document(uid).get().await() }
            val blob = doc.getString("data") ?: return "Копии пока нет"
            val text = ru.shturman.Crypto.decrypt(blob)
            if (text.isBlank()) return "Копию не удалось прочитать на этом телефоне"
            val json = org.json.JSONObject(text)
            val ed = settings.edit()
            for (k in json.keys()) {
                when (val v = json.get(k)) {
                    is Boolean -> ed.putBoolean(k, v)
                    is Int -> ed.putInt(k, v)
                    is Long -> ed.putLong(k, v)
                    is Double -> ed.putFloat(k, v.toFloat())
                    is String -> ed.putString(k, v)
                }
            }
            ed.apply()
            "Настройки восстановлены. Перезапустите приложение"
        } catch (e: kotlinx.coroutines.CancellationException) { throw e
        } catch (e: Exception) { ru.shturman.Errors.log("restore", e); friendlyNetError(e) }
    }

    // ---------- данные ----------

    /** Размер временных файлов (кэш карты и загрузок), байты. */
    fun cacheSizeBytes(): Long = runCatching {
        fun size(f: java.io.File): Long = if (f.isDirectory) (f.listFiles()?.sumOf { size(it) } ?: 0L) else f.length()
        size(getApplication<Application>().cacheDir)
    }.getOrDefault(0L)

    /** Удаляет временные файлы. Дорожные данные и настройки не трогает. */
    fun clearCache(): Long {
        val before = cacheSizeBytes()
        runCatching {
            getApplication<Application>().cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        }
        return before - cacheSizeBytes()
    }

    /** Возвращает числовые настройки и переключатели к заводским. Дом, работа и тема остаются. */
    fun resetSettings() {
        settings.edit()
            .remove("voice").remove("fullscreen").remove("satellite").remove("overspeed").remove("night_voice")
            .remove("radar").remove("card_percent").remove("card_distance").remove("card_eta")
            .remove("card_remaining").remove("card_fuel").remove("consumption").remove("fuel_price")
            .remove("dev_level").remove("theme_sounds").remove("turn_vibro").remove("map3d")
            .remove("hud_mirror").remove("night_screen")
            .apply()
        ThemeSounds.enabled = true
        deviation.level = DeviationLevel.MEDIUM
        voice.nightMode = true
        _state.update {
            it.copy(
                voiceOn = true, fullscreen = false, satellite = false, overspeedSound = true, nightVoice = true,
                radarOn = true, cardPercent = true, cardDistance = true, cardEta = true, cardRemaining = false,
                cardFuel = false, consumption = 8.0f, fuelPrice = 60.0f, devLevel = 1, turnVibro = true, map3d = true,
                hudMirror = true, nightScreen = true
            )
        }
    }

    // ---------- караван ----------

    fun createCaravan() {
        _state.update { it.copy(caravanMessage = "Создаю…") }
        viewModelScope.launch {
            try {
                val code = caravan.create()
                _state.update { it.copy(caravanCode = code, caravanLeader = true, caravanMessage = "Караван создан. Код: $code. Скажите его друзьям.") }
            } catch (e: Exception) {
                _state.update { it.copy(caravanMessage = "Не вышло. ${friendlyNetError(e)}") }
            }
        }
    }

    fun joinCaravan(code: String) {
        if (code.length != 4) {
            _state.update { it.copy(caravanMessage = "Код — четыре цифры") }
            return
        }
        _state.update { it.copy(caravanMessage = "Вхожу…") }
        viewModelScope.launch {
            try {
                caravan.join(code)
                _state.update { it.copy(caravanCode = code, caravanLeader = caravan.isLeader, caravanMessage = "Вы в караване $code") }
            } catch (e: Exception) {
                _state.update { it.copy(caravanMessage = "Не вышло. ${friendlyNetError(e)}") }
            }
        }
    }

    fun leaveCaravan() {
        caravan.leave()
        _state.update { it.copy(caravanCode = null, caravanLeader = false, caravanMessage = null, leaderDest = null, members = emptyList()) }
    }

    /** Участник каравана едет туда же, куда ведущий. */
    fun followLeader() {
        val d = _state.value.leaderDest ?: return
        chooseDestination(Place(d.title, "Пункт ведущего", d.position))
    }

    // ---------- голосовое управление ----------

    fun listen() {
        _state.update { it.copy(voiceMessage = null, heardText = null) }
        listener.start()
    }

    /** Результат системного окна распознавания речи (запасной путь, если встроенная служба не отвечает). */
    fun onSystemVoiceResult(text: String?) {
        if (text.isNullOrBlank()) _state.update { it.copy(voiceMessage = "Ничего не услышала, попробуйте ещё раз") } else handleVoice(text)
    }

    private fun reply(text: String) {
        _state.update { it.copy(voiceMessage = text) }
        if (_state.value.voiceOn) voice.say(text)
    }

    private fun handleVoice(text: String) {
        _state.update { it.copy(heardText = text) }
        val audio = getApplication<Application>().getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
        when (val cmd = parseVoiceCommand(text)) {
            VoiceCommand.Home -> _state.value.home?.let { reply("Едем домой"); chooseDestination(it) } ?: reply("Дом не задан")
            VoiceCommand.Work -> _state.value.work?.let { reply("Едем на работу"); chooseDestination(it) } ?: reply("Работа не задана")
            VoiceCommand.Cancel -> { clearRoute(); reply("Маршрут отменён") }
            VoiceCommand.Louder -> audio.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_RAISE, android.media.AudioManager.FLAG_SHOW_UI)
            VoiceCommand.Quieter -> audio.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_LOWER, android.media.AudioManager.FLAG_SHOW_UI)
            is VoiceCommand.To -> viewModelScope.launch {
                val found = runCatching { Geocoder.search(cmd.query) }.getOrDefault(emptyList()).firstOrNull()
                if (found != null) {
                    reply("Едем: ${found.title}")
                    chooseDestination(found)
                } else {
                    reply("Не нашла такой адрес")
                }
            }
            VoiceCommand.Unknown -> reply("Не расслышала, повторите")
        }
    }

    /** Запоминает «Дом» или «Работа» (kind = "home" или "work"). */
    fun setFavorite(kind: String, place: Place) {
        val o = org.json.JSONObject()
            .put("title", place.title).put("subtitle", place.subtitle)
            .put("lat", place.position.lat).put("lon", place.position.lon)
        settings.edit().putString("fav_$kind", o.toString()).apply()
        _state.update { if (kind == "home") it.copy(home = place) else it.copy(work = place) }
    }

    /** Кнопка «Проверить голос» в настройках. */
    fun testVoice() {
        _state.update { it.copy(voiceStatus = "Говорю…") }
        voice.say("Через 200 метров, направо")
    }

    fun setVoiceOn(on: Boolean) {
        settings.edit().putBoolean("voice", on).apply()
        _state.update { it.copy(voiceOn = on) }
    }

    /** Приехали: говорим об этом и убираем маршрут. */
    fun arrived() {
        if (_state.value.voiceOn) voice.say("Вы приехали")
        val ext = ru.shturman.api.ExternalTrip.active
        clearRoute()
        if (ext != null && ext.hasNext) _state.update { it.copy(nextOrderId = ext.orderId) }
    }


    /** Кнопка «Синхронизировать» в настройках. */
    fun syncNow() {
        if (_state.value.syncing) return
        _state.update { it.copy(syncing = true, syncMessage = null) }
        viewModelScope.launch {
            val message = try {
                sharedSync.sync()
            } catch (e: Exception) {
                ru.shturman.Errors.log("sync", e)
                "Не вышло. ${friendlyNetError(e)}"
            }
            if (!message.startsWith("Не вышло")) ThemeSounds.play(ThemeSound.SYNC) else ThemeSounds.play(ThemeSound.ERROR)
            _state.update { it.copy(syncing = false, syncMessage = message) }
        }
    }

    fun clearRoute() {
        routeGen++
        ru.shturman.api.ExternalTrip.active = null
        autoStart = false
        finishTrip()
        stopNavService()
        _state.update {
            it.copy(
                destination = null, options = emptyList(), selected = 0, routeError = null,
                routeLoading = false, navigating = false, position = null, turn = null,
                departOffsetMin = 0, limitKmh = null, weather = null,
                departAdvice = null, seasonNote = null, hazard = null, cameraAhead = null, notes = emptyList()
            )
        }
    }

    override fun onCleared() {
        stopNavService()
        ru.shturman.service.NavService.onCancel = null
        listener.stop()
        caravan.leave()
        voice.shutdown()
        recorder.flush()
        router.close()
    }
}
