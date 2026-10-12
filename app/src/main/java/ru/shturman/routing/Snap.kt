package ru.shturman.routing

import kotlin.math.cos
import kotlin.math.hypot

/** Точка на линии маршрута, ближайшая к положению телефона. */
class Snap(
    /** Куда поставить стрелку: точка прямо на линии маршрута. */
    val point: LatLon,
    /** Куда смотрит дорога в этой точке, градусы по часовой от севера. */
    val bearing: Double,
    /** Номер вершины маршрута, с которой начинается отрезок под стрелкой. */
    val index: Int,
    /** Сколько метров проехано от начала маршрута. */
    val traveledM: Double,
    /** Насколько GPS-точка была в стороне от линии, метры. */
    val offsetM: Double
)

private const val METERS_PER_DEGREE = 111_320.0

/**
 * Находит на маршруте точку, ближайшую к (here). Ищет только рядом с прошлым положением,
 * чтобы стрелка не прыгала назад и не перескакивала на встречный участок.
 * null — если маршрут пустой.
 */
fun snapToRoute(route: RouteOption, here: LatLon, fromIndex: Int, window: Int = 150): Snap? {
    val pts = route.points
    if (pts.size < 2) return null
    val last = pts.size - 2
    val start = (fromIndex - 3).coerceIn(0, last)
    val end = (fromIndex + window).coerceIn(start, last)
    val k = cos(Math.toRadians(here.lat))

    var best: Snap? = null
    var bestD = Double.MAX_VALUE
    for (i in start..end) {
        // Считаем в метрах на плоскости, с началом координат в точке here
        val ax = (pts[i].lon - here.lon) * METERS_PER_DEGREE * k
        val ay = (pts[i].lat - here.lat) * METERS_PER_DEGREE
        val bx = (pts[i + 1].lon - here.lon) * METERS_PER_DEGREE * k
        val by = (pts[i + 1].lat - here.lat) * METERS_PER_DEGREE
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 < 1e-6) 0.0 else ((-ax * dx - ay * dy) / len2).coerceIn(0.0, 1.0)
        val px = ax + t * dx
        val py = ay + t * dy
        val d = hypot(px, py)
        if (d < bestD) {
            bestD = d
            best = Snap(
                point = LatLon(here.lat + py / METERS_PER_DEGREE, here.lon + px / (METERS_PER_DEGREE * k)),
                bearing = bearingDeg(pts[i], pts[i + 1]),
                index = i,
                traveledM = route.cumM[i] + t * hypot(dx, dy),
                offsetM = d
            )
        }
    }
    return best
}
