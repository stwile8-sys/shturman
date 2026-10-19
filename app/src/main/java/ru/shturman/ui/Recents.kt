package ru.shturman.ui

import android.content.Context
import ru.shturman.routing.LatLon
import ru.shturman.routing.Place
import ru.shturman.routing.distanceM

/**
 * Недавние места из поиска: последние 5 выбранных адресов. Хранятся только на телефоне
 * (отдельный файл, в резервную копию и на сервер не попадают).
 */
object Recents {
    private const val FILE = "recents"
    private const val KEY = "places"
    private const val MAX = 5

    fun load(context: Context): List<Place> = runCatching {
        val arr = org.json.JSONArray(context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Place(o.getString("t"), o.optString("s"), LatLon(o.getDouble("a"), o.getDouble("o")))
        }
    }.getOrDefault(emptyList())

    fun add(context: Context, place: Place) {
        runCatching {
            // то же место (ближе 50 м с тем же названием) поднимается наверх, а не повторяется
            val rest = load(context).filterNot { it.title == place.title && distanceM(it.position, place.position) < 50.0 }
            val list = (listOf(place) + rest).take(MAX)
            val arr = org.json.JSONArray()
            for (p in list) {
                arr.put(org.json.JSONObject().put("t", p.title).put("s", p.subtitle).put("a", p.position.lat).put("o", p.position.lon))
            }
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
        }
    }
}
