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
import ru.shturman.routing.Geocoder
import ru.shturman.routing.LatLon
import ru.shturman.routing.NextTurn
import ru.shturman.routing.Place
import ru.shturman.routing.RoadData
import ru.shturman.routing.RouteOption
import ru.shturman.routing.RoutePosition
import ru.shturman.routing.Router
import ru.shturman.routing.LimitSeg
import ru.shturman.routing.Snap
import ru.shturman.routing.SpeedRecorder
import ru.shturman.routing.SpeedStore
import ru.shturman.routing.forecastTimeSec
import ru.shturman.routing.lastKnownPosition
import ru.shturman.routing.locateOnRoute
import ru.shturman.routing.nextTurn
import ru.shturman.routing.snapToRoute
import java.util.Date

/** Дальше этого расстояния от линии маршрута стрелка уже не прилипает к дороге. */
private const val SNAP_MAX_M = 40.0

/** Столько подряд положений вдали от маршрута нужно, чтобы решить, что мы свернули, и построить новый путь. */
private const val OFF_ROUTE_FIXES = 4
private const val REROUTE_PAUSE_MS = 15_000L

/** Всё, что показывает экран: состояние данных, поиск, маршрут и поездка. */
data class NavState(
    val dataReady: Boolean = false,
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

    /** Ограничение скорости на участке, где мы сейчас едем, км/ч (если оно известно). */
    val limitKmh: Int? = null
) {
    val route: RouteOption? get() = options.getOrNull(selected)
}

class NavViewModel(app: Application) : AndroidViewModel(app) {

    private val roadData = RoadData(app)
    private val router = Router(roadData)

    private var limits: List<LimitSeg> = emptyList()
    private val speedStore = SpeedStore(app)
    private val recorder = SpeedRecorder(speedStore)
    private var offRouteCount = 0
    private var rerouting = false
    private var lastRerouteAt = 0L

    private val _state = MutableStateFlow(NavState(dataReady = roadData.isReady()))
    val state: StateFlow<NavState> = _state

