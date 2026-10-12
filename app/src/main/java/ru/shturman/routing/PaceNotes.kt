package ru.shturman.routing

import kotlin.math.abs
import kotlin.math.min

/** Одна «запись пилота» из раллийного дорожника: куда и насколько круто гнётся дорога впереди. */
class PaceNote(
    /** 1 — шпилька, 6 — едва заметный изгиб (как в ралли). */
    val severity: Int,
    val isLeft: Boolean,
    /** Сколько метров до начала изгиба. */
    val distanceM: Double,
    val lengthM: Double
)

private const val STEP_M = 10.0

/**
 * Читает форму дороги впереди (а не только повороты на перекрёстках) и выдаёт дорожник: ближайшие
 * изгибы с направлением и резкостью. Резкость считается по радиусу: чем меньше радиус, тем меньше цифра.
 */
fun paceNotes(route: RouteOption, traveledM: Double, aheadM: Double = 900.0, maxNotes: Int = 3): List<PaceNote> {
    val pts = route.points
    val cum = route.cumM
    if (pts.size < 2 || cum.size != pts.size) return emptyList()
    val endM = min(cum.last(), traveledM + aheadM)
    if (endM - traveledM < 3 * STEP_M) return emptyList()

    // Точки линии через каждые 10 метров
    val samples = ArrayList<LatLon>()
    var i = 0
    var d = traveledM.coerceAtLeast(0.0)
    while (d <= endM) {
        while (i < pts.size - 2 && cum[i + 1] < d) i++
        val seg = cum[i + 1] - cum[i]
        val t = if (seg < 1e-6) 0.0 else ((d - cum[i]) / seg).coerceIn(0.0, 1.0)
        samples.add(LatLon(pts[i].lat + (pts[i + 1].lat - pts[i].lat) * t, pts[i].lon + (pts[i + 1].lon - pts[i].lon) * t))
        d += STEP_M
    }
    if (samples.size < 4) return emptyList()

    val heading = DoubleArray(samples.size - 1) { bearingDeg(samples[it], samples[it + 1]) }
    val notes = ArrayList<PaceNote>()
    var start = -1
    var acc = 0.0
    var calm = 0

    fun close(endStep: Int) {
        if (start < 0) return
        val angle = abs(acc)
        val length = (endStep - start + 1) * STEP_M
        if (angle >= 15.0) {
            val radius = length / Math.toRadians(angle)
            val severity = when {
                radius <= 15 -> 1
                radius <= 30 -> 2
                radius <= 50 -> 3
                radius <= 80 -> 4
                radius <= 140 -> 5
                else -> 6
            }
            if (radius <= 300) notes.add(PaceNote(severity, acc < 0, start * STEP_M, length))
        }
        start = -1
        acc = 0.0
        calm = 0
    }

    for (k in 0 until heading.size - 1) {
        val delta = turnAngle(heading[k], heading[k + 1])
        if (abs(delta) >= 2.0) {
            if (start >= 0 && (delta < 0) != (acc < 0)) close(k)
            if (start < 0) start = k
            acc += delta
            calm = 0
        } else if (start >= 0) {
            calm++
            if (calm >= 3) close(k)
        }
    }
    close(heading.size - 2)
    return notes.take(maxNotes)
}
