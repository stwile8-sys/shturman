package ru.shturman.routing

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Камеры скорости: из OpenStreetMap (обновляются раз в неделю) плюс добавленные вручную.
 * Файлы лежат в памяти телефона.
 */
class Cameras(context: Context) {
    private val osmFile = File(context.filesDir, "cameras_osm.json")
    private val manualFile = File(context.filesDir, "cameras_manual.json")

    fun load(): List<LatLon> = read(osmFile) + read(manualFile)

    fun addManual(p: LatLon) {
        val list = read(manualFile) + p
        write(manualFile, list)
    }

    /** Раз в неделю подтягивает камеры из OSM. Возвращает true, если список обновился. */
    suspend fun updateIfStale(): Boolean = withContext(Dispatchers.IO) {
        if (osmFile.exists() && System.currentTimeMillis() - osmFile.lastModified() < WEEK_MS) return@withContext false
        try {
            val query = "[out:json][timeout:60];node[\"highway\"=\"speed_camera\"](55.6,27.0,59.3,31.6);out;"
            val conn = (URL("https://overpass-api.de/api/interpreter?data=" + URLEncoder.encode(query, "UTF-8"))
                .openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 70_000
            }
            val text = try {
                if (conn.responseCode != 200) return@withContext false
                conn.inputStream.use { it.readBytes().decodeToString() }
            } finally {
                conn.disconnect()
            }
            val elements = JSONObject(text).getJSONArray("elements")
            val list = ArrayList<LatLon>()
            for (i in 0 until elements.length()) {
                val e = elements.getJSONObject(i)
                list += LatLon(e.getDouble("lat"), e.getDouble("lon"))
            }
            write(osmFile, list)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun read(f: File): List<LatLon> = runCatching {
        if (!f.exists()) return emptyList()
        val arr = JSONArray(f.readText())
        List(arr.length()) { i -> arr.getJSONArray(i).let { LatLon(it.getDouble(0), it.getDouble(1)) } }
    }.getOrDefault(emptyList())

    private fun write(f: File, list: List<LatLon>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONArray().put(it.lat).put(it.lon)) }
        f.writeText(arr.toString())
    }

    private companion object {
        const val WEEK_MS = 7L * 24 * 3600 * 1000
    }
}

/** Камеры, стоящие на маршруте (не дальше 35 м от линии): расстояние от начала маршрута, по возрастанию. */
fun camerasOnRoute(route: RouteOption, cameras: List<LatLon>): List<Double> {
    val result = ArrayList<Double>()
    for (c in cameras) {
        var best = -1
        var bestD = 35.0
        for (i in route.points.indices) {
            val d = distanceM(route.points[i], c)
            if (d < bestD) {
                bestD = d
                best = i
            }
        }
        if (best >= 0) result += route.cumM[best]
    }
    return result.sorted()
}

fun camerasGeoJson(list: List<LatLon>): String {
    val sb = StringBuilder("""{"type":"FeatureCollection","features":[""")
    list.forEachIndexed { i, p ->
        if (i > 0) sb.append(',')
        sb.append(String.format(java.util.Locale.US,
            """{"type":"Feature","properties":{},"geometry":{"type":"Point","coordinates":[%.6f,%.6f]}}""", p.lon, p.lat))
    }
    return sb.append("]}").toString()
}

/** Камеры, стоящие не дальше 35 м от линии, в порядке их появления вдоль неё. */
fun camerasNear(points: List<LatLon>, cameras: List<LatLon>): List<LatLon> {
    if (points.isEmpty()) return emptyList()
    val found = ArrayList<Pair<Int, LatLon>>()
    for (c in cameras) {
        var best = -1
        var bestD = 35.0
        for (i in points.indices) {
            val d = distanceM(points[i], c)
            if (d < bestD) { bestD = d; best = i }
        }
        if (best >= 0) found += best to c
    }
    return found.sortedBy { it.first }.map { it.second }
}
