package ru.shturman.routing

import kotlin.math.abs
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
fun snapToRoute(
    route: RouteOption, here: LatLon, fromIndex: Int, window: Int = 150,
    /** Скорость, м/с, и курс по GPS (минус — неизвестен): чтобы не прилипнуть к встречному участку. */
    speedMps: Float = 0f, courseDeg: Float = -1f
): Snap? {
    val pts = route.points
    if (pts.size < 2) return null
    val last = pts.size - 2
    val start = (fromIndex - 3).coerceIn(0, last)
    val end = (fromIndex + window).coerceIn(start, last)
    val k = cos(Math.toRadians(here.lat))

    // Расстояние от here до каждого отрезка и где на отрезке ближайшая точка (0…1)
    val n = end - start + 1
    val dist = DoubleArray(n)
    val frac = DoubleArray(n)
    var nearest = start
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
        val d = hypot(ax + t * dx, ay + t * dy)
        dist[i - start] = d
        frac[i - start] = t
        if (d < bestD) { bestD = d; nearest = i }
    }

    // Дорога «туда-обратно» или петля: рядом может быть несколько участков маршрута. Из почти одинаково
    // близких (не дальше ближайшего + 10 м) берём тот, что раньше по ходу, а на скорости — только те,
    // что смотрят примерно туда же, куда едем (не больше 100° от курса).
    val useCourse = speedMps > 3f && courseDeg >= 0f
    var pick = -1
    var pickD = Double.MAX_VALUE
    var anchorM = 0.0
    for (i in start..end) {
        val d = dist[i - start]
        if (d > bestD + 10.0) continue
        if (useCourse && distanceM(pts[i], pts[i + 1]) >= 1.0 &&
            abs(turnAngle(bearingDeg(pts[i], pts[i + 1]), courseDeg.toDouble())) > 100.0
        ) continue
        // где на маршруте окажемся, если прилипнем к этому отрезку
        val alongM = route.cumM[i] + frac[i - start] * (route.cumM[i + 1] - route.cumM[i])
        if (pick < 0) {
            pick = i; pickD = d; anchorM = alongM
        } else if (alongM - anchorM < 50.0) {
            // тот же проезд этого места (соседние отрезки): берём самый близкий
            if (d < pickD) { pick = i; pickD = d }
        }
    }
    if (pick < 0) pick = nearest

    val ax = (pts[pick].lon - here.lon) * METERS_PER_DEGREE * k
    val ay = (pts[pick].lat - here.lat) * METERS_PER_DEGREE
    val dx = (pts[pick + 1].lon - here.lon) * METERS_PER_DEGREE * k - ax
    val dy = (pts[pick + 1].lat - here.lat) * METERS_PER_DEGREE - ay
    val t = frac[pick - start]
    val px = ax + t * dx
    val py = ay + t * dy
    return Snap(
        point = LatLon(here.lat + py / METERS_PER_DEGREE, here.lon + px / (METERS_PER_DEGREE * k)),
        bearing = bearingDeg(pts[pick], pts[pick + 1]),
        index = pick,
        traveledM = route.cumM[pick] + t * hypot(dx, dy),
        offsetM = dist[pick - start]
    )
}
