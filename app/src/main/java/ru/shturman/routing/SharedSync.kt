package ru.shturman.routing

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

/**
 * Общая база Штурмана в Firestore: сюда уходят только обезличенные цифры (квадрат карты, час недели,
 * скорость или время ожидания), без аккаунта и без маршрутов. Остальные пользователи подтягивают их себе.
 */
class SharedSync(context: Context, private val store: SpeedStore) {
    private val prefs = context.getSharedPreferences("sync", Context.MODE_PRIVATE)
    private val db get() = FirebaseFirestore.getInstance()
    companion object {
        /** Две синхронизации сразу (автоматическая, фоновая и по кнопке) не запускаются: вторая ждёт первую. */
        private val lock = kotlinx.coroutines.sync.Mutex()
        private const val HOUR = 3600_000L
        /** Глубже этого в общую базу не заглядываем: старше 28 дней данные всё равно забываются. */
        private const val PULL_MAX_AGE_MS = 28L * 24 * HOUR

        /**
         * Остановка для общей базы: только если проездов не меньше двух (по одному можно угадать чужую поездку),
         * а место — центр квадрата ключа (около 33 × 28 м), без точных координат проезда.
         */
        internal fun shareableStop(r: SharedRow): SharedRow? {
            if (r.n < 2) return null
            val p = r.key.removePrefix("s").split(':')
            if (p.size != 3) return null
            val a = p[0].toLongOrNull() ?: return null
            val b = p[1].toLongOrNull() ?: return null
            return SharedRow(r.key, (a + 0.5) * 0.0003, (b + 0.5) * 0.0005, r.value, r.n, r.updated)
        }
    }

    /** Обезличенные данные о дорогах отправляют все пользователи (выключателя нет). */
    val share: Boolean get() = true

    val lastSyncAt: Long get() = prefs.getLong("last_sync", 0L)

    /** Отправляет свои новые данные (если разрешено) и забирает чужие. Возвращает короткий итог. */
    suspend fun sync(): String {
        // Без входа в аккаунт общая база не пускает: не тратим время и сеть
        if (com.google.firebase.auth.FirebaseAuth.getInstance().currentUser == null) return "Не вышло: нужен вход в аккаунт"
        return lock.withLock { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { syncLocked() } }
    }

    private suspend fun syncLocked(): String {
        val started = System.currentTimeMillis()
        var pushed = 0
        if (share) {
            pushed += push("stops", { store.dirtyStops(prefs.getLong("push_stops", 0L), 300) }, "push_stops") { shareableStop(it) }
            // ячейку с одним проездом не отправляем: по ней можно угадать чужую поездку; уйдёт, когда проедут ещё раз
            pushed += push("cells", { store.dirtyCells(prefs.getLong("push_cells", 0L), 300) }, "push_cells") { it.takeIf { r -> r.n >= 2 } }
        }
        val pulled = pull("stops", "pull_stops") + pull("cells", "pull_cells")
        val result = "Отправлено $pushed, получено $pulled"
        prefs.edit().putLong("last_sync", started).putString("last_result", result).apply()
        return result
    }

    /** Итог последней синхронизации («Отправлено 12, получено 340») или null, если её ещё не было. */
    val lastResult: String? get() = prefs.getString("last_result", null)

    /** prepare: что отправить вместо строки (null — не отправлять). */
    private suspend fun push(collection: String, rows: () -> List<SharedRow>, mark: String, prepare: (SharedRow) -> SharedRow?): Int {
        val list = rows()
        var sent = 0
        for (r0 in list) {
            val r1 = prepare(r0) ?: continue
            // время в общей базе — с точностью до часа, без минут и секунд проезда
            val r = SharedRow(r1.key, r1.lat, r1.lon, r1.value, r1.n, r1.updated / HOUR * HOUR)
            val ref = db.collection(collection).document(r.key)
            db.runTransaction { tx ->
                val snap = tx.get(ref)
                val mine = r
                val merged = if (snap.exists()) {
                    val remote = SharedRow(
                        r.key,
                        snap.getDouble("lat") ?: 0.0, snap.getDouble("lon") ?: 0.0,
                        snap.getDouble("value") ?: 0.0, (snap.getLong("n") ?: 1L).toInt(),
                        snap.getLong("updated") ?: 0L
                    )
                    combineRows(remote, mine)
                } else mine
                tx.set(
                    ref,
                    mapOf(
                        "lat" to merged.lat, "lon" to merged.lon, "value" to merged.value,
                        "n" to merged.n.toLong(), "updated" to merged.updated
                    )
                )
                null
            }.await()
            sent++
        }
        if (list.isNotEmpty()) prefs.edit().putLong(mark, list.maxOf { it.updated }).apply()
        return sent
    }

    private suspend fun pull(collection: String, mark: String): Int {
        var count = 0
        // время в общей базе округлено до часа: берём с запасом в час, повторы при вливании отсекаются
        var since = (prefs.getLong(mark, 0L) - HOUR).coerceAtLeast(0L)
        since = maxOf(since, System.currentTimeMillis() - PULL_MAX_AGE_MS)
        while (true) {
            val snap = db.collection(collection)
                .whereGreaterThan("updated", since)
                .orderBy("updated", Query.Direction.ASCENDING)
                .limit(500)
                .get().await()
            if (snap.isEmpty) break
            val page = ArrayList<SharedRow>(snap.size())
            for (d in snap.documents) {
                val row = SharedRow(
                    d.id,
                    d.getDouble("lat") ?: 0.0, d.getDouble("lon") ?: 0.0,
                    d.getDouble("value") ?: continue, (d.getLong("n") ?: 1L).toInt(),
                    d.getLong("updated") ?: continue
                )
                page += row
                since = maxOf(since, row.updated)
            }
            // даже если строки страницы оказались неполными, идём дальше, а не по кругу
            snap.documents.lastOrNull()?.getLong("updated")?.let { since = maxOf(since, it) }
            // вся страница — одной записью в базу
            store.mergeAll(page, stops = collection == "stops")
            count += page.size
            prefs.edit().putLong(mark, since).apply()
            if (snap.size() < 500) break
        }
        return count
    }
}
