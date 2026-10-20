package ru.shturman

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.shturman.routing.LatLon
import ru.shturman.routing.Maneuver
import ru.shturman.routing.RoutePosition
import ru.shturman.routing.nextTurn
import ru.shturman.routing.RouteOption
import ru.shturman.routing.bearingDeg
import ru.shturman.routing.distanceM
import ru.shturman.routing.fuelLiters
import ru.shturman.routing.fuelText
import ru.shturman.routing.snapToRoute
import ru.shturman.routing.turnAngle
import ru.shturman.routing.turnSeverity

class GeoFuelTest {
    private fun line(n: Int): List<LatLon> = List(n) { LatLon(57.0 + it * 0.0009, 28.0) }
    private fun route(pts: List<LatLon>) = RouteOption(100.0, 1000.0, pts, emptyList())

    @Test fun severityBoundaries() {
        assertNull(turnSeverity(9.9)); assertEquals(6, turnSeverity(10.0)); assertEquals(6, turnSeverity(24.9))
        assertEquals(5, turnSeverity(25.0)); assertEquals(4, turnSeverity(45.0)); assertEquals(3, turnSeverity(70.0))
        assertEquals(2, turnSeverity(100.0)); assertEquals(1, turnSeverity(140.0)); assertEquals(1, turnSeverity(180.0))
        assertEquals(3, turnSeverity(-90.0)); assertNull(turnSeverity(0.0))
    }

    @Test fun turnAngleWraps() {
        assertEquals(90.0, turnAngle(0.0, 90.0), 1e-9)
        assertEquals(-90.0, turnAngle(0.0, 270.0), 1e-9)
        assertEquals(20.0, turnAngle(350.0, 10.0), 1e-9)
    }

    @Test fun distanceAndBearing() {
        val a = LatLon(57.0, 28.0)
        assertEquals(0.0, distanceM(a, a), 1e-6)
        assertEquals(111.2, distanceM(a, LatLon(57.001, 28.0)), 1.0)
        assertEquals(0.0, bearingDeg(a, LatLon(57.01, 28.0)), 0.5)
        assertEquals(180.0, bearingDeg(a, LatLon(56.99, 28.0)), 0.5)
    }

    @Test fun cumulativeLength() {
        assertEquals(0, route(emptyList()).cumM.size)
        assertEquals(1000.0, route(emptyList()).totalM, 0.0)
        assertEquals(0.0, route(line(1)).totalM, 0.0)
        val r = route(line(5000))
        assertEquals(4999 * 100.07, r.totalM, 100.0)
        assertTrue(r.cumM[10] > r.cumM[9])
    }

    @Test fun snapEdgeCases() {
        assertNull(snapToRoute(route(emptyList()), LatLon(57.0, 28.0), 0))
        assertNull(snapToRoute(route(line(1)), LatLon(57.0, 28.0), 0))
        val r = route(line(12))
        val s = snapToRoute(r, LatLon(57.0 + 5.5 * 0.0009, 28.00005), 0)
        assertNotNull(s)
        assertEquals(5, s!!.index)
        assertTrue(s.offsetM < 10.0)
    }

    @Test fun snapPrefersOwnDirection() {
        // дорога «туда-обратно»: туда на север, обратно на юг почти по той же линии
        val pts = List(6) { LatLon(57.0 + it * 0.0009, 28.0) } + List(5) { LatLon(57.0 + (4 - it) * 0.0009, 28.00002) }
        val r = route(pts)
        val here = LatLon(57.0 + 2.5 * 0.0009, 28.00001)
        assertEquals(2, snapToRoute(r, here, 0)!!.index)
        assertEquals(2, snapToRoute(r, here, 0, speedMps = 10f, courseDeg = 0f)!!.index)
        assertEquals(7, snapToRoute(r, here, 0, speedMps = 10f, courseDeg = 180f)!!.index)
    }

    @Test fun turnSideFromManeuverType() {
        val pts = line(12)
        fun turnOf(type: Int) = nextTurn(RouteOption(100.0, 1000.0, pts, listOf(Maneuver(type, "", "", 5))), RoutePosition(0, 0.0))
        // выезд направо почти без угла — всё равно подсказка, «плавно направо»
        val exit = turnOf(20)!!
        assertEquals(6, exit.severity); assertEquals(false, exit.isLeft)
        // разворот налево: сторона из типа, резкость — «резко»
        val u = turnOf(13)!!
        assertEquals(1, u.severity); assertEquals(true, u.isLeft)
        // обычный поворот на прямой дороге — не поворот
        assertNull(turnOf(15))
    }

    @Test fun fuelBasics() {
        assertEquals(0.0, fuelLiters(0.0, 100.0, 8.0), 0.0)
        assertEquals(0.0, fuelLiters(1000.0, 0.0, 8.0), 0.0)
        // 10 км за 10 мин = 60 км/ч -> коэффициент 0.95
        assertEquals(10.0 * 8.0 / 100.0 * 0.95, fuelLiters(10000.0, 600.0, 8.0), 1e-9)
        // пробка медленнее тратит больше на тот же путь
        assertTrue(fuelLiters(10000.0, 3000.0, 8.0) > fuelLiters(10000.0, 600.0, 8.0))
    }

    @Test fun fuelTextFormat() {
        assertTrue(fuelText(5.0, 100.0).endsWith("500 ₽"))
        assertTrue(fuelText(5.0, 120.0).endsWith("600 ₽"))
    }
}