    init {
        downloadDataIfNeeded()
        // Раз в неделю подтягиваем свежие дорожные данные, чтобы старые значения не оставались
        viewModelScope.launch {
            if (roadData.updateIfStale()) router.close()
        }
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
                _state.update { it.copy(dataError = "Не удалось скачать дорожные данные: ${e.message ?: e.javaClass.simpleName}") }
            }
        }
    }

    fun search(query: String) {
        if (query.trim().length < 3) {
            _state.update { it.copy(results = emptyList(), searching = false, searchError = null) }
            return
        }
        _state.update { it.copy(searching = true, searchError = null) }
        viewModelScope.launch {
            try {
                val found = Geocoder.search(query)
                _state.update { it.copy(results = found, searching = false) }
            } catch (e: Exception) {
                _state.update { it.copy(searching = false, searchError = "Поиск не сработал: ${e.message ?: "нет интернета"}") }
            }
        }
    }

    fun chooseDestination(place: Place) {
        _state.update { it.copy(destination = place, results = emptyList(), searchError = null) }
        buildRoute()
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
            _state.update { it.copy(routeError = "Дорожные данные ещё загружаются, ${(s.dataProgress * 100).toInt()}%") }
            return
        }
        val from = lastKnownPosition(getApplication())
        if (from == null) {
            _state.update { it.copy(routeError = "Нет сигнала GPS: выйдите на открытое место и подождите") }
            return
        }
        _state.update { it.copy(routeLoading = true, routeError = null) }
        viewModelScope.launch {
            try {
                val departAt = Date(System.currentTimeMillis() + s.departOffsetMin * 60_000L)
                val result = router.route(from, destination.position, departAt)
                // Время каждого варианта пересчитываем по запомненным скоростям на это время недели
                // Тяжёлый подсчёт идёт не в главном потоке; при любой ошибке остаётся время движка
                val forecast = withContext(Dispatchers.Default) {
                    runCatching {
                        result.options.map { o ->
                            RouteOption(forecastTimeSec(o, speedStore, departAt.time), o.lengthM, o.points, o.maneuvers)
                        }.sortedBy { it.timeSec }
                    }.getOrDefault(result.options)
                }
                _state.update {
                    it.copy(
                        routeLoading = false,
                        options = forecast,
                        selected = 0,
                        timeAware = result.timeAware
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        routeLoading = false,
                        options = emptyList(),
                        routeError = "Не удалось построить маршрут: ${e.message ?: e.javaClass.simpleName}"
                    )
                }
            }
        }
    }

    fun startNavigation() {
        val route = _state.value.route ?: return
        offRouteCount = 0
        loadLimits(route)
        val position = RoutePosition(0, 0.0)
        _state.update { it.copy(navigating = true, position = position, turn = nextTurn(route, position)) }
    }

    /**
     * Вызывается на каждое новое положение GPS. Во время поездки привязывает его к линии маршрута,
     * обновляет пройденный путь и ближайший поворот и возвращает точку, где рисовать стрелку.
     * Если телефон ушёл от маршрута дальше SNAP_MAX_M, стрелка остаётся на настоящем GPS-положении.
     */
    fun onFix(here: LatLon, speedMps: Float = 0f): Snap? {
        val s = _state.value
        // Запоминаем, как быстро здесь едут: всегда в поездке, а вне её только на скорости
        if (s.navigating || speedMps > 3f) {
            recorder.add(here.lat, here.lon, speedMps.toDouble(), System.currentTimeMillis())
        }
        val route = s.route ?: return null
        if (!s.navigating) return null

        val fromIndex = s.position?.index ?: 0
        val snap = snapToRoute(route, here, fromIndex)?.takeIf { it.offsetM <= SNAP_MAX_M }
        val position = if (snap != null) {
            RoutePosition(snap.index, snap.traveledM)
        } else {
            locateOnRoute(route, here, fromIndex)
        }
        val limit = limits.firstOrNull { position.traveledM >= it.startM && position.traveledM < it.endM }?.kmh
        _state.update { it.copy(position = position, turn = nextTurn(route, position), limitKmh = limit) }

        // Съехали с маршрута: после нескольких подряд положений вдали строим новый путь отсюда
        offRouteCount = if (snap == null) offRouteCount + 1 else 0
        if (offRouteCount >= OFF_ROUTE_FIXES) reroute(here)
        return snap
    }

    private fun reroute(here: LatLon) {
        val destination = _state.value.destination ?: return
        val now = System.currentTimeMillis()
        if (rerouting || now - lastRerouteAt < REROUTE_PAUSE_MS) return
        rerouting = true
        lastRerouteAt = now
        viewModelScope.launch {
            try {
                val result = router.route(here, destination.position, Date())
                if (result.options.isNotEmpty() && _state.value.navigating) {
                    val fresh = result.options[0]
                    val position = RoutePosition(0, 0.0)
                    loadLimits(fresh)
                    _state.update {
                        it.copy(
                            options = listOf(fresh), selected = 0,
                            position = position, turn = nextTurn(fresh, position)
                        )
                    }
                    offRouteCount = 0
                }
            } catch (e: Exception) {
                // Не вышло, попробуем снова через паузу
            } finally {
                rerouting = false
            }
        }
    }

    /** Узнаёт у движка ограничения скорости вдоль маршрута (в фоне). */
    private fun loadLimits(route: RouteOption) {
        limits = emptyList()
        viewModelScope.launch {
            val found = runCatching { router.speedLimits(route.points) }.getOrDefault(emptyList())
            limits = found
        }
    }

    fun clearRoute() {
        _state.update {
            it.copy(
                destination = null, options = emptyList(), selected = 0, routeError = null,
                routeLoading = false, navigating = false, position = null, turn = null,
                departOffsetMin = 0, limitKmh = null
            )
        }
    }

    override fun onCleared() {
        recorder.flush()
        router.close()
    }
}
