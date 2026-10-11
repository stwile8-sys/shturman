package ru.shturman.routing

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

/**
 * Общая база Штурмана в Firestore: сюда уходят только обезличенные цифры (квадрат карты, час недели,
 * скорость или время ожидания), без аккаунта и без маршрутов. Остальные пользователи подтягивают их себе.
 */
class SharedSync(context: Context, private val store: SpeedStore) {
    private val prefs = context.getSharedPreferences("sync", Context.MODE_PRIVATE)
    private val db get() = FirebaseFirestore.getInstance()

    var share: Boolean
        get() = prefs.getBoolean("share", true)
        set(v) { prefs.edit().putBoolean("share", v).apply() }

    val lastSyncAt: Long get() = prefs.getLong("last_sync", 0L)

    /** Отправляет свои новые данные (если разрешено) и забирает чужие. Возвращает короткий итог. */
    suspend fun sync(): String {
        val started = System.currentTimeMillis()
        var pushed = 0
        if (share) {
            pushed += push("stops", { store.dirtyStops(prefs.getLong("push_stops", 0L), 300) }, "push_stops")
            pushed += push("cells", { store.dirtyCells(prefs.getLong("push_cells", 0L), 300) }, "push_cells")
        }
        val pulled = pull("stops", "pull_stops") + pull("cells", "pull_cells")
        prefs.edit().putLong("last_sync", started).apply()
        return "Отправлено $pushed, получено $pulled"
    }

    private suspend fun push(collection: String, rows: () -> List<SharedRow>, mark: String): Int {
        val list = rows()
        for (r in list) {
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
        }
        if (list.isNotEmpty()) prefs.edit().putLong(mark, list.maxOf { it.updated }).apply()
        return list.size
    }

    private suspend fun pull(collection: String, mark: String): Int {
        var count = 0
        var since = prefs.getLong(mark, 0L)
        while (true) {
            val snap = db.collection(collection)
                .whereGreaterThan("updated", since)
                .orderBy("updated", Query.Direction.ASCENDING)
                .limit(500)
                .get().await()
            if (snap.isEmpty) break
            for (d in snap.documents) {
                val row = SharedRow(
                    d.id,
                    d.getDouble("lat") ?: 0.0, d.getDouble("lon") ?: 0.0,
                    d.getDouble("value") ?: continue, (d.getLong("n") ?: 1L).toInt(),
                    d.getLong("updated") ?: continue
                )
                if (collection == "stops") store.mergeStop(row) else store.mergeCell(row)
                since = maxOf(since, row.updated)
                count++
            }
            prefs.edit().putLong(mark, since).apply()
            if (snap.size() < 500) break
        }
        return count
    }
}
