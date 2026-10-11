package ru.shturman

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
import ru.shturman.routing.lastKnownPosition
import ru.shturman.routing.locateOnRoute
import ru.shturman.routing.nextTurn
import java.util.Date

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
    val turn: NextTurn? = null
) {
    val route: RouteOption? get() = options.getOrNull(selected)
}

class NavViewModel(app: Application) : AndroidViewModel(app) {

    private val roadData = RoadData(app)
    private val router = Router(roadData)

    private val _state = MutableStateFlow(NavState(dataReady = roadData.isReady()))
    val state: StateFlow<NavState> = _state

    init {
        downloadDataIfNeeded()
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
                _state.update {
                    it.copy(
                        routeLoading = false,
                        options = result.options,
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
        val position = RoutePosition(0, 0.0)
        _state.update { it.copy(navigating = true, position = position, turn = nextTurn(route, position)) }
    }

    /** Вызывается каждую секунду во время поездки: обновляет пройденный путь и ближайший поворот. */
    fun tick(here: LatLon) {
        val s = _state.value
        val route = s.route ?: return
        if (!s.navigating) return
        val position = locateOnRoute(route, here, s.position?.index ?: 0)
        _state.update { it.copy(position = position, turn = nextTurn(route, position)) }
    }

    fun clearRoute() {
        _state.update {
            it.copy(
                destination = null, options = emptyList(), selected = 0, routeError = null,
                routeLoading = false, navigating = false, position = null, turn = null,
                departOffsetMin = 0
            )
        }
    }

    override fun onCleared() {
        router.close()
    }
}
