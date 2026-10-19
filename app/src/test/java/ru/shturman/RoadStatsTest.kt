package ru.shturman

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.shturman.routing.CellRow
import ru.shturman.routing.StopRow
import ru.shturman.routing.bestHourText
import ru.shturman.routing.computeRoadStats

class RoadStatsTest {
    private val now = 1_800_000_000_000L
    private val day = 24L * 3600 * 1000

    @Test fun emptyBase() {
        val s = computeRoadStats(emptyList(), emptyList(), now)
        assertEquals(0, s.places)
        assertEquals(0, s.score)
        assertEquals("Пока пусто", s.verdict)
    }

    @Test fun countsPlacesUsableAndFresh() {
        val cells = listOf(
            CellRow(1, 1, 32, 8.0, 5, now - day / 2),   // понедельник 8–9, свежая, годится
            CellRow(1, 1, 56, 10.0, 1, now - 3 * day),  // один проезд — для прогноза мало
            CellRow(2, 5, 32, 4.0, 3, now - 40 * day)   // старше 4 недель
        )
        val s = computeRoadStats(cells, emptyList(), now)
        assertEquals(2, s.places)
        assertEquals(3, s.records)
        assertEquals(1, s.usable)
        assertEquals(1, s.fresh24h)
        assertEquals(1, s.fresh7d)
        assertEquals(2, s.hours[32])
        assertEquals("понедельник, 8–9 ч", bestHourText(s.hours))
        assertTrue(s.score in 1..100)
    }

    @Test fun groupsStopsByPlace() {
        // один светофор в два разных часа и ещё один в стороне
        val stops = listOf(
            StopRow(57.80001, 28.33001, 32, 40.0, 2, now),
            StopRow(57.80002, 28.33002, 33, 60.0, 2, now),
            StopRow(57.81, 28.35, 32, 20.0, 1, now)
        )
        val s = computeRoadStats(emptyList(), stops, now)
        assertEquals(2, s.stopPlaces)
        assertEquals(44, s.avgWaitSec)
    }
}
