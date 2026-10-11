package ru.shturman.ui

import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * Минималистичная 3D гоночная машинка из трёх объёмных деталей: кузов, кабина, антикрыло.
 * Рисуется на карте как выдавленные многоугольники и поворачивается по курсу.
 * Цвета и пропорции можно менять прямо здесь.
 */
object CarModel {

    private const val BODY_COLOR = "#3DDCFF"
    private const val CABIN_COLOR = "#F2FBFF"
    private const val WING_COLOR = "#1AA5C4"

    // Форма в долях длины машины. x — вправо, y — вперёд, центр в (0, 0).
    private val BODY = listOf(
        -0.30 to -0.50, 0.30 to -0.50, 0.36 to -0.10, 0.22 to 0.35,
        0.09 to 0.50, -0.09 to 0.50, -0.22 to 0.35, -0.36 to -0.10
    )
    private val CABIN = listOf(
        -0.17 to -0.22, 0.17 to -0.22, 0.14 to 0.10, -0.14 to 0.10
    )
    private val WING = listOf(
        -0.38 to -0.50, 0.38 to -0.50, 0.38 to -0.42, -0.38 to -0.42
    )

    /**
     * Возвращает GeoJSON машинки.
     * lat, lon — положение; bearing — курс в градусах по часовой от севера;
     * lengthMeters — длина машинки в метрах на местности.
     */
    fun geoJson(lat: Double, lon: Double, bearing: Double, lengthMeters: Double): String {
        val l = lengthMeters
        val sb = StringBuilder("""{"type":"FeatureCollection","features":[""")
        sb.append(feature(BODY, lat, lon, bearing, l, BODY_COLOR, 0.02 * l, 0.20 * l))
        sb.append(',')
        sb.append(feature(CABIN, lat, lon, bearing, l, CABIN_COLOR, 0.20 * l, 0.34 * l))
        sb.append(',')
        sb.append(feature(WING, lat, lon, bearing, l, WING_COLOR, 0.20 * l, 0.27 * l))
        sb.append("]}")
        return sb.toString()
    }

    private fun feature(
        shape: List<Pair<Double, Double>>,
        lat: Double, lon: Double, bearing: Double, l: Double,
        color: String, base: Double, height: Double
    ): String {
        val theta = Math.toRadians(bearing)
        val fe = sin(theta)   // вперёд: на восток
        val fn = cos(theta)   // вперёд: на север
        val re = cos(theta)   // вправо: на восток
        val rn = -sin(theta)  // вправо: на север
        val metersPerDegLat = 111320.0
        val metersPerDegLon = 111320.0 * cos(Math.toRadians(lat))

        val coords = StringBuilder()
        val ring = shape + shape.first()
        for ((i, p) in ring.withIndex()) {
            val x = p.first * l
            val y = p.second * l
            val east = x * re + y * fe
            val north = x * rn + y * fn
            val pLon = lon + east / metersPerDegLon
            val pLat = lat + north / metersPerDegLat
            if (i > 0) coords.append(',')
            coords.append(String.format(Locale.US, "[%.7f,%.7f]", pLon, pLat))
        }
        return String.format(
            Locale.US,
            """{"type":"Feature","properties":{"c":"%s","b":%.2f,"h":%.2f},"geometry":{"type":"Polygon","coordinates":[[%s]]}}""",
            color, base, height, coords.toString()
        )
    }
}
