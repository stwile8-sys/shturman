package ru.shturman.routing

import kotlin.math.roundToInt

/**
 * Скорость для экрана: точная и без дрожания.
 *  - Основа — скорость, которую мерит сам приёмник GPS (по эффекту Доплера). Она точнее любых расчётов по точкам
 *    и показывает настоящую скорость (спидометр машины обычно завышает на 3–7 %).
 *  - Если приёмник скорость не дал — считаем по пути между двумя точными положениями (не дальше 10 секунд).
 *  - Лёгкое сглаживание без заметной задержки и отсечение невозможных скачков (резче 0,8 g).
 *  - Стоя на месте GPS «плавает» на 1–4 км/ч: ниже 3 км/ч показываем 0, тронулись быстрее 4 км/ч — сразу видно.
 *  - Если точной скорости нет дольше 6 секунд, старое значение не держим.
 */
class SpeedMeter {
    private var value = 0.0
    private var moving = false
    private var lastMs = -1L
    private var lastLat = 0.0
    private var lastLon = 0.0
    private var lastAcc = 999f
    private var lastMeasuredMs = -1L

    /**
     * timeMs — время положения (монотонное), speedMps — скорость от приёмника или null, если её нет.
     * Возвращает скорость в м/с для показа.
     */
    fun update(timeMs: Long, lat: Double, lon: Double, accuracyM: Float, speedMps: Float?): Double {
        val dt = if (lastMs < 0) -1.0 else (timeMs - lastMs) / 1000.0
        var measured: Double? = speedMps?.takeIf { it.isFinite() && it >= 0f && it < 120f }?.toDouble()
        if (measured == null && dt in 1.0..10.0 && accuracyM <= 25f && lastAcc <= 25f) {
            measured = distanceM(LatLon(lastLat, lastLon), LatLon(lat, lon)) / dt
        }
        if (dt != 0.0) {
            lastMs = timeMs
            lastLat = lat
            lastLon = lon
            lastAcc = accuracyM
        }
        if (measured != null) {
            val sinceMeasured = if (lastMeasuredMs < 0) Double.MAX_VALUE else (timeMs - lastMeasuredMs) / 1000.0
            value = if (sinceMeasured > 6.0) {
                // давно не было точной скорости — начинаем с нового значения
                measured
            } else {
                // невозможный рывок режем до 0,8 g за прошедшее время, потом мягко сглаживаем
                val step = 8.0 * sinceMeasured.coerceIn(0.25, 3.0)
                val m = measured.coerceIn(value - step, value + step)
                value + K * (m - value)
            }
            lastMeasuredMs = timeMs
        } else if (lastMeasuredMs >= 0 && (timeMs - lastMeasuredMs) > 6_000L) {
            value = 0.0
        }
        moving = if (moving) value >= STOP_MPS else value >= GO_MPS
        return if (moving) value else 0.0
    }

    companion object {
        /** Доля нового измерения: при 1–2 положениях в секунду скорость догоняет реальную примерно за секунду. */
        private const val K = 0.7
        /** Ниже ~3 км/ч считаем, что стоим. */
        private const val STOP_MPS = 0.85
        /** Быстрее ~4 км/ч — поехали. */
        private const val GO_MPS = 1.15

        /** Км/ч для показа: округление до целого, а не отбрасывание дробной части. */
        fun kmh(mps: Double): Int = (mps * 3.6).roundToInt()
    }
}
