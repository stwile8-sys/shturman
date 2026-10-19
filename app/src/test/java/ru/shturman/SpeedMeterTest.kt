package ru.shturman

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.shturman.routing.SpeedMeter

class SpeedMeterTest {
    private val lat = 57.8
    private val lon = 28.3

    @Test fun standingStillShowsZero() {
        val m = SpeedMeter()
        // стоим: приёмник «плавает» на 1–3 км/ч
        val shown = listOf(0.4f, 0.9f, 0.6f, 0.8f, 0.5f).mapIndexed { i, v -> m.update(i * 1000L, lat, lon, 5f, v) }
        assertTrue(shown.all { it == 0.0 })
    }

    @Test fun roundsInsteadOfCutting() {
        val m = SpeedMeter()
        var v = 0.0
        repeat(6) { v = m.update(it * 1000L, lat, lon, 5f, 16.66f) } // 59,98 км/ч
        assertEquals(60, SpeedMeter.kmh(v))
    }

    @Test fun ignoresImpossibleJump() {
        val m = SpeedMeter()
        var v = 0.0
        repeat(5) { v = m.update(it * 1000L, lat, lon, 5f, 15f) }
        v = m.update(5000L, lat, lon, 5f, 40f) // скачок до 144 км/ч за секунду невозможен
        assertTrue(SpeedMeter.kmh(v) < 80)
    }

    @Test fun followsRealAcceleration() {
        val m = SpeedMeter()
        var v = 0.0
        // разгон 2 м/с² от 0 до 20 м/с
        for (i in 0..10) v = m.update(i * 1000L, lat, lon, 5f, (i * 2).toFloat())
        repeat(3) { v = m.update((11 + it) * 1000L, lat, lon, 5f, 20f) }
        assertTrue(SpeedMeter.kmh(v) in 70..72)
    }

    @Test fun derivesSpeedWithoutDoppler() {
        val m = SpeedMeter()
        var v = 0.0
        // каждую секунду на 20 м севернее, приёмник скорость не даёт
        for (i in 0..8) v = m.update(i * 1000L, lat + i * 20.0 / 111_000.0, lon, 5f, null)
        assertTrue(SpeedMeter.kmh(v) in 68..76)
    }

    @Test fun dropsStaleSpeed() {
        val m = SpeedMeter()
        repeat(4) { m.update(it * 1000L, lat, lon, 5f, 15f) }
        // дальше только грубые положения без скорости
        val v = m.update(11_000L, lat, lon, 300f, null)
        assertEquals(0.0, v, 0.0)
    }
}
