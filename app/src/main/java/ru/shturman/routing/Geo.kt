package ru.shturman.routing

import java.util.Locale
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Точка на карте. */
data class LatLon(val lat: Double, val lon: Double)

/** Расстояние между двумя точками в метрах. */
fun distanceM(a: LatLon, b: LatLon): Double {
    val r = 6371000.0
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLon = Math.toRadians(b.lon - a.lon)
    val h = sin(dLat / 2).pow(2) +
        cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).pow(2)
    return 2 * r * asin(min(1.0, sqrt(h)))
}

/** Направление от точки a к точке b в градусах по часовой от севера. */
fun bearingDeg(a: LatLon, b: LatLon): Double {
    val p1 = Math.toRadians(a.lat)
    val p2 = Math.toRadians(b.lat)
    val dl = Math.toRadians(b.lon - a.lon)
    val y = sin(dl) * cos(p2)
    val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
    return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
}

/** Угол поворота в градусах: отрицательный — налево, положительный — направо. */
fun turnAngle(before: Double, after: Double): Double =
    ((after - before + 540.0) % 360.0) - 180.0

/**
 * Резкость поворота по шкале из ралли: 1 — самый резкий, 6 — самый плавный.
 * Меньше 10° считается прямой дорогой, для неё возвращается null.
 */
fun turnSeverity(angle: Double): Int? {
    val a = abs(angle)
    return when {
        a < 10 -> null
        a < 25 -> 6
        a < 45 -> 5
        a < 70 -> 4
        a < 100 -> 3
        a < 140 -> 2
        else -> 1
    }
}

/** Расшифровка линии маршрута, которую присылает Valhalla (polyline, 6 знаков после запятой). */
fun decodePolyline6(encoded: String): List<LatLon> {
    val out = ArrayList<LatLon>()
    var index = 0
    var lat = 0
    var lng = 0
    while (index < encoded.length) {
        var shift = 0
        var result = 0
        var b: Int
        do {
            b = encoded[index++].code - 63
            result = result or ((b and 0x1f) shl shift)
            shift += 5
        } while (b >= 0x20)
        lat += if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)

        shift = 0
        result = 0
        do {
            b = encoded[index++].code - 63
            result = result or ((b and 0x1f) shl shift)
            shift += 5
        } while (b >= 0x20)
        lng += if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)

        out.add(LatLon(lat / 1e6, lng / 1e6))
    }
    return out
}

private const val EMPTY_COLLECTION = """{"type":"FeatureCollection","features":[]}"""

/** GeoJSON линии для карты; null — пустая карта без линии. */
fun lineGeoJson(points: List<LatLon>?): String {
    if (points == null || points.size < 2) return EMPTY_COLLECTION
    val sb = StringBuilder("""{"type":"Feature","properties":{},"geometry":{"type":"LineString","coordinates":[""")
    for ((i, p) in points.withIndex()) {
        if (i > 0) sb.append(',')
        sb.append(String.format(Locale.US, "[%.6f,%.6f]", p.lon, p.lat))
    }
    sb.append("]}}")
    return sb.toString()
}

/** GeoJSON одной точки (пункт назначения); null — пусто. */
fun pointGeoJson(p: LatLon?): String {
    if (p == null) return EMPTY_COLLECTION
    return String.format(
        Locale.US,
        """{"type":"Feature","properties":{},"geometry":{"type":"Point","coordinates":[%.6f,%.6f]}}""",
        p.lon, p.lat
    )
}
