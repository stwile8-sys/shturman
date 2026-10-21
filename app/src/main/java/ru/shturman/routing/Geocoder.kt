package ru.shturman.routing

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Адрес или место, найденное через поиск. */
data class Place(val title: String, val subtitle: String, val position: LatLon)

/** Поиск адресов: бесплатный сервис Photon на данных OpenStreetMap (с подсказками по мере набора). */
object Geocoder {
    /** Псков: если своё положение неизвестно, результаты рядом с ним поднимаются выше. */
    val PSKOV = LatLon(57.8136, 28.3496)

    /** Обычный поиск (как раньше): умный разбор запроса — в [SmartSearch]. */
    suspend fun search(query: String): List<Place> = SmartSearch.search(query, null).places

    /**
     * Один запрос к Photon. [near] — вокруг какой точки искать в первую очередь, [tags] — только места
     * этих видов (например "amenity:fuel"), [fallbackTitle] — название для мест без имени (безымянная АЗС).
     */
    suspend fun text(
        query: String,
        near: LatLon = PSKOV,
        tags: List<String> = emptyList(),
        limit: Int = 10,
        fallbackTitle: String? = null
    ): List<Place> = withRetry(attempts = 2) {
        val q = URLEncoder.encode(query.trim().take(150), "UTF-8")
        val filter = tags.joinToString("") { "&osm_tag=" + URLEncoder.encode(it, "UTF-8") }
        fetch("https://photon.komoot.io/api/?q=$q&limit=$limit&lat=${near.lat}&lon=${near.lon}$filter", fallbackTitle)
    }

    /** Ближайшие места нужного вида вокруг точки (заправки, аптеки…): обратный поиск с фильтром. */
    suspend fun nearby(
        near: LatLon,
        tags: List<String>,
        radiusKm: Int = 15,
        limit: Int = 10,
        fallbackTitle: String? = null
    ): List<Place> = withRetry(attempts = 2) {
        val filter = tags.joinToString("") { "&osm_tag=" + URLEncoder.encode(it, "UTF-8") }
        fetch("https://photon.komoot.io/reverse?lat=${near.lat}&lon=${near.lon}&radius=$radiusKm&limit=$limit$filter", fallbackTitle)
    }

    private suspend fun fetch(address: String, fallbackTitle: String?): List<Place> = withContext(Dispatchers.IO) {
        val conn = URL(address).openConnection() as HttpURLConnection
        val text = try {
            conn.connectTimeout = 8000
            conn.readTimeout = 12000
            conn.setRequestProperty("User-Agent", "Shturman/0.1 (Android)")
            if (conn.responseCode !in 200..299) throw IOException("Поиск недоступен (${conn.responseCode})")
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
        parse(text, fallbackTitle)
    }

    internal fun parse(text: String, fallbackTitle: String? = null): List<Place> {
        val features = JSONObject(text).optJSONArray("features") ?: return emptyList()
        val out = ArrayList<Place>()
        for (i in 0 until features.length()) {
            val f = features.getJSONObject(i)
            val coords = f.optJSONObject("geometry")?.optJSONArray("coordinates") ?: continue
            val p = f.optJSONObject("properties") ?: continue

            val name = p.optString("name")
            val street = p.optString("street")
            val house = p.optString("housenumber")
            val streetLine = listOf(street, house).filter { it.isNotEmpty() }.joinToString(", ")

            val title = when {
                name.isNotEmpty() -> name
                fallbackTitle != null -> fallbackTitle
                streetLine.isNotEmpty() -> streetLine
                else -> continue
            }
            val subtitle = listOf(
                if (title != streetLine) streetLine else "",
                p.optString("city").ifEmpty { p.optString("district") }.ifEmpty { p.optString("county") },
                p.optString("state")
            ).filter { it.isNotEmpty() && it != title }.distinct().joinToString(", ")

            out.add(Place(title, subtitle, LatLon(coords.getDouble(1), coords.getDouble(0))))
        }
        return out
    }
}
