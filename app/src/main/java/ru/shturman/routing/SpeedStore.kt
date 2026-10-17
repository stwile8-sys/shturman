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
class SpeedStore(context: Context) : SQLiteOpenHelper(context, "speeds.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(STOPS_TABLE)
        db.execSQL(
            "CREATE TABLE cells (k TEXT PRIMARY KEY, speed REAL NOT NULL, n INTEGER NOT NULL, updated INTEGER NOT NULL)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
        db.execSQL(STOPS_TABLE)
    }

    override fun onOpen(db: SQLiteDatabase) {
        db.execSQL(STOPS_TABLE)
        db.execSQL(OBS_TABLE)
        db.execSQL(DANGER_TABLE)
        db.execSQL("CREATE INDEX IF NOT EXISTS obs_k ON stop_obs (k)")
    }

    init {
        runCatching {
            val old = arrayOf((System.currentTimeMillis() - EXPIRE_MS).toString())
            writableDatabase.delete("cells", "updated < ?", old)
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

    /** Записывает одну остановку (у светофора, перекрёстка или в заторе): где, во сколько и сколько простояли. */
    @Synchronized
    fun recordStop(lat: Double, lon: Double, waitSec: Double, atMs: Long) {
        runCatching {
            val key = "s" + SpeedStore.stopKey(lat, lon, atMs)
            val db = writableDatabase
            // Каждое наблюдение сохраняем отдельно: раз в неделю из них выбирается самое частое время
            db.insert("stop_obs", null, ContentValues().apply { put("k", key); put("wait", waitSec); put("at", atMs) })
            var row: DoubleArray? = null
            var updated = 0L
            db.rawQuery("SELECT lat, lon, wait, n, updated FROM stops WHERE k = ?", arrayOf(key)).use { c ->
                if (c.moveToFirst()) {
                    row = doubleArrayOf(c.getDouble(0), c.getDouble(1), c.getDouble(2), c.getInt(3).toDouble())
                    updated = c.getLong(4)
                }
            }
            val old = row
            val values = ContentValues().apply { put("k", key) }
            if (old == null || atMs - updated >= REPLACE_MS) {
                // Нового нет или старое устарело (светофор перенастроили): берём новое значение целиком
                values.put("lat", lat); values.put("lon", lon); values.put("wait", waitSec); values.put("n", 1)
            } else {
                val n = old[3]
                val alpha = maxOf(1.0 / (n + 1), 0.3)
                values.put("lat", old[0] + (lat - old[0]) / (n + 1))
                values.put("lon", old[1] + (lon - old[1]) / (n + 1))
                values.put("wait", old[2] + alpha * (waitSec - old[2]))
                values.put("n", minOf(n + 1, 20.0).toInt())
            }
            values.put("updated", atMs)
            db.insertWithOnConflict("stops", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    /** Все известные остановки в прямоугольнике. Записи светофоров не удаляются: их только обновляют новые проезды. */
    @Synchronized
    fun stopsIn(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double, nowMs: Long): List<StopSpot> = runCatching {
        val out = ArrayList<StopSpot>()
        readableDatabase.rawQuery(
            "SELECT k, lat, lon, wait FROM stops WHERE updated > ? AND lat BETWEEN ? AND ? AND lon BETWEEN ? AND ?",
            arrayOf("0", minLat.toString(), maxLat.toString(), minLon.toString(), maxLon.toString())
        ).use { c ->
            while (c.moveToNext()) {
                val bucket = c.getString(0).substringAfterLast(':').toInt()
                out += StopSpot(LatLon(c.getDouble(1), c.getDouble(2)), bucket, c.getDouble(3))
            }
        }
        out
    }.getOrDefault(emptyList())

    /**
     * Раз в неделю пересчитывает время ожидания каждого светофора, где за последние 7 дней были проезды:
     * проезды раскладываются по корзинам в 5 секунд, берётся самая частая корзина (при равенстве — более
     * свежая), а значением становится среднее внутри неё. Записи светофоров без новых проездов не трогаются
     * и не удаляются. Сырые наблюдения старше 14 дней чистятся.
     */
    @Synchronized
    fun recomputeStops(nowMs: Long): Int = runCatching {
        val db = writableDatabase
        val weekAgo = nowMs - 7L * 24 * 3600 * 1000
        val byKey = LinkedHashMap<String, MutableList<Pair<Double, Long>>>()
        db.rawQuery("SELECT k, wait, at FROM stop_obs WHERE at > ?", arrayOf(weekAgo.toString())).use { c ->
            while (c.moveToNext()) byKey.getOrPut(c.getString(0)) { ArrayList() }.add(c.getDouble(1) to c.getLong(2))
        }
        var changed = 0
        for ((key, obs) in byKey) {
            val bins = obs.groupBy { (it.first / 5.0).toInt() }
            val best = bins.values.maxWithOrNull(
                compareBy<List<Pair<Double, Long>>> { it.size }.thenBy { list -> list.maxOf { it.second } }
            ) ?: continue
            val mode = best.map { it.first }.average()
            val values = ContentValues().apply {
                put("wait", mode)
                put("n", minOf(obs.size, 20))
                put("updated", nowMs)
            }
            if (db.update("stops", values, "k = ?", arrayOf(key)) > 0) changed++
        }
        db.delete("stop_obs", "at < ?", arrayOf((nowMs - 14L * 24 * 3600 * 1000).toString()))
        changed
    }.getOrDefault(0)

    /** Запоминает место резкого торможения (квадрат около 100 м). Чем чаще, тем опаснее место. */
    @Synchronized
    fun recordDanger(lat: Double, lon: Double, atMs: Long) {
        runCatching {
            val key = "${floor(lat / 0.0009).toLong()}:${floor(lon / 0.0016).toLong()}"
            val db = writableDatabase
            var n = 0
            db.rawQuery("SELECT n FROM danger WHERE k = ?", arrayOf(key)).use { c -> if (c.moveToFirst()) n = c.getInt(0) }
            val v = ContentValues().apply { put("k", key); put("lat", lat); put("lon", lon); put("n", n + 1); put("updated", atMs) }
            db.insertWithOnConflict("danger", null, v, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    /** Места, где резко тормозили не меньше minCount раз. */
    @Synchronized
    fun dangerSpots(minCount: Int): List<LatLon> = runCatching {
        val out = ArrayList<LatLon>()
        readableDatabase.rawQuery("SELECT lat, lon FROM danger WHERE n >= ?", arrayOf(minCount.toString())).use { c ->
            while (c.moveToNext()) out += LatLon(c.getDouble(0), c.getDouble(1))
        }
        out
    }.getOrDefault(emptyList())

    // ---------- обмен с общей базой ----------

    @Synchronized
    fun dirtyStops(since: Long, limit: Int): List<SharedRow> = runCatching {
        val out = ArrayList<SharedRow>()
        readableDatabase.rawQuery(
            "SELECT k, lat, lon, wait, n, updated FROM stops WHERE updated > ? ORDER BY updated LIMIT ?",
            arrayOf(since.toString(), limit.toString())
        ).use { c ->
            while (c.moveToNext()) out += SharedRow(c.getString(0), c.getDouble(1), c.getDouble(2), c.getDouble(3), c.getInt(4), c.getLong(5))
        }
        out
    }.getOrDefault(emptyList())

    @Synchronized
    fun dirtyCells(since: Long, limit: Int): List<SharedRow> = runCatching {
        val out = ArrayList<SharedRow>()
        readableDatabase.rawQuery(
            "SELECT k, speed, n, updated FROM cells WHERE updated > ? ORDER BY updated LIMIT ?",
            arrayOf(since.toString(), limit.toString())
        ).use { c ->
            while (c.moveToNext()) out += SharedRow(c.getString(0), 0.0, 0.0, c.getDouble(1), c.getInt(2), c.getLong(3))
        }
        out
    }.getOrDefault(emptyList())

    /** Вливает строку из общей базы в свою (светофоры). */
    @Synchronized
    fun mergeStop(r: SharedRow) {
        runCatching {
            val db = writableDatabase
            var mine: SharedRow? = null
            db.rawQuery("SELECT lat, lon, wait, n, updated FROM stops WHERE k = ?", arrayOf(r.key)).use { c ->
                if (c.moveToFirst()) mine = SharedRow(r.key, c.getDouble(0), c.getDouble(1), c.getDouble(2), c.getInt(3), c.getLong(4))
            }
            val m = mine?.let { combineRows(it, r) } ?: r
            val v = ContentValues().apply {
                put("k", m.key); put("lat", m.lat); put("lon", m.lon); put("wait", m.value); put("n", m.n); put("updated", m.updated)
            }
            db.insertWithOnConflict("stops", null, v, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    /** Вливает строку из общей базы в свою (скорости). */
    @Synchronized
    fun mergeCell(r: SharedRow) {
        runCatching {
            val db = writableDatabase
            var mine: SharedRow? = null
            db.rawQuery("SELECT speed, n, updated FROM cells WHERE k = ?", arrayOf(r.key)).use { c ->
                if (c.moveToFirst()) mine = SharedRow(r.key, 0.0, 0.0, c.getDouble(0), c.getInt(1), c.getLong(2))
            }
            val m = mine?.let { combineRows(it, r) } ?: r
            val v = ContentValues().apply { put("k", m.key); put("speed", m.value); put("n", m.n); put("updated", m.updated) }
            db.insertWithOnConflict("cells", null, v, SQLiteDatabase.CONFLICT_REPLACE)
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
        @Volatile private var instance: SpeedStore? = null

        /** Одна база на всё приложение: экран карты, фоновый сбор и синхронизация пишут в неё по очереди. */
        fun shared(context: Context): SpeedStore =
            instance ?: synchronized(this) { instance ?: SpeedStore(context.applicationContext).also { instance = it } }

        private const val REPLACE_MS = 14L * 24 * 3600 * 1000
        private const val EXPIRE_MS = 28L * 24 * 3600 * 1000

        private const val DANGER_TABLE =
            "CREATE TABLE IF NOT EXISTS danger (k TEXT PRIMARY KEY, lat REAL NOT NULL, lon REAL NOT NULL, n INTEGER NOT NULL, updated INTEGER NOT NULL)"

        private const val OBS_TABLE =
            "CREATE TABLE IF NOT EXISTS stop_obs (id INTEGER PRIMARY KEY AUTOINCREMENT, k TEXT NOT NULL, wait REAL NOT NULL, at INTEGER NOT NULL)"

        private const val STOPS_TABLE =
            "CREATE TABLE IF NOT EXISTS stops (k TEXT PRIMARY KEY, lat REAL NOT NULL, lon REAL NOT NULL, " +
                "wait REAL NOT NULL, n INTEGER NOT NULL, updated INTEGER NOT NULL)"

        fun bucketOf(atMs: Long): Int {
            val cal = Calendar.getInstance().apply { timeInMillis = atMs }
            return (cal.get(Calendar.DAY_OF_WEEK) - 1) * 24 + cal.get(Calendar.HOUR_OF_DAY)
        }

        /** Ключ места остановки (квадрат около 33 × 28 м) и часа недели. */
        fun stopKey(lat: Double, lon: Double, atMs: Long): String =
            "${floor(lat / 0.0003).toLong()}:${floor(lon / 0.0005).toLong()}:${bucketOf(atMs)}"

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

/** Строка для обмена: ключ, положение (для светофоров), значение (секунды ожидания или скорость), число наблюдений, время. */
class SharedRow(val key: String, val lat: Double, val lon: Double, val value: Double, val n: Int, val updated: Long)

/**
 * Объединяет две записи об одном месте. Если одна заметно новее (больше 14 дней разницы), берётся она целиком,
 * иначе значения усредняются с весом по числу наблюдений.
 */
fun combineRows(a: SharedRow, b: SharedRow): SharedRow {
    val newer = if (a.updated >= b.updated) a else b
    val older = if (newer === a) b else a
    if (newer.updated - older.updated >= 14L * 24 * 3600 * 1000) return newer
    val total = a.n + b.n
    val w = if (total > 0) a.n.toDouble() / total else 0.5
    return SharedRow(
        key = a.key,
        lat = a.lat * w + b.lat * (1 - w),
        lon = a.lon * w + b.lon * (1 - w),
        value = a.value * w + b.value * (1 - w),
        n = minOf(total, 20),
        updated = newer.updated
    )
}

/** Место, где обычно стоят, и сколько. */
class StopSpot(val position: LatLon, val bucket: Int, val waitSec: Double)

/**
 * Замечает остановки: стоял не меньше 3 секунд, потом поехал. Дольше 5 минут не считаем:
 * это уже парковка или разговор, а не светофор.
 */
/** Запись в базу идёт в отдельном потоке, чтобы не тормозить карту. */
private val dbWriter = java.util.concurrent.Executors.newSingleThreadExecutor()

class StopRecorder(private val store: SpeedStore) {
    private var moved = false
    private var stoppedAt = 0L
    private var lat = 0.0
    private var lon = 0.0

    fun add(here: LatLon, speedMps: Double, nowMs: Long) {
        if (speedMps < 0.8) {
            if (stoppedAt == 0L) {
                stoppedAt = nowMs
                lat = here.lat
                lon = here.lon
            }
        } else if (speedMps > 2.0) {
            if (stoppedAt != 0L && moved) {
                // Стоянку в самом начале пути (пока ещё не поехали) не считаем светофором
                val wait = (nowMs - stoppedAt) / 1000.0
                val la = lat; val lo = lon; val st = stoppedAt
                if (wait in 3.0..300.0) dbWriter.execute { runCatching { store.recordStop(la, lo, wait, st) } }
            }
            stoppedAt = 0L
            moved = true
        }
    }

    fun reset() {
        stoppedAt = 0L
        moved = false
    }
}

/** Собирает скорости во время езды: на каждый квадрат — одна средняя за проезд. */
class SpeedRecorder(private val store: SpeedStore) {
    private var cell: String? = null
    private var sum = 0.0
    private var count = 0
    private var at = 0L

    fun add(lat: Double, lon: Double, speedMps: Double, nowMs: Long) {
        // Стоянки считаются отдельно (StopRecorder), здесь только скорость в движении
        if (speedMps < 1.0) return
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
        if (k != null && count >= 3) {
            val avg = sum / count; val t = at
            dbWriter.execute { runCatching { store.record(k, avg, t) } }
        }
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
    val arrival = DoubleArray(route.points.size)
    for (i in 1 until route.points.size) {
        val a = route.points[i - 1]
        val b = route.points[i]
        val len = distanceM(a, b)
        val at = departMs + (t * 1000).toLong()
        val key = SpeedStore.key((a.lat + b.lat) / 2, (a.lon + b.lon) / 2, at)
        val v = if (cache.containsKey(key)) cache[key] else store.expected(key, at).also { cache[key] = it }
        t += len / (v ?: baseline).coerceIn(1.5, 40.0)
        arrival[i] = t
    }
    return t + stopDelaySec(route, store, departMs, arrival)
}

/** Сколько времени в сумме обычно уходит на стоянки (светофоры, заторы) на этом маршруте в это время. */
private fun stopDelaySec(route: RouteOption, store: SpeedStore, departMs: Long, arrival: DoubleArray): Double {
    val pts = route.points
    var minLat = Double.MAX_VALUE; var maxLat = -Double.MAX_VALUE
    var minLon = Double.MAX_VALUE; var maxLon = -Double.MAX_VALUE
    for (p in pts) {
        if (p.lat < minLat) minLat = p.lat
        if (p.lat > maxLat) maxLat = p.lat
        if (p.lon < minLon) minLon = p.lon
        if (p.lon > maxLon) maxLon = p.lon
    }
    val spots = store.stopsIn(minLat - 0.001, maxLat + 0.001, minLon - 0.002, maxLon + 0.002, departMs)
    if (spots.isEmpty()) return 0.0
    // Для каждой остановки ищем ближайшую точку маршрута (не дальше 25 м) и смотрим, совпадает ли час недели
    val waitAt = HashMap<Int, Double>()
    for (spot in spots) {
        var best = -1
        var bestD = 25.0
        for (i in pts.indices) {
            val d = distanceM(pts[i], spot.position)
            if (d < bestD) { bestD = d; best = i }
        }
        if (best < 0) continue
        val at = departMs + (arrival[best] * 1000).toLong()
        if (SpeedStore.bucketOf(at) != spot.bucket) continue
        waitAt[best] = maxOf(waitAt[best] ?: 0.0, spot.waitSec)
    }
    // Соседние точки одного и того же светофора считаем один раз (в пределах 40 м берём наибольшую)
    var total = 0.0
    var lastCum = -1e9
    var lastWait = 0.0
    for (idx in waitAt.keys.sorted()) {
        val w = waitAt.getValue(idx)
        if (route.cumM[idx] - lastCum < 40.0) {
            if (w > lastWait) { total += w - lastWait; lastWait = w }
        } else {
            total += w
            lastWait = w
        }
        lastCum = route.cumM[idx]
    }
    return total
}
