package ru.shturman.routing

import kotlin.math.roundToInt

/** Запись о скорости: квадрат карты (a, b), час недели, средняя скорость, сколько раз проезжали, когда обновлено. */
class CellRow(val a: Long, val b: Long, val bucket: Int, val speedMps: Double, val n: Int, val updated: Long)

/** Запись об остановке (светофор, затор): место, час недели, сколько обычно стоят. */
class StopRow(val lat: Double, val lon: Double, val bucket: Int, val waitSec: Double, val n: Int, val updated: Long)

/** Точка для картинки «карта знаний»: центр квадрата, средняя скорость и сколько всего проездов. */
class KnownSpot(val position: LatLon, val kmh: Double, val passes: Int)

/**
 * Насколько хорошо собраны данные о дорогах — в цифрах, понятных без программирования.
 * Считается по общей базе на этом телефоне (после синхронизации в ней данные всех водителей).
 */
class RoadStats(
    /** Участков дорог (квадратов около 130 × 130 м), где знаем скорость хотя бы в один час недели. */
    val places: Int,
    /** Примерная длина этих дорог, км. */
    val roadKm: Int,
    /** Всего записей «участок + час недели». */
    val records: Int,
    /** Из них годятся для прогноза: проехали хотя бы дважды и не старше 4 недель. */
    val usable: Int,
    /** Сколько участков знаем в каждый час недели (168: воскресенье 0–23, понедельник 24–47, …). */
    val hours: IntArray,
    /** Сколько участков обновилось за сутки и за неделю. */
    val fresh24h: Int,
    val fresh7d: Int,
    /** Мест, где обычно стоят (светофоры, заторы), и среднее ожидание там. */
    val stopPlaces: Int,
    val avgWaitSec: Int,
    /** Готовность прогноза пробок, 0–100. */
    val score: Int,
    val spots: List<KnownSpot>,
    val stops: List<LatLon>
) {
    val verdict: String get() = when {
        score >= 70 -> "Хорошо"
        score >= 40 -> "Средне"
        places == 0 -> "Пока пусто"
        else -> "Мало данных"
    }
}

private const val DAY_MS = 24L * 3600 * 1000

/** Считает сводку по строкам базы. Ничего не знает об Android — проверяется тестами. */
fun computeRoadStats(cells: List<CellRow>, stops: List<StopRow>, nowMs: Long): RoadStats {
    val hours = IntArray(168)
    val byPlace = HashMap<Pair<Long, Long>, DoubleArray>() // сумма скорость×n, сумма n, свежесть
    var usable = 0
    val fresh24 = HashSet<Pair<Long, Long>>()
    val fresh7 = HashSet<Pair<Long, Long>>()
    for (c in cells) {
        val key = c.a to c.b
        if (c.bucket in 0..167) hours[c.bucket]++
        if (c.n >= 2 && nowMs - c.updated < 28 * DAY_MS) usable++
        if (nowMs - c.updated < DAY_MS) fresh24.add(key)
        if (nowMs - c.updated < 7 * DAY_MS) fresh7.add(key)
        val acc = byPlace.getOrPut(key) { DoubleArray(2) }
        acc[0] += c.speedMps * c.n
        acc[1] += c.n.toDouble()
    }
    val spots = byPlace.map { (k, v) ->
        KnownSpot(
            LatLon((k.first + 0.5) * 0.0012, (k.second + 0.5) * 0.0022),
            if (v[1] > 0) v[0] / v[1] * 3.6 else 0.0,
            v[1].toInt()
        )
    }
    // места остановок без учёта часа: одно место — один светофор
    val stopPlaces = stops.groupBy { (it.lat / 0.0003).toLong() to (it.lon / 0.0005).toLong() }
    val avgWait = if (stops.isEmpty()) 0 else (stops.sumOf { it.waitSec * it.n } / stops.sumOf { it.n }.coerceAtLeast(1)).roundToInt()

    val places = byPlace.size
    val records = cells.size
    // Часы, где данных достаточно: не меньше трети от самого «знакомого» часа (и хотя бы 5 участков)
    val maxHour = hours.maxOrNull() ?: 0
    val covered = if (maxHour == 0) 0 else hours.count { it >= maxOf(5, (maxHour * 0.33).toInt()) }
    val usableShare = if (records == 0) 0.0 else usable.toDouble() / records
    val freshShare = if (places == 0) 0.0 else (fresh7.size.toDouble() / places).coerceAtMost(1.0)
    val score = if (places == 0) 0 else (100 * (0.4 * usableShare + 0.35 * covered / 168.0 + 0.25 * freshShare)).roundToInt().coerceIn(0, 100)

    return RoadStats(
        places = places,
        roadKm = (places * 0.13).roundToInt(),
        records = records,
        usable = usable,
        hours = hours,
        fresh24h = fresh24.size,
        fresh7d = fresh7.size,
        stopPlaces = stopPlaces.size,
        avgWaitSec = avgWait,
        score = score,
        spots = spots,
        stops = stopPlaces.values.map { g -> LatLon(g.map { it.lat }.average(), g.map { it.lon }.average()) }
    )
}

/** Названия дней для часа недели (0 — воскресенье, как в Calendar). */
private val DAYS = listOf("воскресенье", "понедельник", "вторник", "среда", "четверг", "пятница", "суббота")

/** «будни 8–9» / «суббота 14–15»: какой час недели знаем лучше всего. */
fun bestHourText(hours: IntArray): String? {
    val i = hours.indices.maxByOrNull { hours[it] } ?: return null
    if (hours[i] == 0) return null
    val day = i / 24
    val h = i % 24
    return "${DAYS[day]}, $h–${h + 1} ч"
}
