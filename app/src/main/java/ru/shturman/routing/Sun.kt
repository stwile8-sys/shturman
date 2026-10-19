package ru.shturman.routing

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Высота солнца над горизонтом в градусах для места и момента (упрощённая формула, точность ~0,5°).
 * Меньше нуля — солнце село.
 */
fun sunElevationDeg(at: LatLon, timeMs: Long): Double {
    val d = timeMs / 86_400_000.0 + 2440587.5 - 2451545.0 // дни от эпохи J2000
    val g = Math.toRadians((357.529 + 0.98560028 * d) % 360.0)
    val q = (280.459 + 0.98564736 * d) % 360.0
    val l = Math.toRadians((q + 1.915 * sin(g) + 0.020 * sin(2 * g)) % 360.0)
    val e = Math.toRadians(23.439 - 0.00000036 * d)
    val ra = atan2(cos(e) * sin(l), cos(l))
    val dec = asin(sin(e) * sin(l))
    val gmstHours = (18.697374558 + 24.06570982441908 * d) % 24.0
    val ha = Math.toRadians(gmstHours * 15.0 + at.lon) - ra
    val lat = Math.toRadians(at.lat)
    return Math.toDegrees(asin(sin(lat) * sin(dec) + cos(lat) * cos(dec) * cos(ha)))
}

/** Уже темно: солнце ниже горизонта больше чем на 3° (после заката и сумерек). */
fun isDark(at: LatLon, timeMs: Long): Boolean = sunElevationDeg(at, timeMs) < -3.0
