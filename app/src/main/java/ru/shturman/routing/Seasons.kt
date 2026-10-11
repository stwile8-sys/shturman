package ru.shturman.routing

import java.util.Calendar

/** Поправка времени в пути на сезон и день: во сколько раз дольше обычного (1.0 — без поправки) и пояснение. */
class SeasonEffect(val slowdown: Double, val note: String?)

/** Школьные каникулы в России (примерные даты): осенние, зимние, весенние, летние. */
private fun isSchoolHoliday(cal: Calendar): Boolean {
    val m = cal.get(Calendar.MONTH) + 1
    val d = cal.get(Calendar.DAY_OF_MONTH)
    return (m == 10 && d >= 26) || (m == 11 && d <= 3) ||
        (m == 12 && d >= 28) || (m == 1 && d <= 11) ||
        (m == 3 && d >= 23 && d <= 31) ||
        (m in 6..8)
}

/**
 * Сезонность на момент whenMs: зима и межсезонье, дачный поток (май–сентябрь, пятница и воскресенье вечером,
 * суббота утром, для длинных поездок), часы пик в будни и школьные каникулы (утренних заторов меньше).
 */
fun seasonEffect(whenMs: Long, lengthM: Double): SeasonEffect {
    val cal = Calendar.getInstance().apply { timeInMillis = whenMs }
    val month = cal.get(Calendar.MONTH) + 1
    val dow = cal.get(Calendar.DAY_OF_WEEK) // 1 = воскресенье
    val hour = cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE) / 60.0
    val weekday = dow in Calendar.MONDAY..Calendar.FRIDAY
    val notes = ArrayList<String>()
    var factor = 1.0

    when (month) {
        12, 1, 2 -> { factor *= 1.08; notes += "зима" }
        11, 3 -> { factor *= 1.04; notes += "межсезонье" }
    }

    val dachaSeason = month in 5..9
    val dachaTime = (dow == Calendar.FRIDAY && hour in 15.0..21.5) ||
        (dow == Calendar.SUNDAY && hour in 15.0..21.5) ||
        (dow == Calendar.SATURDAY && hour in 6.0..11.5)
    if (dachaSeason && dachaTime && lengthM >= 15_000) {
        factor *= 1.12
        notes += "дачный поток"
    }

    val rush = weekday && (hour in 7.5..9.5 || hour in 16.5..19.0)
    if (rush) {
        if (isSchoolHoliday(cal) && hour < 12) {
            factor *= 1.02
            notes += "каникулы, утром свободнее"
        } else {
            factor *= 1.07
            notes += "час пик"
        }
    }
    return SeasonEffect(factor, notes.takeIf { it.isNotEmpty() }?.joinToString(", "))
}
