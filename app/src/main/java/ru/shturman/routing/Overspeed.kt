package ru.shturman.routing

/** Насколько можно превысить ограничение без сигнала, км/ч. */
const val OVERSPEED_TOLERANCE_KMH = 15

/**
 * Сигнал о превышении: один раз на одно превышение.
 * Срабатывает, когда скорость выше ограничения больше чем на [toleranceKmh]. Снова сработает только после того,
 * как скорость опустится до «ограничение + [rearmKmh]» (запас, чтобы скачки GPS не давали повторов),
 * или если ограничение стало ниже (новый знак — новое превышение).
 */
class OverspeedAlarm(
    private val toleranceKmh: Int = OVERSPEED_TOLERANCE_KMH,
    private val rearmKmh: Int = OVERSPEED_TOLERANCE_KMH - 5
) {
    private var armed = true
    private var lastLimit: Int? = null

    /** Новое показание. true — пора дать один сигнал. limit = null — ограничение на этом участке неизвестно. */
    fun update(kmh: Int, limit: Int?): Boolean {
        if (limit == null) {
            // Участок без данных: сигнала нет, но если сбросили скорость под прошлым знаком — снова готовы
            lastLimit?.let { if (kmh <= it + rearmKmh) armed = true }
            return false
        }
        val prev = lastLimit
        if (prev != null && limit < prev) armed = true
        lastLimit = limit
        if (kmh <= limit + rearmKmh) {
            armed = true
            return false
        }
        if (armed && kmh > limit + toleranceKmh) {
            armed = false
            return true
        }
        return false
    }

    /** Начало новой поездки. */
    fun reset() {
        armed = true
        lastLimit = null
    }
}
