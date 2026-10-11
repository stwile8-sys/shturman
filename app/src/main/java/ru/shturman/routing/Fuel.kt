package ru.shturman.routing

/**
 * Расход бензина на маршруте. База — расход «на 100 км» из настроек; от средней скорости зависит поправка:
 * в пробках и на малой скорости двигатель тратит больше, на 60–90 км/ч экономнее всего, быстрее 100 — снова больше.
 * Время простоя на светофорах уже входит во время маршрута, поэтому средняя скорость у такого маршрута ниже.
 */
fun fuelLiters(lengthM: Double, timeSec: Double, litersPer100: Double): Double {
    if (lengthM <= 0 || timeSec <= 0) return 0.0
    val avgKmh = lengthM / timeSec * 3.6
    val factor = when {
        avgKmh < 20 -> 1.45
        avgKmh < 35 -> 1.2
        avgKmh < 60 -> 1.05
        avgKmh < 90 -> 0.95
        avgKmh < 110 -> 1.08
        else -> 1.25
    }
    return lengthM / 1000.0 * litersPer100 / 100.0 * factor
}

fun fuelText(liters: Double, pricePerLiter: Double): String {
    val l = String.format(java.util.Locale("ru"), "%.1f", liters)
    return "$l л · ${Math.round(liters * pricePerLiter)} ₽"
}
