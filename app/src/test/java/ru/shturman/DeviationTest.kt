package ru.shturman

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.shturman.routing.DeviationDetector
import ru.shturman.routing.DeviationEvent
import ru.shturman.routing.DeviationLevel
import ru.shturman.routing.friendlyNetError
import ru.shturman.routing.withRetry
import java.io.IOException
import java.net.UnknownHostException

class DeviationTest {
    private val INF = Double.POSITIVE_INFINITY

    /** Прогоняет раз в секунду и возвращает первое событие и секунду, на которой оно случилось. */
    private fun run(
        d: DeviationDetector, seconds: Int, off: Double, acc: Float = 5f, speed: Float = 10f,
        course: Float = 0f, bearing: Double = 0.0, turn: Double = INF, startS: Int = 0
    ): Pair<DeviationEvent, Int>? {
        for (s in startS until startS + seconds) {
            val e = d.update(s * 1000L, off, acc, speed, course, bearing, turn)
            if (e != DeviationEvent.NONE) return e to s
        }
        return null
    }

    @Test fun onRouteNothing() {
        assertEquals(null, run(DeviationDetector(), 60, 5.0))
    }

    @Test fun offRouteAfterHold() {
        val r = run(DeviationDetector(), 30, 70.0, course = 90f, bearing = 0.0)
        assertEquals(DeviationEvent.OFF_ROUTE, r!!.first)
        assertTrue(r.second in 5..6)
    }

    @Test fun badAccuracyIgnored() {
        assertEquals(null, run(DeviationDetector(), 60, 200.0, acc = 40f))
    }

    @Test fun parallelRoadWaitsTenSeconds() {
        val r = run(DeviationDetector(), 30, 60.0, course = 0f, bearing = 0.0)
        assertEquals(DeviationEvent.OFF_ROUTE, r!!.first)
        assertTrue(r.second >= 10)
    }

    @Test fun lowSensitivityIgnoresSixtyMeters() {
        assertEquals(null, run(DeviationDetector(DeviationLevel.LOW), 60, 60.0, course = 90f))
    }

    @Test fun highSensitivityFiresAtFortyMeters() {
        val r = run(DeviationDetector(DeviationLevel.HIGH), 10, 40.0, course = 90f)
        assertEquals(DeviationEvent.OFF_ROUTE, r!!.first)
        assertTrue(r.second in 3..4)
    }

    @Test fun missedTurnWhenTurnWasJustBefore() {
        val r = run(DeviationDetector(), 30, 70.0, course = 90f, turn = 30.0)
        assertEquals(DeviationEvent.MISSED_TURN, r!!.first)
    }

    @Test fun uTurn() {
        val d = DeviationDetector()
        assertEquals(null, run(d, 2, 70.0, course = 0f, bearing = 90.0))
        val r = run(d, 10, 70.0, course = 180f, bearing = 90.0, startS = 2)
        assertEquals(DeviationEvent.U_TURN, r!!.first)
    }

    @Test fun reverseOnRouteThenPause() {
        val d = DeviationDetector()
        val r = run(d, 10, 3.0, course = 180f, bearing = 0.0)
        assertEquals(DeviationEvent.REVERSE, r!!.first)
        // повторного «в обратную сторону» сразу нет
        assertEquals(null, run(d, 5, 3.0, course = 180f, bearing = 0.0, startS = r.second + 1))
    }

    @Test fun longReverseIsUTurn() {
        // едем против маршрута 10 секунд подряд — это разворот, маршрут перестраивается
        val d = DeviationDetector()
        var event: Pair<DeviationEvent, Int>? = null
        for (s in 0 until 30) {
            val e = d.update(s * 1000L, 3.0, 5f, 10f, 180f, 0.0, INF)
            if (e == DeviationEvent.U_TURN) { event = e to s; break }
        }
        assertEquals(DeviationEvent.U_TURN, event!!.first)
        assertEquals(10, event.second)
    }

    @Test fun shortReverseNoUTurn() {
        val d = DeviationDetector()
        for (s in 0 until 8) assertTrue(d.update(s * 1000L, 3.0, 5f, 10f, 180f, 0.0, INF) != DeviationEvent.U_TURN)
        // снова едем по маршруту — отсчёт заново
        d.update(8_000L, 3.0, 5f, 10f, 0f, 0.0, INF)
        for (s in 9 until 18) assertTrue(d.update(s * 1000L, 3.0, 5f, 10f, 180f, 0.0, INF) != DeviationEvent.U_TURN)
    }

    @Test fun stoppedOffRouteOnce() {
        val d = DeviationDetector()
        var count = 0
        for (s in 0..400) {
            if (d.update(s * 1000L, 70.0, 5f, 0f, -1f, -1.0, INF) == DeviationEvent.STOPPED_OFF) count++
        }
        assertEquals(1, count)
    }

    @Test fun levelFallback() {
        assertEquals(DeviationLevel.MEDIUM, DeviationLevel.of(99))
        assertEquals(DeviationLevel.LOW, DeviationLevel.of(0))
    }

    @Test fun friendlyErrors() {
        assertTrue(friendlyNetError(UnknownHostException("x")).contains("интернета"))
        assertTrue(friendlyNetError(RuntimeException("wrap", UnknownHostException())).contains("интернета"))
        assertTrue(friendlyNetError(RuntimeException()).isNotBlank())
    }

    @Test fun retryThreeTimes() = runBlocking {
        var n = 0
        val v = withRetry(3, 1) { n++; if (n < 3) throw IOException("x"); "ok" }
        assertEquals("ok", v); assertEquals(3, n)
        var m = 0
        try { withRetry(3, 1) { m++; throw IOException("x") } } catch (e: IOException) { }
        assertEquals(3, m)
        var k = 0
        try { withRetry(3, 1) { k++; throw IllegalStateException("bug") } } catch (e: IllegalStateException) { }
        assertEquals(1, k)
    }
}
