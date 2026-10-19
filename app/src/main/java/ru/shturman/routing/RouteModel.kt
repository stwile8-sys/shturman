package ru.shturman.routing

/** Типы манёвров Valhalla, которые разбираем особо. */
const val MANEUVER_ROUNDABOUT_ENTER = 26
const val MANEUVER_ROUNDABOUT_EXIT = 27

/**
 * Полоса перед манёвром. directions — какие стрелки нарисованы на полосе (биты, как в Valhalla):
 * 1 нет стрелки, 2 прямо, 4 резко налево, 8 налево, 16 плавно налево, 32 плавно направо, 64 направо,
 * 128 резко направо, 256 разворот, 512 перестроение влево, 1024 перестроение вправо.
 * active — по этой полосе лучше всего ехать (бит направления), valid — по ней тоже можно, 0 — нельзя.
 */
class Lane(val directions: Int, val active: Int = 0, val valid: Int = 0) {
    val good: Boolean get() = active != 0 || valid != 0
}

/** Один манёвр маршрута (поворот, съезд и т.п.). */
class Maneuver(
    val type: Int,
    val instruction: String,
    val street: String,
    val startIndex: Int,
    /** Для въезда на кольцо: номер съезда. */
    val roundaboutExits: Int = 0,
    /** Полосы перед манёвром (если они размечены в данных), слева направо. */
    val lanes: List<Lane> = emptyList()
)

/** Вариант маршрута: линия, длина, время и манёвры. */
class RouteOption(
    val timeSec: Double,
    val lengthM: Double,
    val points: List<LatLon>,
    val maneuvers: List<Maneuver>,
    /** Название варианта: «Самый быстрый», «Без камер», «Спокойный». */
    val label: String = ""
) {
    /** Накопленное расстояние от начала маршрута до каждой точки, в метрах. */
    val cumM: DoubleArray = DoubleArray(points.size).also { arr ->
        for (i in 1 until points.size) arr[i] = arr[i - 1] + distanceM(points[i - 1], points[i])
    }

    val totalM: Double get() = if (cumM.isEmpty()) lengthM else cumM.last()
}

/** Следующий поворот для карточки в правом верхнем углу. */
class NextTurn(
    val severity: Int,
    val isLeft: Boolean,
    val distanceM: Double,
    val street: String,
    /** Кольцо: номер съезда (0 — это не кольцо). */
    val roundaboutExit: Int = 0,
    /** Уже на кольце: distanceM — до нашего съезда. */
    val onRoundabout: Boolean = false,
    /** Куда уходим с кольца относительно направления въезда, градусы (минус — влево). */
    val exitAngle: Double = 0.0,
    /** Полосы перед манёвром, слева направо (пусто — нет данных). */
    val lanes: List<Lane> = emptyList(),
    /** Номер манёвра в маршруте: по нему видно, что начался следующий поворот. */
    val key: Int = -1
) {
    val isRoundabout: Boolean get() = roundaboutExit > 0
}

/** Ближайшая к машине точка линии маршрута: индекс и сколько уже проехано. */
class RoutePosition(val index: Int, val traveledM: Double)

/** Находит точку маршрута, ближайшую к (lat, lon). Ищет вперёд от fromIndex, чтобы не прыгать назад. */
fun locateOnRoute(route: RouteOption, here: LatLon, fromIndex: Int): RoutePosition {
    val pts = route.points
    if (pts.isEmpty()) return RoutePosition(0, 0.0)
    val start = (fromIndex - 5).coerceAtLeast(0)
    val end = (fromIndex + 400).coerceAtMost(pts.size - 1)
    var best = start
    var bestD = Double.MAX_VALUE
    for (i in start..end) {
        val d = distanceM(pts[i], here)
        if (d < bestD) {
            bestD = d
            best = i
        }
    }
    return RoutePosition(best, route.cumM[best])
}

private fun pointBack(pts: List<LatLon>, idx: Int, meters: Double): LatLon {
    var i = idx
    while (i > 0 && distanceM(pts[i], pts[idx]) < meters) i--
    return pts[i]
}

private fun pointAhead(pts: List<LatLon>, idx: Int, meters: Double): LatLon {
    var i = idx
    while (i < pts.size - 1 && distanceM(pts[idx], pts[i]) < meters) i++
    return pts[i]
}

/** Ближайший впереди поворот. Резкость считаем по форме дороги, как в ралли. Кольцо — отдельно, с номером съезда. */
fun nextTurn(route: RouteOption, position: RoutePosition): NextTurn? {
    val list = route.maneuvers
    for ((k, m) in list.withIndex()) {
        if (m.startIndex <= position.index) continue
        val pts = route.points
        if (m.type == MANEUVER_ROUNDABOUT_EXIT) {
            // Уже едем по кольцу: до съезда показываем кольцо с тем же номером
            val enter = list.getOrNull(k - 1)?.takeIf { it.type == MANEUVER_ROUNDABOUT_ENTER && it.roundaboutExits > 0 }
                ?: continue
            return roundaboutTurn(route, enter, m, position, onRing = true, key = k)
        }
        if (m.type == MANEUVER_ROUNDABOUT_ENTER && m.roundaboutExits > 0) {
            val exit = list.getOrNull(k + 1)?.takeIf { it.type == MANEUVER_ROUNDABOUT_EXIT }
            return roundaboutTurn(route, m, exit, position, onRing = false, key = k)
        }
        // 0..8 — старт, финиш, «продолжайте прямо» и подобное, не поворот
        if (m.type in 0..8) continue
        val idx = m.startIndex.coerceIn(0, route.points.size - 1)
        val before = bearingDeg(pointBack(pts, idx, 15.0), pts[idx])
        val after = bearingDeg(pts[idx], pointAhead(pts, idx, 15.0))
        val angle = turnAngle(before, after)
        val severity = turnSeverity(angle) ?: continue
        return NextTurn(
            severity = severity,
            isLeft = angle < 0,
            distanceM = (route.cumM[idx] - position.traveledM).coerceAtLeast(0.0),
            street = m.street,
            lanes = m.lanes,
            key = k
        )
    }
    return null
}

/** Кольцо: до въезда (или, если уже на кольце, до съезда), номер съезда и куда уходим с кольца. */
private fun roundaboutTurn(route: RouteOption, enter: Maneuver, exit: Maneuver?, position: RoutePosition, onRing: Boolean, key: Int): NextTurn {
    val pts = route.points
    val last = pts.size - 1
    val inIdx = enter.startIndex.coerceIn(0, last)
    val outIdx = (exit?.startIndex ?: inIdx).coerceIn(0, last)
    val before = bearingDeg(pointBack(pts, inIdx, 20.0), pts[inIdx])
    val after = bearingDeg(pts[outIdx], pointAhead(pts, outIdx, 20.0))
    val angle = if (exit != null) turnAngle(before, after) else 0.0
    val target = if (onRing) outIdx else inIdx
    return NextTurn(
        severity = 3,
        isLeft = angle < 0,
        distanceM = (route.cumM[target] - position.traveledM).coerceAtLeast(0.0),
        street = (exit?.street ?: "").ifEmpty { enter.street },
        roundaboutExit = enter.roundaboutExits,
        onRoundabout = onRing,
        exitAngle = angle,
        lanes = if (onRing) emptyList() else enter.lanes,
        key = key
    )
}
