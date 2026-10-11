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

    suspend fun search(query: String): List<Place> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query, "UTF-8")
        // Результаты рядом с Псковом поднимаются выше
        val url = URL("https://photon.komoot.io/api/?q=$q&limit=8&lat=57.8136&lon=28.3496")

        val conn = url.openConnection() as HttpURLConnection
        val text = try {
            conn.connectTimeout = 10000
            conn.readTimeout = 15000
            conn.setRequestProperty("User-Agent", "Shturman/0.1 (Android)")
            if (conn.responseCode !in 200..299) throw IOException("Поиск недоступен (${conn.responseCode})")
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }

        val features = JSONObject(text).getJSONArray("features")
        val out = ArrayList<Place>()
        for (i in 0 until features.length()) {
            val f = features.getJSONObject(i)
            val coords = f.getJSONObject("geometry").getJSONArray("coordinates")
            val p = f.getJSONObject("properties")

            val name = p.optString("name")
            val street = p.optString("street")
            val house = p.optString("housenumber")
            val streetLine = listOf(street, house).filter { it.isNotEmpty() }.joinToString(", ")

            val title = when {
                name.isNotEmpty() -> name
                streetLine.isNotEmpty() -> streetLine
                else -> continue
            }
            val subtitle = listOf(
                if (name.isNotEmpty()) streetLine else "",
                p.optString("city").ifEmpty { p.optString("county") },
                p.optString("state")
            ).filter { it.isNotEmpty() && it != title }.distinct().joinToString(", ")

            out.add(Place(title, subtitle, LatLon(coords.getDouble(1), coords.getDouble(0))))
        }
        out
    }
}
