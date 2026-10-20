package ru.shturman.routing

import kotlin.math.abs

/** Чувствительность определения съезда с маршрута: как далеко от линии и как долго нужно быть, чтобы считать съезд. */
enum class DeviationLevel(val label: String, val offsetM: Double, val holdMs: Long, val pauseMs: Long) {
    LOW("Низкая", 80.0, 8_000L, 20_000L),
    MEDIUM("Средняя", 50.0, 5_000L, 10_000L),
    HIGH("Высокая", 35.0, 3_000L, 10_000L);

    companion object {
        fun of(index: Int): DeviationLevel = values().getOrElse(index) { MEDIUM }
    }
}

/** Что заметил детектор. */
enum class DeviationEvent { NONE, OFF_ROUTE, U_TURN, MISSED_TURN, REVERSE, STOPPED_OFF }

/**
 * Определяет, что водитель ушёл с маршрута, развернулся, пропустил поворот, едет назад или стоит вне маршрута.
 * Ничего не знает про карту и телефон: получает цифры и время, поэтому его легко проверять тестами.
 */
class DeviationDetector(var level: DeviationLevel = DeviationLevel.MEDIUM) {
    private var offSince = -1L
    private var courseAtOff = -1f
    private var uturnSince = -1L
    private var stillSince = -1L
    private var reverseSince = -1L
    private var lastReverseAt = Long.MIN_VALUE / 2
    /** Начало непрерывного движения против маршрута: долго (10 с) — значит, развернулись, строим новый путь. */
    private var reverseStart = -1L
    private var uturnFired = false
    private var stoppedFired = false

    fun reset() {
        offSince = -1L; courseAtOff = -1f; uturnSince = -1L; stillSince = -1L
        reverseSince = -1L; reverseStart = -1L; uturnFired = false; stoppedFired = false
    }

    /**
     * @param offsetM расстояние от положения до линии маршрута, метры
     * @param accuracyM погрешность GPS; хуже 30 м — решение откладывается
     * @param speedMps скорость, м/с
     * @param courseDeg курс движения по GPS (отрицательный — неизвестен)
     * @param routeBearing куда смотрит дорога маршрута в ближайшей точке (отрицательный — неизвестно)
     * @param turnDistM расстояние до ближайшего поворота, когда водитель ещё был на маршруте (бесконечность — нет)
     */
    fun update(
        nowMs: Long, offsetM: Double, accuracyM: Float, speedMps: Float,
        courseDeg: Float, routeBearing: Double, turnDistM: Double
    ): DeviationEvent {
        // Слабый сигнал GPS: ничего не решаем и таймеры не двигаем, ждём, пока сигнал выправится
        if (accuracyM > MAX_ACCURACY_M) return DeviationEvent.NONE

        val hasCourse = courseDeg >= 0f && speedMps > 2f
        val onRoute = offsetM <= level.offsetM

        if (onRoute) {
            offSince = -1L; courseAtOff = -1f; uturnSince = -1L; stillSince = -1L
            uturnFired = false; stoppedFired = false
            // Едем по маршруту, но в обратную сторону
            if (hasCourse && routeBearing >= 0 && speedMps > 3f &&
                abs(turnAngle(routeBearing, courseDeg.toDouble())) > 140.0
            ) {
                if (reverseStart < 0) reverseStart = nowMs
                if (nowMs - reverseStart >= REVERSE_UTURN_MS) {
                    // следующий раз — не раньше чем через столько же, если новый путь так и не построился
                    reverseStart = nowMs
                    return DeviationEvent.U_TURN
                }
                if (reverseSince < 0) reverseSince = nowMs
                if (nowMs - reverseSince >= REVERSE_HOLD_MS && nowMs - lastReverseAt >= REVERSE_PAUSE_MS) {
                    lastReverseAt = nowMs
                    reverseSince = -1L
                    return DeviationEvent.REVERSE
                }
            } else { reverseSince = -1L; reverseStart = -1L }
            return DeviationEvent.NONE
        }
        reverseSince = -1L
        reverseStart = -1L

        // Вне маршрута
        if (offSince < 0) {
            offSince = nowMs
            courseAtOff = if (hasCourse) courseDeg else -1f
        }
        val held = nowMs - offSince

        // Стоим вне маршрута дольше 3 минут
        if (speedMps < 1f) {
            if (stillSince < 0) stillSince = nowMs
            if (!stoppedFired && nowMs - stillSince >= STOP_HOLD_MS) {
                stoppedFired = true
                return DeviationEvent.STOPPED_OFF
            }
        } else stillSince = -1L

        // Разворот: курс изменился примерно на 180° и держится дольше 3 секунд
        if (hasCourse && courseAtOff >= 0 && !uturnFired &&
            abs(turnAngle(courseAtOff.toDouble(), courseDeg.toDouble())) >= 150.0
        ) {
            if (uturnSince < 0) uturnSince = nowMs
            if (nowMs - uturnSince >= UTURN_HOLD_MS) {
                uturnFired = true
                return DeviationEvent.U_TURN
            }
        } else if (!uturnFired) uturnSince = -1L

        // Параллельная улица рядом с маршрутом: не пересчитываем сразу, ждём 10 секунд
        val parallel = hasCourse && routeBearing >= 0 && offsetM < PARALLEL_MAX_M &&
            abs(turnAngle(routeBearing, courseDeg.toDouble())) < 35.0
        val need = if (parallel) maxOf(level.holdMs, PARALLEL_HOLD_MS) else level.holdMs
        if (held >= need) {
            offSince = nowMs // следующий съезд считаем заново, чтобы не пересчитывать каждую секунду
            return if (offsetM >= MISSED_FAR_M && turnDistM <= MISSED_TURN_NEAR_M) DeviationEvent.MISSED_TURN
            else DeviationEvent.OFF_ROUTE
        }
        return DeviationEvent.NONE
    }

    companion object {
        const val MAX_ACCURACY_M = 30f
        const val UTURN_HOLD_MS = 3_000L
        const val STOP_HOLD_MS = 180_000L
        const val PARALLEL_HOLD_MS = 10_000L
        const val PARALLEL_MAX_M = 120.0
        const val REVERSE_HOLD_MS = 4_000L
        const val REVERSE_PAUSE_MS = 30_000L
        /** Едем против маршрута дольше этого — считаем разворотом и перестраиваем путь. */
        const val REVERSE_UTURN_MS = 10_000L
        /** Пропущенный поворот: ушли от маршрута на 60+ м, а поворот был прямо перед этим (не дальше 80 м). */
        const val MISSED_FAR_M = 60.0
        const val MISSED_TURN_NEAR_M = 80.0
    }
}
