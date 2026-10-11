package ru.shturman.routing

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.Calendar
import kotlin.math.floor

/**
 * Память о реальных скоростях: для каждого квадрата карты (около 250 × 130 м) и каждого часа недели
 * хранится, как быстро там ехали на самом деле.
 *
 * Правила «забывания», чтобы старое не залипало (было 60, стало 30):
 *  - значение старше 14 дней при новом проезде заменяется новым, а не усредняется;
 *  - свежие значения усредняются, новые весят не меньше 30%;
 *  - всё, что не обновлялось 28 дней, удаляется.
 */
class SpeedStore(context: Context) : SQLiteOpenHelper(context, "speeds.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE cells (k TEXT PRIMARY KEY, speed REAL NOT NULL, n INTEGER NOT NULL, updated INTEGER NOT NULL)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {}

    init {
        runCatching {
            writableDatabase.delete("cells", "updated < ?", arrayOf((System.currentTimeMillis() - EXPIRE_MS).toString()))
        }
    }

    /** Записывает одну среднюю скорость проезда через квадрат. */
    @Synchronized
    fun record(key: String, speedMps: Double, atMs: Long) {
        runCatching {
            val db = writableDatabase
            var oldSpeed = 0.0
            var n = 0
            var updated = 0L
            db.rawQuery("SELECT speed, n, updated FROM cells WHERE k = ?", arrayOf(key)).use { c ->
                if (c.moveToFirst()) {
                    oldSpeed = c.getDouble(0)
                    n = c.getInt(1)
                    updated = c.getLong(2)
                }
            }
            val fresh = n > 0 && atMs - updated < REPLACE_MS
            val newSpeed: Double
            val newN: Int
            if (!fresh) {
                newSpeed = speedMps
                newN = 1
            } else {
                val alpha = maxOf(1.0 / (n + 1), 0.3)
                newSpeed = oldSpeed + alpha * (speedMps - oldSpeed)
                newN = minOf(n + 1, 20)
            }
            val values = ContentValues().apply {
                put("k", key)
                put("speed", newSpeed)
                put("n", newN)
                put("updated", atMs)
            }
            db.insertWithOnConflict("cells", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    /** Ожидаемая скорость в квадрате в это время или null, если данных мало или они старые. */
    @Synchronized
    fun expected(key: String, nowMs: Long): Double? = runCatching {
        readableDatabase.rawQuery("SELECT speed, n, updated FROM cells WHERE k = ?", arrayOf(key)).use { c ->
            if (c.moveToFirst() && c.getInt(1) >= 2 && nowMs - c.getLong(2) < EXPIRE_MS) c.getDouble(0) else null
        }
    }.getOrNull()

    companion object {
        private const val REPLACE_MS = 14L * 24 * 3600 * 1000
        private const val EXPIRE_MS = 28L * 24 * 3600 * 1000

        /** Ключ квадрата и часа недели. */
        fun key(lat: Double, lon: Double, atMs: Long): String {
            val cal = Calendar.getInstance().apply { timeInMillis = atMs }
            val bucket = (cal.get(Calendar.DAY_OF_WEEK) - 1) * 24 + cal.get(Calendar.HOUR_OF_DAY)
            val a = floor(lat / 0.0012).toLong()
            val b = floor(lon / 0.0022).toLong()
            return "$a:$b:$bucket"
        }
    }
}

/** Собирает скорости во время езды: на каждый квадрат — одна средняя за проезд. */
class SpeedRecorder(private val store: SpeedStore) {
    private var cell: String? = null
    private var sum = 0.0
    private var count = 0
    private var at = 0L

    fun add(lat: Double, lon: Double, speedMps: Double, nowMs: Long) {
        val key = SpeedStore.key(lat, lon, nowMs)
        if (key != cell) flush()
        cell = key
        sum += speedMps
        count++
        at = nowMs
    }

    fun flush() {
        val k = cell
        // Меньше трёх измерений — это краешек квадрата, не считаем
        if (k != null && count >= 3) store.record(k, sum / count, at)
        cell = null
        sum = 0.0
        count = 0
    }
}

/** Пересчитывает время маршрута по запомненным скоростям; где данных нет, берёт среднюю скорость движка. */
fun forecastTimeSec(route: RouteOption, store: SpeedStore, departMs: Long): Double {
    if (route.points.size < 2 || route.timeSec <= 0) return route.timeSec
    val baseline = (route.lengthM / route.timeSec).coerceAtLeast(2.0)
    var t = 0.0
    val cache = HashMap<String, Double?>()
    for (i in 1 until route.points.size) {
        val a = route.points[i - 1]
        val b = route.points[i]
        val len = distanceM(a, b)
        val at = departMs + (t * 1000).toLong()
        val key = SpeedStore.key((a.lat + b.lat) / 2, (a.lon + b.lon) / 2, at)
        val v = if (cache.containsKey(key)) cache[key] else store.expected(key, at).also { cache[key] = it }
        t += len / (v ?: baseline).coerceIn(1.5, 40.0)
    }
    return t
}
