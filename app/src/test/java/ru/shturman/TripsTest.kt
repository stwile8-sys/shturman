package ru.shturman

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.shturman.stats.Refuel
import ru.shturman.stats.Trip
import ru.shturman.stats.TripMath

class TripsTest {
    private val day = TripMath.DAY_MS
    private val now = 1_700_000_000_000L

    @Test fun roundTrip() {
        val t = Trip(now, 12.345, 900, 88, 49, 150)
        val d = TripMath.decode(TripMath.encode(t))!!
        assertEquals(t.startMs, d.startMs); assertEquals(12.345, d.km, 0.001); assertEquals(150, d.costRub)
    }

    @Test fun badLines() {
        assertNull(TripMath.decode("")); assertNull(TripMath.decode("1;2;3")); assertNull(TripMath.decode("a;b;c;d;e;f"))
    }

    @Test fun avgSpeed() {
        assertEquals(60, TripMath.avgKmh(30.0, 1800)); assertEquals(0, TripMath.avgKmh(0.0, 100)); assertEquals(0, TripMath.avgKmh(5.0, 0))
    }

    @Test fun trimOldAndLimit() {
        val old = Trip(now - 100 * day, 1.0, 60, 50, 50, 1)
        val fresh = Trip(now - day, 2.0, 60, 50, 50, 1)
        assertEquals(listOf(fresh), TripMath.trim(listOf(old, fresh), now))
        val many = List(700) { Trip(now - it * 1000L, 1.0, 60, 50, 50, 1) }
        assertEquals(TripMath.MAX_TRIPS, TripMath.trim(many, now).size)
    }

    @Test fun periodTotals() {
        val a = Trip(now - 10 * day, 10.0, 600, 60, 60, 100)
        val b = Trip(now - day, 5.0, 300, 60, 60, 50)
        assertEquals(5.0, TripMath.km(listOf(a, b), now - 7 * day), 1e-9)
        assertEquals(15.0, TripMath.km(listOf(a, b), now - 30 * day), 1e-9)
        assertEquals(1, TripMath.count(listOf(a, b), now - 7 * day))
        assertEquals(150, TripMath.cost(listOf(a, b), now - 30 * day))
    }

    @Test fun csvHasHeaderAndRows() {
        val lines = TripMath.csv(listOf(Trip(now, 1.0, 60, 40, 30, 10))).trim().lines()
        assertEquals(2, lines.size); assertTrue(lines[1].endsWith(";1.0;60;40;30;10"))
    }

    @Test fun refuelRoundTripEscapes() {
        val r = TripMath.decodeRefuel(TripMath.encode(Refuel(now, "АЗС; Лукойл", 40.0, 58.5)))!!
        assertEquals("АЗС, Лукойл", r.place); assertEquals(40.0, r.liters, 1e-9)
    }

    @Test fun serviceReminder() {
        assertFalse(TripMath.serviceDue(10000.0, 0.0, 0))
        assertFalse(TripMath.serviceDue(14999.0, 5000.0, 10000))
        assertTrue(TripMath.serviceDue(15000.0, 5000.0, 10000))
    }
}

class QuietHoursTest {
    @Test fun quietBoundaries() {
        val q = ru.shturman.service.AutoPrefs::isQuietHour
        assertTrue(q(23)); assertTrue(q(0)); assertTrue(q(6)); assertFalse(q(7)); assertFalse(q(22)); assertFalse(q(12))
    }
}

class CompassTest {
    @Test fun sides() {
        assertEquals("С", ru.shturman.ui.compassSide(0)); assertEquals("С", ru.shturman.ui.compassSide(359))
        assertEquals("В", ru.shturman.ui.compassSide(90)); assertEquals("Ю", ru.shturman.ui.compassSide(180))
        assertEquals("З", ru.shturman.ui.compassSide(270)); assertEquals("СВ", ru.shturman.ui.compassSide(45))
    }
}
