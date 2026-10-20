package ru.shturman.stats

import android.content.SharedPreferences
import java.util.Locale

/** Одна завершённая поездка. */
data class Trip(
    val startMs: Long,
    val km: Double,
    val durationSec: Long,
    val maxKmh: Int,
    val avgKmh: Int,
    val costRub: Int
)

/** Одна заправка. */
data class Refuel(val atMs: Long, val place: String, val liters: Double, val pricePerLiter: Double)

/** Чистая логика статистики: без Android, чтобы её можно было проверить тестами. */
object TripMath {
    const val DAY_MS = 86_400_000L
    const val KEEP_DAYS = 90
    const val MAX_TRIPS = 500

    fun avgKmh(km: Double, durationSec: Long): Int =
        if (durationSec <= 0 || km <= 0) 0 else (km / (durationSec / 3600.0)).toInt()

    fun encode(t: Trip): String =
        String.format(Locale.US, "%d;%.3f;%d;%d;%d;%d", t.startMs, t.km, t.durationSec, t.maxKmh, t.avgKmh, t.costRub)

    fun decode(line: String): Trip? {
        val p = line.trim().split(';')
        if (p.size != 6) return null
        return runCatching {
            Trip(p[0].toLong(), p[1].toDouble(), p[2].toLong(), p[3].toInt(), p[4].toInt(), p[5].toInt())
        }.getOrNull()
    }

    /** Выкидывает записи старше трёх месяцев и ограничивает размер списка. */
    fun trim(trips: List<Trip>, nowMs: Long): List<Trip> =
        trips.filter { nowMs - it.startMs <= KEEP_DAYS * DAY_MS }.sortedBy { it.startMs }.takeLast(MAX_TRIPS)

    /** Километры за период [fromMs, now]. */
    fun km(trips: List<Trip>, fromMs: Long) = trips.filter { it.startMs >= fromMs }.sumOf { it.km }
    fun count(trips: List<Trip>, fromMs: Long) = trips.count { it.startMs >= fromMs }
    fun cost(trips: List<Trip>, fromMs: Long) = trips.filter { it.startMs >= fromMs }.sumOf { it.costRub }

    fun csv(trips: List<Trip>): String {
        val sb = StringBuilder("начало;км;секунд;макс_кмч;средняя_кмч;стоимость_руб\n")
        for (t in trips) {
            sb.append(java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(java.util.Date(t.startMs)))
                .append(';').append(String.format(Locale.US, "%.1f", t.km)).append(';').append(t.durationSec)
                .append(';').append(t.maxKmh).append(';').append(t.avgKmh).append(';').append(t.costRub).append('\n')
        }
        return sb.toString()
    }

    fun encode(r: Refuel): String =
        String.format(Locale.US, "%d;%s;%.2f;%.2f", r.atMs, r.place.replace(';', ',').replace('\n', ' '), r.liters, r.pricePerLiter)

    fun decodeRefuel(line: String): Refuel? {
        val p = line.trim().split(';')
        if (p.size != 4) return null
        return runCatching { Refuel(p[0].toLong(), p[1], p[2].toDouble(), p[3].toDouble()) }.getOrNull()
    }

    fun refuelCsv(list: List<Refuel>): String {
        val sb = StringBuilder("дата;место;литры;цена_за_литр;сумма\n")
        for (r in list) {
            sb.append(java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date(r.atMs))).append(';')
                .append(r.place).append(';').append(String.format(Locale.US, "%.1f;%.2f;%.0f", r.liters, r.pricePerLiter, r.liters * r.pricePerLiter)).append('\n')
        }
        return sb.toString()
    }

    /** Нужно ли напомнить о ТО: интервал задан и пробег с прошлого ТО его достиг. */
    fun serviceDue(odometerKm: Double, lastServiceKm: Double, intervalKm: Int): Boolean =
        intervalKm > 0 && odometerKm - lastServiceKm >= intervalKm
}

/** Хранилище поездок и заправок в настройках телефона. */
class TripLog(private val prefs: SharedPreferences) {
    fun trips(): List<Trip> = ru.shturman.Crypto.decrypt(prefs.getString("trips", "") ?: "").lines().mapNotNull { TripMath.decode(it) }

    /** Запись идёт в фоне (не в главном потоке), поэтому изменения журнала — по одному. */
    @Synchronized
    fun add(t: Trip, nowMs: Long = System.currentTimeMillis()) {
        val all = TripMath.trim(trips() + t, nowMs)
        prefs.edit().putString("trips", ru.shturman.Crypto.encrypt(all.joinToString("\n") { TripMath.encode(it) }))
            .putFloat("odometer", (prefs.getFloat("odometer", 0f) + t.km).toFloat()).apply()
    }

    @Synchronized
    fun clearTrips() { prefs.edit().remove("trips").apply() }

    val odometerKm: Double get() = prefs.getFloat("odometer", 0f).toDouble()

    fun refuels(): List<Refuel> = ru.shturman.Crypto.decrypt(prefs.getString("refuels", "") ?: "").lines().mapNotNull { TripMath.decodeRefuel(it) }

    @Synchronized
    fun addRefuel(r: Refuel) {
        val all = (refuels() + r).takeLast(200)
        prefs.edit().putString("refuels", ru.shturman.Crypto.encrypt(all.joinToString("\n") { TripMath.encode(it) })).apply()
    }

    var serviceIntervalKm: Int
        get() = prefs.getInt("service_km", 0)
        set(v) { prefs.edit().putInt("service_km", v.coerceIn(0, 100000)).apply() }

    fun markServiceDone() { prefs.edit().putFloat("service_at", odometerKm.toFloat()).apply() }
    val lastServiceKm: Double get() = prefs.getFloat("service_at", 0f).toDouble()
    fun serviceDue() = TripMath.serviceDue(odometerKm, lastServiceKm, serviceIntervalKm)
}
