package ru.shturman.routing

/** Один манёвр маршрута (поворот, съезд и т.п.). */
class Maneuver(
    val type: Int,
    val instruction: String,
    val street: String,
    val startIndex: Int
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
    val street: String
)

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

/** Ближайший впереди поворот. Резкость считаем по форме дороги, как в ралли. */
fun nextTurn(route: RouteOption, position: RoutePosition): NextTurn? {
    for (m in route.maneuvers) {
        if (m.startIndex <= position.index) continue
        // 0..8 — старт, финиш, «продолжайте прямо» и подобное, не поворот
        if (m.type in 0..8) continue
        val idx = m.startIndex.coerceIn(0, route.points.size - 1)
        val pts = route.points
        val before = bearingDeg(pointBack(pts, idx, 15.0), pts[idx])
        val after = bearingDeg(pts[idx], pointAhead(pts, idx, 15.0))
        val angle = turnAngle(before, after)
        val severity = turnSeverity(angle) ?: continue
        return NextTurn(
            severity = severity,
            isLeft = angle < 0,
            distanceM = (route.cumM[idx] - position.traveledM).coerceAtLeast(0.0),
            street = m.street
        )
    }
    return null
}
